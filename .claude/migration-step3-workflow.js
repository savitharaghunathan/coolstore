export const meta = {
  name: 'step3-translate-all',
  description: 'Step 3: Translate all remaining coolstore files using implementer → reviewers → fixer pipeline',
  phases: [
    { title: 'Identify Queue', detail: 'Read manifest, find files without translated output' },
    { title: 'Translate', detail: 'Fan out implementers per file' },
    { title: 'Review', detail: 'Two adversarial reviewers per file, separate contexts' },
    { title: 'Fix', detail: 'Apply confirmed findings only' },
    { title: 'Report', detail: 'Burndown and cost log' },
  ],
}

// Helper: manually parse manifest from disk (mocked, pass files as args instead)
phase('Identify Queue')

// Files to translate (from manifest analysis): entries with status TODO or REROUND and no translated output
const filesToTranslate = [
  // Source models
  'src/main/java/com/redhat/coolstore/model/CatalogItemEntity.java',
  'src/main/java/com/redhat/coolstore/model/InventoryEntity.java',
  'src/main/java/com/redhat/coolstore/model/OrderItem.java',
  'src/main/java/com/redhat/coolstore/model/Promotion.java',
  'src/main/java/com/redhat/coolstore/model/ShoppingCart.java',
  'src/main/java/com/redhat/coolstore/model/ShoppingCartItem.java',
  // Persistence & Resources
  'src/main/java/com/redhat/coolstore/persistence/Resources.java',
  // REST endpoints
  'src/main/java/com/redhat/coolstore/rest/CartEndpoint.java',
  'src/main/java/com/redhat/coolstore/rest/OrderEndpoint.java',
  'src/main/java/com/redhat/coolstore/rest/ProductEndpoint.java',
  'src/main/java/com/redhat/coolstore/rest/RestApplication.java',
  // Services (some completed, some TODO)
  'src/main/java/com/redhat/coolstore/service/ShippingService.java',
  'src/main/java/com/redhat/coolstore/service/ShoppingCartService.java',
  'src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java',
  'src/main/java/com/redhat/coolstore/utils/Transformers.java',
  // Tests
  'src/test/java/com/redhat/coolstore/model/CatalogItemEntityTest.java',
  'src/test/java/com/redhat/coolstore/model/InventoryEntityTest.java',
  'src/test/java/com/redhat/coolstore/model/OrderItemTest.java',
  'src/test/java/com/redhat/coolstore/model/OrderTest.java',
  'src/test/java/com/redhat/coolstore/model/ProductTest.java',
  'src/test/java/com/redhat/coolstore/model/PromotionTest.java',
  'src/test/java/com/redhat/coolstore/model/ShoppingCartItemTest.java',
  'src/test/java/com/redhat/coolstore/model/ShoppingCartTest.java',
  'src/test/java/com/redhat/coolstore/rest/CartEndpointTest.java',
  'src/test/java/com/redhat/coolstore/rest/OrderEndpointTest.java',
  'src/test/java/com/redhat/coolstore/rest/ProductEndpointTest.java',
  'src/test/java/com/redhat/coolstore/service/CatalogServiceTest.java',
  'src/test/java/com/redhat/coolstore/service/OrderServiceMDBTest.java',
  'src/test/java/com/redhat/coolstore/service/OrderServiceTest.java',
  'src/test/java/com/redhat/coolstore/service/ProductServiceTest.java',
  'src/test/java/com/redhat/coolstore/service/PromoServiceTest.java',
  'src/test/java/com/redhat/coolstore/service/ShippingServiceTest.java',
  'src/test/java/com/redhat/coolstore/service/ShoppingCartOrderProcessorTest.java',
  'src/test/java/com/redhat/coolstore/service/ShoppingCartServiceTest.java',
  'src/test/java/com/redhat/coolstore/utils/ProducersTest.java',
  'src/test/java/com/redhat/coolstore/utils/TransformersTest.java',
  // Config
  'pom.xml',
  'src/main/resources/application.properties',
]

log(`Queue size: ${filesToTranslate.length} files`)

// Translate: one implementer per file
phase('Translate')

