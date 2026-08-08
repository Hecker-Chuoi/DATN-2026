import { useState } from 'react';
import { tokenStore } from './api.js';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import UsersPage from './pages/UsersPage.jsx';
import GoogleCallback from './pages/GoogleCallback.jsx';
import './App.css';

/**
 * App – simple pathname-based routing (no react-router-dom dependency).
 *
 * Routes:
 *   /auth/callback  → GoogleCallback  (Keycloak redirects here after Google login)
 *   /               → LoginPage       (if not authenticated)
 *   /               → UsersPage       (if authenticated)
 *   register view   → RegisterPage    (toggled via state)
 */
function App() {
  const [user, setUser] = useState(() => {
    // Restore session if token exists in sessionStorage
    const token = tokenStore.get();
    return token ? { accessToken: token, username: 'User' } : null;
  });
  const [view, setView] = useState('login'); // 'login' | 'register'

  const handleLoginSuccess = (userData) => {
    setUser(userData);
  };

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch (err) {
      console.warn('Backend logout failed or was already cleared:', err);
    }
    tokenStore.clear();
    setUser(null);
    setView('login');
    // Clear ?code=... from URL if we came from Google callback
    window.history.replaceState({}, '', '/');
  };

  // ── Route: /auth/callback (Google OAuth2 redirect target) ────────
  if (window.location.pathname === '/auth/callback') {
    if (user) {
      // Already authenticated (e.g. page refresh after callback) – skip callback
      window.history.replaceState({}, '', '/');
    } else {
      return (
        <GoogleCallback
          onLoginSuccess={(userData) => {
            handleLoginSuccess(userData);
            // Clean the ?code=... from URL after successful login
            window.history.replaceState({}, '', '/');
          }}
        />
      );
    }
  }

  // ── Route: / (authenticated) ───────────────────────────────────────
  if (user) {
    return (
      <UsersPage
        currentUser={user}
        onLogout={handleLogout}
      />
    );
  }

  // ── Route: /register ─────────────────────────────────────────────
  if (view === 'register') {
    return (
      <RegisterPage
        onRegisterSuccess={() => setView('login')}
        onGoLogin={() => setView('login')}
      />
    );
  }

  // ── Route: / (unauthenticated) ───────────────────────────────────
  return (
    <LoginPage
      onLoginSuccess={handleLoginSuccess}
      onGoRegister={() => setView('register')}
    />
  );
}

export default App;

