import React, { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import Header from './Header.jsx';
import CreateProjectModal from '../modals/CreateProjectModal.jsx';
import CreateTeamModal from '../modals/CreateTeamModal.jsx';
import { teamApi } from '../../api.js';

export default function WorkspaceLayout({ currentUser, onLogout }) {
  const [teams, setTeams] = useState([]);
  const [loadingTeams, setLoadingTeams] = useState(true);
  const [isCreateProjectOpen, setIsCreateProjectOpen] = useState(false);
  const [isCreateTeamOpen, setIsCreateTeamOpen] = useState(false);
  const [selectedTeamForProject, setSelectedTeamForProject] = useState(null);

  const fetchTeams = async () => {
    try {
      setLoadingTeams(true);
      const res = await teamApi.listAll();
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

  return (
    <div style={{ display: 'flex', height: '100vh', width: '100vw', overflow: 'hidden', backgroundColor: '#F8FAFC', color: '#0F172A', fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      {/* ─── Left Sidebar (Linear Style Accordion) ─── */}
      <Sidebar
        teams={teams}
        loading={loadingTeams}
        onOpenCreateTeam={() => setIsCreateTeamOpen(true)}
        onOpenCreateProject={(teamId) => {
          setSelectedTeamForProject(teamId || null);
          setIsCreateProjectOpen(true);
        }}
        currentUser={currentUser}
      />

      {/* ─── Main Content Container ─── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Header
          currentUser={currentUser}
          onLogout={onLogout}
          onOpenCreateProject={() => {
            setSelectedTeamForProject(null);
            setIsCreateProjectOpen(true);
          }}
        />

        <main style={{ flex: 1, overflowY: 'auto', padding: '24px 32px', backgroundColor: '#F8FAFC' }}>
          <Outlet context={{ teams, refreshTeams: fetchTeams, onOpenCreateProject: () => setIsCreateProjectOpen(true) }} />
        </main>
      </div>

      {/* ─── Modals ─── */}
      {isCreateProjectOpen && (
        <CreateProjectModal
          teams={teams}
          initialTeamId={selectedTeamForProject}
          onClose={() => setIsCreateProjectOpen(false)}
          onSuccess={() => {
            setIsCreateProjectOpen(false);
            fetchTeams();
            // Broadcast event or navigate
            window.dispatchEvent(new CustomEvent('project-created'));
          }}
        />
      )}

      {isCreateTeamOpen && (
        <CreateTeamModal
          currentUser={currentUser}
          onClose={() => setIsCreateTeamOpen(false)}
          onSuccess={() => {
            setIsCreateTeamOpen(false);
            fetchTeams();
          }}
        />
      )}
    </div>
  );
}
