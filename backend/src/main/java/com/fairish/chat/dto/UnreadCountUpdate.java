package com.fairish.chat.dto;

/**
 * Authoritative unread-count update pushed to a participant's private STOMP
 * queue. Clients assign this value instead of incrementing locally, so repeated
 * delivery or reconnects cannot double-count.
 */
public class UnreadCountUpdate {

    private String conversationId;
    private int unreadCount;

    public UnreadCountUpdate() {
    }

    public UnreadCountUpdate(String conversationId, int unreadCount) {
        this.conversationId = conversationId;
        this.unreadCount = Math.max(unreadCount, 0);
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = Math.max(unreadCount, 0);
    }
}
