#!/usr/bin/env bash
# CoolStore OpenShift deploy (MigIQ deploy stage) — automates DEPLOYMENT_REPORT.md §3.
# Implements the mig-deploy skill workflow: prerequisites → namespace → secrets →
# config → dependencies (with existence checks) → image build/push → app → route →
# health verification (endpoint plan §4).
#
# Usage:
#   ./scripts/deploy-openshift.sh [NAMESPACE] [IMAGE_TAG] [REGISTRY]
#   NAMESPACE defaults to coolstore, IMAGE_TAG to 1.0.0.
#   REGISTRY defaults to the OpenShift internal registry default-route.
#
# Prerequisites: oc logged in; Strimzi (AMQ Streams) operator installed;
# registry.redhat.io pull secret for the PostgreSQL image; podman or docker.
# Out-of-band secrets (never committed): coolstore-secrets, keycloak-admin.
set -euo pipefail
cd "$(dirname "$0")/.."

NAMESPACE="${1:-coolstore}"
TAG="${2:-1.0.0}"
FAIL=0

log()  { echo "[$(date +%H:%M:%S)] $*"; }
ok()   { echo "✓ $*"; }
warn() { echo "⚠ $*"; }
die()  { echo "ERROR: $*" >&2; exit 1; }

# ---------- 1. Prerequisites (fail fast per skill) ----------
command -v oc >/dev/null    || die "oc CLI not found — install and run: oc login <cluster-url>"
oc whoami >/dev/null 2>&1   || die "not logged into OpenShift — run: oc login <cluster-url>"
BUILD_CMD="$(command -v podman || command -v docker || true)"
[ -n "$BUILD_CMD" ]          || die "neither podman nor docker found (image build required)"
for f in Dockerfile deployment.yaml service.yaml route.yaml configmap.yaml \
         postgresql-statefulset.yaml kafka.yaml keycloak.yaml realm-export.json; do
  [ -f "$f" ] || die "containerization artifact missing: $f (run mig-containerize?)"
done
ok "prerequisites: logged in as $(oc whoami) @ $(oc whoami --show-server), builder: $(basename "$BUILD_CMD")"

# ---------- 2. Namespace ----------
if oc get namespace "$NAMESPACE" >/dev/null 2>&1; then
  ok "namespace '$NAMESPACE' exists"
else
  oc new-project "$NAMESPACE" >/dev/null; ok "created namespace '$NAMESPACE'"
fi
oc project "$NAMESPACE" >/dev/null

# ---------- 3. Secrets (deployment order: secrets first) ----------
if ! oc get secret coolstore-secrets >/dev/null 2>&1; then
  die "secret 'coolstore-secrets' not found. Create out-of-band (never commit values):
  oc -n $NAMESPACE create secret generic coolstore-secrets \\
    --from-literal=db-username=<user> --from-literal=db-password=<pass> \\
    --from-literal=oidc-client-secret=<rotated-secret>"
fi
grep -q "CHANGE_ME" secret.yaml && warn "secret.yaml still contains CHANGE_ME placeholders (template — skipped applying; live secret used instead)" || true
if ! oc get secret keycloak-admin >/dev/null 2>&1; then
  die "secret 'keycloak-admin' not found. Create out-of-band:
  oc -n $NAMESPACE create secret generic keycloak-admin \\
    --from-literal=KEYCLOAK_ADMIN=<admin> --from-literal=KEYCLOAK_ADMIN_PASSWORD=<pass>"
fi
ok "secrets present (coolstore-secrets, keycloak-admin)"

# ---------- 4. Non-sensitive config + Keycloak realm ----------
oc apply -f configmap.yaml >/dev/null; ok "configmap coolstore-config applied"
if ! oc get configmap keycloak-realm >/dev/null 2>&1; then
  oc create configmap keycloak-realm --from-file=realm-export.json=realm-export.json >/dev/null
fi
ok "keycloak realm ConfigMap present (realm 'eap')"

# ---------- 5. Build + push image ----------
REGISTRY="${3:-$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}' 2>/dev/null || true)}"
[ -n "$REGISTRY" ] || die "could not resolve OpenShift internal registry — pass REGISTRY as arg 3"
IMAGE="$REGISTRY/$NAMESPACE/coolstore:$TAG"
log "building image $IMAGE (multi-stage, UBI9)…"
"$BUILD_CMD" build -t "$IMAGE" -f Dockerfile .
"$BUILD_CMD" login -u "$(oc whoami)" -p "$(oc whoami -t)" "$REGISTRY" >/dev/null 2>&1
"$BUILD_CMD" push "$IMAGE"; ok "image pushed: $IMAGE"

