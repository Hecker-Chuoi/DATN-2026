import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams, useOutletContext, useNavigate } from 'react-router-dom';
import {
  FolderKanban,
  Plus,
  Search,
  RefreshCw,
  Star,
  MoreHorizontal,
  Link2,
  SlidersHorizontal,
  Kanban,
  Zap,
  Layers,
  Sparkles,
  Bookmark,
  Users,
  Calendar,
  ChevronRight,
  Folder,
  Trash2,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi } from '../api.js';
import CreateProjectModal from '../components/project/CreateProjectModal.jsx';
import DeleteProjectModal from '../components/project/DeleteProjectModal.jsx';

export default function ProjectListPage() {
  const { teams, currentUser } = useOutletContext() || {};
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const teamIdFilter = searchParams.get('teamId') || '';

  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState('ALL'); // ALL, SCRUM, KANBAN, TEMPLATES
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isStarred, setIsStarred] = useState(false);

  // Delete project state
  const [projectToDelete, setProjectToDelete] = useState(null);
  const [activeMenuProjectId, setActiveMenuProjectId] = useState(null);

  useEffect(() => {
    const handleDocumentClick = () => {
      setActiveMenuProjectId(null);
    };
    if (activeMenuProjectId) {
      document.addEventListener('click', handleDocumentClick);
    }
    return () => {
      document.removeEventListener('click', handleDocumentClick);
    };
  }, [activeMenuProjectId]);

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
        backgroundColor: '#FFFFFF',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        overflow: 'hidden',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      {/* ─── Top Header Breadcrumbs Bar ─── */}
      <div
        style={{
          height: 48,
          minHeight: 48,
          padding: '0 24px',
          borderBottom: '1px solid #F1F5F9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{
              width: 24,
              height: 24,
              borderRadius: 6,
              backgroundColor: '#EFF6FF',
              color: '#2563EB',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <FolderKanban size={14} />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
            {currentTeam ? (
              <>
                <span style={{ color: '#64748B', fontWeight: 500 }}>{currentTeam.name}</span>
                <span style={{ color: '#CBD5E1' }}>/</span>
                <span>Projects</span>
              </>
            ) : (
              <span>Projects</span>
            )}
          </div>
          <button
            onClick={() => setIsStarred(!isStarred)}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: isStarred ? '#EAB308' : '#94A3B8',
              padding: 2,
              display: 'flex',
              alignItems: 'center',
            }}
            title={isStarred ? 'Bỏ yêu thích' : 'Yêu thích'}
          >
            <Star size={15} fill={isStarred ? '#EAB308' : 'none'} />
          </button>
          <button
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#94A3B8',
              padding: 2,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <MoreHorizontal size={16} />
          </button>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button
            onClick={() => {
              navigator.clipboard.writeText(window.location.href);
              toast.success('Đã sao chép liên kết');
            }}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#64748B',
              padding: 6,
              borderRadius: 4,
              display: 'flex',
              alignItems: 'center',
            }}
            title="Sao chép liên kết"
          >
            <Link2 size={16} />
          </button>
        </div>
      </div>

      {/* ─── Subheader / Navigation Tabs & Actions Bar ─── */}
      <div
        style={{
          padding: '12px 24px 8px 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        {/* Navigation Pills */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {[
            { id: 'ALL', label: 'Tất cả dự án' },
            { id: 'SCRUM', label: 'Dự án Scrum' },
            { id: 'KANBAN', label: 'Dự án Kanban' },
            { id: 'TEMPLATES', label: 'Dự án mẫu' },
          ].map((tab) => {
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  padding: '6px 14px',
                  borderRadius: 20,
                  fontSize: 13,
                  fontWeight: active ? 600 : 500,
                  border: 'none',
                  cursor: 'pointer',
                  backgroundColor: active ? '#F1F5F9' : 'transparent',
                  color: active ? '#0F172A' : '#64748B',
                  transition: 'all 0.15s ease',
                }}
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Action Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* Search Box */}
          <div style={{ position: 'relative', width: 220 }}>
            <Search
              size={13}
              color="#94A3B8"
              style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
            />
            <input
              type="text"
              placeholder="Tìm kiếm dự án..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{
                width: '100%',
                padding: '6px 10px 6px 28px',
                borderRadius: 6,
                border: '1px solid #E2E8F0',
                fontSize: 12,
                color: '#0F172A',
                outline: 'none',
                backgroundColor: '#FFFFFF',
              }}
            />
          </div>

          <button
            onClick={() => setIsCreateModalOpen(true)}
            style={{
              backgroundColor: '#FFFFFF',
              color: '#0F172A',
              border: '1px solid #E2E8F0',
              padding: '6px 12px',
              borderRadius: 6,
              fontSize: 13,
              fontWeight: 500,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
            }}
          >
            <Plus size={14} />
            <span>Tạo dự án mới</span>
          </button>

          <button
            onClick={fetchProjects}
            title="Làm mới"
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid #E2E8F0',
              padding: '6px 8px',
              borderRadius: 6,
              color: '#64748B',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <RefreshCw size={14} />
          </button>
        </div>
      </div>

      {/* ─── Projects Content List ─── */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '12px 24px 32px 24px' }}>
        {loading ? (
          <div style={{ padding: 48, textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Đang tải danh sách dự án...
          </div>
        ) : filteredProjects.length === 0 ? (
          <div
            style={{
              padding: '60px 24px',
              textAlign: 'center',
              backgroundColor: '#FAFAFA',
              borderRadius: 12,
              border: '1px dashed #CBD5E1',
              marginTop: 12,
            }}
          >
            <div
              style={{
                width: 48,
                height: 48,
                borderRadius: 12,
                backgroundColor: '#EFF6FF',
                color: '#2563EB',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 16px auto',
              }}
            >
              <FolderKanban size={24} />
            </div>
            <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', marginBottom: 6 }}>
              Chưa có dự án nào
            </h3>
            <p style={{ fontSize: 13, color: '#64748B', maxWidth: 420, margin: '0 auto 20px auto', lineHeight: 1.5 }}>
              Bắt đầu tạo dự án Scrum hoặc Kanban đầu tiên để tự động khởi tạo Workflow, Board và quản lý công việc.
            </p>
            <button
              onClick={() => setIsCreateModalOpen(true)}
              style={{
                backgroundColor: '#2563EB',
                color: '#FFFFFF',
                border: 'none',
                padding: '8px 18px',
                borderRadius: 6,
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
              }}
            >
              <Plus size={15} />
              <span>Tạo dự án mới ngay</span>
            </button>
          </div>
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
              gap: 16,
              marginTop: 8,
            }}
          >
            {filteredProjects.map((project) => {
              const isScrum = project.projectType === 'SOFTWARE_SCRUM';
              const isSystemAdmin = currentUser?.roles?.includes('ADMIN') || currentUser?.roles?.includes('ROLE_ADMIN');
              const canDeleteProject = isSystemAdmin || (currentUser && (project.leadUserId === currentUser.id || project.createdBy === currentUser.id));

              return (
                <div
                  key={project.id}
                  onClick={() => navigate(`/projects/${project.id}`)}
                  style={{
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #E2E8F0',
                    borderRadius: 10,
                    padding: 18,
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    minHeight: 150,
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = '#93C5FD';
                    e.currentTarget.style.boxShadow = '0 4px 12px rgba(0, 0, 0, 0.05)';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = '#E2E8F0';
                    e.currentTarget.style.boxShadow = 'none';
                  }}
                >
                  <div>
                    {/* Top Row: Key & Type Badge & Action Menu */}
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                      <span
                        style={{
                          fontSize: 11,
                          fontWeight: 700,
                          color: '#2563EB',
                          backgroundColor: '#EFF6FF',
                          padding: '2px 8px',
                          borderRadius: 4,
                        }}
                      >
                        {project.key}
                      </span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span
                          style={{
                            fontSize: 11,
                            fontWeight: 600,
                            color: isScrum ? '#7C3AED' : '#059669',
                            backgroundColor: isScrum ? '#F5F3FF' : '#ECFDF5',
                            padding: '2px 8px',
                            borderRadius: 12,
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                          }}
                        >
                          {isScrum ? <Zap size={11} /> : <Kanban size={11} />}
                          <span>{isScrum ? 'Quy trình Scrum' : 'Bảng Kanban'}</span>
                        </span>

                        {canDeleteProject && (
                          <div style={{ position: 'relative' }}>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setActiveMenuProjectId(activeMenuProjectId === project.id ? null : project.id);
                              }}
                              style={{
                                border: 'none',
                                background: activeMenuProjectId === project.id ? '#F1F5F9' : 'transparent',
                                cursor: 'pointer',
                                padding: 3,
                                borderRadius: 4,
                                color: activeMenuProjectId === project.id ? '#0F172A' : '#94A3B8',
                                display: 'flex',
                                alignItems: 'center',
                                transition: 'all 0.15s ease',
                              }}
                              title="Tùy chọn dự án"
                              onMouseEnter={(e) => {
                                e.currentTarget.style.color = '#0F172A';
                                e.currentTarget.style.backgroundColor = '#F1F5F9';
                              }}
                              onMouseLeave={(e) => {
                                if (activeMenuProjectId !== project.id) {
                                  e.currentTarget.style.color = '#94A3B8';
                                  e.currentTarget.style.backgroundColor = 'transparent';
                                }
                              }}
                            >
                              <MoreHorizontal size={14} />
                            </button>

                            {activeMenuProjectId === project.id && (
                              <div
                                onClick={(e) => e.stopPropagation()}
                                style={{
                                  position: 'absolute',
                                  top: '100%',
                                  right: 0,
                                  marginTop: 4,
                                  backgroundColor: '#FFFFFF',
                                  borderRadius: 8,
                                  boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1)',
                                  border: '1px solid #E2E8F0',
                                  minWidth: 180,
                                  zIndex: 30,
                                  overflow: 'hidden',
                                  padding: '4px',
                                }}
                              >
                                <button
                                  type="button"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setActiveMenuProjectId(null);
                                    setProjectToDelete(project);
                                  }}
                                  style={{
                                    width: '100%',
                                    padding: '8px 12px',
                                    borderRadius: 6,
                                    border: 'none',
                                    backgroundColor: 'transparent',
                                    color: '#EF4444',
                                    fontSize: 12,
                                    fontWeight: 500,
                                    cursor: 'pointer',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 8,
                                    textAlign: 'left',
                                    transition: 'all 0.15s ease',
                                  }}
                                  onMouseEnter={(e) => {
                                    e.currentTarget.style.backgroundColor = '#FEF2F2';
                                  }}
                                  onMouseLeave={(e) => {
                                    e.currentTarget.style.backgroundColor = 'transparent';
                                  }}
                                >
                                  <Trash2 size={13} color="#EF4444" />
                                  <span>Xóa dự án (Thùng rác)</span>
                                </button>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Project Name */}
                    <h4 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: '0 0 6px 0', lineHeight: 1.3 }}>
                      {project.name}
                    </h4>

                    {/* Description */}
                    <p
                      style={{
                        fontSize: 12,
                        color: '#64748B',
                        margin: 0,
                        lineHeight: 1.4,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                      }}
                    >
                      {project.description || 'Không có mô tả cho dự án này.'}
                    </p>
                  </div>

                  {/* Bottom Row */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      borderTop: '1px solid #F1F5F9',
                      paddingTop: 12,
                      marginTop: 12,
                      fontSize: 11,
                      color: '#94A3B8',
                    }}
                  >
                    <span>Mã: {project.id?.substring(0, 8)}...</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#2563EB', fontWeight: 600 }}>
                      <span>Mở bảng</span>
                      <ChevronRight size={12} />
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Create Project Modal */}
      <CreateProjectModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        teams={teams}
        defaultTeamId={teamIdFilter || undefined}
        onProjectCreated={handleProjectCreated}
      />

      {/* Delete Project Modal */}
      <DeleteProjectModal
        isOpen={!!projectToDelete}
        onClose={() => setProjectToDelete(null)}
        project={projectToDelete}
        onDeleted={() => {
          fetchProjects();
        }}
      />
    </div>
  );
}
