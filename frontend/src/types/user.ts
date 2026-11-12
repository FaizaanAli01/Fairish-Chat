/**
 * Public projection of another user. The backend never returns another user's
 * email address, so it is intentionally absent from this type.
 */
export interface PublicUser {
  id: string;
  username: string;
  profileImage?: string | null;
  bio?: string | null;
  online?: boolean;
  lastSeen?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * The authenticated user's own profile. Only this shape may contain an email.
 */
export interface User extends PublicUser {
  email: string;
}

export interface UpdateProfilePayload {
  username?: string;
  profileImage?: string;
  bio?: string;
}
