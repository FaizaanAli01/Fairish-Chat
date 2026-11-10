import { User } from './user';

export interface Conversation {
  id: string;
  participantIds: string[];
  otherUser?: User | null;
  lastMessageText?: string | null;
  lastMessageSenderId?: string | null;
  lastMessageAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConversationRequest {
  recipientId: string;
}
