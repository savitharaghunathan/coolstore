# Migration Artifacts

All migration work is tracked in this directory.

## Files in this Directory

| File | Purpose | Status |
|------|---------|--------|
| **RULEBOOK.md** | Translation rules (authoritative source for all decisions) | ✅ READY |
| **PATTERNS.md** | Concrete before/after code examples for critical files | ✅ READY |
| **inventory.tsv** | Manifest of all 51 Java files with categories & status | ✅ READY |
| **manifest.tsv** | Work queue for Step 3 (generated after Step 1) | ⏳ Generated in workflow |
| **stress-test/** | Results from Step 2 bakeoff | ⏳ Phase 2 |
| **cost-log.tsv** | Token spend per phase (written at each gate) | ⏳ Phase 1+ |
| **ERRORS.md** | Compilation errors queued during Step 4 | ⏳ Phase 4 |

## Quick Links

- **Get started**: Read `../SETUP.md` (overview of the migration setup)
- **Understand the rules**: Read `RULEBOOK.md` (detailed translation rules)
- **See examples**: Read `PATTERNS.md` (before/after code for critical files)
- **Check what's being migrated**: Read `inventory.tsv` (all 51 files)

## Phase Overview

1. **Step 1: Map & Rules** ✅ (Complete)
   - Dependency map created
   - Gap inventory generated → inventory.tsv
   - Rulebook written → RULEBOOK.md
   - Critical patterns documented → PATTERNS.md

2. **Step 2: Stress Test** ⏳ (Next)
   - Bakeoff on ProductService
   - Validate rulebook against real code
   - Iterate until confident

3. **Step 3: Translate** ⏳
   - Fan out to all 51 files
   - Generate manifest.tsv
   - Implementers follow rulebook

4. **Step 4: Compile** ⏳
   - Build pom.xml
   - Parallel error fixing
   - Fill ERRORS.md

5. **Step 5: Run It** ⏳
   - Smoke test against Quarkus app

6. **Step 6: Match Behavior** ⏳
   - Run test suite
   - Validate parity

## Key Facts

- **Source**: 51 Java files (models, services, REST endpoints, tests, config)
- **Target**: Quarkus 3.x with SmallRye Reactive Messaging
- **Critical patterns**: 
  - Message producer: JMSContext + Topic → @Channel + Emitter
  - Message consumer: @MessageDriven → @Incoming method
  - Services: @Stateless → @ApplicationScoped
- **Configuration**: web.xml + persistence.xml → application.properties

## Standing Rules

1. **Rulebook is read-only in loops** (cite it, don't edit it)
2. **Queues live on disk** (mark done via file existence)
3. **Sign-off gates** (always wait for human approval)
4. **Adversarial reviewers** (two independent reviews)
5. **No git mutate, compiler loop, or long tests** (safeguards via settings.json)
6. **Recurring failures move upstream** (fix the rule)
7. **Old code is the spec** (validate against both versions)
8. **Unknown is an answer** (conservative > stalled)

---

**Setup completed**: 2026-08-19  
**Target**: Quarkus 3.x + SmallRye Reactive Messaging  
**Next step**: Read `../SETUP.md`, then run Step 2 (Stress Test)
