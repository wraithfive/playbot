import { render } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { axe } from 'jest-axe';
import { MemoryRouter } from 'react-router-dom';
import Login from '../Login';
import Navbar from '../Navbar';
import Footer from '../Footer';
import PrivacyPolicy from '../PrivacyPolicy';
import TermsOfService from '../TermsOfService';

// Mock the authApi
vi.mock('../../api/client', () => ({
  authApi: {
    login: vi.fn(),
    logout: vi.fn(),
  },
}));

describe('Accessibility Tests', () => {
  describe('Login Component', () => {
    it('should have no accessibility violations', async () => {
      const { container } = render(
        <MemoryRouter>
          <Login />
        </MemoryRouter>
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should have proper button labels', async () => {
      const { getByRole } = render(
        <MemoryRouter>
          <Login />
        </MemoryRouter>
      );

      const loginButton = getByRole('button', { name: /login with discord/i });
      expect(loginButton).toHaveAccessibleName();
    });

    it('should have proper link labels', async () => {
      const { getByRole } = render(
        <MemoryRouter>
          <Login />
        </MemoryRouter>
      );

      const privacyLink = getByRole('link', { name: /privacy policy/i });
      const termsLink = getByRole('link', { name: /terms of service/i });

      expect(privacyLink).toHaveAccessibleName();
      expect(termsLink).toHaveAccessibleName();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(
        <MemoryRouter>
          <Login />
        </MemoryRouter>
      );

      const h1 = container.querySelector('h1');
      const h2 = container.querySelector('h2');

      expect(h1).toBeInTheDocument();
      expect(h2).toBeInTheDocument();
      expect(h1?.textContent).toBe('Playbot');
      expect(h2?.textContent).toBe('Admin Panel');
    });
  });

  describe('Navbar Component', () => {
    it('should have no accessibility violations', async () => {
      const { container } = render(<Navbar />);

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should have proper button label', () => {
      const { getByRole } = render(<Navbar />);

      const logoutButton = getByRole('button', { name: /logout/i });
      expect(logoutButton).toHaveAccessibleName();
    });

    it('should use nav element with proper semantics', () => {
      const { container } = render(<Navbar />);

      const nav = container.querySelector('nav');
      expect(nav).toBeInTheDocument();
    });

    it('should have proper heading level', () => {
      const { container } = render(<Navbar />);

      const h1 = container.querySelector('h1');
      expect(h1).toBeInTheDocument();
      expect(h1?.textContent).toBe('Playbot Admin');
    });
  });

  describe('Footer Component', () => {
    it('should have no accessibility violations', async () => {
      const { container } = render(
        <MemoryRouter>
          <Footer />
        </MemoryRouter>
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should have proper link labels', () => {
      const { getByRole } = render(
        <MemoryRouter>
          <Footer />
        </MemoryRouter>
      );

      const privacyLink = getByRole('link', { name: /privacy policy/i });
      const termsLink = getByRole('link', { name: /terms of service/i });

      expect(privacyLink).toHaveAccessibleName();
      expect(termsLink).toHaveAccessibleName();
    });
  });

  describe('PrivacyPolicy Component', () => {
    it('should have no accessibility violations', async () => {
      const { container } = render(
        <MemoryRouter>
          <PrivacyPolicy />
        </MemoryRouter>
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(
        <MemoryRouter>
          <PrivacyPolicy />
        </MemoryRouter>
      );

      const h1 = container.querySelector('h1');
      expect(h1).toBeInTheDocument();
      expect(h1?.textContent).toBe('Privacy Policy');
    });
  });

  describe('TermsOfService Component', () => {
    it('should have no accessibility violations', async () => {
      const { container } = render(
        <MemoryRouter>
          <TermsOfService />
        </MemoryRouter>
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('should have proper heading hierarchy', () => {
      const { container } = render(
        <MemoryRouter>
          <TermsOfService />
        </MemoryRouter>
      );

      const h1 = container.querySelector('h1');
      expect(h1).toBeInTheDocument();
      expect(h1?.textContent).toBe('Terms of Service');
    });
  });

  describe('Keyboard Navigation', () => {
    it('login button should be keyboard accessible', () => {
      const { getByRole } = render(
        <MemoryRouter>
          <Login />
        </MemoryRouter>
      );

      const button = getByRole('button', { name: /login with discord/i });
      expect(button).not.toHaveAttribute('tabindex', '-1');
    });

    it('logout button should be keyboard accessible', () => {
      const { getByRole } = render(<Navbar />);

      const button = getByRole('button', { name: /logout/i });
      expect(button).not.toHaveAttribute('tabindex', '-1');
    });

    it('all links should be keyboard accessible', () => {
      const { getAllByRole } = render(
        <MemoryRouter>
          <Login />
        </MemoryRouter>
      );

      const links = getAllByRole('link');
      links.forEach((link) => {
        expect(link).not.toHaveAttribute('tabindex', '-1');
      });
    });
  });
});
