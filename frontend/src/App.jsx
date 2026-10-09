import React, { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { Toaster, toast } from 'react-hot-toast';
import { userTokenStore, adminTokenStore, authApi, userApi } from './api.js';
import { PermissionProvider } from './context/PermissionContext.jsx';

import WorkspaceLayout from './components/layout/WorkspaceLayout.jsx';
import ProjectListPage from './pages/ProjectListPage.jsx';
import ProjectDetailPage from './pages/ProjectDetailPage.jsx';
import ProjectBoardPage from './pages/ProjectBoardPage.jsx';
import ProjectBacklogPage from './pages/ProjectBacklogPage.jsx';
import ProjectReportsPage from './pages/ProjectReportsPage.jsx';
import ProjectMembersPage from './pages/ProjectMembersPage.jsx';
import ProjectWorkflowPage from './pages/ProjectWorkflowPage.jsx';
import TeamDetailPage from './pages/TeamDetailPage.jsx';
import AdminDashboard from './pages/AdminDashboard.jsx';
import UserLoginPage from './pages/UserLoginPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import GoogleCallback from './pages/GoogleCallback.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import { WorkspaceProvider } from './context/WorkspaceContext.jsx';
import CreateWorkspaceModal from './components/workspace/CreateWorkspaceModal.jsx';
import WorkspaceRedirect from './components/workspace/WorkspaceRedirect.jsx';
import WorkspaceListPage from './pages/WorkspaceListPage.jsx';
import WorkspaceMembersPage from './pages/WorkspaceMembersPage.jsx';
import WorkspaceSettingsPage from './pages/WorkspaceSettingsPage.jsx';
import AccessDenied from './components/common/AccessDenied.jsx';
import InvitationsPage from './pages/InvitationsPage.jsx';
import VerifyEmailPage from './pages/VerifyEmailPage.jsx';
import ForgotPasswordPage from './pages/ForgotPasswordPage.jsx';
import ResetPasswordPage from './pages/ResetPasswordPage.jsx';

// Keycloak public URL (đổi qua .env.local: VITE_KEYCLOAK_URL) – mặc định cổng 8088 dùng chung của nhóm
const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8088';

/**
 * Vỏ cho toàn bộ phần đã đăng nhập.
 *
 * WorkspaceProvider phải nằm TRÊN WorkspaceLayout để trang /workspaces (chưa
 * chọn workspace nên không có sidebar) cũng đọc được danh sách workspace.
 * CreateWorkspaceModal cũng mount ở đây, một instance duy nhất dùng chung cho cả
 * switcher trong Sidebar và trang danh sách.
 */
function AuthedShell() {
  return (
    <WorkspaceProvider>
      <Outlet />
      <CreateWorkspaceModal />
    </WorkspaceProvider>
  );
}

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

    const keycloakLogoutUrl = `${KEYCLOAK_URL}/realms/datn-realm/protocol/openid-connect/logout?client_id=datn-backend&post_logout_redirect_uri=${encodeURIComponent(window.location.origin + '/login')}`;
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

    const keycloakLogoutUrl = `${KEYCLOAK_URL}/realms/datn-realm/protocol/openid-connect/logout?client_id=datn-backend&post_logout_redirect_uri=${encodeURIComponent(window.location.origin + '/admin/login')}`;
    window.location.href = keycloakLogoutUrl;
  };

  return (
    <BrowserRouter>
     <PermissionProvider currentUser={currentUser}>
      {/* ─── Global Toast Notifications Container ─── */}
      <Toaster
        position="bottom-right"
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
              <Navigate to="/" replace />
            ) : (
              <UserLoginPage onLoginSuccess={handleUserLoginSuccess} />
            )
          }
        />
        <Route
          path="/register"
          element={
            isUserAuth ? (
              <Navigate to="/" replace />
            ) : (
              <RegisterPage />
            )
          }
        />
        <Route
          path="/auth/callback"
          element={<GoogleCallback onLoginSuccess={handleUserLoginSuccess} />}
        />
        {/* Public: link từ email xác thực / đặt lại mật khẩu (identity-service gửi qua notification-service) */}
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />

        {/* ─── 2. User Work Management Portal (`/w/:workspaceId/**`) ─── */}
        <Route element={isUserAuth ? <AuthedShell /> : <Navigate to="/login" replace />}>
          {/* Chưa chọn workspace -> không có sidebar */}
          <Route path="/workspaces" element={<WorkspaceListPage />} />

          {/* Root và các đường dẫn phẳng cũ: điều hướng về workspace phù hợp,
              để link đã bookmark không bị 404. */}
          <Route index element={<WorkspaceRedirect />} />
          <Route path="/projects" element={<WorkspaceRedirect sub="/projects" />} />
          <Route path="/my-issues" element={<WorkspaceRedirect sub="/my-issues" />} />
          <Route path="/profile" element={<WorkspaceRedirect sub="/profile" />} />
          <Route path="/invitations" element={<WorkspaceRedirect sub="/invitations" />} />

          <Route
            path="/w/:workspaceId"
            element={<WorkspaceLayout currentUser={currentUser} onLogout={handleUserLogout} />}
          >
            <Route index element={<Navigate to="projects" replace />} />
            <Route path="projects" element={<ProjectListPage />} />
            {/* ProjectDetailPage dong vai tro layout: header + tab bar + <Outlet/>.
                Nho vay moi tab co URL rieng, deep-link va nut Back cua trinh duyet hoat dong dung. */}
            <Route path="projects/:projectId" element={<ProjectDetailPage />}>
              <Route index element={<Navigate to="board" replace />} />
              <Route path="board" element={<ProjectBoardPage />} />
              <Route path="backlog" element={<ProjectBacklogPage />} />
              <Route path="reports" element={<ProjectReportsPage />} />
              <Route path="members" element={<ProjectMembersPage />} />
              <Route path="workflow" element={<ProjectWorkflowPage />} />
            </Route>
            <Route path="teams/:teamId" element={<TeamDetailPage />} />
            <Route path="members" element={<WorkspaceMembersPage />} />
            <Route path="settings" element={<WorkspaceSettingsPage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="invitations" element={<InvitationsPage />} />
            <Route path="403" element={<AccessDenied status={403} title="Bạn không có quyền truy cập" message="Bạn không có quyền xem nội dung này. Hãy liên hệ người quản trị để được cấp quyền." />} />
            <Route path="404" element={<AccessDenied status={404} />} />
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

        {/* 401: phiên hết hạn (interceptor chuyển hướng tới đây khi refresh token thất bại) */}
        <Route path="/401" element={<AccessDenied status={401} />} />

        {/* Catch-all: về workspace phù hợp thay vì đường dẫn phẳng /projects
            (vốn không còn tồn tại sau khi workspace vào URL). */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
     </PermissionProvider>
    </BrowserRouter>
  );
}

export default App;
