import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import Login from '../Login';
import { MemoryRouter } from 'react-router-dom';
import * as client from '../../api/client';

// Mock the authApi
vi.mock('../../api/client', () => ({
  authApi: {
    login: vi.fn(),
  },
}));

describe('Login', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders headings and description', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    );
    expect(screen.getByText(/playbot/i)).toBeInTheDocument();
    expect(screen.getByText(/admin panel/i)).toBeInTheDocument();
    expect(screen.getByText(/manage your discord server/i)).toBeInTheDocument();
  });

  it('renders login button', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    );
    expect(screen.getByRole('button', { name: /login with discord/i })).toBeInTheDocument();
  });

  it('renders legal links', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    );
    expect(screen.getByText(/privacy policy/i)).toBeInTheDocument();
    expect(screen.getByText(/terms of service/i)).toBeInTheDocument();
  });

  it('calls authApi.login when login button is clicked', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    );

    const loginButton = screen.getByRole('button', { name: /login with discord/i });
    await user.click(loginButton);

    expect(client.authApi.login).toHaveBeenCalledTimes(1);
  });

  it('renders permission notice', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    );
    expect(screen.getByText(/you need administrator or manage server permissions/i)).toBeInTheDocument();
  });

  it('renders Discord SVG icon in login button', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    );
    const button = screen.getByRole('button', { name: /login with discord/i });
    const svg = button.querySelector('svg');
    expect(svg).toBeInTheDocument();
    expect(svg).toHaveAttribute('width', '24');
    expect(svg).toHaveAttribute('height', '24');
  });

  it('legal links have correct routes', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    );
    const privacyLink = screen.getByText(/privacy policy/i).closest('a');
    const termsLink = screen.getByText(/terms of service/i).closest('a');

    expect(privacyLink).toHaveAttribute('href', '/privacy');
    expect(termsLink).toHaveAttribute('href', '/terms');
  });
});
