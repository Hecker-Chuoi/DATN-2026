import React, { useState, useEffect } from 'react';
import { useSearchParams, useOutletContext, Link, useNavigate } from 'react-router-dom';
import {
  FolderKanban,
  Plus,
  Copy,
  Sparkles,
  Search,
  Filter,
  Calendar,
  Users,
  Layers,
  ArrowRight,
  Globe,
  Lock,
  CheckCircle2,
  Clock,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  Circle,
  XCircle,
  ChevronDown
} from 'lucide-react';
import { projectApi } from '../api.js';
import CreateProjectModal from '../components/modals/CreateProjectModal.jsx';

export default function ProjectListPage() {
  const { teams, onOpenCreateProject } = useOutletContext();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const teamIdFilter = searchParams.get('teamId') || '';

  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState('all'); // 'all' | 'active'
  const [cloningProject, setCloningProject] = useState(null);

  // Pagination state (0-based index theo Spring Data JPA)
  const [page, setPage] = useState(0);
  const [pageSize] = useState(12);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const fetchProjects = async (targetPage = page) => {
    try {
      setLoading(true);
      const params = {
        page: targetPage,
        size: pageSize,
        teamId: teamIdFilter || undefined,
        status: activeTab === 'active' ? 'ACTIVE' : undefined,
        search: searchTerm.trim() || undefined,
      };

      const res = await projectApi.listMyProjects(params);
      console.log('ProjectListPage loaded projects:', res);

      if (res && res.data) {
        if (res.data.content) {
          setProjects(res.data.content);
          setTotalPages(res.data.totalPages || 1);
          setTotalElements(res.data.totalElements || res.data.content.length);
          setPage(res.data.pageNumber || 0);
        } else if (Array.isArray(res.data)) {
          setProjects(res.data);
          setTotalElements(res.data.length);
        }
      } else if (Array.isArray(res)) {
        setProjects(res);
        setTotalElements(res.length);
      }
    } catch (err) {
      console.error('Failed to fetch projects:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setPage(0);
    fetchProjects(0);

    const handleProjectCreated = () => fetchProjects(0);
    window.addEventListener('project-created', handleProjectCreated);
    return () => window.removeEventListener('project-created', handleProjectCreated);
  }, [teamIdFilter, activeTab]);

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    fetchProjects(0);
  };

  const handlePageChange = (newPage) => {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
      fetchProjects(newPage);
    }
  };

  const currentTeam = teams?.find((t) => t.id === teamIdFilter);

  const formatDate = (dateStr) => {
    if (!dateStr) return '---';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch {
      return dateStr;
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'ACTIVE':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 5,
              fontSize: 12,
              fontWeight: 500,
              color: '#059669',
              backgroundColor: '#ECFDF5',
              border: '1px solid #A7F3D0',
              padding: '2px 8px',
              borderRadius: 12,
            }}
          >
            <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#10B981' }} />
            Active
          </span>
        );
      case 'PLANNING':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 5,
              fontSize: 12,
              fontWeight: 500,
              color: '#2563EB',
              backgroundColor: '#EFF6FF',
              border: '1px solid #BFDBFE',
              padding: '2px 8px',
              borderRadius: 12,
            }}
          >
            <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#3B82F6' }} />
            Planning
          </span>
        );
      case 'COMPLETED':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 5,
              fontSize: 12,
              fontWeight: 500,
              color: '#16A34A',
              backgroundColor: '#F0FDF4',
              border: '1px solid #BBF7D0',
              padding: '2px 8px',
              borderRadius: 12,
            }}
          >
            <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#22C55E' }} />
            Completed
          </span>
        );
      case 'ON_HOLD':
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 5,
              fontSize: 12,
              fontWeight: 500,
              color: '#D97706',
              backgroundColor: '#FFFBEB',
              border: '1px solid #FDE68A',
              padding: '2px 8px',
              borderRadius: 12,
            }}
          >
            <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#F59E0B' }} />
            On Hold
          </span>
        );
      default:
        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 5,
              fontSize: 12,
              fontWeight: 500,
              color: '#64748B',
              backgroundColor: '#F1F5F9',
              border: '1px solid #E2E8F0',
              padding: '2px 8px',
              borderRadius: 12,
            }}
          >
            <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#94A3B8' }} />
            {status || 'Draft'}
          </span>
        );
    }
  };

  return (
    <div style={{ backgroundColor: '#FFFFFF', height: '100%', maxHeight: '100%', display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
      {/* ════════ 1. TOP HEADER (Unified Header Bar) ════════ */}
      <div
        style={{
          height: 48,
          minHeight: 48,
          flexShrink: 0,
          padding: '0 24px',
          borderBottom: '1px solid #E2E8F0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
        }}
      >
        {/* Left: Breadcrumbs / Title */}
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

        {/* Right: Actions */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            onClick={() => fetchProjects(page)}
            title="Tải lại danh sách"
            style={{
              background: 'none',
              border: 'none',
              color: '#64748B',
              cursor: 'pointer',
              padding: '5px',
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'all 0.15s ease',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = '#F1F5F9';
              e.currentTarget.style.color = '#0F172A';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = 'transparent';
              e.currentTarget.style.color = '#64748B';
            }}
          >
            <RefreshCw size={15} />
          </button>

          <button
            onClick={onOpenCreateProject}
            title="Tạo dự án mới"
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
              boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
              transition: 'background-color 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
          >
            <Plus size={15} />
            <span>Tạo dự án</span>
          </button>
        </div>
      </div>

      {/* ════════ 2. SUB FILTER BAR (Linear Style Pills & Search) ════════ */}
      <div
        style={{
          flexShrink: 0,
          padding: '8px 24px',
          borderBottom: '1px solid #F1F5F9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
          gap: 16,
        }}
      >
        {/* Left: Tab Pills */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <button
            onClick={() => setActiveTab('all')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '5px 12px',
              borderRadius: 6,
              fontSize: 13,
              fontWeight: 600,
              color: '#0F172A',
              backgroundColor: '#F1F5F9',
              border: 'none',
              cursor: 'pointer',
            }}
          >
            <span>All projects</span>
            <span
              style={{
                fontSize: 11,
                padding: '1px 6px',
                borderRadius: 10,
                backgroundColor: '#E2E8F0',
                color: '#0F172A',
                fontWeight: 600,
              }}
            >
              {totalElements || projects.length}
            </span>
          </button>
        </div>

        {/* Right: Search Input */}
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
            <Search size={14} color="#94A3B8" style={{ position: 'absolute', left: 10 }} />
            <input
              type="text"
              placeholder="Tìm kiếm dự án... (Enter)"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{
                width: 220,
                padding: '5px 10px 5px 30px',
                borderRadius: 6,
                border: '1px solid #E2E8F0',
                fontSize: 12,
                outline: 'none',
                color: '#0F172A',
                backgroundColor: '#F8FAFC',
                transition: 'border-color 0.15s ease, width 0.2s ease',
              }}
              onFocus={(e) => {
                e.target.style.borderColor = '#2563EB';
                e.target.style.backgroundColor = '#FFFFFF';
                e.target.style.width = '260px';
              }}
              onBlur={(e) => {
                e.target.style.borderColor = '#E2E8F0';
                e.target.style.backgroundColor = '#F8FAFC';
                e.target.style.width = '220px';
              }}
            />
          </div>
        </form>
      </div>

      {/* ════════ 3. TABLE LIST HEADER & ROWS (SCROLLABLE) ════════ */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* Table Column Headers */}
        <div
          style={{
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            padding: '8px 24px',
            backgroundColor: '#FAFAFA',
            borderBottom: '1px solid #E2E8F0',
            fontSize: 12,
            fontWeight: 600,
            color: '#64748B',
            userSelect: 'none',
          }}
        >
          <div style={{ flex: 3.5, minWidth: 240 }}>Tên dự án</div>
          <div style={{ flex: 1.5, minWidth: 120 }}>Nhóm</div>
          <div style={{ flex: 1.2, minWidth: 100 }}>Trạng thái</div>
          <div style={{ flex: 1.2, minWidth: 110 }}>Hạn chót</div>
          <div style={{ flex: 1, minWidth: 80 }}>Tác vụ</div>
          <div style={{ flex: 1, minWidth: 90 }}>Quyền xem</div>
          <div style={{ width: 110, textAlign: 'right' }}>Thao tác</div>
        </div>

        {/* Loading / Empty / Data Rows (Internal Scroll) */}
        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
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
                flex: 1,
              }}
            >
              <FolderKanban size={40} color="#94A3B8" style={{ marginBottom: 12 }} />
              <h3 style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', marginBottom: 4 }}>
                Không tìm thấy dự án nào
              </h3>
              <p style={{ fontSize: 13, color: '#64748B', maxWidth: 360, margin: '0 0 16px 0' }}>
                {searchTerm
                  ? 'Thử tìm kiếm với từ khóa khác hoặc xóa bộ lọc.'
                  : 'Hãy bắt đầu tạo dự án đầu tiên để tổ chức công việc và theo dõi tiến độ.'}
              </p>
              <button
                onClick={onOpenCreateProject}
                style={{
                  backgroundColor: '#2563EB',
                  color: '#FFFFFF',
                  border: 'none',
                  borderRadius: 6,
                  padding: '7px 16px',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Tạo dự án mới
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {projects.map((project, idx) => {
                const pId = project.id || project.projectId;
                const pName = project.name || project.title || 'Dự án chưa đặt tên';
                const pKey = project.key || project.code || 'PRJ';
                const teamName = project.teamName || teams.find((t) => t.id === project.teamId)?.name;

                return (
                  <div
                    key={pId || `project-row-${idx}`}
                    onClick={() => pId && navigate(`/projects/${pId}`)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      padding: '12px 24px',
                      borderBottom: '1px solid #F1F5F9',
                      backgroundColor: '#FFFFFF',
                      cursor: 'pointer',
                      transition: 'background-color 0.12s ease',
                      fontSize: 13,
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.backgroundColor = '#F8FAFC';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.backgroundColor = '#FFFFFF';
                    }}
                  >
                    {/* Column 1: Name + Key + Description */}
                    <div style={{ flex: 3.5, minWidth: 240, display: 'flex', alignItems: 'center', gap: 10, overflow: 'hidden', paddingRight: 12 }}>
                      <div
                        style={{
                          width: 28,
                          height: 28,
                          borderRadius: 6,
                          backgroundColor: '#EFF6FF',
                          border: '1px solid #DBEAFE',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          flexShrink: 0,
                        }}
                      >
                        <FolderKanban size={15} color="#2563EB" />
                      </div>

                      <div style={{ overflow: 'hidden', minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <span style={{ fontWeight: 600, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            {pName}
                          </span>
                          {pKey && (
                            <span
                              style={{
                                fontSize: 10,
                                fontWeight: 700,
                                color: '#64748B',
                                backgroundColor: '#F1F5F9',
                                padding: '1px 5px',
                                borderRadius: 4,
                                flexShrink: 0,
                              }}
                            >
                              {pKey}
                            </span>
                          )}
                        </div>
                        {project.description && (
                          <div
                            style={{
                              fontSize: 12,
                              color: '#94A3B8',
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              marginTop: 2,
                            }}
                          >
                            {project.description}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Column 2: Team */}
                    <div style={{ flex: 1.5, minWidth: 120, display: 'flex', alignItems: 'center', gap: 6, color: '#475569', fontSize: 12 }}>
                      {teamName ? (
                        <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                          <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#6366F1' }} />
                          <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{teamName}</span>
                        </span>
                      ) : (
                        <span style={{ color: '#CBD5E1' }}>---</span>
                      )}
                    </div>

                    {/* Column 3: Status */}
                    <div style={{ flex: 1.2, minWidth: 100 }}>
                      {getStatusBadge(project.status)}
                    </div>

                    {/* Column 4: Target Date */}
                    <div style={{ flex: 1.2, minWidth: 110, display: 'flex', alignItems: 'center', gap: 5, color: '#64748B', fontSize: 12 }}>
                      <Calendar size={13} color="#94A3B8" />
                      <span>{formatDate(project.targetDate || project.createdAt)}</span>
                    </div>

                    {/* Column 5: Tasks Count */}
                    <div style={{ flex: 1, minWidth: 80, display: 'flex', alignItems: 'center', gap: 5, color: '#64748B', fontSize: 12 }}>
                      <Layers size={13} color="#94A3B8" />
                      <span><b>{project.taskCount || 0}</b></span>
                    </div>

                    {/* Column 6: Visibility */}
                    <div style={{ flex: 1, minWidth: 90, display: 'flex', alignItems: 'center', gap: 4, color: '#64748B', fontSize: 12 }}>
                      {project.isPublic ? (
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#10B981' }}>
                          <Globe size={13} />
                          <span>Public</span>
                        </span>
                      ) : (
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#64748B' }}>
                          <Lock size={13} />
                          <span>Private</span>
                        </span>
                      )}
                    </div>

                    {/* Column 7: Action (Clone Button) */}
                    <div
                      style={{
                        width: 110,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'flex-end',
                        gap: 8,
                      }}
                    >
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setCloningProject(project);
                        }}
                        title="Nhân bản cấu trúc dự án này"
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 4,
                          backgroundColor: '#F8FAFC',
                          border: '1px solid #E2E8F0',
                          borderRadius: 6,
                          padding: '4px 8px',
                          fontSize: 12,
                          fontWeight: 500,
                          color: '#475569',
                          cursor: 'pointer',
                          transition: 'all 0.15s ease',
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.backgroundColor = '#EFF6FF';
                          e.currentTarget.style.color = '#2563EB';
                          e.currentTarget.style.borderColor = '#BFDBFE';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.backgroundColor = '#F8FAFC';
                          e.currentTarget.style.color = '#475569';
                          e.currentTarget.style.borderColor = '#E2E8F0';
                        }}
                      >
                        <Copy size={12} />
                        <span>Clone</span>
                      </button>

                      <ChevronRight size={15} color="#CBD5E1" />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* ════════ 4. PAGINATION FOOTER (ALWAYS FIXED AT BOTTOM) ════════ */}
      <div
        style={{
          height: 48,
          minHeight: 48,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 24px',
          borderTop: '1px solid #E2E8F0',
          backgroundColor: '#FFFFFF',
          fontSize: 12,
          color: '#64748B',
          userSelect: 'none',
        }}
      >
        <div>
          Hiển thị trang <b>{page + 1}</b> / <b>{totalPages}</b> (Tổng số <b>{totalElements}</b> dự án)
        </div>

        {totalPages > 1 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <button
              onClick={() => handlePageChange(page - 1)}
              disabled={page === 0}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                padding: '5px 10px',
                borderRadius: 6,
                border: '1px solid #E2E8F0',
                backgroundColor: page === 0 ? '#F8FAFC' : '#FFFFFF',
                color: page === 0 ? '#CBD5E1' : '#334155',
                cursor: page === 0 ? 'not-allowed' : 'pointer',
                fontSize: 12,
                fontWeight: 500,
              }}
            >
              <ChevronLeft size={14} />
              <span>Trước</span>
            </button>

            {Array.from({ length: totalPages }, (_, i) => i).map((pIndex) => (
              <button
                key={`page-btn-${pIndex}`}
                onClick={() => handlePageChange(pIndex)}
                style={{
                  minWidth: 28,
                  height: 28,
                  borderRadius: 6,
                  border: pIndex === page ? '1px solid #2563EB' : '1px solid #E2E8F0',
                  backgroundColor: pIndex === page ? '#2563EB' : '#FFFFFF',
                  color: pIndex === page ? '#FFFFFF' : '#334155',
                  fontWeight: pIndex === page ? 700 : 500,
                  cursor: 'pointer',
                  fontSize: 12,
                }}
              >
                {pIndex + 1}
              </button>
            ))}

            <button
              onClick={() => handlePageChange(page + 1)}
              disabled={page >= totalPages - 1}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                padding: '5px 10px',
                borderRadius: 6,
                border: '1px solid #E2E8F0',
                backgroundColor: page >= totalPages - 1 ? '#F8FAFC' : '#FFFFFF',
                color: page >= totalPages - 1 ? '#CBD5E1' : '#334155',
                cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                fontSize: 12,
                fontWeight: 500,
              }}
            >
              <span>Sau</span>
              <ChevronRight size={14} />
            </button>
          </div>
        )}
      </div>

      {/* ─── Clone Modal ─── */}
      {cloningProject && (
        <CreateProjectModal
          teams={teams}
          sourceProject={cloningProject}
          onClose={() => setCloningProject(null)}
          onSuccess={() => {
            setCloningProject(null);
            fetchProjects(0);
          }}
        />
      )}
    </div>
  );
}

