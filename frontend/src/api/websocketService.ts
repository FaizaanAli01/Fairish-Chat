import { Client, StompSubscription, IMessage } from '@stomp/stompjs';
import { WS_URL } from '../config/env';
import { Message, MessageReceiptUpdate } from '../types/message';
import { UnreadCountUpdate } from '../types/conversation';

/**
 * Returns the configured WebSocket endpoint URL.
 * Tokens are transmitted strictly via STOMP CONNECT headers to prevent
 * token exposure in access logs and proxies.
 */
export const getWebSocketUrl = (_token?: string | null): string => {
  return WS_URL;
};

class WebSocketService {
  private client: Client | null = null;
  private currentToken: string | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private pendingSubscriptions: Map<string, (message: Message) => void> = new Map();
  private unreadSubscription: StompSubscription | null = null;
  private unreadCallback: ((update: UnreadCountUpdate) => void) | null = null;
  private receiptSubscription: StompSubscription | null = null;
  private receiptCallback: ((update: MessageReceiptUpdate) => void) | null = null;
  private connectCallbacks: Set<() => void> = new Set();
  private disconnectCallbacks: Set<() => void> = new Set();
  private errorCallbacks: Set<(error: any) => void> = new Set();

  /**
   * Initializes and activates the STOMP over WebSocket connection.
   */
  public connect(token: string): void {
    if (this.client && this.client.active && this.currentToken === token) {
      return;
    }

    // Clean up any stale client
    if (this.client) {
      this.disconnect();
    }

    this.currentToken = token;

    const wsUrl = getWebSocketUrl(token);

    this.client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
        token: token,
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (msg: string) => {
        if (import.meta.env.DEV) {
          console.debug('[STOMP]', msg);
        }
      },
      onConnect: () => {
        console.info('[STOMP] Connected successfully to WebSocket broker');

        // Re-establish any pending subscriptions
        this.pendingSubscriptions.forEach((callback, conversationId) => {
          this.doSubscribe(conversationId, callback);
        });
        this.doSubscribeUnread();
        this.doSubscribeReceipts();

        this.connectCallbacks.forEach((cb) => {
          try {
            cb();
          } catch (e) {
            console.error('[STOMP] Error in connect callback:', e);
          }
        });
      },
      onStompError: (frame) => {
        console.error('[STOMP] Broker reported error:', frame.headers['message'], frame.body);
        this.errorCallbacks.forEach((cb) => {
          try {
            cb(frame);
          } catch (e) {
            console.error('[STOMP] Error in error callback:', e);
          }
        });
      },
      onWebSocketClose: () => {
        console.warn('[STOMP] WebSocket connection closed');
        this.disconnectCallbacks.forEach((cb) => {
          try {
            cb();
          } catch (e) {
            console.error('[STOMP] Error in disconnect callback:', e);
          }
        });
      },
    });

    this.client.activate();
  }

  /**
   * Subscribes to real-time messages for a conversation topic:
   * /topic/conversations/{conversationId}
   * Returns an unsubscribe function.
   */
  public subscribeToConversation(
    conversationId: string,
    onMessage: (message: Message) => void
  ): () => void {
    // Store in pending subscriptions so it gets activated on reconnect
    this.pendingSubscriptions.set(conversationId, onMessage);

    if (this.client && this.client.connected) {
      this.doSubscribe(conversationId, onMessage);
    }

    // Return cleanup function
    return () => {
      this.pendingSubscriptions.delete(conversationId);
      const sub = this.subscriptions.get(conversationId);
      if (sub) {
        try {
          sub.unsubscribe();
        } catch (e) {
          console.warn('[STOMP] Error during unsubscribe:', e);
        }
        this.subscriptions.delete(conversationId);
      }
    };
  }

  /**
   * Subscribes to the authenticated user's private unread-count queue. The
   * backend pushes the authoritative total after every incoming message and
   * after a read reset, so client-side increments are never required.
   */
  public subscribeToUnread(onUpdate: (update: UnreadCountUpdate) => void): () => void {
    this.unreadCallback = onUpdate;
    this.doSubscribeUnread();

    return () => {
      if (this.unreadCallback === onUpdate) {
        this.unreadCallback = null;
        this.unsubscribeUnread();
      }
    };
  }

  private doSubscribeUnread(): void {
    if (!this.client || !this.client.connected || !this.unreadCallback) return;

    this.unsubscribeUnread();
    try {
      this.unreadSubscription = this.client.subscribe('/user/queue/unread', (stompMessage: IMessage) => {
        try {
          const update: UnreadCountUpdate = JSON.parse(stompMessage.body);
          this.unreadCallback?.(update);
        } catch (e) {
          console.error('[STOMP] Failed to parse unread count update:', e, stompMessage.body);
        }
      });
    } catch (err) {
      console.error('[STOMP] Failed to subscribe to unread counts:', err);
    }
  }

  private unsubscribeUnread(): void {
    if (this.unreadSubscription) {
      try {
        this.unreadSubscription.unsubscribe();
      } catch {}
      this.unreadSubscription = null;
    }
  }

  /**
   * Subscribes to the authenticated user's private receipt queue. The backend
   * only sends DELIVERED/SEEN events for messages this user originally sent,
   * and never includes message content.
   */
  public subscribeToReceipts(onUpdate: (update: MessageReceiptUpdate) => void): () => void {
    this.receiptCallback = onUpdate;
    this.doSubscribeReceipts();

    return () => {
      if (this.receiptCallback === onUpdate) {
        this.receiptCallback = null;
        this.unsubscribeReceipts();
      }
    };
  }

  private doSubscribeReceipts(): void {
    if (!this.client || !this.client.connected || !this.receiptCallback) return;

    this.unsubscribeReceipts();
    try {
      this.receiptSubscription = this.client.subscribe('/user/queue/receipts', (stompMessage: IMessage) => {
        try {
          const update: MessageReceiptUpdate = JSON.parse(stompMessage.body);
          this.receiptCallback?.(update);
        } catch (e) {
          console.error('[STOMP] Failed to parse receipt update:', e, stompMessage.body);
        }
      });
    } catch (err) {
      console.error('[STOMP] Failed to subscribe to receipts:', err);
    }
  }

  private unsubscribeReceipts(): void {
    if (this.receiptSubscription) {
      try {
        this.receiptSubscription.unsubscribe();
      } catch {}
      this.receiptSubscription = null;
    }
  }

  private doSubscribe(conversationId: string, onMessage: (message: Message) => void): void {
    if (!this.client || !this.client.connected) return;

    // Unsubscribe existing subscription for this conversation if any
    const existing = this.subscriptions.get(conversationId);
    if (existing) {
      try {
        existing.unsubscribe();
      } catch {}
      this.subscriptions.delete(conversationId);
    }

    const topic = `/topic/conversations/${conversationId}`;
    try {
      const sub = this.client.subscribe(topic, (stompMessage: IMessage) => {
        try {
          const parsed: Message = JSON.parse(stompMessage.body);
          onMessage(parsed);
        } catch (e) {
          console.error('[STOMP] Failed to parse message body:', e, stompMessage.body);
        }
      });
      this.subscriptions.set(conversationId, sub);
      console.info(`[STOMP] Subscribed to [${topic}]`);
    } catch (err) {
      console.error(`[STOMP] Failed to subscribe to [${topic}]:`, err);
    }
  }

  /**
   * Sends a message via STOMP /app/conversations/{conversationId}/sendMessage.
   * Returns true if published over WebSocket, false if not connected.
   */
  public sendMessage(conversationId: string, content: string): boolean {
    if (!this.client || !this.client.connected) {
      return false;
    }

    try {
      this.client.publish({
        destination: `/app/conversations/${conversationId}/sendMessage`,
        body: JSON.stringify({ content }),
      });
      return true;
    } catch (e) {
      console.error('[STOMP] Failed to publish message:', e);
      return false;
    }
  }

  public isConnected(): boolean {
    return !!(this.client && this.client.connected);
  }

  public onConnect(cb: () => void): () => void {
    this.connectCallbacks.add(cb);
    return () => this.connectCallbacks.delete(cb);
  }

  public onDisconnect(cb: () => void): () => void {
    this.disconnectCallbacks.add(cb);
    return () => this.disconnectCallbacks.delete(cb);
  }

  public onError(cb: (error: any) => void): () => void {
    this.errorCallbacks.add(cb);
    return () => this.errorCallbacks.delete(cb);
  }

  /**
   * Closes the STOMP client and clears all active subscriptions.
   */
  public disconnect(): void {
    this.subscriptions.forEach((sub) => {
      try {
        sub.unsubscribe();
      } catch {}
    });
    this.subscriptions.clear();
    this.pendingSubscriptions.clear();
    this.unsubscribeUnread();
    this.unsubscribeReceipts();

    if (this.client) {
      try {
        this.client.deactivate();
      } catch (e) {
        console.warn('[STOMP] Error during deactivation:', e);
      }
      this.client = null;
    }

    this.currentToken = null;
  }
}

export const websocketService = new WebSocketService();
