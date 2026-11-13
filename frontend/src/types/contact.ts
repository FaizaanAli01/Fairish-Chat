import { PublicUser } from './user';

/**
 * Mirrors the backend ContactStatus enum. A pair has exactly one contact
 * request document, and only ACCEPTED grants chat access.
 */
export type ContactStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED';

/**
 * Contact request projection returned by /api/contacts/requests/*. Both users
 * are public projections, so no email address is ever available here.
 */
export interface ContactRequest {
  id: string;
  requester: PublicUser;
  recipient: PublicUser;
  status: ContactStatus;
  createdAt: string;
  updatedAt: string;
  respondedAt?: string | null;
}

/**
 * An accepted contact of the authenticated user.
 */
export interface Contact {
  user: PublicUser;
  acceptedAt: string;
}
