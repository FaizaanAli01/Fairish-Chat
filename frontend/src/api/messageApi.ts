import { axiosClient } from './axiosClient';
import {
  Message,
  MessageDeliveryResponse,
  PaginatedMessages,
  SendMessageRequest,
} from '../types/message';

export const messageApi = {
  getMessages: async (conversationId: string, page = 0, size = 20): Promise<PaginatedMessages> => {
    const response = await axiosClient.get<PaginatedMessages>(`/conversations/${conversationId}/messages`, {
      params: { page, size },
    });
    return response.data;
  },

  sendMessage: async (conversationId: string, content: string): Promise<Message> => {
    const response = await axiosClient.post<Message>(`/conversations/${conversationId}/messages`, {
      content,
    } as SendMessageRequest);
    return response.data;
  },

  /**
   * Acknowledges that this device received incoming messages. The backend
   * ignores identifiers that do not belong to the conversation or were sent by
   * the current user.
   */
  markDelivered: async (
    conversationId: string,
    messageIds: string[]
  ): Promise<MessageDeliveryResponse> => {
    const response = await axiosClient.post<MessageDeliveryResponse>(
      `/conversations/${conversationId}/messages/delivered`,
      { messageIds }
    );
    return response.data;
  },
};
