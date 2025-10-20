import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer className="footer">
      <div className="footer-links">
        <Link to="/privacy" className="footer-link">
          Privacy Policy
        </Link>
        <Link to="/terms" className="footer-link">
          Terms of Service
        </Link>
      </div>
      <p className="footer-text">
        Playbot &copy; {new Date().getFullYear()} - Discord Gacha Bot
      </p>
    </footer>
  );
}
