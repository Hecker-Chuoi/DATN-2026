import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Outlet, useParams, useNavigate } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import { teamApi, invitationApi } from '../../api.js';
import CreateProjectModal from '../project/CreateProjectModal.jsx';
import CreateTeamModal from '../modals/CreateTeamModal.jsx';
import { useWorkspace } from '../../context/WorkspaceContext.jsx';
import { useWorkspaceRole } from '../../hooks/useWorkspaceRole.js';

export default function WorkspaceLayout({ currentUser, onLogout }) {
  const { workspaceId } = useParams();
  const navigate = useNavigate();
  const { loading: workspaceLoading } = useWorkspace();
  const { notMember } = useWorkspaceRole();
  const [teams, setTeams] = useState([]);
  const [loadingTeams, setLoadingTeams] = useState(true);
  const [isCreateProjectModalOpen, setIsCreateProjectModalOpen] = useState(false);
  const [isCreateTeamModalOpen, setIsCreateTeamModalOpen] = useState(false);
  const [selectedTeamIdForProject, setSelectedTeamIdForProject] = useState(null);

  // Danh sách team PHẢI được scope theo workspace đang xem, nếu không thì đổi
  // workspace vẫn thấy y nguyên team cũ và tính năng chỉ là trang trí.
  const fetchTeams = useCallback(async () => {
    if (!workspaceId) return;
    try {
      setLoadingTeams(true);
      const res = await teamApi.listMine({ workspaceId });
      setTeams(Array.isArray(res?.data) ? res.data : []);
    } catch (err) {
      console.error('Failed to load teams:', err);
      setTeams([]);
    } finally {
      setLoadingTeams(false);
    }
  }, [workspaceId]);

  // Phụ thuộc workspaceId: một lần navigate đổi workspace là đủ để fetch lại,
  // không cần điều phối refetch thủ công ở switcher.
  useEffect(() => {
    fetchTeams();
  }, [fetchTeams]);

  // Lời mời đang chờ: nguồn chung cho badge sidebar và trang /invitations. Poll 30s là đủ, không cần WebSocket.
  const [invitations, setInvitations] = useState([]);
  const fetchInvitations = async () => {
    try {
      const res = await invitationApi.my();
      setInvitations(Array.isArray(res?.data) ? res.data : []);
    } catch {
      setInvitations([]);
    }
  };
  useEffect(() => {
    fetchInvitations();
    const timer = setInterval(fetchInvitations, 30000);
    return () => clearInterval(timer);
  }, []);

  const handleOpenCreateProject = (teamId = null) => {
    setSelectedTeamIdForProject(teamId);
    setIsCreateProjectModalOpen(true);
  };

  // Chỉ trưởng nhóm (myRole LEADER, owner cũng là LEADER) hoặc Global ADMIN mới được tạo dự án; backend chặn lại lần nữa.
  const isSystemAdmin = Boolean(currentUser?.roles?.includes('ADMIN') || currentUser?.roles?.includes('ROLE_ADMIN'));
  const leaderTeams = useMemo(() => {
    return isSystemAdmin ? teams : teams.filter((t) => t.myRole === 'LEADER');
  }, [isSystemAdmin, teams]);
  const canCreateProject = isSystemAdmin || leaderTeams.length > 0;

  // Deep-link vào workspace mình không thuộc: hiện trạng thái rõ ràng, thay vì
  // sidebar rỗng kèm một loạt request lỗi.
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
          Bạn không có quyền truy cập workspace này
        </h2>
        <p style={{ fontSize: 13, color: '#64748B', margin: 0, textAlign: 'center', maxWidth: 420 }}>
          Workspace không tồn tại, hoặc bạn đã bị xóa khỏi danh sách thành viên. Hãy liên hệ chủ
          sở hữu workspace để được thêm lại.
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
          Về danh sách workspace
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
      {/* ─── Left Sidebar (Linear Style Accordion) ─── */}
      <Sidebar
        teams={teams}
        loading={loadingTeams}
        onOpenCreateTeam={() => setIsCreateTeamModalOpen(true)}
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
          <Outlet
            context={{
              teams,
              leaderTeams,
              canCreateProject,
              isSystemAdmin,
              currentUser,
              invitations,
              refreshInvitations: fetchInvitations,
              refreshTeams: fetchTeams,
              onOpenCreateProject: handleOpenCreateProject,
              onOpenCreateTeam: () => setIsCreateTeamModalOpen(true),
            }}
          />
        </main>
      </div>

      {/* ─── Global Create Team Modal ─── */}
      <CreateTeamModal
        isOpen={isCreateTeamModalOpen}
        onClose={() => setIsCreateTeamModalOpen(false)}
        onTeamCreated={() => {
          fetchTeams();
        }}
      />

      {/* ─── Global Create Project Modal ─── */}
      <CreateProjectModal
        isOpen={isCreateProjectModalOpen}
        onClose={() => setIsCreateProjectModalOpen(false)}
        teams={leaderTeams}
        allowPersonal={isSystemAdmin}
        defaultTeamId={selectedTeamIdForProject}
        onProjectCreated={() => {
          fetchTeams();
        }}
      />
    </div>
  );
}
