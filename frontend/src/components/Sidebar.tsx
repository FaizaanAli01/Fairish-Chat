import React, { useState, useEffect } from 'react';
import { Search, MessageSquare, User as UserIcon, Users, RefreshCw, Sparkles, UserPlus } from 'lucide-react';
import { userApi } from '../api/userApi';
import { PublicUser } from '../types/user';
import { Conversation } from '../types/conversation';
import { LoadingSpinner } from './LoadingSpinner';

interface SidebarProps {
  conversations: Conversation[];
  selectedConversationId?: string | null;
  onSelectConversation: (conversation: Conversation) => void;
  onStartConversationWithUser: (user: PublicUser) => void;
  isLoadingConversations: boolean;
  onRefreshConversations: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  conversations,
  selectedConversationId,
  onSelectConversation,
  onStartConversationWithUser,
  isLoadingConversations,
  onRefreshConversations,
}) => {
  const [activeTab, setActiveTab] = useState<'conversations' | 'users'>('conversations');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<PublicUser[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  // Search users debounce
  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults([]);
      return;
    }

    const timer = setTimeout(async () => {
      setIsSearching(true);
      try {
        const results = await userApi.searchUsers(searchQuery);
        setSearchResults(results);
      } catch (err) {
        console.error('Failed to search users:', err);
      } finally {
        setIsSearching(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchQuery]);

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

  const getInitials = (name?: string) => {
    if (!name) return 'U';
    return name.slice(0, 2).toUpperCase();
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
            onClick={() => setActiveTab('users')}
            className={`flex-1 flex items-center justify-center space-x-1.5 py-1.5 px-3 rounded-lg text-xs font-semibold transition-all duration-200 truncate ${
              activeTab === 'users'
                ? 'bg-violet-primary text-white shadow-violet-sm'
                : 'text-content-secondary hover:text-content-primary hover:bg-dark-hover'
            }`}
          >
            <Users className="w-3.5 h-3.5 flex-shrink-0" />
            <span className="truncate">Find Users</span>
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

      {/* Tab: Users Directory (Search) */}
      {activeTab === 'users' ? (
        <div className="flex-1 flex flex-col overflow-hidden min-h-0 bg-dark-sidebar">
          {/* Search Input Container */}
          <div className="p-3 border-b border-dark-border flex-shrink-0">
            <div className="relative">
              <Search className="w-4 h-4 text-content-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                type="text"
                placeholder="Search registered users..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                autoFocus
                className="w-full pl-9 pr-3.5 py-2.5 bg-dark-panel border border-dark-border rounded-xl text-xs sm:text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-violet-primary focus:ring-1 focus:ring-violet-primary transition-all"
              />
            </div>
          </div>

          {/* Search Results Area */}
          <div className="flex-1 overflow-y-auto p-3 space-y-1.5 min-h-0">
            {isSearching ? (
              <div className="py-10">
                <LoadingSpinner size="sm" text="Searching users..." />
              </div>
            ) : searchQuery.trim() ? (
              searchResults.length > 0 ? (
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-content-muted px-2 py-1 mb-1">
                    Matching Users ({searchResults.length})
                  </p>
                  <div className="space-y-1">
                    {searchResults.map((u) => (
                      <button
                        key={u.id}
                        onClick={() => onStartConversationWithUser(u)}
                        className="w-full flex items-center space-x-3 p-2.5 sm:p-3 rounded-xl text-left bg-dark-panel/60 hover:bg-dark-panel border border-transparent hover:border-dark-border transition-all group min-w-0"
                      >
                        <div className="w-10 h-10 rounded-full bg-dark-bg border border-dark-border flex items-center justify-center overflow-hidden flex-shrink-0 text-xs font-semibold text-violet-lavender shadow-sm">
                          {u.profileImage ? (
                            <img src={u.profileImage} alt={u.username} className="w-full h-full object-cover" />
                          ) : (
                            <span>{getInitials(u.username)}</span>
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className="font-semibold text-xs sm:text-sm text-content-primary truncate block group-hover:text-violet-lavender transition-colors">
                            {u.username}
                          </span>
                          <p className="text-[11px] text-content-secondary truncate mt-0.5">
                            {u.bio || 'No bio yet'}
                          </p>
                        </div>
                        <span className="text-xs font-semibold text-violet-lavender bg-violet-primary/10 border border-violet-primary/20 px-2.5 py-1 rounded-lg opacity-90 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity flex-shrink-0">
                          Chat
                        </span>
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="py-12 text-center text-content-muted text-xs space-y-2">
                  <UserIcon className="w-8 h-8 mx-auto text-content-muted/60" />
                  <p>No registered users found matching "{searchQuery}"</p>
                </div>
              )
            ) : (
              <div className="px-3 py-10 text-center space-y-3.5">
                <div className="w-12 h-12 mx-auto rounded-2xl bg-dark-panel border border-dark-border flex items-center justify-center text-violet-lavender shadow-sm">
                  <Sparkles className="w-6 h-6 text-violet-primary" />
                </div>
                <div className="space-y-1">
                  <h4 className="text-xs sm:text-sm font-semibold text-content-primary">Search User Directory</h4>
                  <p className="text-xs text-content-secondary max-w-xs mx-auto leading-relaxed">
                    Type a username to find other registered users and start an instant conversation.
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
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
                  <div className="w-10 h-10 sm:w-11 sm:h-11 rounded-full bg-dark-bg border border-dark-border flex items-center justify-center overflow-hidden flex-shrink-0 relative shadow-sm text-xs font-bold text-violet-lavender">
                    {otherUser?.profileImage ? (
                      <img src={otherUser.profileImage} alt={otherUser.username} className="w-full h-full object-cover" />
                    ) : (
                      <span>{getInitials(otherUser?.username)}</span>
                    )}
                  </div>

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
                  Find a colleague or registered friend to start your first chat.
                </p>
              </div>
              <button
                onClick={() => setActiveTab('users')}
                className="inline-flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-violet-primary hover:bg-violet-hover text-white text-xs font-semibold shadow-violet-sm transition-all"
              >
                <UserPlus className="w-3.5 h-3.5" />
                <span>Find Someone</span>
              </button>
            </div>
          )}
        </div>
      )}
    </aside>
  );
};
