import React, { useState, useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import { teamApi, invitationApi } from '../../api.js';
import CreateProjectModal from '../project/CreateProjectModal.jsx';
import CreateTeamModal from '../modals/CreateTeamModal.jsx';

export default function WorkspaceLayout({ currentUser, onLogout }) {
  const [teams, setTeams] = useState([]);
  const [loadingTeams, setLoadingTeams] = useState(true);
  const [isCreateProjectModalOpen, setIsCreateProjectModalOpen] = useState(false);
  const [isCreateTeamModalOpen, setIsCreateTeamModalOpen] = useState(false);
  const [selectedTeamIdForProject, setSelectedTeamIdForProject] = useState(null);

  const fetchTeams = async () => {
    try {
      setLoadingTeams(true);
      const res = await teamApi.listMine();
      if (res && res.data) {
        setTeams(res.data);
      }
    } catch (err) {
      console.error('Failed to load teams:', err);
    } finally {
      setLoadingTeams(false);
    }
  };

  useEffect(() => {
    fetchTeams();
  }, []);

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
  const leaderTeams = isSystemAdmin ? teams : teams.filter((t) => t.myRole === 'LEADER');
  const canCreateProject = isSystemAdmin || leaderTeams.length > 0;

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
        canCreateProject={canCreateProject}
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
