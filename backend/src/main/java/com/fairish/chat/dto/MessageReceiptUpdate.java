package com.fairish.chat.dto;

import com.fairish.chat.model.MessageStatus;

import java.time.Instant;
import java.util.List;

/**
 * Receipt event pushed to the sender's private {@code /user/queue/receipts}
 * queue. It deliberately carries no message content: only the conversation,
 * the new status, the affected identifiers (for delivery acknowledgements) and
 * the server timestamp.
 *
 * <p>A SEEN event is conversation-level and uses {@code occurredAt} as a
 * watermark: every outgoing message created at or before that instant is
 * considered seen. This keeps the payload bounded even when a long history is
 * opened at once.</p>
 */
public class MessageReceiptUpdate {

    private String conversationId;
    private MessageStatus status;
    private List<String> messageIds;
    private Instant occurredAt;

    public MessageReceiptUpdate() {
    }

    public MessageReceiptUpdate(String conversationId, MessageStatus status,
                                List<String> messageIds, Instant occurredAt) {
        this.conversationId = conversationId;
        this.status = status;
        this.messageIds = messageIds;
        this.occurredAt = occurredAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public List<String> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<String> messageIds) {
        this.messageIds = messageIds;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