# ---------- 6. Dependencies (existence-checked per skill step 4) ----------
oc get crd kafkas.kafka.strimzi.io >/dev/null 2>&1 \
  || die "Strimzi operator not installed — required for kafka.yaml (oc apply of Kafka/KafkaTopic CRs)"
for dep_file in kafka.yaml postgresql-statefulset.yaml keycloak.yaml; do
  log "applying dependency $dep_file …"
  oc apply -f "$dep_file" >/dev/null
done
log "waiting for dependencies to become ready (up to 5 min each)…"
oc wait --for=condition=ready pod -l name=postgresql -n "$NAMESPACE" --timeout=300s >/dev/null \
  && ok "postgresql ready" || { warn "postgresql not ready — check: oc get pods -l name=postgresql"; FAIL=1; }
oc wait --for=condition=ready pod -l name=keycloak -n "$NAMESPACE" --timeout=300s >/dev/null \
  && ok "keycloak ready" || { warn "keycloak not ready — check: oc get pods -l name=keycloak"; FAIL=1; }

# ---------- 7. Application ----------
oc apply -f deployment.yaml >/dev/null
oc apply -f service.yaml    >/dev/null
oc apply -f route.yaml      >/dev/null
oc set image deployment/coolstore coolstore="$IMAGE" >/dev/null   # retag from quay.io placeholder
ok "app manifests applied (deployment 2 replicas, service, route — image $IMAGE)"

log "waiting for rollout (up to 5 min)…"
if oc rollout status deployment/coolstore -n "$NAMESPACE" --timeout=300s; then
  ok "deployment/coolstore rolled out"
else
  oc get pods -n "$NAMESPACE"; oc logs deployment/coolstore -n "$NAMESPACE" --tail=50 || true
  die "rollout failed — see pods/logs above; rollback: oc rollout undo deployment/coolstore -n $NAMESPACE"
fi

# ---------- 8. Health verification (graph-derived endpoint plan §4) ----------
ROUTE_URL="https://$(oc get route coolstore -n "$NAMESPACE" -o jsonpath='{.spec.host}')"
log "route URL: $ROUTE_URL"
check() { # name url expect_fragment
  if BODY=$(curl -fsSk "$2" 2>/dev/null) && echo "$BODY" | grep -q "$3"; then ok "$1"; else warn "$1 FAILED (expected '$3')"; FAIL=1; fi
}
check "GET /q/health (liveness, public)"    "$ROUTE_URL/q/health" '"UP"'
check "GET /q/health/ready (readiness)"     "$ROUTE_URL/q/health/ready" '"UP"'
check "GET / (SPA root, public)"            "$ROUTE_URL/" '<!DOCTYPE html'
check "GET /services/rest/products (public)" "$ROUTE_URL/services/rest/products" '"itemId"'
CODE=$(curl -s -o /dev/null -w "%{http_code}" -k "$ROUTE_URL/services/rest/cart/smoke-test")
[ "$CODE" = "401" ] && ok "cart protected by OIDC (401 unauthenticated)" || { warn "cart returned $CODE (expected 401)"; FAIL=1; }
oc wait --for=condition=ready pod -l app=coolstore -n "$NAMESPACE" --timeout=60s >/dev/null \
  && ok "all coolstore pods Ready" || { warn "some pods not Ready"; FAIL=1; }

# ---------- 9. Summary ----------
echo "========================================="
echo "  DEPLOYMENT $( [ $FAIL -eq 0 ] && echo COMPLETE || echo 'COMPLETE WITH WARNINGS' )"
echo "========================================="
echo "Cluster:    $(oc whoami --show-server)"
echo "Namespace:  $NAMESPACE"
echo "Image:      $IMAGE"
echo "Route:      $ROUTE_URL"
echo "Pods:"; oc get pods -n "$NAMESPACE" -o wide | sed 's/^/  /'
[ $FAIL -eq 0 ] || echo "(see ⚠ warnings above; rollback: oc rollout undo deployment/coolstore -n $NAMESPACE)"
exit $FAIL
