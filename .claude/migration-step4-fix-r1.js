export const meta = {
  name: 'step4-fix-round1',
  description: 'Fix Round 1 compilation errors — namespace updates and JMS pattern fixes',
  phases: [
    { title: 'Fix OrderServiceMDB', detail: 'Update javax.* → jakarta.*, fix @Incoming pattern' },
    { title: 'Fix InventoryNotificationMDB', detail: 'SmallRye @Incoming setup, transaction handling' },
    { title: 'Fix OrderService', detail: 'Remove audit logging references or stub them' },
    { title: 'Fix DataBaseMigrationStartup', detail: 'Replace @TransactionManagement with Quarkus equivalent' },
    { title: 'Fix NonCatalogLogger', detail: 'WebLogic compat shim — remove or adapt' },
    { title: 'Review & Apply', detail: 'Adversarial review and confirmed fixes' },
  ],
}

phase('Fix OrderServiceMDB')

// Core issue: still using javax.ejb, javax.jms
const fixMDB = await agent(
  `Fix OrderServiceMDB.java compilation errors.

Read src/main/java/com/redhat/coolstore/service/OrderServiceMDB.java

Errors:
- Lines 3-9: javax.ejb and javax.jms packages do not exist (should be jakarta.*)
- Line 14: @MessageDriven not found (should be @Incoming from SmallRye)
- Lines 15-17: @ActivationConfigProperty (EJB config) should be removed, use application.properties instead
- Lines 20,23: @Inject missing (add jakarta.inject.Inject import)
- Line 26: onMessage() method signature wrong for Incoming pattern
- Lines 27,29,31,32: Message, TextMessage classes missing (jakarta.jms.*)
- Line 42: JMSException missing (jakarta.jms.JMSException)

Fix strategy per RULEBOOK:
1. Change @MessageDriven to @ApplicationScoped
2. Add @Incoming("orders") annotation
3. Remove @ActivationConfigProperty lines
4. Add jakarta.inject.Inject import
5. Change method signature: onMessage(String message) with @Incoming
6. Update all javax.jms.* imports to jakarta.jms.*
7. Update all javax.ejb.* imports to jakarta.ejb.*

Write the corrected OrderServiceMDB.java:`,
  {
    label: 'fix:OrderServiceMDB',
    phase: 'Fix OrderServiceMDB',
    model: 'haiku',
  }
)

phase('Fix InventoryNotificationMDB')

const fixInventoryMDB = await agent(
  `Fix InventoryNotificationMDB.java compilation errors.

Read src/main/java/com/redhat/coolstore/service/InventoryNotificationMDB.java

Errors:
- Lines 9-12: jakarta.jms packages not found (missing dependencies in pom.xml OR wrong pattern)
- Line 32: @MessageDriven signature wrong, MessageListener not found
- Line 46: onMessage() method signature mismatch
- Lines 51,52: TextMessage not found (jakarta.jms.TextMessage)
- Line 67: JMSException not found (jakarta.jms.JMSException)

The rulebook says @MessageDriven → @Incoming (SmallRye Reactive Messaging).

Fix strategy:
1. Replace @MessageDriven with @ApplicationScoped
2. Add @Incoming("inventory-updates") annotation
3. Remove MessageListener interface implementation
4. Change method to: void processUpdate(@Payload String message) with @Transactional
5. Update all jakarta.jms.* imports (if present, they should work if pom.xml has kafka connector dependency)
6. Add SLF4J logging (Logger, LoggerFactory)

Write the corrected InventoryNotificationMDB.java:`,
    {
      label: 'fix:InventoryNotificationMDB',
      phase: 'Fix InventoryNotificationMDB',
      model: 'haiku',
    }
)

phase('Fix OrderService')

const fixOrderService = await agent(
  `Fix OrderService.java compilation errors.

Read src/main/java/com/redhat/coolstore/service/OrderService.java

Errors:
- Lines 3-5: com.enterprise.audit.logging.* packages do not exist (external library not available)
- Lines 31,40: FileSystemAuditLogger not found
- Lines 36,47: AuditLoggingException not found
- Line 37: AuditConfiguration not found
- Line 66: Order.getId() method not found

The audit logging library is not in Quarkus dependencies.

Fix strategy:
1. Comment out or remove the audit logging imports (lines 3-5)
2. Remove all FileSystemAuditLogger and AuditLoggingException references
3. Remove AuditConfiguration usage
4. Add [TODO(port): audit logging library not in dependencies — stubbed out] comment
5. Verify Order model has getId() getter (check Order.java and add if missing)
6. Leave the save() logic but remove audit wrapping

Write the corrected OrderService.java:`,
    {
      label: 'fix:OrderService',
      phase: 'Fix OrderService',
      model: 'haiku',
    }
)

phase('Fix DataBaseMigrationStartup')

const fixDBMigration = await agent(
  `Fix DataBaseMigrationStartup.java compilation errors.

Read src/main/java/com/redhat/coolstore/utils/DataBaseMigrationStartup.java

Errors:
- Lines 9-10: jakarta.ejb packages not found
- Line 20: @TransactionManagement not found
- Line 20: TransactionManagementType enum not found

In Quarkus, @Stateless/@Startup → @ApplicationScoped + Quarkus startup events.

Fix strategy:
1. Change @Stateless to @ApplicationScoped
2. Remove @TransactionManagement + TransactionManagementType (not needed in Quarkus)
3. Add @Startup and @Initialize (Quarkus startup event pattern) OR use @WebListener
4. Update all jakarta.ejb.* imports appropriately
5. Keep the database migration logic, just adapt the startup trigger

Write the corrected DataBaseMigrationStartup.java:`,
    {
      label: 'fix:DataBaseMigrationStartup',
      phase: 'Fix DataBaseMigrationStartup',
      model: 'haiku',
    }
)

phase('Fix NonCatalogLogger')

const fixLogger = await agent(
  `Fix NonCatalogLogger.java compilation error.

Read src/main/java/weblogic/i18n/logging/NonCatalogLogger.java

Error:
- Lines 11,15: Cannot assign a value to static final variable log

This is a WebLogic compat shim. In Quarkus, this pattern doesn't work.

Fix strategy:
1. Check if this file is actually used anywhere (grep: grep -r "NonCatalogLogger" src/)
2. If not used: Delete the entire file (it's just a WebLogic stub)
3. If used: Convert to a regular utility class with static getter methods, not field assignment

Most likely: just delete it. Check usage first.

Write the corrected NonCatalogLogger.java or note that it should be deleted:`,
    {
      label: 'fix:NonCatalogLogger',
      phase: 'Fix NonCatalogLogger',
      model: 'haiku',
    }
)

phase('Review & Apply')

log(`Round 1 fixes generated. Next step: adversarial review of each fix.`)
log(`Then rebuild: mvn clean compile → migration/build-output-r2.txt`)

return {
  status: 'round1-fixes-ready',
  fixCount: 5,
  nextStep: 'adversarial review + rebuild',
}
