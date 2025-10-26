import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import Navbar from '../Navbar';
import * as client from '../../api/client';

// Mock the authApi
vi.mock('../../api/client', () => ({
  authApi: {
    logout: vi.fn(),
  },
}));

describe('Navbar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the app name', () => {
    render(<Navbar />);
    expect(screen.getByText(/playbot admin/i)).toBeInTheDocument();
  });

  it('renders logout button', () => {
    render(<Navbar />);
    expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument();
  });

  it('calls authApi.logout when logout button is clicked', async () => {
    const user = userEvent.setup();
    render(<Navbar />);

    const logoutButton = screen.getByRole('button', { name: /logout/i });
    await user.click(logoutButton);

    expect(client.authApi.logout).toHaveBeenCalledTimes(1);
  });

  it('applies correct CSS classes', () => {
    const { container } = render(<Navbar />);
    expect(container.querySelector('.navbar')).toBeInTheDocument();
    expect(container.querySelector('.nav-container')).toBeInTheDocument();
    expect(container.querySelector('.nav-title')).toBeInTheDocument();
  });
});
