# Web Tier Migration Report — Task Group 7 (+ TG3.1)

**Java EE 7 → Quarkus 3.8.1 migration: legacy JSP web tier → Quarkus static resources**
**Scope enforced:** only `src/main/webapp/**` (removed) and `src/main/resources/META-INF/resources/**` (created). `pom.xml`, `application.properties`, `src/main/java/**`, `src/test/**`, `README.md` untouched.
**Validation mode:** static only (no build tools available): grep checks, file existence, JSON parse, git rename-similarity, checksums.

---

## 1. Assets moved (git mv — content verbatim)

All moved from `src/main/webapp/` → `src/main/resources/META-INF/resources/` (Quarkus serves classpath `META-INF/resources/` at the web root `/`, so all root-absolute references continue to resolve unchanged):

| Source (src/main/webapp/) | Destination (…/META-INF/resources/) | Notes |
|---|---|---|
| `app/` | `app/` | AngularJS app: `app.js`, `controllers/`, `services/`, `directives/`, `routes/`, `css/`, `imgs/` |
| `partials/` | `partials/` | Angular view partials |
| `bower_components/` | `bower_components/` | 48 MB vendored libraries (angular, patternfly, jquery, keycloak 1.9.8, …) — moved **verbatim**, no rewrites |
| `coolstore.json` | `coolstore.json` | moved verbatim |
| `keycloak.json` | `keycloak.json` | moved, then edited (see §4) — only intentionally-modified moved asset |

**Integrity proof (git rename detection, staged):** every moved file resolved as a rename at **100 % similarity** except `keycloak.json` (55 % — the intentional edit in §4) and the `index.jsp → index.html` pair (98 % — the intentional conversion in §2). Nothing in `bower_components/` was rewritten.

## 2. `index.jsp` → static `index.html` conversion

`src/main/resources/META-INF/resources/index.html` (new) replaces `src/main/webapp/index.jsp` (deleted).

**JSP constructs resolved:**
- `<% request.getSession(true); %>` scriptlet (file header, 3 lines) — forced server-side HTTP session creation on page load. **Dropped**: the file is a static template shell for an AngularJS SPA; it needs no server-side session (auth is handled client-side by the vendored Keycloak JS adapter, and the backend is JAX-RS/REST). The rendered output begins at `<!doctype html>`.
- **No other JSP constructs existed** — no `<%@ page %>`/`<%@ taglib %>` directives, no `<jsp:include>`/`<%@ include %>` includes, no EL `${…}`, no JSTL. The remainder of the file was already plain HTML.
- **All asset references kept EXACTLY as-is** (`/app/app.js`, `/app/routes/routes.js`, `/app/controllers/controllers.js`, `/app/services/cart.js`, `/app/services/catalog.js`, `/app/directives/header.js`, `/app/css/coolstore.css`, all `/bower_components/...` script/link tags): they are root-absolute and resolve identically from the Quarkus web root.
- `index.jsp` contained **no reference to `health.jsp`** — nothing to drop (health is now `/q/health`, see §3).

**Byte-fidelity verified:** md5 of the rendered portion of the original JSP (`tail -n +5 index.jsp`) equals md5 of `index.html` content; the only difference is a single trailing newline added at EOF. Git agrees: rename similarity index.jsp → index.html = 98 % (exactly the removed scriptlet lines).

## 3. Deletions

| File | Reason |
|---|---|
| `src/main/webapp/index.jsp` | Converted to static `index.html` (§2) |
| `src/main/webapp/health.jsp` | Replaced by **quarkus-smallrye-health** endpoints `/q/health` + `/q/health/ready` (dependency added to pom.xml by another agent). File content was a plain-text `1` (UP response). |
| `src/main/webapp/WEB-INF/web.xml` | Legacy descriptor; contained only `<distributable/>`. Meaningless in Quarkus. |
| `src/main/webapp/WEB-INF/beans.xml` | Legacy CDI marker (`bean-discovery-mode="all"`). CDI is built into Quarkus; no descriptor needed. |
| `src/main/webapp/` tree | Removed entirely — `git rm` cleaned the now-empty `WEB-INF/` and `webapp/` directories. **Verified: `src/main/webapp` no longer exists.** |

## 4. `keycloak.json` (now at `src/main/resources/META-INF/resources/keycloak.json`)

Configures the vendored Keycloak JS adapter; the app initializes it via `new Keycloak('keycloak.json')` (`app/app.js:29`) — relative path, resolves fine from the Quarkus web root.

