import React, { useState, useEffect } from 'react';
import { useSearchParams, useOutletContext, Link } from 'react-router-dom';
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
  Clock
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
  const [activeTab, setActiveTab] = useState('all'); // 'all' | 'active' | 'templates'
  const [cloningProject, setCloningProject] = useState(null);

  const fetchProjects = async () => {
    try {
      setLoading(true);
      const res = await projectApi.listAll(teamIdFilter || null);
      if (res && res.data) {
        setProjects(res.data);
      }
    } catch (err) {
      console.error('Failed to fetch projects:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProjects();

    const handleProjectCreated = () => fetchProjects();
    window.addEventListener('project-created', handleProjectCreated);
    return () => window.removeEventListener('project-created', handleProjectCreated);
  }, [teamIdFilter]);

  const filteredProjects = projects.filter((p) => {
    const matchSearch =
      p.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.key?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.description?.toLowerCase().includes(searchTerm.toLowerCase());

    if (!matchSearch) return false;

    if (activeTab === 'templates') return p.isTemplate;
    if (activeTab === 'active') return p.status === 'ACTIVE' && !p.isTemplate;
    return true;
  });

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
            Quản lý các dự án, cột mốc Sprint và các mẫu quy trình làm việc chuẩn hóa.
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
            count={projects.length}
            active={activeTab === 'all'}
            onClick={() => setActiveTab('all')}
          />
          <FilterTab
            label="Đang hoạt động"
            count={projects.filter((p) => p.status === 'ACTIVE' && !p.isTemplate).length}
            active={activeTab === 'active'}
            onClick={() => setActiveTab('active')}
          />
          <FilterTab
            label="Mẫu Template"
            icon={<Sparkles size={13} color="#8B5CF6" />}
            count={projects.filter((p) => p.isTemplate).length}
            active={activeTab === 'templates'}
            onClick={() => setActiveTab('templates')}
          />
        </div>

        {/* Search input */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 260 }}>
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
              placeholder="Tìm theo tên hoặc mã key..."
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
        </div>
      </div>

      {/* ─── Projects Grid ─── */}
      {loading ? (
        <div style={{ padding: '60px 0', textAlign: 'center', color: '#64748B', fontSize: 14 }}>
          Đang tải danh sách dự án...
        </div>
      ) : filteredProjects.length === 0 ? (
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
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))',
            gap: 18,
          }}
        >
          {filteredProjects.map((project) => (
            <ProjectCard
              key={project.id}
              project={project}
              onClone={() => setCloningProject(project)}
            />
          ))}
        </div>
      )}

      {/* ─── Clone Modal ─── */}
      {cloningProject && (
        <CreateProjectModal
          teams={teams}
          sourceProject={cloningProject}
          onClose={() => setCloningProject(null)}
          onSuccess={() => {
            setCloningProject(null);
            fetchProjects();
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

  const statusStyle = getStatusColor(project.status);

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        borderRadius: 12,
        border: '1px solid #E2E8F0',
        padding: '20px',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
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
        {/* Top Header with Key and Template badge */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span
              style={{
                fontSize: 12,
                fontWeight: 700,
                color: '#2563EB',
                backgroundColor: '#EFF6FF',
                padding: '2px 8px',
                borderRadius: 5,
                border: '1px solid #DBEAFE',
                letterSpacing: '0.02em',
              }}
            >
              {project.key}
            </span>

            {project.isTemplate && (
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 600,
                  color: '#7C3AED',
                  backgroundColor: '#F5F3FF',
                  padding: '2px 8px',
                  borderRadius: 5,
                  border: '1px solid #DDD6FE',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 3,
                }}
              >
                <Sparkles size={11} />
                Template
              </span>
            )}
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
            {project.status}
          </span>
        </div>

        {/* Project Title */}
        <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: '0 0 6px 0', lineHeight: 1.3 }}>
          {project.name}
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
