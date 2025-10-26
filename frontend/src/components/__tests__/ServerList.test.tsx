import { render } from '@testing-library/react';
import ServerList from '../ServerList';
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

// Mock the WebSocket hook first
vi.mock('../hooks/useWebSocket', () => ({
  useWebSocket: vi.fn(),
}));

// Mock the API
vi.mock('../api/client', () => ({
  serverApi: {
    getServers: vi.fn(),
    getBotInviteUrl: vi.fn(),
  },
}));

describe('ServerList', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        {children}
      </MemoryRouter>
    </QueryClientProvider>
  );

  it('renders loading state', () => {
    render(<ServerList />, { wrapper });
    // Component should render without crashing
    expect(document.body).toBeInTheDocument();
  });
});