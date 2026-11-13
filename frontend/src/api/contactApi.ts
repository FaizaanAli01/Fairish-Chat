import { axiosClient } from './axiosClient';
import { Contact, ContactRequest } from '../types/contact';

/**
 * Client for the contact request lifecycle. Chat is only allowed once an
 * accepted contact relationship exists, so the UI refreshes conversations
 * after every mutation.
 */
export const contactApi = {
  getContacts: async (): Promise<Contact[]> => {
    const response = await axiosClient.get<Contact[]>('/contacts');
    return response.data;
  },

  getIncomingRequests: async (): Promise<ContactRequest[]> => {
    const response = await axiosClient.get<ContactRequest[]>('/contacts/requests/incoming');
    return response.data;
  },

  getOutgoingRequests: async (): Promise<ContactRequest[]> => {
    const response = await axiosClient.get<ContactRequest[]>('/contacts/requests/outgoing');
    return response.data;
  },

  sendRequest: async (recipientId: string): Promise<ContactRequest> => {
    const response = await axiosClient.post<ContactRequest>('/contacts/requests', { recipientId });
    return response.data;
  },

  acceptRequest: async (requestId: string): Promise<ContactRequest> => {
    const response = await axiosClient.post<ContactRequest>(
      `/contacts/requests/${encodeURIComponent(requestId)}/accept`
    );
    return response.data;
  },

  declineRequest: async (requestId: string): Promise<ContactRequest> => {
    const response = await axiosClient.post<ContactRequest>(
      `/contacts/requests/${encodeURIComponent(requestId)}/decline`
    );
    return response.data;
  },

  cancelRequest: async (requestId: string): Promise<void> => {
    await axiosClient.delete(`/contacts/requests/${encodeURIComponent(requestId)}`);
  },

  removeContact: async (contactUserId: string): Promise<void> => {
    await axiosClient.delete(`/contacts/${encodeURIComponent(contactUserId)}`);
  },
};
