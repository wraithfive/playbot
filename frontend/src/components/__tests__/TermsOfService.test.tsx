import { render, screen } from '@testing-library/react';
import TermsOfService from '../TermsOfService';

describe('TermsOfService', () => {
  it('renders the main heading', () => {
    render(<TermsOfService />);
    expect(screen.getByText('Terms of Service')).toBeInTheDocument();
  });

  it('renders last updated date', () => {
    render(<TermsOfService />);
  const dateElement = screen.getByText(/last updated/i, { selector: '.last-updated' });
    expect(dateElement).toHaveClass('last-updated');
  });

  it('renders section headings', () => {
    render(<TermsOfService />);
    expect(screen.getByText(/acceptance of terms/i)).toBeInTheDocument();
    expect(screen.getByText(/description of service/i)).toBeInTheDocument();
    expect(screen.getByText(/user eligibility/i)).toBeInTheDocument();
    expect(screen.getByText(/server administrator responsibilities/i)).toBeInTheDocument();
  });
});
