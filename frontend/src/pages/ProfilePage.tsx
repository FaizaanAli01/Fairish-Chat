import React, { useState, useEffect, useRef } from 'react';
import { Navbar } from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import {
  User,
  Mail,
  Calendar,
  Save,
  CheckCircle,
  ArrowLeft,
  Image as ImageIcon,
  Upload,
  Trash2,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { ErrorMessage } from '../components/ErrorMessage';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { Avatar } from '../components/Avatar';
import { userApi } from '../api/userApi';

const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
const ALLOWED_AVATAR_TYPES = ['image/jpeg', 'image/png'];

export const ProfilePage: React.FC = () => {
  const { user, updateProfile, refreshUser } = useAuth();

  const [username, setUsername] = useState(user?.username || '');
  const [profileImage, setProfileImage] = useState(user?.profileImage || '');
  const [bio, setBio] = useState(user?.bio || '');
  const [isSaving, setIsSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const [isRemovingAvatar, setIsRemovingAvatar] = useState(false);
  const [avatarUploadProgress, setAvatarUploadProgress] = useState(0);
  const [avatarError, setAvatarError] = useState<string | null>(null);
  const [avatarSuccess, setAvatarSuccess] = useState<string | null>(null);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    return () => {
      if (avatarPreviewUrl) {
        URL.revokeObjectURL(avatarPreviewUrl);
      }
    };
  }, [avatarPreviewUrl]);

  useEffect(() => {
    if (user) {
      setUsername(user.username || '');
      setProfileImage(user.profileImage || '');
      setBio(user.bio || '');
    }
  }, [user]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSuccessMsg(null);
    setErrorMsg(null);
    setFieldErrors({});

    if (!username.trim() || username.trim().length < 3) {
      setFieldErrors({ username: 'Username must be at least 3 characters long' });
      return;
    }

    setIsSaving(true);
    try {
      await updateProfile({
        username: username.trim(),
        profileImage: profileImage.trim() || undefined,
        bio: bio.trim() || undefined,
      });
      setSuccessMsg('Profile updated successfully!');
      await refreshUser();
    } catch (err: any) {
      if (err.response?.data?.message) {
        setErrorMsg(err.response.data.message);
      } else if (err.response?.data?.validationErrors) {
        setFieldErrors(err.response.data.validationErrors);
      } else {
        setErrorMsg('Failed to update profile. Please try again.');
      }
    } finally {
      setIsSaving(false);
    }
  };

  const handleAvatarSelected = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // Reset so selecting the same file again still fires onChange.
    event.target.value = '';
    if (!file) return;

    setAvatarError(null);
    setAvatarSuccess(null);

    if (file.type && !ALLOWED_AVATAR_TYPES.includes(file.type)) {
      setAvatarError('Please choose a JPEG or PNG image.');
      return;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      setAvatarError('Avatar must be 5 MB or smaller.');
      return;
    }

    setAvatarPreviewUrl(URL.createObjectURL(file));
    setIsUploadingAvatar(true);
    setAvatarUploadProgress(0);
    try {
      await userApi.uploadAvatar(file, setAvatarUploadProgress);
      await refreshUser();
      // The backend clears the legacy URL when an avatar is uploaded.
      setProfileImage('');
      setAvatarPreviewUrl(null);
      setAvatarSuccess('Avatar updated successfully!');
    } catch (err: any) {
      setAvatarPreviewUrl(null);
      setAvatarError(
        err.response?.data?.message || 'Failed to upload avatar. Please try again.'
      );
    } finally {
      setIsUploadingAvatar(false);
      setAvatarUploadProgress(0);
    }
  };

  const handleRemoveAvatar = async () => {
    setAvatarError(null);
    setAvatarSuccess(null);
    setAvatarPreviewUrl(null);
    setIsRemovingAvatar(true);
    try {
      await userApi.deleteAvatar();
      await refreshUser();
      setAvatarSuccess('Avatar removed.');
    } catch (err: any) {
      setAvatarError(
        err.response?.data?.message || 'Failed to remove avatar. Please try again.'
      );
    } finally {
      setIsRemovingAvatar(false);
    }
  };

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  const isAvatarBusy = isUploadingAvatar || isRemovingAvatar;

  return (
    <div className="min-h-screen bg-dark-bg flex flex-col">
      <Navbar />

      <main className="flex-1 max-w-3xl w-full mx-auto p-4 sm:p-6 lg:p-8 space-y-5">
        {/* Navigation link */}
        <div>
          <Link
            to="/app"
            className="inline-flex items-center space-x-2 text-xs sm:text-sm font-semibold text-content-secondary hover:text-content-primary transition-colors group"
          >
            <ArrowLeft className="w-4 h-4 group-hover:-translate-x-0.5 transition-transform" />
            <span>Back to Messages</span>
          </Link>
        </div>

        {/* Profile Card */}
        <div className="bg-dark-sidebar border border-dark-border rounded-2xl p-5 sm:p-8 shadow-card space-y-7">
          {/* Header */}
          <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4 sm:gap-5 pb-6 border-b border-dark-border">
            <Avatar
              userId={user?.id}
              username={user?.username}
              hasAvatar={user?.hasAvatar}
              avatarUpdatedAt={user?.avatarUpdatedAt}
              legacyImageUrl={profileImage}
              previewSrc={avatarPreviewUrl}
              shape="rounded"
              className="w-20 h-20 sm:w-24 sm:h-24 text-lg shadow-violet-sm"
            />

            <div className="text-center sm:text-left space-y-1.5 flex-1 min-w-0">
              <h1 className="text-xl sm:text-2xl font-bold text-content-primary truncate">
                {user?.username}
              </h1>
              <p className="text-xs sm:text-sm text-content-secondary flex items-center justify-center sm:justify-start space-x-2 truncate">
                <Mail className="w-4 h-4 text-content-muted flex-shrink-0" />
                <span className="truncate">{user?.email}</span>
              </p>
              <p className="text-xs text-content-muted flex items-center justify-center sm:justify-start space-x-2 pt-0.5">
                <Calendar className="w-4 h-4 text-content-muted flex-shrink-0" />
                <span>Joined {formatDate(user?.createdAt)}</span>
              </p>

              {/* Avatar controls */}
              <div className="pt-2 flex flex-wrap items-center justify-center sm:justify-start gap-2">
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/jpeg,image/png"
                  className="hidden"
                  onChange={handleAvatarSelected}
                  aria-label="Choose an avatar image"
                />
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isAvatarBusy}
                  className="px-3.5 py-2 bg-violet-primary hover:bg-violet-hover text-white font-semibold text-xs rounded-xl shadow-violet-sm transition-all duration-150 flex items-center space-x-1.5 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isUploadingAvatar ? (
                    <LoadingSpinner size="sm" text="Uploading..." />
                  ) : (
                    <>
                      <Upload className="w-3.5 h-3.5" />
                      <span>Upload avatar</span>
                    </>
                  )}
                </button>

                {user?.hasAvatar && (
                  <button
                    type="button"
                    onClick={handleRemoveAvatar}
                    disabled={isAvatarBusy}
                    className="px-3.5 py-2 bg-dark-panel hover:bg-dark-hover text-content-secondary hover:text-status-error border border-dark-border hover:border-red-500/30 font-semibold text-xs rounded-xl transition-all duration-150 flex items-center space-x-1.5 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {isRemovingAvatar ? (
                      <LoadingSpinner size="sm" text="Removing..." />
                    ) : (
                      <>
                        <Trash2 className="w-3.5 h-3.5" />
                        <span>Remove avatar</span>
                      </>
                    )}
                  </button>
                )}
              </div>

              <p className="text-[11px] text-content-muted pt-1">
                JPEG or PNG, up to 5 MB. Images are resized to 512×512.
              </p>

              {isUploadingAvatar && (
                <div className="w-full max-w-xs sm:max-w-sm pt-1">
                  <div
                    className="h-1.5 w-full bg-dark-panel rounded-full overflow-hidden border border-dark-border"
                    role="progressbar"
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={avatarUploadProgress}
                  >
                    <div
                      className="h-full bg-violet-primary transition-all"
                      style={{ width: `${avatarUploadProgress}%` }}
                    />
                  </div>
                  <p className="text-[11px] text-content-muted mt-1">
                    {avatarUploadProgress}% uploaded
                  </p>
                </div>
              )}

              {avatarError && (
                <p className="text-xs text-status-error pt-1" role="alert">
                  {avatarError}
                </p>
              )}

              {avatarSuccess && (
                <p className="text-xs text-status-success pt-1">{avatarSuccess}</p>
              )}
            </div>
          </div>

          {/* Feedback Messages */}
          <ErrorMessage message={errorMsg || undefined} validationErrors={fieldErrors} />

          {successMsg && (
            <div className="bg-status-success/10 border border-status-success/25 rounded-xl p-3 sm:p-3.5 text-status-success text-xs sm:text-sm flex items-center space-x-2.5 shadow-sm">
              <CheckCircle className="w-4 h-4 sm:w-5 sm:h-5 flex-shrink-0" />
              <span className="font-medium">{successMsg}</span>
            </div>
          )}

          {/* Edit Form */}
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-content-secondary mb-2">
                Username
              </label>
              <div className="relative">
                <User className="w-4 h-4 text-content-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="text"
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Your unique handle"
                  className="w-full pl-10 pr-4 py-2.5 bg-dark-panel border border-dark-border rounded-xl text-xs sm:text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-violet-primary focus:ring-1 focus:ring-violet-primary transition-all"
                />
              </div>
              <p className="text-[11px] text-content-muted mt-1">Must be unique across all Fairish Chat accounts.</p>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-content-secondary mb-2">
                Legacy Image URL (Optional)
              </label>
              <div className="relative">
                <ImageIcon className="w-4 h-4 text-content-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="url"
                  value={profileImage}
                  onChange={(e) => setProfileImage(e.target.value)}
                  placeholder="https://example.com/avatar.jpg"
                  className="w-full pl-10 pr-4 py-2.5 bg-dark-panel border border-dark-border rounded-xl text-xs sm:text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-violet-primary focus:ring-1 focus:ring-violet-primary transition-all"
                />
              </div>
              <p className="text-[11px] text-content-muted mt-1">
                Only visible to you. Uploading an avatar replaces this URL.
              </p>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-content-secondary mb-2">
                Bio
              </label>
              <textarea
                rows={3}
                value={bio}
                onChange={(e) => setBio(e.target.value)}
                placeholder="Tell other chat members about yourself..."
                maxLength={250}
                className="w-full px-4 py-2.5 bg-dark-panel border border-dark-border rounded-xl text-xs sm:text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-violet-primary focus:ring-1 focus:ring-violet-primary transition-all resize-none"
              />
              <div className="flex justify-between text-[11px] text-content-muted mt-1">
                <span>Brief bio for your profile preview</span>
                <span>{bio.length}/250</span>
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-content-muted mb-2">
                Email Address (Read-Only)
              </label>
              <input
                type="email"
                disabled
                value={user?.email || ''}
                className="w-full px-4 py-2.5 bg-dark-panel/40 border border-dark-border/60 rounded-xl text-xs sm:text-sm text-content-muted cursor-not-allowed"
              />
            </div>

            <div className="pt-2 flex justify-end">
              <button
                type="submit"
                disabled={isSaving}
                className="px-5 py-2.5 bg-violet-primary hover:bg-violet-hover text-white font-semibold text-xs sm:text-sm rounded-xl shadow-violet-sm hover:shadow-violet-glow transition-all duration-150 flex items-center space-x-2 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSaving ? (
                  <LoadingSpinner size="sm" text="Saving..." />
                ) : (
                  <>
                    <Save className="w-4 h-4" />
                    <span>Save Changes</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
};
