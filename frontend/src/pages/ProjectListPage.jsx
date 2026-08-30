import React, { useState, useEffect } from 'react';
import { useSearchParams, useOutletContext, useNavigate } from 'react-router-dom';
import { FolderKanban, Plus, Search, RefreshCw, ChevronRight } from 'lucide-react';
import { projectApi } from '../api.js';

export default function ProjectListPage() {
  const { teams, onOpenCreateProject } = useOutletContext();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const teamIdFilter = searchParams.get('teamId') || '';

  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  const fetchProjects = async () => {
    try {
      setLoading(true);
      const res = await projectApi.listAll({ teamId: teamIdFilter || undefined });
      if (res && res.data) {
        setProjects(Array.isArray(res.data) ? res.data : (res.data.content || []));
      }
    } catch (err) {
      console.warn('Projects not yet created or loading error:', err);
      setProjects([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProjects();
  }, [teamIdFilter]);

  const currentTeam = teams?.find((t) => t.id === teamIdFilter);

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        overflow: 'hidden',
      }}
    >
      {/* ─── Header ─── */}
      <div
        style={{
          height: 48,
          minHeight: 48,
          padding: '0 24px',
          borderBottom: '1px solid #E2E8F0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
          {currentTeam ? (
            <>
              <span style={{ color: '#64748B', fontWeight: 500 }}>{currentTeam.name}</span>
              <span style={{ color: '#94A3B8' }}>›</span>
              <span>Projects</span>
            </>
          ) : (
            <span>Projects</span>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            onClick={fetchProjects}
            title="Làm mới"
            style={{
              background: 'none',
              border: 'none',
              color: '#64748B',
              cursor: 'pointer',
              padding: '6px',
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <RefreshCw size={15} />
          </button>
          <button
            onClick={onOpenCreateProject}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 5,
              backgroundColor: '#2563EB',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: 6,
              padding: '6px 12px',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Plus size={15} />
            <span>Tạo dự án</span>
          </button>
        </div>
      </div>

      {/* ─── Body List ─── */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>
        {loading ? (
          <div style={{ padding: '60px 0', textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Đang tải danh sách dự án...
          </div>
        ) : projects.length === 0 ? (
          <div
            style={{
              padding: '60px 20px',
              textAlign: 'center',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <FolderKanban size={40} color="#94A3B8" style={{ marginBottom: 12 }} />
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#0F172A', marginBottom: 4 }}>
              Chưa có dự án nào
            </h3>
            <p style={{ fontSize: 13, color: '#64748B', maxWidth: 400, margin: '0 0 16px 0' }}>
              Bắt đầu tạo dự án Scrum hoặc Kanban để quản lý các tính năng, nhiệm vụ và chu kỳ phát triển.
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {projects.map((proj) => (
              <div
                key={proj.id}
                onClick={() => navigate(`/projects/${proj.id}`)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '14px 18px',
                  border: '1px solid #E2E8F0',
                  borderRadius: 8,
                  backgroundColor: '#FFFFFF',
                  cursor: 'pointer',
                  transition: 'border-color 0.15s ease',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <FolderKanban size={20} color="#2563EB" />
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>{proj.name}</div>
                    <div style={{ fontSize: 12, color: '#64748B' }}>Key: {proj.key} • {proj.projectType || 'SCRUM'}</div>
                  </div>
                </div>
                <ChevronRight size={16} color="#94A3B8" />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
