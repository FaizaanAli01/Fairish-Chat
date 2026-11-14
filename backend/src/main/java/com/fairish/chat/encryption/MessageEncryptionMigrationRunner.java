package com.fairish.chat.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Opt-in command-line entry point for the legacy plaintext migration.
 *
 * <p>It only exists when {@code app.encryption.migration.enabled=true} and is
 * therefore never part of a normal application start. Approved usage:</p>
 *
 * <pre>
 *   MESSAGE_ENCRYPTION_MIGRATION_ENABLED=true \
 *   MESSAGE_ENCRYPTION_MIGRATION_MODE=dry-run ./mvnw spring-boot:run
 * </pre>
 *
 * <p>The application exits after the runner completes, so {@code apply} must be
 * a deliberate second invocation after reviewing the dry-run counts.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.encryption.migration", name = "enabled", havingValue = "true")
public class MessageEncryptionMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MessageEncryptionMigrationRunner.class);

    private final MessageEncryptionMigrationService migrationService;
    private final MessageEncryptionProperties properties;

    public MessageEncryptionMigrationRunner(MessageEncryptionMigrationService migrationService,
                                            MessageEncryptionProperties properties) {
        this.migrationService = migrationService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String mode = properties.getMigration().getMode();
        boolean dryRun;
        if ("dry-run".equalsIgnoreCase(mode)) {
            dryRun = true;
        } else if ("apply".equalsIgnoreCase(mode)) {
            dryRun = false;
        } else {
            throw new IllegalStateException(
                    "app.encryption.migration.mode must be either 'dry-run' or 'apply'.");
        }

        MessageEncryptionMigrationService.MigrationReport report = migrationService.migrate(dryRun);
        if (dryRun) {
            log.warn("Migration dry run complete. Re-run with MESSAGE_ENCRYPTION_MIGRATION_MODE=apply "
                    + "after verifying the counts and taking a backup.");
        } else {
            log.info("Migration apply complete: {} message(s), {} preview(s) migrated.",
                    report.messagesMigrated(), report.previewsMigrated());
        }
    }
}
