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
  ChevronRight
} from 'lucide-react';
import { projectApi } from '../api.js';
import CreateProjectModal from '../components/modals/CreateProjectModal.jsx';

export default function ProjectListPage() {
  const { teams, onOpenCreateProject } = useOutletContext();
  const [searchParams, setSearchParams] = useSearchParams();
  const teamIdFilter = searchParams.get('teamId') || '';

  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [activeTab, setActiveTab] = useState('all'); // 'all' | 'active'
  const [cloningProject, setCloningProject] = useState(null);

  // Pagination state (0-based index theo Spring Data JPA)
  const [page, setPage] = useState(0);
  const [pageSize] = useState(9);
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

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      {/* ─── Top Header Section ─── */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0F172A', margin: 0 }}>
            {teamIdFilter
              ? `Dự án thuộc ${teams.find((t) => t.id === teamIdFilter)?.name || 'Nhóm'}`
              : 'Dự án & Quy trình làm việc'}
          </h1>
          <p style={{ fontSize: 13, color: '#64748B', marginTop: 4, marginBottom: 0 }}>
            Quản lý các dự án, cột mốc Sprint và các quy trình làm việc chuẩn hóa.
          </p>
        </div>

        <button
          onClick={onOpenCreateProject}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            backgroundColor: '#2563EB',
            color: '#FFFFFF',
            border: 'none',
            borderRadius: 8,
            padding: '8px 16px',
            fontSize: 13,
            fontWeight: 600,
            cursor: 'pointer',
            boxShadow: '0 2px 4px rgba(37,99,235,0.2)',
            transition: 'background-color 0.15s ease',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
          onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
        >
          <Plus size={16} />
          <span>Tạo dự án mới</span>
        </button>
      </div>

      {/* ─── Filter Bar & Search ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          backgroundColor: '#FFFFFF',
          padding: '12px 16px',
          borderRadius: 10,
          border: '1px solid #E2E8F0',
          marginBottom: 24,
          boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
        }}
      >
        {/* Tab Filters */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <FilterTab
            label="Tất cả dự án"
            count={activeTab === 'all' ? totalElements : projects.length}
            active={activeTab === 'all'}
            onClick={() => setActiveTab('all')}
          />
          <FilterTab
            label="Đang hoạt động"
            count={activeTab === 'active' ? totalElements : projects.filter((p) => p.status === 'ACTIVE').length}
            active={activeTab === 'active'}
            onClick={() => setActiveTab('active')}
          />
        </div>

        {/* Search input & Refresh Button */}
        <form onSubmit={handleSearchSubmit} style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 260 }}>
          <div
            style={{
              position: 'relative',
              width: '100%',
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <Search size={15} color="#94A3B8" style={{ position: 'absolute', left: 10 }} />
            <input
              type="text"
              placeholder="Tìm theo tên hoặc mã key... (Enter)"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{
                width: '100%',
                padding: '6px 12px 6px 32px',
                borderRadius: 6,
                border: '1px solid #E2E8F0',
                fontSize: 13,
                outline: 'none',
                color: '#0F172A',
                backgroundColor: '#F8FAFC',
              }}
            />
          </div>

          <button
            type="button"
            onClick={() => fetchProjects(page)}
            title="Làm mới danh sách"
            style={{
              background: '#FFFFFF',
              border: '1px solid #E2E8F0',
              borderRadius: 6,
              padding: '6px 10px',
              color: '#64748B',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 12,
              flexShrink: 0,
            }}
          >
            <RefreshCw size={13} />
            <span>Tải lại</span>
          </button>
        </form>
      </div>

      {/* ─── Projects Grid ─── */}
      {loading ? (
        <div style={{ padding: '60px 0', textAlign: 'center', color: '#64748B', fontSize: 14 }}>
          Đang tải danh sách dự án...
        </div>
      ) : projects.length === 0 ? (
        <div
          style={{
            padding: '60px 20px',
            textAlign: 'center',
            backgroundColor: '#FFFFFF',
            borderRadius: 12,
            border: '1px dashed #CBD5E1',
          }}
        >
          <FolderKanban size={40} color="#94A3B8" style={{ margin: '0 auto 12px auto' }} />
          <h3 style={{ fontSize: 16, fontWeight: 600, color: '#0F172A', marginBottom: 4 }}>
            Không tìm thấy dự án nào
          </h3>
          <p style={{ fontSize: 13, color: '#64748B', maxWidth: 360, margin: '0 auto 16px auto' }}>
            {searchTerm
              ? 'Thử tìm kiếm với từ khóa khác'
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
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))',
              gap: 18,
            }}
          >
            {projects.map((project, idx) => (
              <ProjectCard
                key={project.id || `project-${idx}`}
                project={project}
                onClone={() => setCloningProject(project)}
              />
            ))}
          </div>

          {/* ─── Pagination Controls ─── */}
          {totalPages > 1 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginTop: 28,
                padding: '12px 16px',
                backgroundColor: '#FFFFFF',
                borderRadius: 10,
                border: '1px solid #E2E8F0',
                fontSize: 13,
                color: '#64748B',
              }}
            >
              <div>
                Hiển thị trang <b>{page + 1}</b> / <b>{totalPages}</b> (Tổng số <b>{totalElements}</b> dự án)
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <button
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page === 0}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                    padding: '6px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    backgroundColor: page === 0 ? '#F1F5F9' : '#FFFFFF',
                    color: page === 0 ? '#94A3B8' : '#334155',
                    cursor: page === 0 ? 'not-allowed' : 'pointer',
                    fontWeight: 500,
                  }}
                >
                  <ChevronLeft size={15} />
                  <span>Trang trước</span>
                </button>

                {Array.from({ length: totalPages }, (_, i) => i).map((pIndex) => (
                  <button
                    key={`page-btn-${pIndex}`}
                    onClick={() => handlePageChange(pIndex)}
                    style={{
                      minWidth: 32,
                      height: 32,
                      borderRadius: 6,
                      border: pIndex === page ? '1px solid #2563EB' : '1px solid #CBD5E1',
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
                    padding: '6px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    backgroundColor: page >= totalPages - 1 ? '#F1F5F9' : '#FFFFFF',
                    color: page >= totalPages - 1 ? '#94A3B8' : '#334155',
                    cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                    fontWeight: 500,
                  }}
                >
                  <span>Trang sau</span>
                  <ChevronRight size={15} />
                </button>
              </div>
            </div>
          )}
        </>
      )}

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

