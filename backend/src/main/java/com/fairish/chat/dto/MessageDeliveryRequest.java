package com.fairish.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Recipient acknowledgement for one or more messages in a conversation.
 *
 * <p>Identifiers are verified server-side against the conversation and the
 * actual sender; foreign or malformed identifiers are ignored rather than
 * trusted.</p>
 */
public class MessageDeliveryRequest {

    public static final int MAX_MESSAGE_IDS = 200;

    @NotEmpty(message = "messageIds must not be empty")
    @Size(max = MAX_MESSAGE_IDS, message = "At most 200 message IDs may be acknowledged per request")
    private List<String> messageIds;

    public MessageDeliveryRequest() {
    }

    public MessageDeliveryRequest(List<String> messageIds) {
        this.messageIds = messageIds;
    }

    public List<String> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<String> messageIds) {
        this.messageIds = messageIds;
    }
}
