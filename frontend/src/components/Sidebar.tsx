import React, { useCallback, useState } from 'react';
import { MessageSquare, Users, RefreshCw, UserPlus } from 'lucide-react';
import { PublicUser } from '../types/user';
import { Conversation } from '../types/conversation';
import { LoadingSpinner } from './LoadingSpinner';
import { Avatar } from './Avatar';
import { ContactsPanel } from './ContactsPanel';

interface SidebarProps {
  conversations: Conversation[];
  selectedConversationId?: string | null;
  onSelectConversation: (conversation: Conversation) => void;
  onStartConversationWithUser: (user: PublicUser) => void;
  isLoadingConversations: boolean;
  onRefreshConversations: () => void;
  onContactsChanged: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  conversations,
  selectedConversationId,
  onSelectConversation,
  onStartConversationWithUser,
  isLoadingConversations,
  onRefreshConversations,
  onContactsChanged,
}) => {
  const [activeTab, setActiveTab] = useState<'conversations' | 'contacts'>('conversations');
  const [pendingRequestCount, setPendingRequestCount] = useState(0);

  const handlePendingCountChange = useCallback((count: number) => {
    setPendingRequestCount(count);
  }, []);

  const formatMessageTime = (dateStr?: string | null) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const isToday =
      date.getDate() === now.getDate() &&
      date.getMonth() === now.getMonth() &&
      date.getFullYear() === now.getFullYear();

    if (isToday) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  };

  return (
    <aside
      className={`border-r border-dark-border bg-dark-sidebar flex-col h-full flex-shrink-0 w-full md:w-80 lg:w-88 min-h-0 ${
        selectedConversationId ? 'hidden md:flex' : 'flex'
      }`}
    >
      {/* Segmented Tab Navigation */}
      <div className="p-3 border-b border-dark-border flex items-center justify-between gap-2 flex-shrink-0 bg-dark-sidebar">
        <div className="flex bg-dark-panel p-1 rounded-xl border border-dark-border flex-1 min-w-0">
          <button
            onClick={() => setActiveTab('conversations')}
            className={`flex-1 flex items-center justify-center space-x-1.5 py-1.5 px-3 rounded-lg text-xs font-semibold transition-all duration-200 truncate ${
              activeTab === 'conversations'
                ? 'bg-violet-primary text-white shadow-violet-sm'
                : 'text-content-secondary hover:text-content-primary hover:bg-dark-hover'
            }`}
          >
            <MessageSquare className="w-3.5 h-3.5 flex-shrink-0" />
            <span className="truncate">Chats</span>
            {conversations.length > 0 && (
              <span
                className={`ml-1 px-1.5 py-0.5 rounded-full text-[10px] font-bold flex-shrink-0 ${
                  activeTab === 'conversations'
                    ? 'bg-violet-hover text-white'
                    : 'bg-dark-border text-content-secondary'
                }`}
              >
                {conversations.length}
              </span>
            )}
          </button>

          <button
            onClick={() => setActiveTab('contacts')}
            className={`flex-1 flex items-center justify-center space-x-1.5 py-1.5 px-3 rounded-lg text-xs font-semibold transition-all duration-200 truncate ${
              activeTab === 'contacts'
                ? 'bg-violet-primary text-white shadow-violet-sm'
                : 'text-content-secondary hover:text-content-primary hover:bg-dark-hover'
            }`}
          >
            <Users className="w-3.5 h-3.5 flex-shrink-0" />
            <span className="truncate">Contacts</span>
            {pendingRequestCount > 0 && (
              <span
                className={`ml-1 min-w-[1.1rem] h-[1.1rem] px-1 rounded-full text-[10px] font-bold flex items-center justify-center flex-shrink-0 ${
                  activeTab === 'contacts'
                    ? 'bg-white text-violet-primary'
                    : 'bg-status-warning text-dark-bg'
                }`}
                aria-label={`${pendingRequestCount} pending contact requests`}
              >
                {pendingRequestCount}
              </span>
            )}
          </button>
        </div>

        {activeTab === 'conversations' && (
          <button
            onClick={onRefreshConversations}
            disabled={isLoadingConversations}
            title="Refresh conversation list"
            aria-label="Refresh conversations"
            className="p-2 text-content-secondary hover:text-violet-lavender hover:bg-dark-panel rounded-xl border border-transparent hover:border-dark-border transition-all disabled:opacity-50 flex-shrink-0"
          >
            <RefreshCw className={`w-4 h-4 ${isLoadingConversations ? 'animate-spin text-violet-primary' : ''}`} />
          </button>
        )}
      </div>

      {/* Tab: Contacts (search, requests, accepted contacts) */}
      {activeTab === 'contacts' ? (
        <ContactsPanel
          onContactsChanged={onContactsChanged}
          onStartConversationWithUser={onStartConversationWithUser}
          onPendingCountChange={handlePendingCountChange}
        />
      ) : (
        /* Tab: Active Conversations */
        <div className="flex-1 overflow-y-auto p-2.5 sm:p-3 space-y-1.5 min-h-0 bg-dark-sidebar">
          {isLoadingConversations && conversations.length === 0 ? (
            <div className="py-14">
              <LoadingSpinner size="sm" text="Loading chats..." />
            </div>
          ) : conversations.length > 0 ? (
            conversations.map((c) => {
              const otherUser = c.otherUser;
              const isSelected = selectedConversationId === c.id;
              const hasLastMessage = Boolean(c.lastMessageText);

              return (
                <button
                  key={c.id}
                  onClick={() => onSelectConversation(c)}
                  className={`w-full flex items-center space-x-3 p-2.5 sm:p-3 rounded-2xl text-left transition-all duration-150 min-w-0 ${
                    isSelected
                      ? 'bg-dark-panel border border-violet-primary/40 text-content-primary shadow-violet-sm'
                      : 'hover:bg-dark-hover border border-transparent text-content-secondary hover:text-content-primary'
                  }`}
                >
                  {/* User Avatar */}
                  <Avatar
                    userId={otherUser?.id}
                    username={otherUser?.username}
                    hasAvatar={otherUser?.hasAvatar}
                    avatarUpdatedAt={otherUser?.avatarUpdatedAt}
                    className="w-10 h-10 sm:w-11 sm:h-11 text-xs font-bold shadow-sm"
                  />

                  {/* Conversation Preview */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-0.5 gap-1">
                      <span className={`font-semibold text-xs sm:text-sm truncate ${isSelected ? 'text-content-primary font-bold' : 'text-slate-200'}`}>
                        {otherUser?.username || 'Chat Participant'}
                      </span>
                      {c.lastMessageAt && (
                        <span className="text-[10px] text-content-muted flex-shrink-0">
                          {formatMessageTime(c.lastMessageAt)}
                        </span>
                      )}
                    </div>
                    <p className={`text-xs truncate ${hasLastMessage ? 'text-content-secondary' : 'text-content-muted italic'}`}>
                      {c.lastMessageText || 'No messages yet'}
                    </p>
                  </div>
                </button>
              );
            })
          ) : (
            <div className="px-4 py-12 text-center space-y-4">
              <div className="w-12 h-12 mx-auto rounded-2xl bg-dark-panel border border-dark-border flex items-center justify-center text-violet-lavender shadow-sm">
                <MessageSquare className="w-6 h-6 text-violet-primary" />
              </div>
              <div className="space-y-1">
                <h4 className="text-sm font-semibold text-content-primary">No conversations yet</h4>
                <p className="text-xs text-content-secondary leading-relaxed max-w-xs mx-auto">
                  Add a contact first. Chat unlocks once they accept your request.
                </p>
              </div>
              <button
                onClick={() => setActiveTab('contacts')}
                className="inline-flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-violet-primary hover:bg-violet-hover text-white text-xs font-semibold shadow-violet-sm transition-all"
              >
                <UserPlus className="w-3.5 h-3.5" />
                <span>Manage Contacts</span>
              </button>
            </div>
          )}
        </div>
      )}
    </aside>
  );
};
