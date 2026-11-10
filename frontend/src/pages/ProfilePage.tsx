import React, { useState, useEffect } from 'react';
import { Navbar } from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { User, Mail, Calendar, Save, CheckCircle, ArrowLeft, Image as ImageIcon } from 'lucide-react';
import { Link } from 'react-router-dom';
import { ErrorMessage } from '../components/ErrorMessage';
import { LoadingSpinner } from '../components/LoadingSpinner';

export const ProfilePage: React.FC = () => {
  const { user, updateProfile, refreshUser } = useAuth();

  const [username, setUsername] = useState(user?.username || '');
  const [profileImage, setProfileImage] = useState(user?.profileImage || '');
  const [bio, setBio] = useState(user?.bio || '');
  const [isSaving, setIsSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

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

  const formatDate = (dateStr?: string) => {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  const getInitials = (name?: string) => {
    if (!name) return 'U';
    return name.slice(0, 2).toUpperCase();
  };

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
          <div className="flex flex-col sm:flex-row items-center sm:items-start space-y-4 sm:space-y-0 sm:space-x-5 pb-6 border-b border-dark-border">
            <div className="w-20 h-20 sm:w-24 sm:h-24 rounded-2xl bg-dark-panel border border-violet-primary/30 flex items-center justify-center overflow-hidden flex-shrink-0 shadow-violet-sm text-lg font-bold text-violet-lavender">
              {profileImage ? (
                <img src={profileImage} alt={username} className="w-full h-full object-cover" />
              ) : (
                <span>{getInitials(username)}</span>
              )}
            </div>

            <div className="text-center sm:text-left space-y-1.5 flex-1 min-w-0">
              <h1 className="text-xl sm:text-2xl font-bold text-content-primary truncate">{user?.username}</h1>
              <p className="text-xs sm:text-sm text-content-secondary flex items-center justify-center sm:justify-start space-x-2 truncate">
                <Mail className="w-4 h-4 text-content-muted flex-shrink-0" />
                <span className="truncate">{user?.email}</span>
              </p>
              <p className="text-xs text-content-muted flex items-center justify-center sm:justify-start space-x-2 pt-0.5">
                <Calendar className="w-4 h-4 text-content-muted flex-shrink-0" />
                <span>Joined {formatDate(user?.createdAt)}</span>
              </p>
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
                Profile Image URL
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
              <p className="text-[11px] text-content-muted mt-1">Direct link to an image file (PNG, JPG, WebP).</p>
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
