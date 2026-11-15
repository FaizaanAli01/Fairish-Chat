import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Navbar } from '../components/Navbar';
import { Sidebar } from '../components/Sidebar';
import { useAuth } from '../context/AuthContext';
import { conversationApi } from '../api/conversationApi';
import { messageApi } from '../api/messageApi';
import { websocketService } from '../api/websocketService';
import { Conversation, UnreadCountUpdate } from '../types/conversation';
import {
  Message,
  MessageReceiptUpdate,
  resolveMessageStatus,
} from '../types/message';
import { PublicUser } from '../types/user';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { ErrorMessage } from '../components/ErrorMessage';
import { Avatar } from '../components/Avatar';
import {
  MessageSquare,
  Send,
  RefreshCw,
  Clock,
  CheckCircle2,
  ArrowLeft,
  Check,
  CheckCheck,
} from 'lucide-react';

// Maximum composer height before it starts scrolling vertically.
const COMPOSER_MAX_HEIGHT = 144;

// Parses a server timestamp for safe ordering comparisons. ISO strings must not
// be compared lexicographically because fractional-second digits are optional.
const toMillis = (value: string | null | undefined): number => {
  if (!value) return 0;
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? 0 : parsed;
};

export const DashboardPage: React.FC = () => {
  const { user, token } = useAuth();

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);

  const [isLoadingConversations, setIsLoadingConversations] = useState<boolean>(true);
  const [isLoadingMessages, setIsLoadingMessages] = useState<boolean>(false);
  const [isLoadingOlder, setIsLoadingOlder] = useState<boolean>(false);
  const [isSending, setIsSending] = useState<boolean>(false);

  const [currentPage, setCurrentPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(false);
  const [messageInput, setMessageInput] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const messageContainerRef = useRef<HTMLDivElement | null>(null);
  const selectedConversationRef = useRef<Conversation | null>(null);
  const messageInputRef = useRef<HTMLTextAreaElement | null>(null);
  // Batched delivery acknowledgements, grouped by conversation. Keeping this in
  // a ref avoids re-rendering for every incoming message.
  const pendingDeliveryAcksRef = useRef<Map<string, Set<string>>>(new Map());
  const deliveryTimerRef = useRef<number | null>(null);

  // Keep selectedConversationRef in sync with state for WebSocket message callbacks
  useEffect(() => {
    selectedConversationRef.current = selectedConversation;
  }, [selectedConversation]);

  // Grow the composer with its content up to the maximum height, then scroll.
  useEffect(() => {
    const textarea = messageInputRef.current;
    if (!textarea) return;

    const previousScrollTop = textarea.scrollTop;
    const wasAtBottom =
      previousScrollTop + textarea.clientHeight >= textarea.scrollHeight - 1;

    textarea.style.height = 'auto';
    const nextHeight = Math.min(textarea.scrollHeight, COMPOSER_MAX_HEIGHT);
    textarea.style.height = `${nextHeight}px`;
    textarea.style.overflowY = textarea.scrollHeight > COMPOSER_MAX_HEIGHT ? 'auto' : 'hidden';

    // Collapsing the textarea to measure its content can reset scrollTop.
    // Keep the caret visible when typing at the end, otherwise preserve the
    // user's current scroll position while editing a long message.
    if (textarea.scrollHeight > textarea.clientHeight) {
      textarea.scrollTop = wasAtBottom ? textarea.scrollHeight : previousScrollTop;
    } else {
      textarea.scrollTop = 0;
    }
  }, [messageInput, selectedConversation?.id]);

  // Connect STOMP WebSocket client when JWT token is available; cleanly disconnect on unmount or token change
  useEffect(() => {
    if (token) {
      websocketService.connect(token);
    } else {
      websocketService.disconnect();
    }
    return () => {
      websocketService.disconnect();
    };
  }, [token]);

  // Scroll to bottom helper
  const scrollToBottom = (behavior: ScrollBehavior = 'smooth') => {
    messagesEndRef.current?.scrollIntoView({ behavior });
  };

  // Send all queued delivery acknowledgements, one request per conversation.
  const flushDeliveryAcks = useCallback(async () => {
    const pending = pendingDeliveryAcksRef.current;
    pendingDeliveryAcksRef.current = new Map();

    for (const [conversationId, ids] of pending.entries()) {
      if (ids.size === 0) continue;
      try {
        await messageApi.markDelivered(conversationId, Array.from(ids));
      } catch (err) {
        // Receipts are reconciled from history after a reconnect, so a failed
        // acknowledgement is recoverable and must not surface as a chat error.
        console.error('Failed to acknowledge message delivery:', err);
      }
    }
  }, []);

  // Coalesce rapid incoming messages into one request per conversation.
  const queueDeliveryAcks = useCallback((conversationId: string, messageIds: string[]) => {
    if (!conversationId || messageIds.length === 0) return;

    let pending = pendingDeliveryAcksRef.current.get(conversationId);
    if (!pending) {
      pending = new Set<string>();
      pendingDeliveryAcksRef.current.set(conversationId, pending);
    }
    messageIds.forEach((id) => {
      if (id) pending!.add(id);
    });

    if (deliveryTimerRef.current !== null) return;
    deliveryTimerRef.current = window.setTimeout(() => {
      deliveryTimerRef.current = null;
      void flushDeliveryAcks();
    }, 400);
  }, [flushDeliveryAcks]);

  // Cancel a pending batch on unmount; the next reconnect reconciliation will
  // re-send anything that was not acknowledged.
  useEffect(() => {
    return () => {
      if (deliveryTimerRef.current !== null) {
        window.clearTimeout(deliveryTimerRef.current);
        deliveryTimerRef.current = null;
      }
    };
  }, []);

  // Real-time message handler
  const handleIncomingMessage = useCallback((incoming: Message) => {
    // Acknowledge every message from the other participant, including ones in
    // inactive conversations: the client is subscribed to all conversations.
    if (user?.id && incoming.senderId !== user.id) {
      queueDeliveryAcks(incoming.conversationId, [incoming.id]);
    }

    // If incoming message belongs to the currently active conversation, append it
    if (selectedConversationRef.current && incoming.conversationId === selectedConversationRef.current.id) {
      setMessages((prev) => {
        // Prevent duplicate messages
        if (prev.some((m) => m.id === incoming.id)) {
          return prev;
        }
        return [...prev, incoming];
      });
      setTimeout(() => scrollToBottom('smooth'), 50);
    }

    // Update conversation summary in sidebar
    setConversations((prev) => {
      const updated = prev.map((c) => {
        if (c.id === incoming.conversationId) {
          return {
            ...c,
            lastMessageText: incoming.content,
            lastMessageSenderId: incoming.senderId,
            lastMessageAt: incoming.createdAt,
            updatedAt: incoming.createdAt,
          };
        }
        return c;
      });

      // Move active conversation to top of list
      const activeIdx = updated.findIndex((c) => c.id === incoming.conversationId);
      if (activeIdx > 0) {
        const [activeConv] = updated.splice(activeIdx, 1);
        return [activeConv, ...updated];
      }
      return updated;
    });
  }, [queueDeliveryAcks, user?.id]);

  // Compute a unique, sorted comma-separated string of all conversation IDs to monitor.
  const subscribedConversationIdsKey = Array.from(
    new Set([
      ...conversations.map((c) => c.id),
      ...(selectedConversation ? [selectedConversation.id] : []),
    ])
  )
    .filter(Boolean)
    .sort()
    .join(',');

  // Subscribe to all conversations without duplicate subscriptions
  useEffect(() => {
    if (!subscribedConversationIdsKey) return;

    const conversationIds = subscribedConversationIdsKey.split(',');
    const unsubs = conversationIds.map((id) =>
      websocketService.subscribeToConversation(id, handleIncomingMessage)
    );

    return () => {
      unsubs.forEach((unsub) => unsub());
    };
  }, [subscribedConversationIdsKey, handleIncomingMessage]);

  // Optimistically clear the badge, then persist the read state. The backend
  // pushes the same reset to every session of this user.
  const markConversationRead = useCallback(async (conversationId: string) => {
    setConversations((prev) =>
      prev.map((c) => (c.id === conversationId ? { ...c, unreadCount: 0 } : c))
    );
    setSelectedConversation((prev) =>
      prev && prev.id === conversationId ? { ...prev, unreadCount: 0 } : prev
    );

    try {
      await conversationApi.markAsRead(conversationId);
    } catch (err) {
      console.error('Failed to mark conversation as read:', err);
    }
  }, []);

  // Load conversations list
  const fetchConversations = useCallback(async () => {
    setIsLoadingConversations(true);
    setError(null);
    try {
      const data = await conversationApi.getConversations();
      setConversations(data);

      // If we currently have a selected conversation, refresh its reference.
      // If it is no longer visible (for example the contact was removed), close
      // the chat so revoked messaging access cannot continue in the UI.
      if (selectedConversationRef.current) {
        const updated = data.find((c) => c.id === selectedConversationRef.current?.id);
        if (updated) {
          // An open conversation is being viewed, so its badge must stay at
          // zero even if a refresh briefly returns a stale count.
          setSelectedConversation({ ...updated, unreadCount: 0 });
          if ((updated.unreadCount ?? 0) > 0) {
            markConversationRead(updated.id);
          }
        } else {
          setSelectedConversation(null);
          setMessages([]);
          setHasMore(false);
          setCurrentPage(0);
        }
      }

      return data;
    } catch (err: any) {
      console.error('Failed to load conversations:', err);
      setError(err.response?.data?.message || 'Failed to load conversations. Please try again.');
      return null;
    } finally {
      setIsLoadingConversations(false);
    }
  }, [markConversationRead]);

  useEffect(() => {
    fetchConversations();
  }, [fetchConversations]);

  // Authoritative unread updates for every conversation of this user.
  const handleUnreadUpdate = useCallback((update: UnreadCountUpdate) => {
    const isActive = selectedConversationRef.current?.id === update.conversationId;
    const unreadCount = isActive ? 0 : Math.max(update.unreadCount, 0);

    setConversations((prev) =>
      prev.map((c) => (c.id === update.conversationId ? { ...c, unreadCount } : c))
    );
    setSelectedConversation((prev) =>
      prev && prev.id === update.conversationId ? { ...prev, unreadCount } : prev
    );

    // A message can arrive while its conversation is open; treat it as viewed
    // and reset the persisted count for all of this user's sessions.
    if (isActive && update.unreadCount > 0) {
      markConversationRead(update.conversationId);
    }
  }, [markConversationRead]);

  useEffect(() => {
    return websocketService.subscribeToUnread(handleUnreadUpdate);
  }, [handleUnreadUpdate]);

  // Private receipt events for messages this user sent. Updates are monotonic:
  // a late DELIVERED event can never downgrade a SEEN message.
  const handleReceiptUpdate = useCallback((update: MessageReceiptUpdate) => {
    setMessages((prev) => {
      if (prev.length === 0) return prev;

      let changed = false;
      const next = prev.map((msg) => {
        if (msg.conversationId !== update.conversationId || msg.senderId !== user?.id) {
          return msg;
        }

        if (update.status === 'DELIVERED') {
          if (!update.messageIds || !update.messageIds.includes(msg.id)) return msg;
          if (resolveMessageStatus(msg) === 'SEEN') return msg;

          const occurredAt = toMillis(update.occurredAt);
          const existingDeliveredAt = toMillis(msg.deliveredAt);
          if (resolveMessageStatus(msg) === 'DELIVERED' && existingDeliveredAt >= occurredAt) {
            return msg;
          }

          const deliveredAt =
            existingDeliveredAt > occurredAt ? msg.deliveredAt! : update.occurredAt;
          changed = true;
          return { ...msg, deliveredAt, status: 'DELIVERED' as const };
        }

        if (update.status === 'SEEN') {
          // SEEN is a conversation-level watermark: every outgoing message
          // created at or before the server timestamp is considered seen.
          const occurredAt = toMillis(update.occurredAt);
          if (toMillis(msg.createdAt) > occurredAt) return msg;
          if (resolveMessageStatus(msg) === 'SEEN' && msg.seenAt) return msg;

          const existingSeenAt = toMillis(msg.seenAt);
          const seenAt = existingSeenAt > occurredAt ? msg.seenAt! : update.occurredAt;
          changed = true;
          return {
            ...msg,
            deliveredAt: msg.deliveredAt ?? update.occurredAt,
            seenAt,
            status: 'SEEN' as const,
          };
        }

        return msg;
      });

      return changed ? next : prev;
    });
  }, [user?.id]);

  useEffect(() => {
    return websocketService.subscribeToReceipts(handleReceiptUpdate);
  }, [handleReceiptUpdate]);

  // Contact mutations (accept, remove, ...) change who may chat; reload the
  // conversation list so the sidebar and open chat reflect the new access.
  const handleContactsChanged = useCallback(async () => {
    await fetchConversations();
  }, [fetchConversations]);

  // Load initial messages for selected conversation
  const loadConversationMessages = useCallback(async (conversationId: string) => {
    setIsLoadingMessages(true);
    setError(null);
    setCurrentPage(0);
    try {
      const data = await messageApi.getMessages(conversationId, 0, 30);
      // Backend returns page 0 with newest messages first (descending). Reverse to show chronologically (oldest top, newest bottom).
      const chronological = [...data.content].reverse();
      setMessages(chronological);
      setHasMore(data.hasMore);

      // Messages received while this device was offline are only SENT until a
      // history fetch observes them, so acknowledge them in one batch now.
      const undeliveredIds = chronological
        .filter((m) => m.senderId !== user?.id && !m.deliveredAt)
        .map((m) => m.id);
      queueDeliveryAcks(conversationId, undeliveredIds);

      setTimeout(() => scrollToBottom('auto'), 50);
    } catch (err: any) {
      console.error('Failed to load messages:', err);
      setError(err.response?.data?.message || 'Failed to load messages. Please try again.');
    } finally {
      setIsLoadingMessages(false);
    }
  }, [queueDeliveryAcks, user?.id]);

  // After a reconnect, re-fetch conversations and acknowledge anything that
  // was persisted while the socket was down. Only the latest page per
  // conversation is needed: older messages were already acknowledged.
  const reconcileAfterReconnect = useCallback(async () => {
    const data = await fetchConversations();
    if (!data) return;

    await Promise.allSettled(
      data.map(async (conversation) => {
        const latest = await messageApi.getMessages(conversation.id, 0, 30);
        const undeliveredIds = latest.content
          .filter((m) => m.senderId !== user?.id && !m.deliveredAt)
          .map((m) => m.id);
        if (undeliveredIds.length > 0) {
          await messageApi.markDelivered(conversation.id, undeliveredIds);
        }
      })
    );

    // Reload the open conversation so missed messages and authoritative
    // receipt timestamps replace the stale local copy.
    const active = selectedConversationRef.current;
    if (active) {
      await loadConversationMessages(active.id);
    }
  }, [fetchConversations, loadConversationMessages, user?.id]);

  useEffect(() => {
    return websocketService.onConnect(() => {
      void reconcileAfterReconnect();
    });
  }, [reconcileAfterReconnect]);

  // When selected conversation changes
  const handleSelectConversation = (conversation: Conversation) => {
    setSelectedConversation({ ...conversation, unreadCount: 0 });
    markConversationRead(conversation.id);
    loadConversationMessages(conversation.id);
  };

  // Start or open conversation from User Search in Sidebar
  const handleStartConversationWithUser = async (targetUser: PublicUser) => {
    setError(null);
    try {
      const conversation = await conversationApi.createOrGetConversation(targetUser.id);
      const enriched: Conversation = {
        ...conversation,
        otherUser: conversation.otherUser || targetUser,
        unreadCount: 0,
      };

      setConversations((prev) => {
        const exists = prev.some((c) => c.id === enriched.id);
        if (exists) {
          return prev.map((c) => (c.id === enriched.id ? enriched : c));
        }
        return [enriched, ...prev];
      });

      setSelectedConversation(enriched);
      markConversationRead(enriched.id);
      loadConversationMessages(enriched.id);
    } catch (err: any) {
      console.error('Failed to start conversation:', err);
      setError(err.response?.data?.message || 'Failed to start conversation. Please try again.');
    }
  };

  // Load older messages (pagination)
  const handleLoadOlderMessages = async () => {
    if (!selectedConversation || !hasMore || isLoadingOlder) return;

    setIsLoadingOlder(true);
    const nextPage = currentPage + 1;
    try {
      const prevScrollHeight = messageContainerRef.current?.scrollHeight || 0;

      const data = await messageApi.getMessages(selectedConversation.id, nextPage, 30);
      const olderChronological = [...data.content].reverse();

      setMessages((prev) => [...olderChronological, ...prev]);
      setCurrentPage(nextPage);
      setHasMore(data.hasMore);

      // Preserve scroll position after prepending older messages
      setTimeout(() => {
        if (messageContainerRef.current) {
          const newScrollHeight = messageContainerRef.current.scrollHeight;
          messageContainerRef.current.scrollTop = newScrollHeight - prevScrollHeight;
        }
      }, 50);
    } catch (err: any) {
      console.error('Failed to load older messages:', err);
      setError(err.response?.data?.message || 'Failed to load older messages.');
    } finally {
      setIsLoadingOlder(false);
    }
  };

  // Send message via WebSocket with REST fallback
  const handleSendMessage = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!selectedConversation || !messageInput.trim() || isSending) return;

    const contentToSend = messageInput.trim();
    if (contentToSend.length > 2000) {
      setError('Message cannot exceed 2000 characters.');
      return;
    }

    setIsSending(true);
    setError(null);
    setMessageInput('');

    try {
      // 1. Try sending via WebSocket STOMP
      const sentViaWs = websocketService.sendMessage(selectedConversation.id, contentToSend);

      if (!sentViaWs) {
        // 2. Fallback to REST API if WebSocket is currently disconnected
        const newMessage = await messageApi.sendMessage(selectedConversation.id, contentToSend);

        setMessages((prev) => {
          if (prev.some((m) => m.id === newMessage.id)) {
            return prev;
          }
          return [...prev, newMessage];
        });

        setConversations((prev) => {
          const updated = prev.map((c) => {
            if (c.id === selectedConversation.id) {
              return {
                ...c,
                lastMessageText: newMessage.content,
                lastMessageSenderId: newMessage.senderId,
                lastMessageAt: newMessage.createdAt,
                updatedAt: newMessage.createdAt,
              };
            }
            return c;
          });

          const activeIdx = updated.findIndex((c) => c.id === selectedConversation.id);
          if (activeIdx > 0) {
            const [activeConv] = updated.splice(activeIdx, 1);
            return [activeConv, ...updated];
          }
          return updated;
        });

        setTimeout(() => scrollToBottom('smooth'), 50);
      }
    } catch (err: any) {
      console.error('Failed to send message:', err);
      setError(err.response?.data?.message || 'Failed to send message. Please try again.');
      setMessageInput(contentToSend);
    } finally {
      setIsSending(false);
    }
  };

  // Enter sends; Shift+Enter inserts a newline. Ignore Enter while an IME
  // composition is active so non-Latin input methods can commit candidates.
  const handleComposerKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key !== 'Enter' || e.shiftKey || e.nativeEvent.isComposing) return;
    e.preventDefault();
    void handleSendMessage();
  };

  // Timestamp formatter
  const formatTime = (dateStr: string) => {
    const date = new Date(dateStr);
    const now = new Date();

    const isToday =
      date.getDate() === now.getDate() &&
      date.getMonth() === now.getMonth() &&
      date.getFullYear() === now.getFullYear();

    if (isToday) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  };

  const otherUser = selectedConversation?.otherUser;

  return (
    <div className="h-screen h-[100dvh] max-h-[100dvh] bg-dark-bg flex flex-col overflow-hidden">
      <Navbar />

      <div className="flex-1 flex overflow-hidden min-h-0 w-full relative">
        {/* Left Sidebar: Conversations & User Directory */}
        <Sidebar
          conversations={conversations}
          selectedConversationId={selectedConversation?.id}
          onSelectConversation={handleSelectConversation}
          onStartConversationWithUser={handleStartConversationWithUser}
          isLoadingConversations={isLoadingConversations}
          onRefreshConversations={fetchConversations}
          onContactsChanged={handleContactsChanged}
        />

        {/* Main Chat Content Panel */}
        <main
          className={`flex-1 flex-col bg-dark-bg overflow-hidden relative min-w-0 w-full h-full min-h-0 ${
            selectedConversation ? 'flex' : 'hidden md:flex'
          }`}
        >
          {selectedConversation ? (
            /* Selected Conversation Chat Area */
            <div className="flex-1 flex flex-col h-full min-h-0 w-full">
              {/* Chat Header */}
              <div className="px-3 sm:px-6 py-3 border-b border-dark-border bg-dark-sidebar/90 backdrop-blur flex items-center justify-between gap-2 min-w-0 flex-shrink-0">
                <div className="flex items-center space-x-2.5 sm:space-x-3.5 min-w-0 flex-1">
                  {/* Back button (Mobile view < 768px) */}
                  <button
                    onClick={() => setSelectedConversation(null)}
                    aria-label="Back to conversations"
                    className="md:hidden p-2 -ml-1 text-content-secondary hover:text-content-primary hover:bg-dark-hover rounded-xl transition-all flex-shrink-0"
                  >
                    <ArrowLeft className="w-5 h-5" />
                  </button>

                  {/* Avatar / Initials */}
                  <Avatar
                    userId={otherUser?.id}
                    username={otherUser?.username}
                    hasAvatar={otherUser?.hasAvatar}
                    avatarUpdatedAt={otherUser?.avatarUpdatedAt}
                    className="w-9 h-9 sm:w-10 sm:h-10 text-xs font-bold shadow-sm"
                  />

                  <div className="min-w-0 flex-1">
                    <h3 className="text-sm font-bold text-content-primary truncate flex items-center space-x-1.5 sm:space-x-2">
                      <span className="truncate">{otherUser?.username || 'Chat Participant'}</span>
                      <span className="hidden xs:inline-flex items-center text-[10px] text-status-success bg-status-success/10 px-1.5 py-0.5 rounded-full border border-status-success/20 font-medium flex-shrink-0">
                        <CheckCircle2 className="w-2.5 h-2.5 mr-1" />
                        Contact
                      </span>
                    </h3>
                    {otherUser?.bio?.trim() && (
                      <p className="text-xs text-content-secondary truncate mt-0.5">
                        {otherUser.bio}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex items-center space-x-1 sm:space-x-2 flex-shrink-0">
                  <button
                    onClick={() => loadConversationMessages(selectedConversation.id)}
                    disabled={isLoadingMessages}
                    title="Reload message history"
                    aria-label="Reload messages"
                    className="p-2 text-content-secondary hover:text-violet-lavender hover:bg-dark-panel rounded-xl border border-transparent hover:border-dark-border transition-all disabled:opacity-50"
                  >
                    <RefreshCw className={`w-4 h-4 ${isLoadingMessages ? 'animate-spin text-violet-primary' : ''}`} />
                  </button>
                </div>
              </div>

              {/* Error Message Banner */}
              {error && (
                <div className="p-2.5 sm:p-3 bg-red-950/40 border-b border-red-900/40 flex-shrink-0">
                  <ErrorMessage message={error} />
                </div>
              )}

              {/* Message Feed Area */}
              <div
                ref={messageContainerRef}
                className="flex-1 overflow-y-auto p-3 sm:p-5 md:p-6 space-y-3 sm:space-y-3.5 min-h-0 w-full"
              >
                {/* Pagination: Load Older Messages */}
                {hasMore && (
                  <div className="text-center py-1 sm:py-2 flex-shrink-0">
                    <button
                      onClick={handleLoadOlderMessages}
                      disabled={isLoadingOlder}
                      className="inline-flex items-center space-x-2 px-3.5 py-1.5 rounded-full bg-dark-panel hover:bg-dark-hover border border-dark-border text-xs font-semibold text-content-secondary hover:text-content-primary transition-all disabled:opacity-50 shadow-sm"
                    >
                      {isLoadingOlder ? (
                        <>
                          <RefreshCw className="w-3 h-3 animate-spin text-violet-primary" />
                          <span>Loading earlier messages...</span>
                        </>
                      ) : (
                        <>
                          <Clock className="w-3 h-3 text-content-muted" />
                          <span>Load earlier messages</span>
                        </>
                      )}
                    </button>
                  </div>
                )}

                {/* Loading State */}
                {isLoadingMessages ? (
                  <div className="h-64 flex items-center justify-center">
                    <LoadingSpinner size="md" text="Loading message history..." />
                  </div>
                ) : messages.length > 0 ? (
                  /* Messages List */
                  messages.map((msg) => {
                    const isCurrentUser = msg.senderId === user?.id;
                    const status = resolveMessageStatus(msg);

                    return (
                      <div
                        key={msg.id}
                        className={`w-full flex flex-col min-w-0 ${isCurrentUser ? 'items-end' : 'items-start'}`}
                      >
                        <div
                          className={`max-w-[85%] md:max-w-[75%] rounded-2xl px-4 py-2.5 shadow-sm min-w-0 transition-all ${
                            isCurrentUser
                              ? 'bg-violet-primary text-white rounded-br-sm shadow-violet-sm'
                              : 'bg-dark-panel border border-dark-border text-content-primary rounded-bl-sm'
                          }`}
                        >
                          <p className="text-xs sm:text-sm whitespace-pre-wrap [overflow-wrap:anywhere] leading-relaxed">
                            {msg.content}
                          </p>
                        </div>
                        <div
                          className={`flex items-center gap-1 mt-1 px-1.5 select-none flex-shrink-0 ${
                            isCurrentUser ? 'justify-end' : ''
                          }`}
                        >
                          <span className="text-[10px] text-content-muted">
                            {formatTime(msg.createdAt)}
                          </span>
                          {isCurrentUser && status === 'SENT' && (
                            <Check className="w-3 h-3 text-content-muted" aria-label="Sent" />
                          )}
                          {isCurrentUser && status === 'DELIVERED' && (
                            <CheckCheck className="w-3.5 h-3.5 text-content-muted" aria-label="Delivered" />
                          )}
                          {isCurrentUser && status === 'SEEN' && (
                            <CheckCheck className="w-3.5 h-3.5 text-blue-400" aria-label="Seen" />
                          )}
                        </div>
                      </div>
                    );
                  })
                ) : (
                  /* Empty Conversation State */
                  <div className="h-64 flex flex-col items-center justify-center text-center p-6 space-y-3">
                    <div className="w-12 h-12 rounded-2xl bg-dark-panel border border-dark-border flex items-center justify-center text-violet-lavender shadow-sm">
                      <MessageSquare className="w-6 h-6 text-violet-primary" />
                    </div>
                    <div className="space-y-1">
                      <h4 className="text-sm font-semibold text-content-primary">No messages yet</h4>
                      <p className="text-xs text-content-secondary max-w-sm">
                        Say hello to {otherUser?.username || 'this user'} to begin your persistent conversation history.
                      </p>
                    </div>
                  </div>
                )}

                <div ref={messagesEndRef} />
              </div>

              {/* Message Composer Footer */}
              <div className="p-3 sm:p-4 border-t border-dark-border bg-dark-sidebar/95 backdrop-blur pb-[max(0.75rem,env(safe-area-inset-bottom))] flex-shrink-0">
                <form onSubmit={handleSendMessage} className="flex items-end space-x-2 sm:space-x-3 w-full min-w-0">
                  <div className="relative flex-1 min-w-0">
                    <textarea
                      ref={messageInputRef}
                      rows={1}
                      placeholder={`Message ${otherUser?.username || 'participant'}...`}
                      value={messageInput}
                      onChange={(e) => setMessageInput(e.target.value)}
                      onKeyDown={handleComposerKeyDown}
                      maxLength={2000}
                      disabled={isSending}
                      className="block w-full min-w-0 px-4 py-2.5 sm:py-3 bg-dark-panel border border-dark-border rounded-xl text-xs sm:text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-violet-primary focus:ring-1 focus:ring-violet-primary transition-all pr-12 sm:pr-16 disabled:opacity-50 resize-none max-h-36 overflow-y-auto whitespace-pre-wrap [overflow-wrap:anywhere] leading-relaxed"
                    />
                    {messageInput.length > 1800 && (
                      <span className="absolute right-3 bottom-2 pointer-events-none text-[10px] text-content-muted bg-dark-panel px-1 rounded">
                        {2000 - messageInput.length}
                      </span>
                    )}
                  </div>

                  <button
                    type="submit"
                    disabled={!messageInput.trim() || isSending}
                    aria-label="Send message"
                    className="p-2.5 sm:px-4 sm:py-3 bg-violet-primary hover:bg-violet-hover text-white rounded-xl font-semibold flex items-center justify-center space-x-2 text-xs sm:text-sm shadow-violet-sm hover:shadow-violet-glow transition-all duration-150 disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
                  >
                    {isSending ? (
                      <RefreshCw className="w-4 h-4 animate-spin" />
                    ) : (
                      <Send className="w-4 h-4" />
                    )}
                    <span className="hidden sm:inline">Send</span>
                  </button>
                </form>
              </div>
            </div>
          ) : (
            /* Welcome / Empty Selection Screen (desktop / tablet) */
            <div className="flex-1 flex flex-col items-center justify-center px-6 py-10 sm:px-8 text-center max-w-lg mx-auto">
              <div className="relative mb-7">
                <div className="absolute inset-0 rounded-3xl bg-violet-primary/20 blur-2xl scale-125 pointer-events-none" />
                <div className="relative w-16 h-16 sm:w-20 sm:h-20 rounded-3xl bg-dark-panel border border-violet-primary/20 flex items-center justify-center shadow-violet-md">
                  <MessageSquare className="w-8 h-8 sm:w-10 sm:h-10 text-violet-primary" />
                </div>
              </div>

              <h2 className="text-2xl sm:text-3xl font-bold text-content-primary tracking-tight">
                Welcome to Fairish Chat!
              </h2>
              <p className="mt-3 text-sm sm:text-base text-content-secondary leading-relaxed max-w-sm">
                Secure direct 1-to-1 conversations between accepted contacts.
              </p>
              <p className="mt-5 text-xs sm:text-sm text-content-muted leading-relaxed max-w-xs">
                Select a conversation or connect with someone to start chatting.
              </p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};
