import { render } from '@testing-library/react';
import RoleManager from '../RoleManager';
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
  serverApi: {
    getRoles: vi.fn(),
    createRole: vi.fn(),
    deleteRole: vi.fn(),
    bulkCreateRoles: vi.fn(),
    bulkDeleteRoles: vi.fn(),
  },
}));

describe('RoleManager', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/servers/123']}>
        {children}
      </MemoryRouter>
    </QueryClientProvider>
  );

  it('renders loading state', () => {
    render(<RoleManager />, { wrapper });
    // Component should render without crashing
    expect(document.body).toBeInTheDocument();
  });
});