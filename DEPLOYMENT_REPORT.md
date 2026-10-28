# Deployment Report: CoolStore (Quarkus)

**Stage**: MigIQ Deploy (stage 4) · **Date**: 2026-10-28 · **Result**: ✅ Deployed and verified

## Summary

`coolstore-quarkus` is running in namespace `coolstore`: **2/2 replicas ready**, health checks UP
(database + Kafka), REST API answering on the NodePort, Keycloak realm `eap` reachable.

## Target Cluster Reality (deviations from an OpenShift deployment)

The target cluster is **plain Kubernetes 1.37 (single node, arm64)** — no OpenShift APIs:

| Planned (repo manifest) | Actual (this cluster) |
|---|---|
| `oc` CLI | absent — drove everything via the K8s REST API (SA token, cluster-admin) |
| OpenShift internal registry | absent — built with **buildah in a privileged pod**, exported oci-archive, imported into node containerd (`ctr -n k8s.io images import`); `deployment.yaml` image updated to `docker.io/coolstore/coolstore-quarkus:1.0.1` |
| `route.yaml` (Route CR) | Routes API absent — `nodeport-service.yaml` (NodePort :30180) instead |
| `kafka.yaml` (Strimzi CRs) | No Strimzi operator — pre-existing plain `apache/kafka:3.7.0` KRaft Deployment reused (bootstrap `kafka:9092`) |
| `secret.yaml` template | real `coolstore-secrets` created out-of-band via API (no real creds committed) |

## Issues Found & Fixed During Deploy

1. **DB sequences** (blocker): Hibernate `validate` failed — `orders_seq`/`order_items_seq` had
   `INCREMENT 1`, entities expect 50. Fixed via psql Job: `ALTER SEQUENCE … INCREMENT BY 50`.
2. **Kafka CrashLoop** (blocker): controller quorum (`1@kafka:29093`) unreachable — Service lacked
   port 29093. Patched Service (+controller port), pod now stable (0 restarts).
3. **Flyway baseline crash** (blocker, code fix): `DataBaseMigrationStartup` called
   `flyway.baseline()` unconditionally → crash on any already-migrated DB (redeploy scenario).
   Guarded with try/catch; rebuilt image as **1.0.1** and rolled out. This is a real app defect
   worth upstreaming beyond this migration.
4. **Keycloak exposure**: Keycloak Service patched to NodePort **:30181**; backend
   `QUARKUS_OIDC_AUTH_SERVER_URL=http://10.89.0.2:30181/realms/eap` and front-end
   `keycloak.json` (patched at build time) now share one issuer URL (containerization
   Known Issues #1/#2 closed — confidential client `coolstore` verified present in realm).

## Deployed Objects (namespace `coolstore`)

- **Deployment** `coolstore-quarkus` — image `docker.io/coolstore/coolstore-quarkus:1.0.1`, 2 replicas,
  full security context (non-root 1001, RO rootfs, drop ALL), probes `/q/health/{ready,live}`
- **Service** `coolstore-quarkus` (ClusterIP :8080, ClientIP affinity) + **`coolstore-quarkus-external`** (NodePort :30180)
- **HPA** `coolstore-quarkus` (1–3 replicas, 70% CPU)
- **Secret/ConfigMap** `coolstore-secrets` / `coolstore-config` (values via API; git keeps templates)
- Reused supporting services: `postgres-0` (StatefulSet, data preserved), `kafka`, `keycloak`
- Legacy failing `coolstore` Deployment + stale services/jobs deleted

## Verification (post-deploy)

- `oc`-equivalent rollout: readyReplicas 2/2, conditions Available
- `/q/health/ready` + `/q/health/live`: **UP** (SmallRye Kafka channels `orders`, `inventory`, `orders-out` OK; DB OK)
- Flyway: schema validated, `migrate()` clean on existing history
- rgctl-identified endpoints (Order/Cart/ProductEndpoint classes): `/services/products`, `/services/orders`,
  `/services/cart/{id}` → **HTTP 200** (JAX-RS app path is `/services`); secured flows return auth errors as expected
- Front end `GET /` → 200; Keycloak `GET /realms/eap` (NodePort :30181) → 200

## Access

- App: `http://10.89.0.2:30180` (front end + `/services/*` API + `/q/health/*`)
- Keycloak: `http://10.89.0.2:30181` (realm `eap`, admin bootstrap from `coolstore-secrets`)

## Rollback

```bash
kubectl rollout undo deployment/coolstore-quarkus -n coolstore   # back to 1.0.0
```

## Notes / Follow-ups

- Endpoints require bearer tokens from realm `eap` for secured operations (public reads verified).
- In-memory carts + ClientIP affinity unchanged (containerization KI #3 still open).
- Image tag 1.0.1 is an immutable rebuild of 1.0.0 containing the Flyway guard fix — CI should adopt it.
