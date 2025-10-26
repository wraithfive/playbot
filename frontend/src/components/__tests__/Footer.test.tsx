import { render, screen } from '@testing-library/react';
import Footer from '../Footer';
import { MemoryRouter } from 'react-router-dom';

describe('Footer', () => {
  it('renders privacy and terms links', () => {
    render(
      <MemoryRouter>
        <Footer />
      </MemoryRouter>
    );
    expect(screen.getByText(/privacy policy/i)).toBeInTheDocument();
    expect(screen.getByText(/terms of service/i)).toBeInTheDocument();
  });

  it('renders copyright text', () => {
    render(
      <MemoryRouter>
        <Footer />
      </MemoryRouter>
    );
    expect(screen.getByText(/playbot/i)).toBeInTheDocument();
    expect(screen.getByText(/discord gacha bot/i)).toBeInTheDocument();
  });
});
