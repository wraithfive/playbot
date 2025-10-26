import { render, screen } from '@testing-library/react';
import PrivacyPolicy from '../PrivacyPolicy';

describe('PrivacyPolicy', () => {
  it('renders the main heading', () => {
    render(<PrivacyPolicy />);
    expect(screen.getByText('Privacy Policy')).toBeInTheDocument();
  });

  it('renders last updated date', () => {
    render(<PrivacyPolicy />);
    expect(screen.getByText(/last updated: october 20, 2025/i)).toBeInTheDocument();
  });

  it('renders section headings', () => {
    render(<PrivacyPolicy />);
    expect(screen.getByText(/introduction/i)).toBeInTheDocument();
    expect(screen.getByText(/who this policy applies to/i)).toBeInTheDocument();
    expect(screen.getByText(/information we collect/i)).toBeInTheDocument();
  });
});
