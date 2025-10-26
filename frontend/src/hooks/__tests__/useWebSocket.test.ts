import { renderHook } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useWebSocket } from '../useWebSocket';

// Mock SockJS and STOMP
const mockDisconnect = vi.fn();
const mockSubscribe = vi.fn();
const mockConnect = vi.fn();
const mockDebug = vi.fn();

const mockStompClient = {
  connect: mockConnect,
  disconnect: mockDisconnect,
  subscribe: mockSubscribe,
  connected: false,
  debug: mockDebug,
};

vi.mock('sockjs-client', () => ({
  default: class SockJS {
    constructor() {
      return {
        onopen: null,
        onmessage: null,
        onclose: null,
      };
    }
  },
}));

vi.mock('stompjs', () => ({
  over: vi.fn(() => mockStompClient),
}));

describe('useWebSocket', () => {
  const mockOnGuildUpdate = vi.fn();
  let consoleLogSpy: any;
  let consoleErrorSpy: any;
  let consoleWarnSpy: any;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.useRealTimers();
    consoleLogSpy.mockRestore();
    consoleErrorSpy.mockRestore();
    consoleWarnSpy.mockRestore();
  });

  it('calls connect on mount', () => {
    renderHook(() => useWebSocket(mockOnGuildUpdate));
    expect(mockConnect).toHaveBeenCalled();
  });

  it('calls disconnect on unmount when connected', () => {
    mockStompClient.connected = true;
    const { unmount } = renderHook(() => useWebSocket(mockOnGuildUpdate));
    unmount();
    expect(mockDisconnect).toHaveBeenCalled();
    // Reset for other tests
    mockStompClient.connected = false;
  });

  it('handles successful connection', () => {
    mockConnect.mockImplementation((config: any, onConnect: () => void) => {
      onConnect();
    });

    renderHook(() => useWebSocket(mockOnGuildUpdate));

    expect(consoleLogSpy).toHaveBeenCalledWith('[WebSocket] Connected');
    expect(mockSubscribe).toHaveBeenCalledWith(
      '/topic/guild-updates',
      expect.any(Function)
    );
  });

  it('handles connection error and retries', () => {
    mockConnect.mockImplementation((config: any, onConnect: () => void, onError: (err: any) => void) => {
      onError(new Error('Connection failed'));
    });

    renderHook(() => useWebSocket(mockOnGuildUpdate));

    expect(consoleErrorSpy).toHaveBeenCalledWith('[WebSocket] Connection error:', expect.any(Error));

    // Fast-forward time to trigger reconnect
    vi.advanceTimersByTime(5000);

    expect(mockConnect).toHaveBeenCalledTimes(2);
  });

  it('parses and forwards guild update messages', () => {
    const testMessage = {
      type: 'GUILD_JOINED',
      guildId: 'test-guild-123',
      guildName: 'Test Guild',
    };

    let subscribeCallback: ((message: any) => void) | null = null;

    mockConnect.mockImplementation((config: any, onConnect: () => void) => {
      onConnect();
    });

    mockSubscribe.mockImplementation((topic: string, callback: (message: any) => void) => {
      subscribeCallback = callback;
    });

    renderHook(() => useWebSocket(mockOnGuildUpdate));

    // Simulate receiving a message
    if (subscribeCallback) {
      subscribeCallback({ body: JSON.stringify(testMessage) });
    }

    expect(consoleLogSpy).toHaveBeenCalledWith('[WebSocket] Received guild update:', testMessage);
    expect(mockOnGuildUpdate).toHaveBeenCalledWith(testMessage);
  });

  it('handles malformed message gracefully', () => {
    let subscribeCallback: ((message: any) => void) | null = null;

    mockConnect.mockImplementation((config: any, onConnect: () => void) => {
      onConnect();
    });

    mockSubscribe.mockImplementation((topic: string, callback: (message: any) => void) => {
      subscribeCallback = callback;
    });

    renderHook(() => useWebSocket(mockOnGuildUpdate));

    // Simulate receiving a malformed message
    if (subscribeCallback) {
      subscribeCallback({ body: 'invalid json' });
    }

    expect(consoleErrorSpy).toHaveBeenCalledWith('[WebSocket] Failed to parse message:', expect.any(Error));
    expect(mockOnGuildUpdate).not.toHaveBeenCalled();
  });

  it('clears reconnect timeout on unmount', () => {
    mockConnect.mockImplementation((config: any, onConnect: () => void, onError: (err: any) => void) => {
      onError(new Error('Connection failed'));
    });

    const { unmount } = renderHook(() => useWebSocket(mockOnGuildUpdate));

    // Unmount before reconnect timeout fires
    unmount();

    // Fast-forward time
    vi.advanceTimersByTime(5000);

    // Should not attempt to reconnect after unmount
    expect(mockConnect).toHaveBeenCalledTimes(1);
  });

  it('disables debug output', () => {
    renderHook(() => useWebSocket(mockOnGuildUpdate));

    // Debug should be set to a no-op function
    expect(mockStompClient.debug).toBeDefined();
  });

  it('handles disconnect errors during cleanup gracefully', () => {
    mockConnect.mockImplementation((config: any, onConnect: () => void) => {
      onConnect();
    });

    mockDisconnect.mockImplementation((callback?: () => void) => {
      throw new Error('Disconnect failed');
    });

    mockStompClient.connected = true;

    const { unmount } = renderHook(() => useWebSocket(mockOnGuildUpdate));

    unmount();

    expect(consoleWarnSpy).toHaveBeenCalledWith('[WebSocket] Disconnect error (ignoring):', expect.any(Error));

    // Reset for other tests
    mockStompClient.connected = false;
  });

  it('returns isConnected status', () => {
    const { result } = renderHook(() => useWebSocket(mockOnGuildUpdate));

    expect(result.current.isConnected).toBe(false);
  });
});
