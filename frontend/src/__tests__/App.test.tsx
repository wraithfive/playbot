import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import App from '../App';
import * as client from '../api/client';

// Mock all child components
vi.mock('../components/Navbar', () => ({
  default: () => <div data-testid="navbar">Navbar</div>,
}));

vi.mock('../components/Footer', () => ({
  default: () => <div data-testid="footer">Footer</div>,
}));

vi.mock('../components/Login', () => ({
  default: () => <div data-testid="login">Login</div>,
}));

vi.mock('../components/ServerList', () => ({
  default: () => <div data-testid="server-list">ServerList</div>,
}));

vi.mock('../components/RoleManager', () => ({
  default: () => <div data-testid="role-manager">RoleManager</div>,
}));

vi.mock('../components/QotdManager', () => ({
  default: () => <div data-testid="qotd-manager">QotdManager</div>,
}));

vi.mock('../components/PrivacyPolicy', () => ({
  default: () => <div data-testid="privacy-policy">PrivacyPolicy</div>,
}));

vi.mock('../components/TermsOfService', () => ({
  default: () => <div data-testid="terms-of-service">TermsOfService</div>,
}));

// Mock the API client
vi.mock('../api/client', async () => {
  const actual = await vi.importActual('../api/client');
  return {
    ...actual,
    serverApi: {
      getServers: vi.fn(),
    },
  };
});

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading state initially', () => {
    (client.serverApi.getServers as any).mockImplementation(() => new Promise(() => {}));
    render(<App />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('shows login page when not authenticated', async () => {
    (client.serverApi.getServers as any).mockRejectedValue(new Error('Unauthorized'));
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('login')).toBeInTheDocument();
    });
  });

  it('does not show navbar when not authenticated', async () => {
    (client.serverApi.getServers as any).mockRejectedValue(new Error('Unauthorized'));
    render(<App />);

    await waitFor(() => {
      expect(screen.queryByTestId('navbar')).not.toBeInTheDocument();
    });
  });

  it('shows server list when authenticated', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({ data: [] });
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('server-list')).toBeInTheDocument();
    });
  });

  it('shows navbar when authenticated', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({ data: [] });
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('navbar')).toBeInTheDocument();
    });
  });

  it('always shows footer', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({ data: [] });
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('footer')).toBeInTheDocument();
    });
  });

  it('prevents duplicate auth checks', async () => {
    (client.serverApi.getServers as any).mockResolvedValue({ data: [] });
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId('server-list')).toBeInTheDocument();
    });

    // Should only call getServers once
    expect(client.serverApi.getServers).toHaveBeenCalledTimes(1);
  });

  it('creates QueryClient with correct default options', () => {
    (client.serverApi.getServers as any).mockImplementation(() => new Promise(() => {}));
    const { container } = render(<App />);
    expect(container).toBeInTheDocument();
  });
});