function FilterTab({ label, count, active, onClick, icon }) {
  return (
    <button
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        padding: '6px 12px',
        borderRadius: 6,
        fontSize: 13,
        fontWeight: active ? 600 : 500,
        color: active ? '#2563EB' : '#64748B',
        backgroundColor: active ? '#EFF6FF' : 'transparent',
        border: active ? '1px solid #BFDBFE' : '1px solid transparent',
        cursor: 'pointer',
        transition: 'all 0.15s ease',
      }}
    >
      {icon}
      <span>{label}</span>
      <span
        style={{
          fontSize: 11,
          padding: '1px 6px',
          borderRadius: 10,
          backgroundColor: active ? '#2563EB' : '#F1F5F9',
          color: active ? '#FFFFFF' : '#64748B',
          fontWeight: 600,
        }}
      >
        {count}
      </span>
    </button>
  );
}

function ProjectCard({ project, onClone }) {
  const navigate = useNavigate();
  const getStatusColor = (status) => {
    switch (status) {
      case 'ACTIVE':
        return { bg: '#ECFDF5', text: '#059669', border: '#A7F3D0' };
      case 'PLANNING':
        return { bg: '#EFF6FF', text: '#2563EB', border: '#BFDBFE' };
      case 'COMPLETED':
        return { bg: '#F0FDF4', text: '#16A34A', border: '#BBF7D0' };
      case 'ON_HOLD':
        return { bg: '#FFFBEB', text: '#D97706', border: '#FDE68A' };
      default:
        return { bg: '#F1F5F9', text: '#64748B', border: '#E2E8F0' };
    }
  };

  const statusStyle = getStatusColor(project.status || 'ACTIVE');
  const pName = project.name || project.title || 'Dự án chưa đặt tên';
  const pKey = project.key || project.code || 'PRJ';
  const pId = project.id || project.projectId;

  return (
    <div
      onClick={() => pId && navigate(`/projects/${pId}`)}
      style={{
        backgroundColor: '#FFFFFF',
        borderRadius: 12,
        border: '1px solid #E2E8F0',
        padding: '20px',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
        transition: 'all 0.2s ease',
        cursor: 'pointer',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = '#93C5FD';
        e.currentTarget.style.boxShadow = '0 6px 12px rgba(37,99,235,0.06)';
        e.currentTarget.style.transform = 'translateY(-2px)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = '#E2E8F0';
        e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.03)';
        e.currentTarget.style.transform = 'translateY(0)';
      }}
    >
      <div>
        {/* Top Header with Folder Icon and Status */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
          <div
            style={{
              width: 32,
              height: 32,
              borderRadius: 8,
              backgroundColor: '#EFF6FF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: '1px solid #DBEAFE',
            }}
          >
            <FolderKanban size={16} color="#2563EB" />
          </div>

          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: statusStyle.text,
              backgroundColor: statusStyle.bg,
              border: `1px solid ${statusStyle.border}`,
              padding: '2px 8px',
              borderRadius: 12,
            }}
          >
            {project.status || 'ACTIVE'}
          </span>
        </div>

        {/* Project Title */}
        <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: '0 0 6px 0', lineHeight: 1.3 }}>
          {pName}
        </h3>

        {/* Description */}
        <p
          style={{
            fontSize: 13,
            color: '#64748B',
            margin: '0 0 14px 0',
            lineHeight: 1.4,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            minHeight: 36,
          }}
        >
          {project.description || 'Chưa có mô tả chi tiết cho dự án này.'}
        </p>

        {/* Team Tag */}
        {project.teamName && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, color: '#475569', marginBottom: 12 }}>
            <Users size={13} color="#64748B" />
            <span>{project.teamName}</span>
          </div>
        )}
      </div>

      {/* Card Footer */}
      <div
        style={{
          borderTop: '1px solid #F1F5F9',
          paddingTop: 12,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 12, color: '#64748B' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <Layers size={13} color="#94A3B8" />
            <b>{project.taskCount || 0}</b> tasks
          </span>
          {project.isPublic ? (
            <span title="Công khai" style={{ display: 'flex', alignItems: 'center' }}>
              <Globe size={13} color="#10B981" />
            </span>
          ) : (
            <span title="Riêng tư" style={{ display: 'flex', alignItems: 'center' }}>
              <Lock size={13} color="#94A3B8" />
            </span>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {/* Nút Clone Template */}
          <button
            onClick={(e) => {
              e.stopPropagation();
              onClone();
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
              fontWeight: 600,
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
        </div>
      </div>
    </div>
  );
}
