import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Outlet, useParams, useNavigate } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import { projectApi, invitationApi } from '../../api.js';
import { workspaceInvitationApi } from '../../api/workspaceServiceApi';
import CreateProjectModal from '../project/CreateProjectModal.jsx';
import ErrorBoundary from '../common/ErrorBoundary.jsx';
import { useWorkspace } from '../../context/WorkspaceContext.jsx';
import { useWorkspaceRole } from '../../hooks/useWorkspaceRole.js';

export default function WorkspaceLayout({ currentUser, onLogout }) {
  const { workspaceId } = useParams();
  const navigate = useNavigate();
  const { loading: workspaceLoading } = useWorkspace();
  const { notMember, canCreateProject: canCreateProjectByRole } = useWorkspaceRole();

  const [projects, setProjects] = useState([]);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [isCreateProjectModalOpen, setIsCreateProjectModalOpen] = useState(false);

  // Danh sách dự án scope theo workspace đang xem
  const fetchProjects = useCallback(async () => {
    if (!workspaceId) return;
    try {
      setLoadingProjects(true);
      const res = await projectApi.listAll({ workspaceId, size: 50 });
      const content = res?.data?.content || (Array.isArray(res?.data) ? res.data : []);
      setProjects(content);
    } catch (err) {
      console.error('Failed to load projects:', err);
      setProjects([]);
    } finally {
      setLoadingProjects(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  // Lời mời đang chờ: nguồn chung cho badge sidebar và trang /invitations
  const [invitations, setInvitations] = useState([]);
  const fetchInvitations = async () => {
    const [work, ws] = await Promise.allSettled([invitationApi.my(), workspaceInvitationApi.my()]);
    const workList = work.status === 'fulfilled' && Array.isArray(work.value?.data) ? work.value.data : [];
    const wsList = ws.status === 'fulfilled' && Array.isArray(ws.value?.data)
      ? ws.value.data.map((inv) => ({
          ...inv,
          type: 'WORKSPACE',
          targetId: inv.workspaceId,
          targetName: inv.workspaceName,
          roleName: inv.role,
        }))
      : [];
    setInvitations([...wsList, ...workList]);
  };

  useEffect(() => {
    fetchInvitations();
    const timer = setInterval(fetchInvitations, 30000);
    return () => clearInterval(timer);
  }, []);

  const handleOpenCreateProject = () => {
    setIsCreateProjectModalOpen(true);
  };

  const isSystemAdmin = Boolean(currentUser?.roles?.includes('ADMIN') || currentUser?.roles?.includes('ROLE_ADMIN'));
  const canCreateProject = isSystemAdmin || canCreateProjectByRole;

  // Deep-link vào workspace mình không thuộc
  if (!workspaceLoading && notMember) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
          height: '100vh',
          width: '100vw',
          backgroundColor: '#F8FAFC',
          fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        }}
      >
        <h2 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: 0 }}>
          Bạn không có quyền truy cập không gian làm việc này
        </h2>
        <p style={{ fontSize: 13, color: '#64748B', margin: 0, textAlign: 'center', maxWidth: 420 }}>
          Không gian làm việc không tồn tại, hoặc bạn đã bị xóa khỏi danh sách thành viên. Hãy liên hệ chủ
          sở hữu để được thêm lại.
        </p>
        <button
          type="button"
          onClick={() => navigate('/workspaces')}
          style={{
            marginTop: 4,
            background: '#2563EB',
            color: '#FFFFFF',
            border: 'none',
            padding: '9px 18px',
            borderRadius: 8,
            fontSize: 13,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          Về danh sách không gian làm việc
        </button>
      </div>
    );
  }

  return (
    <div
      style={{
        display: 'flex',
        height: '100vh',
        width: '100vw',
        overflow: 'hidden',
        backgroundColor: '#F8FAFC',
        color: '#0F172A',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      {/* ─── Left Sidebar ─── */}
      <Sidebar
        projects={projects}
        loading={loadingProjects}
        onOpenCreateProject={handleOpenCreateProject}
        invitationCount={invitations.length}
        currentUser={currentUser}
        onLogout={onLogout}
      />

      {/* ─── Main Content Container ─── */}
      <div
        style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          backgroundColor: '#FFFFFF',
          height: '100%',
        }}
      >
        <main
          style={{
            flex: 1,
            overflow: 'hidden',
            backgroundColor: '#FFFFFF',
            display: 'flex',
            flexDirection: 'column',
            height: '100%',
          }}
        >
          <ErrorBoundary>
            <Outlet
              context={{
                projects,
                canCreateProject,
                isSystemAdmin,
                currentUser,
                invitations,
                refreshInvitations: fetchInvitations,
                refreshProjects: fetchProjects,
                onOpenCreateProject: handleOpenCreateProject,
              }}
            />
          </ErrorBoundary>
        </main>
      </div>

      {/* ─── Global Create Project Modal ─── */}
      <CreateProjectModal
        isOpen={isCreateProjectModalOpen}
        onClose={() => setIsCreateProjectModalOpen(false)}
        onProjectCreated={() => {
          fetchProjects();
        }}
      />
    </div>
  );
}
