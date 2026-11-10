export interface User {
  id: string;
  username: string;
  email: string;
  profileImage?: string | null;
  bio?: string | null;
  online?: boolean;
  lastSeen?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateProfilePayload {
  username?: string;
  profileImage?: string;
  bio?: string;
}

