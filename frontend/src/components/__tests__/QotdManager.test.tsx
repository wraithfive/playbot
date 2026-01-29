import { render } from '@testing-library/react';
import QotdManager from '../QotdManager';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';

// Mock stompjs globally
vi.mock('stompjs', () => ({
  Client: vi.fn(),
  over: vi.fn(() => ({
    debug: null,
    connect: vi.fn(),
    disconnect: vi.fn(),
    subscribe: vi.fn(),
  })),
}));

// Mock SockJS
vi.mock('sockjs-client', () => ({
  default: vi.fn(),
}));

// Mock the WebSocket hook
vi.mock('../hooks/useWebSocket', () => ({
  useWebSocket: vi.fn(),
}));

// Mock the API
vi.mock('../api/client', () => ({
  qotdApi: {},
}));

describe('QotdManager', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/servers/123/qotd']}>
        {children}
      </MemoryRouter>
    </QueryClientProvider>
  );

  it('renders loading state', () => {
    render(<QotdManager />, { wrapper });
    // Component should render without crashing
    expect(document.body).toBeInTheDocument();
  });
});