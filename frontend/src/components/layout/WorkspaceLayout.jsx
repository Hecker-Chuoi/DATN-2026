import React, { useState, useEffect } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import { teamApi } from '../../api.js';

export default function WorkspaceLayout({ currentUser, onLogout }) {
  const [teams, setTeams] = useState([]);
  const [loadingTeams, setLoadingTeams] = useState(true);

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
        onOpenCreateTeam={() => {
          console.log('Open create team modal');
        }}
        onOpenCreateProject={(teamId) => {
          console.log('Open create project modal for team', teamId);
        }}
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
              refreshTeams: fetchTeams,
              onOpenCreateProject: () => console.log('Create project'),
            }}
          />
        </main>
      </div>
    </div>
  );
}
