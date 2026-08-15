import React, { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { tokenStore, authApi } from './api.js';

import LandingPage from './pages/LandingPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';
import GoogleCallback from './pages/GoogleCallback.jsx';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(() => {
    return !!tokenStore.get();
  });

  const handleLoginSuccess = () => {
    setIsAuthenticated(true);
  };

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch (err) {
      console.warn('Backend logout failed or session already cleared:', err);
    }
    tokenStore.clear();
    setIsAuthenticated(false);

    // Perform Keycloak SSO logout to clear KEYCLOAK_IDENTITY cookie in browser
    const keycloakLogoutUrl = `http://localhost:8088/realms/datn-realm/protocol/openid-connect/logout?client_id=datn-backend&post_logout_redirect_uri=${encodeURIComponent(window.location.origin + '/admin/login')}`;
    window.location.href = keycloakLogoutUrl;
  };

  return (
    <BrowserRouter>
      <Routes>
        {/* Route / : Demo Landing Page */}
        <Route path="/" element={<LandingPage />} />

        {/* Route /admin : Admin Portal Dashboard (Protected) */}
        <Route
          path="/admin"
          element={
            isAuthenticated ? (
              <AdminDashboard onLogout={handleLogout} />
            ) : (
              <Navigate to="/admin/login" replace />
            )
          }
        />

        {/* Route /admin/login : Login Page */}
        <Route
          path="/admin/login"
          element={
            isAuthenticated ? (
              <Navigate to="/admin" replace />
            ) : (
              <LoginPage onLoginSuccess={handleLoginSuccess} />
            )
          }
        />

        {/* Route /admin/register : Register Page */}
        <Route
          path="/admin/register"
          element={
            isAuthenticated ? (
              <Navigate to="/admin" replace />
            ) : (
              <RegisterPage />
            )
          }
        />

        {/* Route /auth/callback : Google OAuth2 Redirect Target */}
        <Route
          path="/auth/callback"
          element={<GoogleCallback onLoginSuccess={handleLoginSuccess} />}
        />

        {/* Fallback to Home */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
