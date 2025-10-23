import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, useEffect } from 'react';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Login from './components/Login';
import ServerList from './components/ServerList';
import RoleManager from './components/RoleManager';
import QotdManager from './components/QotdManager';
import PrivacyPolicy from './components/PrivacyPolicy';
import TermsOfService from './components/TermsOfService';
import { serverApi } from './api/client';
import './App.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30000, // Cache for 30 seconds to prevent duplicate requests
      gcTime: 5 * 60 * 1000, // Keep in cache for 5 minutes
    },
  },
});

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);
  const [authCheckDone, setAuthCheckDone] = useState(false);

  useEffect(() => {
    // Prevent duplicate auth checks in StrictMode
    if (authCheckDone) return;

    // Check if user is authenticated by trying to fetch servers
    serverApi
      .getServers()
      .then(() => setIsAuthenticated(true))
      .catch(() => setIsAuthenticated(false))
      .finally(() => setAuthCheckDone(true));
  }, [authCheckDone]);

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="app">
          {isAuthenticated && <Navbar />}
          <main className="main-content">
            <Routes>
              {/* Public routes */}
              <Route path="/privacy" element={<PrivacyPolicy />} />
              <Route path="/terms" element={<TermsOfService />} />

              {/* Protected routes */}
              {isAuthenticated === null && (
                <Route path="*" element={<div className="loading">Loading...</div>} />
              )}
              {isAuthenticated === false && (
                <Route path="*" element={<Login />} />
              )}
              {isAuthenticated === true && (
                <>
                  <Route path="/" element={<ServerList />} />
                  <Route path="/servers/:guildId" element={<RoleManager />} />
                  <Route path="/servers/:guildId/qotd" element={<QotdManager />} />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </>
              )}
            </Routes>
          </main>
          <Footer />
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
