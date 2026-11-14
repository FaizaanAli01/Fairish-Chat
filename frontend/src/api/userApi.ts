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

  /**
   * Uploads an avatar through the authenticated backend. The server validates
   * and normalizes the image; the response is the updated self profile.
   */
  uploadAvatar: async (file: File, onProgress?: (percent: number) => void): Promise<User> => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await axiosClient.post<User>('/users/me/avatar', formData, {
      // Explicitly mark the body as multipart. Without this the shared client
      // previously JSON-stringified FormData, so the backend never received a
      // multipart request.
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.min(100, Math.round((event.loaded * 100) / event.total)));
        }
      },
    });
    return response.data;
  },

  deleteAvatar: async (): Promise<User> => {
    const response = await axiosClient.delete<User>('/users/me/avatar');
    return response.data;
  },

  /**
   * Fetches avatar bytes through the authenticated proxy. Never use a public
   * object URL or a presigned URL here.
   */
  getAvatarBlob: async (userId: string): Promise<Blob> => {
    const response = await axiosClient.get<Blob>(
      `/users/${encodeURIComponent(userId)}/avatar`,
      { responseType: 'blob' }
    );
    return response.data;
  },
};
