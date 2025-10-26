import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ServerList from '../ServerList';
import * as client from '../../api/client';

// Mock WebSocket hook
vi.mock('../../hooks/useWebSocket', () => ({
  useWebSocket: vi.fn(),
}));

// Mock API client
vi.mock('../../api/client', async () => {
  const actual = await vi.importActual('../../api/client');
  return {
    ...actual,
    serverApi: {
      getServers: vi.fn(),
      getBotInviteUrl: vi.fn(),
      removeBot: vi.fn(),
    },
  };
});

describe('ServerList Integration Tests', () => {
  let queryClient: QueryClient;
  let windowOpenSpy: any;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
    vi.clearAllMocks();
    windowOpenSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
  });

  afterEach(() => {
    windowOpenSpy.mockRestore();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Routes>
          <Route path="/" element={children} />
          <Route path="/servers/:guildId" element={<div>Role Manager</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  it('displays loading state initially', () => {
    (client.serverApi.getServers as any).mockImplementation(() => new Promise(() => {}));

    render(<ServerList />, { wrapper });

    expect(screen.getByText(/loading servers/i)).toBeInTheDocument();
  });

  it('displays servers when data is loaded', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({
      data: [
        {
          id: 'server-1',
          name: 'Test Server',
          iconUrl: null,
          botIsPresent: true,
        },
      ],
    });

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('Test Server')).toBeInTheDocument();
    });
  });

  it('separates servers with and without bot', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({
      data: [
        {
          id: 'server-with-bot',
          name: 'Server With Bot',
          iconUrl: null,
          botIsPresent: true,
        },
        {
          id: 'server-without-bot',
          name: 'Server Without Bot',
          iconUrl: null,
          botIsPresent: false,
        },
      ],
    });

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('Available Servers')).toBeInTheDocument();
      expect(screen.getByText('Add Bot to These Servers')).toBeInTheDocument();
      expect(screen.getByText('Server With Bot')).toBeInTheDocument();
      expect(screen.getByText('Server Without Bot')).toBeInTheDocument();
    });
  });

  it('displays error message when API fails', async () => {
    (client.serverApi.getServers as any).mockRejectedValue(new Error('API Error'));

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText(/error loading servers/i)).toBeInTheDocument();
    });
  });

  it('shows empty state when no servers', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({ data: [] });

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('No Servers Found')).toBeInTheDocument();
    });
  });

  it('handles invite bot button click', async () => {
    const user = userEvent.setup();
    (client.serverApi.getServers as any).mockResolvedValue({
      data: [
        {
          id: 'server-1',
          name: 'Server Without Bot',
          iconUrl: null,
          botIsPresent: false,
        },
      ],
    });

    (client.serverApi.getBotInviteUrl as any).mockResolvedValue({
      data: { inviteUrl: 'https://discord.com/oauth2/authorize?...' },
    });

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('Server Without Bot')).toBeInTheDocument();
    });

    const inviteButton = screen.getByRole('button', { name: /invite bot/i });
    await user.click(inviteButton);

    await waitFor(() => {
      expect(client.serverApi.getBotInviteUrl).toHaveBeenCalledWith('server-1');
    });
  });

  it('displays server icon when available', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({
      data: [
        {
          id: 'server-1',
          name: 'Server With Icon',
          iconUrl: 'https://example.com/icon.png',
          botIsPresent: true,
        },
      ],
    });

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      const img = screen.getByAltText('Server With Icon');
      expect(img).toHaveAttribute('src', 'https://example.com/icon.png');
    });
  });

  it('displays server initial when no icon', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({
      data: [
        {
          id: 'server-1',
          name: 'Test Server',
          iconUrl: null,
          botIsPresent: true,
        },
      ],
    });

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText('T')).toBeInTheDocument(); // First letter
    });
  });

  it('shows remove bot button for servers with bot', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({
      data: [
        {
          id: 'server-1',
          name: 'Server With Bot',
          iconUrl: null,
          botIsPresent: true,
        },
      ],
    });

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /remove bot/i })).toBeInTheDocument();
    });
  });

  it('displays hierarchy warning for servers without bot', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({
      data: [
        {
          id: 'server-1',
          name: 'Server Without Bot',
          iconUrl: null,
          botIsPresent: false,
        },
      ],
    });

    render(<ServerList />, { wrapper });

    await waitFor(() => {
      expect(screen.getByText(/after the bot joins your server/i)).toBeInTheDocument();
      expect(screen.getByText(/above all gacha roles/i)).toBeInTheDocument();
    });
  });
});
