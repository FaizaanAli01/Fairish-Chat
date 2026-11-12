import { axiosClient } from './axiosClient';
import { PublicUser, UpdateProfilePayload, User } from '../types/user';

export const userApi = {
  getCurrentUser: async (): Promise<User> => {
    const response = await axiosClient.get<User>('/users/me');
    return response.data;
  },

  updateProfile: async (data: UpdateProfilePayload): Promise<User> => {
    const response = await axiosClient.put<User>('/users/me', data);
    return response.data;
  },

  searchUsers: async (query: string): Promise<PublicUser[]> => {
    const response = await axiosClient.get<PublicUser[]>('/users/search', {
      params: { query },
    });
    return response.data;
  },
};

