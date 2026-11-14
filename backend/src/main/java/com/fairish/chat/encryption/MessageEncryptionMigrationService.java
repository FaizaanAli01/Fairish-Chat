package com.fairish.chat.encryption;

import com.fairish.chat.model.Conversation;
import com.fairish.chat.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Converts legacy plaintext message records and conversation previews into
 * encrypted records.
 *
 * <p>This utility is never invoked automatically. It has an explicit dry-run
 * mode which only counts candidate documents. The apply mode rewrites one
 * document at a time, leaving unrelated fields untouched and never deleting
 * messages. Run it during a maintenance window, after backing up the database.</p>
 */
@Service
public class MessageEncryptionMigrationService {

    private static final Logger log = LoggerFactory.getLogger(MessageEncryptionMigrationService.class);

    private final MongoTemplate mongoTemplate;
    private final MessageEncryptionService encryptionService;
    private final MessageEncryptionProperties properties;

    public MessageEncryptionMigrationService(MongoTemplate mongoTemplate,
                                             MessageEncryptionService encryptionService,
                                             MessageEncryptionProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.encryptionService = encryptionService;
        this.properties = properties;
    }

    /**
     * @param dryRun when true, no document is modified
     */
    public MigrationReport migrate(boolean dryRun) {
        if (!encryptionService.isEnabled()) {
            throw new IllegalStateException("Cannot migrate messages while encryption is disabled.");
        }

        int batchSize = Math.max(1, properties.getMigration().getBatchSize());

        if (dryRun) {
            long legacyMessages = mongoTemplate.count(legacyMessagesQuery(0), Message.class);
            long legacyPreviews = mongoTemplate.count(legacyPreviewQuery(0), Conversation.class);
            log.info("Message encryption migration DRY RUN: {} legacy message(s), {} legacy preview(s) found. "
                    + "No documents were changed.", legacyMessages, legacyPreviews);
            return new MigrationReport(true, legacyMessages, legacyPreviews, 0, 0);
        }

        long migratedMessages = migrateMessages(batchSize);
        long migratedPreviews = migratePreviews(batchSize);
        log.info("Message encryption migration APPLIED: {} message(s) and {} conversation preview(s) encrypted.",
                migratedMessages, migratedPreviews);
        return new MigrationReport(false, migratedMessages, migratedPreviews,
                migratedMessages, migratedPreviews);
    }

    private long migrateMessages(int batchSize) {
        long migrated = 0;
        while (true) {
            List<Message> batch = mongoTemplate.find(legacyMessagesQuery(batchSize), Message.class);
            if (batch.isEmpty()) {
                return migrated;
            }

            for (Message message : batch) {
                if (message.getContent() == null) {
                    continue;
                }
                EncryptedPayload payload = encryptionService.encrypt(message.getContent());
                Update update = new Update()
                        .set("ciphertext", payload.ciphertext())
                        .set("nonce", payload.nonce())
                        .set("authTag", payload.authTag())
                        .set("encryptionVersion", payload.encryptionVersion())
                        .set("keyId", payload.keyId())
                        .unset("content");
                var result = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(message.getId())), update, Message.class);
                if (result.getModifiedCount() == 0) {
                    throw new IllegalStateException(
                            "Migration could not update message [" + message.getId() + "]; aborting.");
                }
                migrated++;
            }
        }
    }

    private long migratePreviews(int batchSize) {
        long migrated = 0;
        while (true) {
            List<Conversation> batch = mongoTemplate.find(legacyPreviewQuery(batchSize), Conversation.class);
            if (batch.isEmpty()) {
                return migrated;
            }

            for (Conversation conversation : batch) {
                if (conversation.getLastMessageText() == null) {
                    continue;
                }
                EncryptedPayload payload = encryptionService.encrypt(conversation.getLastMessageText());
                Update update = new Update()
                        .set("lastMessageCiphertext", payload.ciphertext())
                        .set("lastMessageNonce", payload.nonce())
                        .set("lastMessageAuthTag", payload.authTag())
                        .set("lastMessageEncryptionVersion", payload.encryptionVersion())
                        .set("lastMessageKeyId", payload.keyId())
                        .unset("lastMessageText");
                var result = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(conversation.getId())), update, Conversation.class);
                if (result.getModifiedCount() == 0) {
                    throw new IllegalStateException(
                            "Migration could not update conversation [" + conversation.getId() + "]; aborting.");
                }
                migrated++;
            }
        }
    }

    private Query legacyMessagesQuery(int limit) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("content").exists(true).ne(null),
                new Criteria().orOperator(
                        Criteria.where("encryptionVersion").exists(false),
                        Criteria.where("encryptionVersion").is(null))));
        if (limit > 0) {
            query.limit(limit);
        }
        return query;
    }

    private Query legacyPreviewQuery(int limit) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("lastMessageText").exists(true).ne(null),
                new Criteria().orOperator(
                        Criteria.where("lastMessageEncryptionVersion").exists(false),
                        Criteria.where("lastMessageEncryptionVersion").is(null))));
        if (limit > 0) {
            query.limit(limit);
        }
        return query;
    }

    public record MigrationReport(
            boolean dryRun,
            long legacyMessagesFound,
            long legacyPreviewsFound,
            long messagesMigrated,
            long previewsMigrated) {
    }
}
