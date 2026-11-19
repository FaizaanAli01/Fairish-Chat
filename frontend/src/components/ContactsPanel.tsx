import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Search,
  UserPlus,
  Check,
  X,
  Clock,
  Users,
  MessageSquare,
  Trash2,
  Inbox,
  Send as SendIcon,
  UserCheck,
  Copy,
} from 'lucide-react';
import { contactApi } from '../api/contactApi';
import { userApi } from '../api/userApi';
import { getApiErrorMessage } from '../api/axiosClient';
import { Contact, ContactRequest } from '../types/contact';
import { PublicUser } from '../types/user';
import { Avatar } from './Avatar';
import { LoadingSpinner } from './LoadingSpinner';
import { ErrorMessage } from './ErrorMessage';

interface ContactsPanelProps {
  onContactsChanged: () => void;
  onStartConversationWithUser: (user: PublicUser) => void;
  onPendingCountChange: (count: number) => void;
}

const errorMessage = (err: unknown, fallback: string): string =>
  getApiErrorMessage(err, fallback);

/** Shows a compact form of a 24-hex User ID without altering the real value. */
const shortenUserId = (id: string): string =>
  id.length > 14 ? `${id.slice(0, 6)}...${id.slice(-6)}` : id;

/**
 * Contact management for the sidebar: search by Fairish User ID or username,
 * send/accept/decline/cancel requests, and remove accepted contacts.
 *
 * <p>All mutations reload the relevant lists from the server, so the UI stays
 * consistent without a page refresh.</p>
 */
