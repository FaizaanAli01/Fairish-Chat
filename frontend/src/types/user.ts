/**
 * Public projection of another user. The backend never returns another user's
 * email address or legacy profile-image URL, so they are intentionally absent
 * from this type.
 *
 * Avatar bytes are fetched from the authenticated
 * `GET /api/users/{id}/avatar` endpoint. `hasAvatar` and `avatarUpdatedAt` are
 * metadata only; no storage key or object URL is ever exposed.
 */
export interface PublicUser {
  id: string;
  username: string;
  bio?: string | null;
  online?: boolean;
  lastSeen?: string | null;
  hasAvatar?: boolean;
  avatarUpdatedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * The authenticated user's own profile. Only this shape may contain an email
 * or the legacy `profileImage` URL, which is visible to the owner only.
 */
export interface User extends PublicUser {
  email: string;
  profileImage?: string | null;
}

export interface UpdateProfilePayload {
  username?: string;
  profileImage?: string;
  bio?: string;
}
