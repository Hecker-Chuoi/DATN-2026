import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams, useOutletContext, useNavigate } from 'react-router-dom';
import {
  FolderKanban,
  Plus,
  Search,
  RefreshCw,
  ChevronRight,
  Zap,
  Kanban,
  Calendar,
  Layers,
  Sparkles,
  SlidersHorizontal,
  Bookmark,
  Users
} from 'lucide-react';
import { projectApi } from '../api.js';
import CreateProjectModal from '../components/project/CreateProjectModal.jsx';

export default function ProjectListPage() {
  const { teams } = useOutletContext() || {};
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const teamIdFilter = searchParams.get('teamId') || '';

  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState('ALL'); // ALL, SCRUM, KANBAN, TEMPLATES
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);

  const fetchProjects = async () => {
    try {
      setLoading(true);
      const res = await projectApi.listAll({
        teamId: teamIdFilter || undefined,
        search: searchTerm || undefined,
      });

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

  const handleProjectCreated = (newProject) => {
    fetchProjects();
  };

  const filteredProjects = useMemo(() => {
    return projects.filter((proj) => {
      // Type Tab Filter
      if (activeTab === 'SCRUM' && proj.projectType !== 'SOFTWARE_SCRUM') return false;
      if (activeTab === 'KANBAN' && proj.projectType !== 'SOFTWARE_KANBAN') return false;
      if (activeTab === 'TEMPLATES' && !proj.isTemplate) return false;

      // Keyword Search Filter
      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase();
        const matchName = proj.name?.toLowerCase().includes(term);
        const matchKey = proj.key?.toLowerCase().includes(term);
        const matchDesc = proj.description?.toLowerCase().includes(term);
        return matchName || matchKey || matchDesc;
      }
      return true;
    });
  }, [projects, activeTab, searchTerm]);

  const currentTeam = teams?.find((t) => t.id === teamIdFilter);

  return (
    <div
      style={{
        backgroundColor: '#F8FAFC',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        overflow: 'hidden',
      }}
    >
      {/* ─── Header Bar ─── */}
      <div
        style={{
          padding: '16px 24px',
          borderBottom: '1px solid #E2E8F0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#64748B', marginBottom: 2 }}>
            {currentTeam ? (
              <>
                <span style={{ fontWeight: 500 }}>{currentTeam.name}</span>
                <span>›</span>
                <span style={{ color: '#0F172A', fontWeight: 600 }}>Projects</span>
              </>
            ) : (
              <span style={{ color: '#0F172A', fontWeight: 600, fontSize: 16 }}>Danh sách Dự án</span>
            )}
          </div>
          <p style={{ fontSize: 12, color: '#64748B', margin: 0 }}>
            Quản lý các dự án phần mềm, chu kỳ Sprint và bảng Kanban trong Workspace
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            onClick={fetchProjects}
            title="Làm mới"
            style={{
              backgroundColor: '#F1F5F9',
              border: '1px solid #E2E8F0',
              color: '#475569',
              cursor: 'pointer',
              padding: '7px 10px',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            <RefreshCw size={14} />
            <span>Làm mới</span>
          </button>
          <button
            onClick={() => setIsCreateModalOpen(true)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              backgroundColor: '#2563EB',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: 8,
              padding: '7px 14px',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              boxShadow: '0 2px 4px rgba(37,99,235,0.2)',
            }}
          >
            <Plus size={16} />
            <span>Tạo dự án</span>
          </button>
        </div>
      </div>

      {/* ─── Filter & Search Toolbar ─── */}
      <div
        style={{
          padding: '12px 24px',
          borderBottom: '1px solid #E2E8F0',
          backgroundColor: '#FFFFFF',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        {/* Tabs Filter */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, backgroundColor: '#F1F5F9', padding: 3, borderRadius: 8 }}>
          {[
            { id: 'ALL', label: 'Tất cả' },
            { id: 'SCRUM', label: 'Scrum' },
            { id: 'KANBAN', label: 'Kanban' },
            { id: 'TEMPLATES', label: 'Dự án Mẫu' },
          ].map((tab) => {
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  padding: '5px 12px',
                  borderRadius: 6,
                  border: 'none',
                  backgroundColor: active ? '#FFFFFF' : 'transparent',
                  color: active ? '#0F172A' : '#64748B',
                  fontWeight: active ? 600 : 500,
                  fontSize: 12,
                  cursor: 'pointer',
                  boxShadow: active ? '0 1px 2px rgba(0,0,0,0.05)' : 'none',
                  transition: 'all 0.15s ease',
                }}
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Search Input */}
        <div style={{ position: 'relative', width: 280 }}>
          <Search size={14} color="#94A3B8" style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }} />
          <input
            type="text"
            placeholder="Tìm theo tên, key hoặc mô tả..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{
              width: '100%',
              padding: '6px 12px 6px 32px',
              borderRadius: 8,
              border: '1px solid #E2E8F0',
              fontSize: 13,
              color: '#0F172A',
              outline: 'none',
              backgroundColor: '#F8FAFC',
              boxSizing: 'border-box',
            }}
          />
        </div>
      </div>

      {/* ─── Projects Grid ─── */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>
        {loading ? (
          <div style={{ padding: '60px 0', textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Đang tải danh sách dự án...
          </div>
        ) : filteredProjects.length === 0 ? (
          <div
            style={{
              padding: '60px 20px',
              textAlign: 'center',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              border: '1px dashed #CBD5E1',
            }}
          >
            <div
              style={{
                width: 56,
                height: 56,
                borderRadius: '50%',
                backgroundColor: '#EFF6FF',
                color: '#2563EB',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                marginBottom: 16,
              }}
            >
              <FolderKanban size={28} />
            </div>
            <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', marginBottom: 6 }}>
              {searchTerm ? 'Không tìm thấy dự án phù hợp' : 'Chưa có dự án nào'}
            </h3>
            <p style={{ fontSize: 13, color: '#64748B', maxWidth: 420, margin: '0 0 20px 0', lineHeight: 1.5 }}>
              {searchTerm
                ? 'Hãy thử thay đổi từ khóa tìm kiếm hoặc chuyển tab lọc sang trạng thái khác.'
                : 'Bắt đầu tạo dự án Scrum hoặc Kanban đầu tiên để tự động khởi tạo Workflow, Board và phân công nhiệm vụ.'}
            </p>
            {!searchTerm && (
              <button
                onClick={() => setIsCreateModalOpen(true)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  backgroundColor: '#2563EB',
                  color: '#FFFFFF',
                  border: 'none',
                  borderRadius: 8,
                  padding: '9px 18px',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                  boxShadow: '0 2px 4px rgba(37,99,235,0.2)',
                }}
              >
                <Plus size={16} />
                <span>Tạo dự án mới ngay</span>
              </button>
            )}
          </div>
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
              gap: 16,
            }}
          >
            {filteredProjects.map((proj) => {
              const isScrum = proj.projectType === 'SOFTWARE_SCRUM';
              const team = teams?.find((t) => t.id === proj.teamId);

              return (
                <div
                  key={proj.id}
                  onClick={() => navigate(`/projects/${proj.id}`)}
                  style={{
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #E2E8F0',
                    borderRadius: 10,
                    padding: '18px 20px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    cursor: 'pointer',
                    transition: 'all 0.2s cubic-bezier(0.16, 1, 0.3, 1)',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                    position: 'relative',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = '#93C5FD';
                    e.currentTarget.style.transform = 'translateY(-2px)';
                    e.currentTarget.style.boxShadow = '0 10px 15px -3px rgba(0, 0, 0, 0.08)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = '#E2E8F0';
                    e.currentTarget.style.transform = 'translateY(0)';
                    e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.04)';
                  }}
                >
                  <div>
                    {/* Top Row: Icon, Key Badge, Type Badge */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div
                          style={{
                            width: 36,
                            height: 36,
                            borderRadius: 8,
                            backgroundColor: isScrum ? '#EFF6FF' : '#F0FDF4',
                            color: isScrum ? '#2563EB' : '#16A34A',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          {isScrum ? <Zap size={18} /> : <Kanban size={18} />}
                        </div>
                        <div>
                          <span
                            style={{
                              display: 'inline-block',
                              padding: '2px 7px',
                              borderRadius: 4,
                              fontSize: 11,
                              fontWeight: 700,
                              backgroundColor: '#F1F5F9',
                              color: '#334155',
                              letterSpacing: '0.05em',
                            }}
                          >
                            {proj.key}
                          </span>
                        </div>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        {proj.isTemplate && (
                          <span
                            style={{
                              padding: '2px 8px',
                              borderRadius: 12,
                              fontSize: 11,
                              fontWeight: 600,
                              backgroundColor: '#FAF5FF',
                              color: '#7E22CE',
                              border: '1px solid #E9D5FF',
                            }}
                          >
                            Template
                          </span>
                        )}
                        <span
                          style={{
                            padding: '2px 8px',
                            borderRadius: 12,
                            fontSize: 11,
                            fontWeight: 600,
                            backgroundColor: isScrum ? '#EFF6FF' : '#F0FDF4',
                            color: isScrum ? '#1D4ED8' : '#15803D',
                          }}
                        >
                          {isScrum ? 'Scrum' : 'Kanban'}
                        </span>
                      </div>
                    </div>

                    {/* Project Name & Description */}
                    <h3 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: '0 0 6px 0' }}>
                      {proj.name}
                    </h3>
                    <p
                      style={{
                        fontSize: 13,
                        color: '#64748B',
                        margin: 0,
                        lineHeight: 1.4,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        minHeight: 36,
                      }}
                    >
                      {proj.description || 'Chưa có mô tả dự án.'}
                    </p>
                  </div>

                  {/* Bottom Metadata */}
                  <div
                    style={{
                      marginTop: 16,
                      paddingTop: 12,
                      borderTop: '1px solid #F1F5F9',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      fontSize: 12,
                      color: '#64748B',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      {team ? (
                        <>
                          <Layers size={13} color="#94A3B8" />
                          <span>{team.name}</span>
                        </>
                      ) : (
                        <span>Cá nhân</span>
                      )}
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#2563EB', fontWeight: 600 }}>
                      <span>Mở Board</span>
                      <ChevronRight size={14} />
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ─── Create Project Modal ─── */}
      <CreateProjectModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        teams={teams}
        defaultTeamId={teamIdFilter || undefined}
        onProjectCreated={handleProjectCreated}
      />
    </div>
  );
}
