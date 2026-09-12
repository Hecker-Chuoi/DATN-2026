import React, { useState, useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import { teamApi } from '../../api.js';
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

  const handleOpenCreateProject = (teamId = null) => {
    setSelectedTeamIdForProject(teamId);
    setIsCreateProjectModalOpen(true);
  };

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
              currentUser,
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
        teams={teams}
        defaultTeamId={selectedTeamIdForProject}
        onProjectCreated={() => {
          fetchTeams();
        }}
      />
    </div>
  );
}