export const ContactsPanel: React.FC<ContactsPanelProps> = ({
  onContactsChanged,
  onStartConversationWithUser,
  onPendingCountChange,
}) => {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [incoming, setIncoming] = useState<ContactRequest[]>([]);
  const [outgoing, setOutgoing] = useState<ContactRequest[]>([]);

  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionKey, setActionKey] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<PublicUser[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [copiedUserId, setCopiedUserId] = useState<string | null>(null);

  useEffect(() => {
    if (!copiedUserId) return;
    const timeout = window.setTimeout(() => setCopiedUserId(null), 2000);
    return () => window.clearTimeout(timeout);
  }, [copiedUserId]);

  const loadContacts = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [contactList, incomingList, outgoingList] = await Promise.all([
        contactApi.getContacts(),
        contactApi.getIncomingRequests(),
        contactApi.getOutgoingRequests(),
      ]);
      setContacts(contactList);
      setIncoming(incomingList);
      setOutgoing(outgoingList);
      onPendingCountChange(incomingList.length);
    } catch (err: any) {
      setError(errorMessage(err, 'Failed to load contacts. Please try again.'));
    } finally {
      setIsLoading(false);
    }
  }, [onPendingCountChange]);

  useEffect(() => {
    loadContacts();
  }, [loadContacts]);

  // Debounced user search. The backend resolves an exact 24-hex Fairish User
  // ID or falls back to a username substring match.
  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults([]);
      setSearchError(null);
      return;
    }

    const timer = setTimeout(async () => {
      setIsSearching(true);
      setSearchError(null);
      try {
        const results = await userApi.searchUsers(searchQuery.trim());
        setSearchResults(results);
      } catch (err: any) {
        setSearchResults([]);
        setSearchError(errorMessage(err, 'Failed to search users.'));
      } finally {
        setIsSearching(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchQuery]);

  const contactIds = useMemo(
    () => new Set(contacts.map((contact) => contact.user.id)),
    [contacts]
  );
  const incomingRequesterIds = useMemo(
    () => new Set(incoming.map((request) => request.requester.id)),
    [incoming]
  );
  const outgoingRecipientIds = useMemo(
    () => new Set(outgoing.map((request) => request.recipient.id)),
    [outgoing]
  );

  const runAction = async (key: string, action: () => Promise<void>, fallback: string) => {
    setActionKey(key);
    setError(null);
    try {
      await action();
      await loadContacts();
      onContactsChanged();
    } catch (err: any) {
      setError(errorMessage(err, fallback));
    } finally {
      setActionKey(null);
    }
  };

  const handleCopyUserId = async (userId: string) => {
    try {
      await navigator.clipboard.writeText(userId);
      setCopiedUserId(userId);
    } catch {
      // Clipboard access can be denied by the browser; leave the UI unchanged.
    }
  };

  const handleSendRequest = (user: PublicUser) =>
    runAction(`send:${user.id}`, () => contactApi.sendRequest(user.id).then(() => undefined),
      'Failed to send contact request.');

  const handleAccept = (request: ContactRequest) =>
    runAction(`accept:${request.id}`, () => contactApi.acceptRequest(request.id).then(() => undefined),
      'Failed to accept contact request.');

  const handleDecline = (request: ContactRequest) =>
    runAction(`decline:${request.id}`, () => contactApi.declineRequest(request.id).then(() => undefined),
      'Failed to decline contact request.');

  const handleCancel = (request: ContactRequest) =>
    runAction(`cancel:${request.id}`, () => contactApi.cancelRequest(request.id),
      'Failed to cancel contact request.');

  const handleRemove = (user: PublicUser) => {
    if (!window.confirm(`Remove ${user.username}? Messaging access will be revoked.`)) {
      return;
    }
    runAction(`remove:${user.id}`, () => contactApi.removeContact(user.id),
      'Failed to remove contact.');
  };

  const renderSearchAction = (user: PublicUser) => {
    if (contactIds.has(user.id)) {
      return (
        <span className="inline-flex items-center space-x-1 text-[11px] font-semibold text-status-success bg-status-success/10 border border-status-success/20 px-2 py-1 rounded-lg flex-shrink-0">
          <UserCheck className="w-3 h-3" />
          <span>Contact</span>
        </span>
      );
    }

    const isPending = incomingRequesterIds.has(user.id) || outgoingRecipientIds.has(user.id);
    if (isPending) {
      return (
        <span className="inline-flex items-center space-x-1 text-[11px] font-semibold text-status-warning bg-status-warning/10 border border-status-warning/20 px-2 py-1 rounded-lg flex-shrink-0">
          <Clock className="w-3 h-3" />
          <span>Pending</span>
        </span>
      );
    }

    const isSending = actionKey === `send:${user.id}`;
    return (
      <button
        onClick={() => handleSendRequest(user)}
        disabled={actionKey !== null}
        className="inline-flex items-center space-x-1 text-[11px] font-semibold text-violet-lavender bg-violet-primary/10 hover:bg-violet-primary/20 border border-violet-primary/25 px-2.5 py-1.5 rounded-lg transition-all disabled:opacity-50 flex-shrink-0"
      >
        <UserPlus className="w-3 h-3" />
        <span>{isSending ? 'Sending...' : 'Add'}</span>
      </button>
    );
  };

  return (
    <div className="flex-1 flex flex-col overflow-hidden min-h-0 bg-dark-sidebar">
      {/* Search by Fairish User ID or username */}
      <div className="p-3 border-b border-dark-border flex-shrink-0">
        <div className="relative">
          <Search className="w-4 h-4 text-content-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            placeholder="Search by Fairish User ID or username..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            autoFocus
            className="w-full pl-9 pr-3.5 py-2.5 bg-dark-panel border border-dark-border rounded-xl text-xs sm:text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-violet-primary focus:ring-1 focus:ring-violet-primary transition-all"
          />
        </div>
        <p className="mt-1.5 px-1 text-[10px] text-content-muted">
          Search by username, or paste the exact 24-character User ID shared by your contact.
        </p>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-4 min-h-0">
        {error && <ErrorMessage message={error} />}

        {/* Search results */}
        {searchQuery.trim() && (
          <section className="space-y-1.5">
            <h4 className="text-[11px] font-semibold uppercase tracking-wider text-content-muted px-1">
              Search Results
            </h4>
            {isSearching ? (
              <LoadingSpinner size="sm" text="Searching users..." />
            ) : searchError ? (
              <ErrorMessage message={searchError} />
            ) : searchResults.length === 0 ? (
              <p className="px-2 py-3 text-xs text-content-muted">
                No user found for "{searchQuery.trim()}".
              </p>
            ) : (
              searchResults.map((user) => (
                <div
                  key={user.id}
                  className="flex items-center space-x-3 p-2.5 rounded-xl bg-dark-panel/60 border border-transparent min-w-0"
                >
                  <Avatar
                    userId={user.id}
                    username={user.username}
                    hasAvatar={user.hasAvatar}
                    avatarUpdatedAt={user.avatarUpdatedAt}
                    className="w-9 h-9 text-xs shadow-sm"
                  />
                  <div className="flex-1 min-w-0">
                    <span className="font-semibold text-xs sm:text-sm text-content-primary truncate block">
                      {user.username}
                    </span>
                    <div className="mt-0.5 flex items-center gap-1.5 min-w-0">
                      {copiedUserId === user.id ? (
                        <span role="status" className="truncate text-[10px] text-violet-lavender">
                          User ID copied
                        </span>
                      ) : (
                        <span className="truncate font-mono text-[10px] text-content-muted">
                          {shortenUserId(user.id)}
                        </span>
                      )}
                      <button
                        type="button"
                        onClick={() => handleCopyUserId(user.id)}
                        title="Copy User ID"
                        aria-label="Copy User ID"
                        className="flex-shrink-0 rounded p-0.5 text-content-muted transition-colors hover:bg-violet-primary/10 hover:text-violet-lavender"
                      >
                        {copiedUserId === user.id ? (
                          <Check className="h-3 w-3" />
                        ) : (
                          <Copy className="h-3 w-3" />
                        )}
                      </button>
                    </div>
                  </div>
                  {renderSearchAction(user)}
                </div>
              ))
            )}
          </section>
        )}

        {isLoading ? (
          <div className="py-10">
            <LoadingSpinner size="sm" text="Loading contacts..." />
          </div>
        ) : (
          <>
            {/* Incoming requests */}
            {incoming.length > 0 && (
              <section className="space-y-1.5">
                <h4 className="flex items-center space-x-1.5 text-[11px] font-semibold uppercase tracking-wider text-content-muted px-1">
                  <Inbox className="w-3 h-3" />
                  <span>Incoming Requests ({incoming.length})</span>
                </h4>
                {incoming.map((request) => (
                  <div
                    key={request.id}
                    className="p-2.5 rounded-xl bg-dark-panel/70 border border-dark-border space-y-2 min-w-0"
                  >
                    <div className="flex items-center space-x-3 min-w-0">
                      <Avatar
                        userId={request.requester.id}
                        username={request.requester.username}
                        hasAvatar={request.requester.hasAvatar}
                        avatarUpdatedAt={request.requester.avatarUpdatedAt}
                        className="w-9 h-9 text-xs shadow-sm"
                      />
                      <div className="flex-1 min-w-0">
                        <span className="font-semibold text-xs sm:text-sm text-content-primary truncate block">
                          {request.requester.username}
                        </span>
                        <span className="text-[10px] text-content-secondary">
                          Wants to connect
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      <button
                        onClick={() => handleAccept(request)}
                        disabled={actionKey !== null}
                        className="flex-1 inline-flex items-center justify-center space-x-1.5 py-1.5 rounded-lg bg-violet-primary hover:bg-violet-hover text-white text-[11px] font-semibold transition-all disabled:opacity-50"
                      >
                        <Check className="w-3.5 h-3.5" />
                        <span>{actionKey === `accept:${request.id}` ? 'Accepting...' : 'Accept'}</span>
                      </button>
                      <button
                        onClick={() => handleDecline(request)}
                        disabled={actionKey !== null}
                        className="flex-1 inline-flex items-center justify-center space-x-1.5 py-1.5 rounded-lg bg-dark-hover hover:bg-dark-border text-content-secondary text-[11px] font-semibold border border-dark-border transition-all disabled:opacity-50"
                      >
                        <X className="w-3.5 h-3.5" />
                        <span>Decline</span>
                      </button>
                    </div>
                  </div>
                ))}
              </section>
            )}

            {/* Outgoing requests */}
            {outgoing.length > 0 && (
              <section className="space-y-1.5">
                <h4 className="flex items-center space-x-1.5 text-[11px] font-semibold uppercase tracking-wider text-content-muted px-1">
                  <SendIcon className="w-3 h-3" />
                  <span>Sent Requests ({outgoing.length})</span>
                </h4>
                {outgoing.map((request) => (
                  <div
                    key={request.id}
                    className="flex items-center space-x-3 p-2.5 rounded-xl bg-dark-panel/60 border border-dark-border min-w-0"
                  >
                    <Avatar
                      userId={request.recipient.id}
                      username={request.recipient.username}
                      hasAvatar={request.recipient.hasAvatar}
                      avatarUpdatedAt={request.recipient.avatarUpdatedAt}
                      className="w-9 h-9 text-xs shadow-sm"
                    />
                    <div className="flex-1 min-w-0">
                      <span className="font-semibold text-xs sm:text-sm text-content-primary truncate block">
                        {request.recipient.username}
                      </span>
                      <span className="inline-flex items-center space-x-1 text-[10px] text-status-warning">
                        <Clock className="w-3 h-3" />
                        <span>Requested</span>
                      </span>
                    </div>
                    <button
                      onClick={() => handleCancel(request)}
                      disabled={actionKey !== null}
                      title="Cancel contact request"
                      className="p-1.5 text-content-secondary hover:text-status-error hover:bg-status-error/10 rounded-lg transition-all disabled:opacity-50 flex-shrink-0"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </section>
            )}

            {/* Accepted contacts */}
            <section className="space-y-1.5">
              <h4 className="flex items-center space-x-1.5 text-[11px] font-semibold uppercase tracking-wider text-content-muted px-1">
                <Users className="w-3 h-3" />
                <span>Contacts ({contacts.length})</span>
              </h4>

              {contacts.length === 0 ? (
                <div className="px-3 py-8 text-center space-y-2">
                  <div className="w-11 h-11 mx-auto rounded-2xl bg-dark-panel border border-dark-border flex items-center justify-center text-violet-lavender shadow-sm">
                    <UserPlus className="w-5 h-5 text-violet-primary" />
                  </div>
                  <p className="text-xs font-semibold text-content-primary">No contacts yet</p>
                  <p className="text-[11px] text-content-secondary leading-relaxed">
                    Search for a Fairish User ID above and send a contact request. Chat unlocks after acceptance.
                  </p>
                </div>
              ) : (
                contacts.map((contact) => (
                  <div
                    key={contact.user.id}
                    className="flex items-center space-x-3 p-2.5 rounded-xl bg-dark-panel/60 hover:bg-dark-panel border border-transparent hover:border-dark-border transition-all min-w-0"
                  >
                    <Avatar
                      userId={contact.user.id}
                      username={contact.user.username}
                      hasAvatar={contact.user.hasAvatar}
                      avatarUpdatedAt={contact.user.avatarUpdatedAt}
                      className="w-9 h-9 text-xs shadow-sm"
                    />
                    <div className="flex-1 min-w-0">
                      <span className="font-semibold text-xs sm:text-sm text-content-primary truncate block">
                        {contact.user.username}
                      </span>
                      <span className="text-[10px] text-content-secondary truncate block">
                        {contact.user.bio || 'Accepted contact'}
                      </span>
                    </div>
                    <button
                      onClick={() => onStartConversationWithUser(contact.user)}
                      title="Chat with contact"
                      className="p-1.5 text-violet-lavender hover:bg-violet-primary/15 rounded-lg transition-all flex-shrink-0"
                    >
                      <MessageSquare className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleRemove(contact.user)}
                      disabled={actionKey !== null}
                      title="Remove contact"
                      className="p-1.5 text-content-secondary hover:text-status-error hover:bg-status-error/10 rounded-lg transition-all disabled:opacity-50 flex-shrink-0"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))
              )}
            </section>
          </>
        )}
      </div>
    </div>
  );
};
