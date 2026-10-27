#!/usr/bin/env bash
# Container smoke test (migration TG8.5): /q/health 200 + one REST round-trip in-container.
# Usage: ./scripts/container-smoke-test.sh coolstore:dev
set -euo pipefail
IMAGE="${1:?usage: container-smoke-test.sh <image>}"
CID=$(podman run -d --rm -p 18080:8080 \
  -e QUARKUS_DATASOURCE_URL="jdbc:h2:mem:smoke;DB_CLOSE_DELAY=-1" \
  -e QUARKUS_DATASOURCE_USERNAME=sa -e QUARKUS_DATASOURCE_PASSWORD= \
  -e KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  -e COOLSTORE_OIDC_AUTH_SERVER_URL=http://localhost:8081/realms/eap \
  -e COOLSTORE_OIDC_SECRET= "$IMAGE")
trap 'podman rm -f $CID >/dev/null 2>&1 || true' EXIT
for i in $(seq 1 60); do
  if curl -sf http://localhost:18080/q/health >/dev/null 2>&1; then break; fi; sleep 1
done
curl -sf http://localhost:18080/q/health | grep -q '"UP"' && echo "PASS: /q/health UP" || { echo "FAIL: /q/health"; exit 1; }
BODY=$(curl -sf http://localhost:18080/services/rest/products) && echo "$BODY" | grep -q '"itemId"' \
  && echo "PASS: /services/rest/products round-trip" || { echo "FAIL: REST round-trip"; exit 1; }
echo "SMOKE TEST PASSED"
