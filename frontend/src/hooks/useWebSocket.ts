import { useEffect, useRef, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client, over } from 'stompjs';

interface GuildUpdateMessage {
  type: 'GUILD_JOINED' | 'GUILD_LEFT' | 'ROLES_CHANGED' | 'QOTD_QUESTIONS_CHANGED' | 'QOTD_SUBMISSIONS_CHANGED';
  guildId: string;
  guildName?: string;
  channelId?: string; // For QOTD_QUESTIONS_CHANGED
  action?: string; // For ROLES_CHANGED: "created", "deleted", "updated"; For QOTD: "added", "deleted", "uploaded", "approved", "rejected", "submitted"
}

export function useWebSocket(onGuildUpdate: (message: GuildUpdateMessage) => void) {
  const clientRef = useRef<Client | null>(null);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const connect = useCallback(() => {
    // Create SockJS connection
    const socket = new SockJS('/ws');
    const stompClient = over(socket);

    // Disable debug output for production
    stompClient.debug = () => {};

    stompClient.connect(
      {},
      () => {
        // On successful connection
        console.log('[WebSocket] Connected');
        
        // Subscribe to guild updates
        stompClient.subscribe('/topic/guild-updates', (message) => {
          try {
            const data: GuildUpdateMessage = JSON.parse(message.body);
            console.log('[WebSocket] Received guild update:', data);
            onGuildUpdate(data);
          } catch (err) {
            console.error('[WebSocket] Failed to parse message:', err);
          }
        });
      },
      (error: unknown) => {
        // On connection error
        console.error('[WebSocket] Connection error:', error);
        // Try to reconnect after 5 seconds
        reconnectTimeoutRef.current = setTimeout(() => {
          console.log('[WebSocket] Attempting to reconnect...');
          connect();
        }, 5000);
      }
    );

    clientRef.current = stompClient;
  }, [onGuildUpdate]);

  useEffect(() => {
    connect();

    return () => {
      // Cleanup on unmount
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (clientRef.current && clientRef.current.connected) {
        console.log('[WebSocket] Disconnecting...');
        try {
          clientRef.current.disconnect(() => {
            console.log('[WebSocket] Disconnected');
          });
        } catch (err) {
          // Ignore disconnect errors during cleanup
          console.warn('[WebSocket] Disconnect error (ignoring):', err);
        }
      }
    };
  }, [connect]);

  return {
    isConnected: clientRef.current?.connected || false,
  };
}