| Key | Before | After | Task requirement |
|---|---|---|---|
| `realm` | `"eap"` | `"eap"` (unchanged) | ✅ "eap" |
| `auth-server-url` | `"http://localhost:8081/"` | `"http://localhost:8081/realms/eap"` | ✅ dev default |
| `resource` (client-id) | `"eap-app"` | `"coolstore"` | ✅ "coolstore" |
| `ssl-required` | `"external"` | unchanged | — |
| `public-client` | `true` | unchanged | — |
| `confidential-port` | `0` | unchanged | — |

JSON validity verified (`python3 -m json.tool`).

**Known limitation (runbook item — per task):** a static JSON file cannot substitute environment variables, so `auth-server-url` is pinned to the dev default `http://localhost:8081/realms/eap`. Runtime override requires either (a) editing this file at deploy time (e.g. `envsubst`/`sed` in the CI pipeline or container entrypoint), or (b) an env-aware serving strategy (e.g. a tiny endpoint that serves a generated config — out of scope). The **backend** quarkus-oidc `auth-server-url` *is* env-injected via `COOLSTORE_OIDC_AUTH_SERVER_URL` (other agent); for in-cluster deploys this frontend file must be updated at deploy time to point at the cluster-internal auth-server URL.

## 5. Verification results (all PASS)

1. `grep -rn "\.jsp\|WEB-INF" src/main/resources/META-INF/resources/ --include="*.html" --include="*.js" --include="*.json" | grep -v bower_components` → **0 matches**.
2. Broader sweep over **all** files in `app/` and `partials/` (any extension) → **0 matches**.
3. `index.html` contains no `health` reference (health.jsp was never referenced) → **PASS**.
4. File count: **2514** files under `META-INF/resources/` = 2513 moved (2517 originally tracked − 4 deleted) + 1 new `index.html` → exact match, nothing lost.
5. `src/main/webapp` does **not** exist → **PASS**.
6. All changes staged via `git mv` / `git rm` / `git add -A src/main/resources/META-INF/resources` (path-scoped so other agents' unstaged changes to `pom.xml`, `src/main/java/**`, `application.properties` were **not** staged or touched).

## 6. Risks / known limitations

1. **🔴 Keycloak adapter v1.9.8 realm-URL composition — verify at first runtime.** The vendored adapter (`bower_components/keycloak` 1.9.8) builds its realm URL as `getRealmUrl() = authServerUrl + '/realms/' + realm` (`bower_components/keycloak/dist/keycloak.js:434-441`; appends `'realms/'` without extra slash when the URL ends in `/`). With `auth-server-url = "http://localhost:8081/realms/eap"` **and** `realm = "eap"` (the values mandated by this task), the adapter would compose `http://localhost:8081/realms/eap/realms/eap` (double path) → login redirects would 404. **Fix if observed:** set `"auth-server-url": "http://localhost:8081"` (server root — modern Keycloak 17+ serves realms at `/realms/<realm>` with no `/auth` context) so the adapter composes the correct `http://localhost:8081/realms/eap`. Alternative: upgrade the vendored keycloak-js to 18+ (which takes a realm URL directly) — out of scope here because vendored bower content must not be rewritten.
2. **🟠 Static `keycloak.json` has no env substitution** — deploy-time edit required for any non-dev auth server (see §4). Backend OIDC is env-driven via `COOLSTORE_OIDC_AUTH_SERVER_URL`; the frontend is not, so dev/prod parity is manual.
3. **🟢 `health.jsp` consumers:** any external scraper of `/health.jsp` must move to `/q/health` (and `/q/health/ready`). Nothing in the web tier referenced it.
4. **🟢 No server-side session creation:** the old JSP forced a session; the static SPA no longer creates one. No web-tier code depended on it.
5. **🟢 Validation was static only** (no build tools in environment): grep/existence/JSON/git-similarity checks all pass, but a dev-mode smoke test is still needed: `index.html` served at `/`, assets load, SPA deep-link refresh behavior, and the OIDC login flow (see risk 1).

## 7. Complete list of paths touched

- **Created:** `src/main/resources/META-INF/resources/index.html` (converted from index.jsp); this report.
- **Moved (git mv, staged renames):** `app/`, `partials/`, `bower_components/`, `coolstore.json`, `keycloak.json` → `src/main/resources/META-INF/resources/`.
- **Edited after move:** `src/main/resources/META-INF/resources/keycloak.json` (realm/client-id/auth-server-url per task).
- **Deleted (git rm, staged):** `src/main/webapp/index.jsp`, `src/main/webapp/health.jsp`, `src/main/webapp/WEB-INF/web.xml`, `src/main/webapp/WEB-INF/beans.xml`; entire `src/main/webapp/` tree removed.
