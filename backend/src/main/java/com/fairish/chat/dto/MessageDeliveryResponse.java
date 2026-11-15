package com.fairish.chat.dto;

import com.fairish.chat.model.MessageStatus;

import java.time.Instant;
import java.util.List;

/**
 * Result of a delivery acknowledgement. Only identifiers and receipt metadata
 * are returned; message content is never part of receipt handling.
 */
public class MessageDeliveryResponse {

    private String conversationId;
    private MessageStatus status;
    private List<String> deliveredMessageIds;
    private Instant deliveredAt;

    public MessageDeliveryResponse() {
    }

    public MessageDeliveryResponse(String conversationId, MessageStatus status,
                                   List<String> deliveredMessageIds, Instant deliveredAt) {
        this.conversationId = conversationId;
        this.status = status;
        this.deliveredMessageIds = deliveredMessageIds;
        this.deliveredAt = deliveredAt;
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

    public List<String> getDeliveredMessageIds() {
        return deliveredMessageIds;
    }

    public void setDeliveredMessageIds(List<String> deliveredMessageIds) {
        this.deliveredMessageIds = deliveredMessageIds;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
