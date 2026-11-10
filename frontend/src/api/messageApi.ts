import { axiosClient } from './axiosClient';
import { Message, PaginatedMessages, SendMessageRequest } from '../types/message';

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
};
