import React, { useEffect, useState } from 'react';
import { userApi } from '../api/userApi';

interface AvatarProps {
  userId?: string | null;
  username?: string | null;
  /** True when the backend reports an uploaded avatar for this user. */
  hasAvatar?: boolean;
  /** Changes whenever the avatar is replaced, triggering a re-fetch. */
  avatarUpdatedAt?: string | null;
  /**
   * Owner-only legacy image URL. Never pass another user's legacy URL: public
   * user projections do not include it.
   */
  legacyImageUrl?: string | null;
  /** Optional local preview (for example, a just-selected file). */
  previewSrc?: string | null;
  shape?: 'circle' | 'rounded';
  className?: string;
  alt?: string;
}

/**
 * Renders an avatar fetched through the authenticated backend proxy.
 *
 * <p>The backend only serves avatar bytes to the owner and accepted contacts.
 * When no avatar is available (or the request is denied) this falls back to the
 * user's initials, preserving the existing UI.</p>
 */
export const Avatar: React.FC<AvatarProps> = ({
  userId,
  username,
  hasAvatar = false,
  avatarUpdatedAt,
  legacyImageUrl,
  previewSrc,
  shape = 'circle',
  className = '',
  alt,
}) => {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setObjectUrl(null);
    setFailed(false);

    if (!hasAvatar || !userId || previewSrc) {
      return;
    }

    let cancelled = false;
    let createdUrl: string | null = null;

    userApi
      .getAvatarBlob(userId)
      .then((blob) => {
        const nextUrl = URL.createObjectURL(blob);
        if (cancelled) {
          URL.revokeObjectURL(nextUrl);
          return;
        }
        createdUrl = nextUrl;
        setObjectUrl(nextUrl);
      })
      .catch(() => {
        if (!cancelled) {
          setFailed(true);
        }
      });

    return () => {
      cancelled = true;
      if (createdUrl) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [userId, hasAvatar, avatarUpdatedAt, previewSrc]);

  const legacyUrl = !hasAvatar && legacyImageUrl ? legacyImageUrl : null;
  const src = previewSrc || objectUrl || (failed ? null : legacyUrl);
  const initials = getInitials(username);
  const shapeClass = shape === 'rounded' ? 'rounded-2xl' : 'rounded-full';

  return (
    <div
      className={`${shapeClass} bg-dark-bg border border-dark-border flex items-center justify-center overflow-hidden flex-shrink-0 text-violet-lavender font-semibold ${className}`}
    >
      {src ? (
        <img
          src={src}
          alt={alt ?? (username ? `${username}'s avatar` : 'User avatar')}
          className="w-full h-full object-cover"
        />
      ) : (
        <span aria-hidden={!username}>{initials}</span>
      )}
    </div>
  );
};

function getInitials(name?: string | null): string {
  if (!name) return 'U';
  return name.slice(0, 2).toUpperCase();
}
