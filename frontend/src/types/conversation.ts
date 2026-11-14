import { PublicUser } from './user';

export interface Conversation {
  id: string;
  participantIds: string[];
  otherUser?: PublicUser | null;
  lastMessageText?: string | null;
  lastMessageSenderId?: string | null;
  lastMessageAt?: string | null;
  /** Server-authoritative unread count for the authenticated user. */
  unreadCount?: number;
  createdAt: string;
  updatedAt: string;
}

export interface UnreadCountUpdate {
  conversationId: string;
  unreadCount: number;
}

export interface CreateConversationRequest {
  recipientId: string;
}