const implementers = await parallel(
  filesToTranslate.map(file => () =>
    agent(
      `You are a Java-to-Quarkus translator. Read the rulebook first at migration/RULEBOOK.md, then translate:

${file}

INSTRUCTIONS:
1. Read the source Java EE 7 file from src/
2. Apply all rules from the RULEBOOK
3. Apply amendments from Step 2 (transaction model, Kafka connector)
4. Write the translated Quarkus file to target path per rulebook
5. For any gap not decided in rulebook, add [TODO(port)], [BUG(port)], or [PERF(port)] marker
6. End with status trailer: [PORT STATUS: X TODOs, Y BUGs, Z PERFs]
7. Never guess about Keycloak or WebSocket config — mark as TODO(port)`,
      {
        label: `impl:${file.split('/').pop()}`,
        phase: 'Translate',
        model: 'haiku',
      }
    )
  )
)

log(`Implementers done: ${implementers.filter(Boolean).length}/${implementers.length}`)

// Review: two adversarial reviewers per file
phase('Review')

const reviews = await pipeline(
  implementers.filter(Boolean).map((impl, idx) => ({
    file: filesToTranslate[idx],
    impl,
    idx,
  })),
  async item => {
    const { file, impl, idx } = item

    const reviewPair = await parallel([
      () => agent(
        `ADVERSARIAL REVIEW 1: Assume this translation is WRONG. Find defects.

File being reviewed: ${file}

Translation (from implementer above):
${impl.substring(0, 2000)}...

Rulebook: migration/RULEBOOK.md

INSTRUCTIONS:
1. Read rulebook section on this file type
2. Find every violation: missing imports, wrong annotations, transaction handling, messaging patterns
3. Every finding MUST cite a rule section or source line
4. Assume it's wrong — be aggressive
5. Return: LIST OF FINDINGS with [RULE: ...] tags`,
        {
          label: `r1:${file.split('/').pop()}`,
          phase: 'Review',
          model: 'claude-sonnet-4-5@20250929',
        }
      ),
      () => agent(
        `ADVERSARIAL REVIEW 2: Find defects the first reviewer missed. Different lens.

File being reviewed: ${file}

Translation (from implementer above):
${impl.substring(0, 2000)}...

Rulebook: migration/RULEBOOK.md

INSTRUCTIONS:
1. Focus on different issues than Reviewer 1 (they looked at patterns/annotations; you look at refactoring gaps, test compatibility, messaging)
2. Assume it's wrong
3. Every finding MUST cite a rule or line
4. Return: LIST OF FINDINGS with [RULE: ...] tags`,
        {
          label: `r2:${file.split('/').pop()}`,
          phase: 'Review',
          model: 'claude-sonnet-4-5@20250929',
        }
      ),
    ])

    return {
      file,
      impl,
      review1: reviewPair[0] || '',
      review2: reviewPair[1] || '',
    }
  }
)

log(`Reviews done: ${reviews.filter(Boolean).length} files`)

// Fix: apply confirmed findings
phase('Fix')

const fixed = await pipeline(
  reviews.filter(Boolean),
  async item => {
    const { file, impl, review1, review2 } = item

    if (!review1 && !review2) {
      log(`${file}: no findings → output accepted`)
      return { file, status: 'no-changes', findings: 0 }
    }

    const fixAgent = await agent(
      `Apply CONFIRMED findings to this translation. Read both reviewers' findings above.

File: ${file}

Rules:
- Apply findings cited by both reviewers OR cited with explicit [RULE: X] reference
- Do NOT apply disputed findings (one reviewer only, no rule cite)
- Do NOT edit the rulebook — only the translated code
- If you cannot fix something, leave it and flag: "CAN'T FIX: <reason>"

Original implementation:
${impl.substring(0, 1500)}

Reviewer 1 findings:
${review1}

Reviewer 2 findings:
${review2}

Output the FIXED translated code:`,
      {
        label: `fix:${file.split('/').pop()}`,
        phase: 'Fix',
        model: 'haiku',
      }
    )

    return {
      file,
      status: 'fixed',
      findings: (review1.match(/\[RULE:/g) || []).length + (review2.match(/\[RULE:/g) || []).length,
      output: fixAgent,
    }
  }
)

// Report
phase('Report')

const processed = fixed.filter(Boolean).length
const totalFindings = fixed.filter(Boolean).reduce((sum, item) => sum + (item.findings || 0), 0)

log(`
=== STEP 3 BATCH COMPLETE ===
Files processed: ${processed}/${filesToTranslate.length}
Total findings reviewed & fixed: ${totalFindings}
Remaining queue: ${filesToTranslate.length - processed}
`)

return {
  status: 'complete',
  filesProcessed: processed,
  totalFindings,
  remaining: filesToTranslate.length - processed,
}
