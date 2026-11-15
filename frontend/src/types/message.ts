export type MessageStatus = 'SENT' | 'DELIVERED' | 'SEEN';

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  createdAt: string;
  deliveredAt?: string | null;
  seenAt?: string | null;
  /** Server-derived receipt state. Older servers may omit this field. */
  status?: MessageStatus;
}

export interface SendMessageRequest {
  content: string;
}

export interface PaginatedMessages {
  content: Message[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

export interface MessageDeliveryResponse {
  conversationId: string;
  status: MessageStatus;
  deliveredMessageIds: string[];
  deliveredAt: string;
}

/** Private queue event sent to the original sender of a message. */
export interface MessageReceiptUpdate {
  conversationId: string;
  status: MessageStatus;
  /** Present for DELIVERED events; omitted for the conversation-level SEEN watermark. */
  messageIds?: string[] | null;
  occurredAt: string;
}

/**
 * Derives the effective status for servers or cached messages that predate the
 * status field.
 */
export const resolveMessageStatus = (message: Message): MessageStatus => {
  if (message.status) return message.status;
  if (message.seenAt) return 'SEEN';
  if (message.deliveredAt) return 'DELIVERED';
  return 'SENT';
};
