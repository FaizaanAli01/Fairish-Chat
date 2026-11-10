import { axiosClient } from './axiosClient';
import { Conversation, CreateConversationRequest } from '../types/conversation';

export const conversationApi = {
  getConversations: async (): Promise<Conversation[]> => {
    const response = await axiosClient.get<Conversation[]>('/conversations');
    return response.data;
  },

  getConversationById: async (id: string): Promise<Conversation> => {
    const response = await axiosClient.get<Conversation>(`/conversations/${id}`);
    return response.data;
  },

  createOrGetConversation: async (recipientId: string): Promise<Conversation> => {
    const response = await axiosClient.post<Conversation>('/conversations', { recipientId } as CreateConversationRequest);
    return response.data;
  },
};
