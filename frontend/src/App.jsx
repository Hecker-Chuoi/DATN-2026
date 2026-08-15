import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { userTokenStore, adminTokenStore, authApi, userApi } from './api.js';

import WorkspaceLayout from './components/layout/WorkspaceLayout.jsx';
import ProjectListPage from './pages/ProjectListPage.jsx';
import TeamDetailPage from './pages/TeamDetailPage.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';
import UserLoginPage from './pages/UserLoginPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import GoogleCallback from './pages/GoogleCallback.jsx';

function App() {
  // ─── User Workspace Auth State ───
  const [isUserAuth, setIsUserAuth] = useState(() => {
    return !!userTokenStore.get();
  });
  const [currentUser, setCurrentUser] = useState(null);

  // ─── Admin Portal Auth State ───
  const [isAdminAuth, setIsAdminAuth] = useState(() => {
    return !!adminTokenStore.get();
  });

  const fetchCurrentUser = async () => {
    try {
      const res = await userApi.me();
      if (res && res.data) {
        setCurrentUser(res.data);
      }
    } catch (err) {
      console.warn('Could not load current user profile:', err);
    }
  };

  useEffect(() => {
    if (isUserAuth) {
      fetchCurrentUser();
    } else {
      setCurrentUser(null);
    }
  }, [isUserAuth]);

  // ─── Handlers for User ───
  const handleUserLoginSuccess = () => {
    setIsUserAuth(true);
    fetchCurrentUser();
  };

  const handleUserLogout = async () => {
    try {
      await authApi.logout();
    } catch (err) {
      console.warn('Backend logout error:', err);
    }
    userTokenStore.clear();
    setIsUserAuth(false);
    setCurrentUser(null);

    const keycloakLogoutUrl = `http://localhost:8088/realms/datn-realm/protocol/openid-connect/logout?client_id=datn-backend&post_logout_redirect_uri=${encodeURIComponent(window.location.origin + '/login')}`;
    window.location.href = keycloakLogoutUrl;
  };

  // ─── Handlers for Admin ───
  const handleAdminLoginSuccess = () => {
    setIsAdminAuth(true);
  };

  const handleAdminLogout = async () => {
    try {
      await authApi.logout();
    } catch (err) {
      console.warn('Backend logout error:', err);
    }
    adminTokenStore.clear();
    setIsAdminAuth(false);

    const keycloakLogoutUrl = `http://localhost:8088/realms/datn-realm/protocol/openid-connect/logout?client_id=datn-backend&post_logout_redirect_uri=${encodeURIComponent(window.location.origin + '/admin/login')}`;
    window.location.href = keycloakLogoutUrl;
  };

  return (
    <BrowserRouter>
      <Routes>
        {/* ─── 1. User Workspace Authentication Routes ─── */}
        <Route
          path="/login"
          element={
            isUserAuth ? (
              <Navigate to="/projects" replace />
            ) : (
              <UserLoginPage onLoginSuccess={handleUserLoginSuccess} />
            )
          }
        />
        <Route
          path="/register"
          element={
            isUserAuth ? (
              <Navigate to="/projects" replace />
            ) : (
              <RegisterPage />
            )
          }
        />
        <Route
          path="/auth/callback"
          element={<GoogleCallback onLoginSuccess={handleUserLoginSuccess} />}
        />

        {/* ─── 2. User Work Management Portal (Root `/`) ─── */}
        <Route
          path="/"
          element={
            isUserAuth ? (
              <WorkspaceLayout currentUser={currentUser} onLogout={handleUserLogout} />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        >
          <Route index element={<Navigate to="/projects" replace />} />
          <Route path="projects" element={<ProjectListPage />} />
          <Route path="marketplace" element={<ProjectListPage />} />
          <Route path="teams/:teamId" element={<TeamDetailPage />} />
          <Route
            path="my-issues"
            element={
              <div style={{ padding: 40, textAlign: 'center', backgroundColor: '#FFFFFF', borderRadius: 12, border: '1px solid #E2E8F0' }}>
                <h3 style={{ color: '#0F172A', marginBottom: 6 }}>My Issues</h3>
                <p style={{ color: '#64748B', fontSize: 13 }}>Danh sách tác vụ của bạn (sẽ hoàn thiện ở Phase 3 & 4).</p>
              </div>
            }
          />
        </Route>

        {/* ─── 3. System Admin Portal (`/admin/**`) ─── */}
        <Route
          path="/admin/login"
          element={
            isAdminAuth ? (
              <Navigate to="/admin" replace />
            ) : (
              <LoginPage onLoginSuccess={handleAdminLoginSuccess} />
            )
          }
        />
        <Route
          path="/admin/auth/callback"
          element={<GoogleCallback onLoginSuccess={handleAdminLoginSuccess} />}
        />
        <Route
          path="/admin"
          element={
            isAdminAuth ? (
              <AdminDashboard onLogout={handleAdminLogout} />
            ) : (
              <Navigate to="/admin/login" replace />
            )
          }
        />
        <Route
          path="/admin/register"
          element={<Navigate to="/register" replace />}
        />

        {/* Catch-all fallback */}
        <Route path="*" element={<Navigate to="/projects" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
