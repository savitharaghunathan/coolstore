# Coolstore Java EE 7 → Quarkus Migration Setup

## ✅ Setup Complete

The code-migration-kit has been configured for migrating the CoolStore monolith from **Java EE 7 (JBoss EAP 7.4)** to **Quarkus 3.x with SmallRye Reactive Messaging**.

## What's Been Set Up

### 1. Kit Installation
- ✅ Cloned `code-migration-kit-with-claude-code` to `migration-kit/`
- ✅ Copied `CLAUDE.md` to repo root (operating manual for all sessions)
- ✅ Copied `settings.json` to `.claude/` (permission safeguards for the migration)

### 2. Migration Artifacts
- ✅ `migration/RULEBOOK.md` — Translation rules for this specific codebase
  - Covers: Namespace changes (javax → jakarta), EJB → CDI, ActiveMQ → SmallRye Messaging
  - Includes concrete before/after code examples
  - Maps all 51 source files to translation rules
  
- ✅ `migration/inventory.tsv` — Complete file manifest (51 Java files)
  - Categorizes by type: Entity, Service, Messaging, Test, Config
  - Flags CRITICAL paths: OrderServiceMDB, OrderServiceMDBTest, InventoryNotificationMDB, ShoppingCartOrderProcessor
  - All others marked TODO

## The Six-Step Workflow

The migration will follow the kit's structured process:

### Step 1: Map & Rules ✅ (DONE)
- Dependency map built
- Gap inventory created
- Rulebook written with SmallRye Messaging patterns

### Step 2: Stress Test (NEXT)
Use `migration-kit/prompts/01-map-and-rules.md` to:
- Validate rulebook against a pilot file (e.g., ProductService)
- Bakeoff: translate one critical file, review, iterate

### Step 3: Translate
Use `migration-kit/prompts/02-stress-test.md` to:
- Fan out translation of all 51 files
- Implementers follow rulebook; no ad-hoc decisions

### Step 4: Compile
Use `migration-kit/prompts/03-translate.md` to:
- Build Quarkus pom.xml
- Run compiler, queue errors
- Parallel fixers burn down error queue

### Step 5: Run It
Use `migration-kit/prompts/04-compile.md` to:
- Start Quarkus app
- Run hello-world smoke test

### Step 6: Match Behavior
Use `migration-kit/prompts/05-run-it.md` to:
- Run test suite against new code
- Validate parity with original behavior

## Key Migration Facts

### Messaging Architecture
- **OLD**: JMS Topics (topic/orders) via @MessageDriven + JMSContext
- **NEW**: SmallRye Reactive Messaging with @Incoming/@Channel/@Emitter
- **Files affected**: OrderServiceMDB, ShoppingCartOrderProcessor, InventoryNotificationMDB, all related tests

### EJB to CDI
- `@Stateless` services → `@ApplicationScoped` beans
- `@Stateful` → `@SessionScoped` (or remove if stateless is viable)
- `@EJB` injection → `@Inject` (already done; no changes)

### Namespace
All `javax.*` → `jakarta.*`:
- `javax.ejb` → `jakarta.ejb`
- `javax.jms` → ~~removed~~ (SmallRye Reactive Messaging replaces it)
- `javax.persistence` → `jakarta.persistence`
- `javax.ws.rs` → `jakarta.ws.rs`

### Configuration
- Delete: `web.xml`, `persistence.xml`
- Create: `application.properties` with Quarkus datasource + OIDC + messaging config

## Files & Locations

```
migration-kit/                          ← Kit source (read-only reference)
migration/
  ├── RULEBOOK.md                      ← Translation rules (authoritative)
  ├── inventory.tsv                    ← File manifest (51 files)
  └── manifest.tsv                     ← Generated after Step 1 (for Step 3 fan-out)
.claude/settings.json                  ← Permission safeguards (do not edit during loop)
CLAUDE.md                              ← Operating manual (read first in each session)
```

## Next Action

To start the migration:

1. **Review the rulebook** — Read `migration/RULEBOOK.md` to understand the patterns
2. **Start Step 2 (Stress Test)** — Open the first prompt in a new session:
   ```
   Copy migration-kit/prompts/01-map-and-rules.md to a new session
   Fill in placeholders
   Run bakeoff on ProductService (low risk, good test)
   ```
3. **Iteratively complete Steps 2–6** — The prompts guide each phase with sign-off gates

## Standing Rules (from CLAUDE.md)

1. **Rulebook is read-only in loops** — Implementers cite it; never edit during translation
2. **Queues live on disk** — Mark work done via manifest output files, not memory
3. **Sign-off gates end workflows** — Always wait for human approval before proceeding
4. **Reviewers are adversarial & separate** — Two independent reviews per file
5. **Banned operations** — No git mutate, compiler loop, or long test runs (blocked by settings.json)
6. **Recurring failures move upstream** — Fix the rule, not the instance
7. **Old code is the spec** — Run tests on both versions before classifying failures
8. **Unknown is an answer** — Conservative TODO beats stalled batch

## Token Budget Guidance

Expect:
- **Step 1 (this step)**: ~5k tokens
- **Step 2 (stress-test)**: ~20–50k tokens
- **Step 3 (translate 51 files)**: ~200–500k tokens (bulk of cost)
- **Step 4 (compile & fix)**: ~50–100k tokens
- **Steps 5–6 (test & verify)**: ~50–100k tokens

**Total estimate**: 300–750k tokens

Set a budget via Claude Code: `/config` → Token budget → Set to 1M for safety margin.

## Troubleshooting

**Q: settings.json denies my command**
A: This is the design. Never route around it. Flag the issue and wait for gate approval to edit settings.json manually.

**Q: I'm not sure what to do next**
A: Read CLAUDE.md. It defines the process. Every prompt in `migration-kit/prompts/` runs one step.

**Q: The rulebook is wrong**
A: Queue the amendment in RULEBOOK.md's "Deviation Log" section. Apply amendments between batches, not during loops.

---

**Setup completed by**: Claude Code
**Date**: 2026-08-19
**Target**: Quarkus 3.x + SmallRye Reactive Messaging
