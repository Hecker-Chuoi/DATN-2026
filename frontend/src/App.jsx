import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster, toast } from 'react-hot-toast';
import { userTokenStore, adminTokenStore, authApi, userApi } from './api.js';

import WorkspaceLayout from './components/layout/WorkspaceLayout.jsx';
import ProjectListPage from './pages/ProjectListPage.jsx';
import ProjectDetailPage from './pages/ProjectDetailPage.jsx';
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
    toast.success('Đã đăng xuất thành công!');
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
    const refreshToken = adminTokenStore.getRefreshToken();
    adminTokenStore.clear();
    setIsAdminAuth(false);
    toast.success('Đã đăng xuất tài khoản Quản trị viên!');

    try {
      if (refreshToken) {
        await authApi.logout(refreshToken);
      }
    } catch (err) {
      console.warn('Backend logout error:', err);
    }

    const keycloakLogoutUrl = `http://localhost:8088/realms/datn-realm/protocol/openid-connect/logout?client_id=datn-backend&post_logout_redirect_uri=${encodeURIComponent(window.location.origin + '/admin/login')}`;
    window.location.href = keycloakLogoutUrl;
  };

  return (
    <BrowserRouter>
      {/* ─── Global Toast Notifications Container ─── */}
      <Toaster
        position="top-right"
        toastOptions={{
          duration: 3500,
          style: {
            background: '#0F172A',
            color: '#FFFFFF',
            fontSize: '13px',
            fontWeight: 500,
            borderRadius: '8px',
            padding: '10px 16px',
            boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1)',
          },
          success: {
            iconTheme: {
              primary: '#10B981',
              secondary: '#FFFFFF',
            },
          },
          error: {
            iconTheme: {
              primary: '#EF4444',
              secondary: '#FFFFFF',
            },
            style: {
              background: '#FEF2F2',
              color: '#991B1B',
              border: '1px solid #FEE2E2',
            },
          },
        }}
      />

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
          <Route path="projects/:projectId" element={<ProjectDetailPage />} />
          <Route path="teams/:teamId" element={<TeamDetailPage />} />
          <Route
            path="my-issues"
            element={
              <div style={{ backgroundColor: '#FFFFFF', minHeight: '100vh', display: 'flex', flexDirection: 'column', flex: 1 }}>
                <div
                  style={{
                    height: 48,
                    minHeight: 48,
                    padding: '0 24px',
                    borderBottom: '1px solid #E2E8F0',
                    display: 'flex',
                    alignItems: 'center',
                    backgroundColor: '#FFFFFF',
                    fontSize: 14,
                    fontWeight: 600,
                    color: '#0F172A',
                  }}
                >
                  <span>My tasks</span>
                </div>
                <div style={{ padding: 40, textAlign: 'center' }}>
                  <h3 style={{ color: '#0F172A', marginBottom: 6, fontSize: 16 }}>My tasks</h3>
                  <p style={{ color: '#64748B', fontSize: 13 }}>Danh sách tác vụ được giao cho bạn.</p>
                </div>
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
