#!/usr/bin/env bash
# Post-deploy OpenShift smoke test (migration TG9.6): health, SPA, catalog REST.
# Usage: ./scripts/openshift-smoke-test.sh https://coolstore-dev.apps.example.com
set -euo pipefail
BASE="${1:?usage: openshift-smoke-test.sh <route-url>}"
BASE="${BASE%/}"
fail=0
check() { # name url expect
  if BODY=$(curl -sf "$2") && echo "$BODY" | grep -q "$3"; then echo "PASS: $1"; else echo "FAIL: $1"; fail=1; fi
}
check "/q/health"        "$BASE/q/health" '"UP"'
check "SPA root"         "$BASE/"          "<!DOCTYPE html"
check "catalog REST"     "$BASE/services/rest/products" '"itemId"'
echo "unauthenticated cart call (expect 401):"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/services/rest/cart/test")
[ "$CODE" = "401" ] && echo "PASS: cart protected (401)" || { echo "FAIL: cart returned $CODE (expected 401)"; fail=1; }
[ $fail -eq 0 ] && echo "OPENSHIFT SMOKE TEST PASSED" || exit 1
