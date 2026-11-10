package com.fairish.chat.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateConversationRequest {

    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    public CreateConversationRequest() {
    }

    public CreateConversationRequest(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }
}
