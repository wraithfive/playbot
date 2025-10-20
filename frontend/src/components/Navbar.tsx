import { authApi } from '../api/client';

export default function Navbar() {
  return (
    <nav className="navbar">
      <div className="nav-container">
        <h1 className="nav-title">Playbot Admin</h1>
        <button
          onClick={() => authApi.logout()}
          className="btn btn-secondary"
        >
          Logout
        </button>
      </div>
    </nav>
  );
}
