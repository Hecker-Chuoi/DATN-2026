import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import {
  FolderKanban,
  Sparkles,
  Search,
  Filter,
  Plus,
  RefreshCw,
  Edit3,
  Trash2,
  Copy,
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  Globe,
  Lock,
  Layers,
  Users,
  Calendar,
  CheckCircle2,
  Clock,
  AlertTriangle,
  X,
  Check,
  LayoutGrid,
  ListFilter
} from 'lucide-react';
import { projectApi, teamApi } from '../../api.js';

export default function AdminProjectManagement() {
  const navigate = useNavigate();

  // ─── States: Projects & Pagination ───
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  // ─── States: Filters & Search ───
  const [searchKeyword, setSearchKeyword] = useState('');
  const [templateFilter, setTemplateFilter] = useState('ALL'); // 'ALL' | 'TEMPLATE_ONLY' | 'STANDARD_ONLY'
  const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL' | 'ACTIVE' | 'PLANNING' | 'COMPLETED' | 'ON_HOLD'
  const [teamFilter, setTeamFilter] = useState('ALL');

  // ─── States: Auxiliary Data ───
  const [teams, setTeams] = useState([]);
  const [stats, setStats] = useState({
    total: 0,
    templates: 0,
    active: 0,
    planning: 0,
    completed: 0,
  });

  // ─── States: Modals ───
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingProject, setEditingProject] = useState(null);
  const [cloningProject, setCloningProject] = useState(null);
  const [deletingProject, setDeletingProject] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // ─── 1. Load Teams for filtering & modals ───
  useEffect(() => {
    async function loadTeams() {
      try {
        const res = await teamApi.listAll();
        if (res && res.data) {
          setTeams(Array.isArray(res.data) ? res.data : []);
        }
      } catch (err) {
        console.warn('Could not load teams:', err);
      }
    }
    loadTeams();
  }, []);

  // ─── 2. Fetch Projects Data ───
  const fetchProjects = useCallback(async (targetPage = page, targetPageSize = pageSize) => {
    setLoading(true);
    try {
      const params = {
        page: targetPage,
        size: targetPageSize,
        search: searchKeyword.trim() || undefined,
        status: statusFilter !== 'ALL' ? statusFilter : undefined,
        teamId: teamFilter !== 'ALL' ? teamFilter : undefined,
      };

      // Xử lý bộ lọc Template
      if (templateFilter === 'TEMPLATE_ONLY') {
        params.isTemplate = true;
      } else if (templateFilter === 'STANDARD_ONLY') {
        params.isTemplate = false;
      }
      // Nếu templateFilter === 'ALL' thì params.isTemplate = undefined (backend trả về tất cả)

      const res = await projectApi.listAll(params);

      if (res && res.data) {
        const pageData = res.data;
        if (pageData.content) {
          setProjects(pageData.content);
          setTotalElements(pageData.totalElements || 0);
          setTotalPages(pageData.totalPages || 1);
          setPage(pageData.pageNumber || 0);
        } else if (Array.isArray(pageData)) {
          setProjects(pageData);
          setTotalElements(pageData.length);
          setTotalPages(1);
          setPage(0);
        }
      }
    } catch (err) {
      console.error('Error fetching admin projects:', err);
      toast.error('Không thể tải danh sách dự án: ' + (err.message || 'Lỗi kết nối'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, searchKeyword, templateFilter, statusFilter, teamFilter]);

  // ─── 3. Fetch Quick Stats (Non-paginated summary) ───
  const fetchStats = async () => {
    try {
      const [allRes, templateRes] = await Promise.all([
        projectApi.listAll({ page: 0, size: 1000 }),
        projectApi.listTemplates(),
      ]);

      const allList = allRes?.data?.content || allRes?.data || [];
      const templateList = templateRes?.data || [];

      const activeCount = allList.filter((p) => p.status === 'ACTIVE').length;
      const planningCount = allList.filter((p) => p.status === 'PLANNING').length;
      const completedCount = allList.filter((p) => p.status === 'COMPLETED').length;

      setStats({
        total: allRes?.data?.totalElements || allList.length,
        templates: templateList.length,
        active: activeCount,
        planning: planningCount,
        completed: completedCount,
      });
    } catch (err) {
      console.warn('Could not calculate admin stats:', err);
    }
  };

  useEffect(() => {
    fetchProjects(0, pageSize);
    fetchStats();
  }, [templateFilter, statusFilter, teamFilter, pageSize]);

  // Trigger search submit
  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    fetchProjects(0, pageSize);
  };

  // Reset Filters
  const handleResetFilters = () => {
    setSearchKeyword('');
    setTemplateFilter('ALL');
    setStatusFilter('ALL');
    setTeamFilter('ALL');
    setPage(0);
  };

  // ─── 4. Delete Project Handler ───
  const handleDeleteConfirm = async () => {
    if (!deletingProject) return;
    setIsSubmitting(true);
    try {
      await projectApi.delete(deletingProject.id);
      toast.success(`Đã xóa dự án "${deletingProject.name}" thành công!`);
      setDeletingProject(null);
      fetchProjects(page, pageSize);
      fetchStats();
    } catch (err) {
      toast.error('Xóa dự án thất bại: ' + (err.message || 'Lỗi hệ thống'));
    } finally {
      setIsSubmitting(false);
    }
  };

  // ─── 5. Update Project Handler ───
  const handleUpdateProject = async (e) => {
    e.preventDefault();
    if (!editingProject) return;
    setIsSubmitting(true);
    try {
      await projectApi.update(editingProject.id, {
        name: editingProject.name.trim(),
        description: editingProject.description,
        status: editingProject.status,
        isPublic: editingProject.isPublic,
        isTemplate: editingProject.isTemplate,
        teamId: editingProject.teamId || null,
        startDate: editingProject.startDate || null,
        endDate: editingProject.endDate || null,
      });
      toast.success(`Cập nhật dự án "${editingProject.name}" thành công!`);
      setEditingProject(null);
      fetchProjects(page, pageSize);
      fetchStats();
    } catch (err) {
      toast.error('Cập nhật thất bại: ' + (err.message || 'Lỗi hệ thống'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ padding: '0 8px 32px 8px', maxWidth: 1400, margin: '0 auto' }}>
      {/* ─── Hero / Header ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 16,
          marginBottom: 24,
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 38,
                height: 38,
                borderRadius: 10,
                background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
                color: '#ffffff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 4px 12px rgba(2, 132, 199, 0.25)',
              }}
            >
              <FolderKanban size={22} />
            </div>
            <div>
              <h1 style={{ fontSize: 22, fontWeight: 700, color: '#0f172a', margin: 0 }}>
                Quản Lý Dự Án & Template Hệ Thống
              </h1>
              <p style={{ fontSize: 13, color: '#64748b', margin: '2px 0 0 0' }}>
                Bảng điều khiển quản trị toàn diện tất cả các dự án, quy trình mẫu, bộ lọc nâng cao và phân quyền.
              </p>
            </div>
          </div>
        </div>

        {/* Action buttons */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            onClick={() => {
              fetchProjects(page, pageSize);
              fetchStats();
            }}
            disabled={loading}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '9px 14px',
              backgroundColor: '#ffffff',
              border: '1px solid #e2e8f0',
              borderRadius: 8,
              fontSize: 13,
              fontWeight: 500,
              color: '#475569',
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f8fafc')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#ffffff')}
          >
            <RefreshCw size={15} className={loading ? 'spin' : ''} />
            <span>Làm mới</span>
          </button>

          <button
            onClick={() => setShowCreateModal(true)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '9px 18px',
              backgroundColor: '#0284c7',
              border: 'none',
              borderRadius: 8,
              fontSize: 13,
              fontWeight: 600,
              color: '#ffffff',
              cursor: 'pointer',
              boxShadow: '0 2px 6px rgba(2, 132, 199, 0.3)',
              transition: 'all 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#0369a1')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#0284c7')}
          >
            <Plus size={16} />
            <span>Tạo Dự Án / Template Mới</span>
          </button>
        </div>
      </div>

      {/* ─── Metric Stat Cards ─── */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: 14,
          marginBottom: 20,
        }}
      >
        <StatCard
          label="Tổng Số Dự Án"
          value={stats.total}
          icon={<FolderKanban size={18} color="#0284c7" />}
          bgColor="#f0f9ff"
          borderColor="#bae6fd"
          onClick={() => {
            setTemplateFilter('ALL');
            setStatusFilter('ALL');
          }}
          active={templateFilter === 'ALL' && statusFilter === 'ALL'}
        />

        <StatCard
          label="Dự Án Mẫu (Templates)"
          value={stats.templates}
          icon={<Sparkles size={18} color="#d97706" />}
          bgColor="#fffbeb"
          borderColor="#fde68a"
          highlight
          badgeText="🌟 Nổi bật"
          onClick={() => setTemplateFilter(templateFilter === 'TEMPLATE_ONLY' ? 'ALL' : 'TEMPLATE_ONLY')}
          active={templateFilter === 'TEMPLATE_ONLY'}
        />

        <StatCard
          label="Đang Hoạt Động (Active)"
          value={stats.active}
          icon={<Clock size={18} color="#16a34a" />}
          bgColor="#f0fdf4"
          borderColor="#bbf7d0"
          onClick={() => setStatusFilter(statusFilter === 'ACTIVE' ? 'ALL' : 'ACTIVE')}
          active={statusFilter === 'ACTIVE'}
        />

        <StatCard
          label="Đang Lập Kế Hoạch"
          value={stats.planning}
          icon={<Calendar size={18} color="#4f46e5" />}
          bgColor="#eef2ff"
          borderColor="#c7d2fe"
          onClick={() => setStatusFilter(statusFilter === 'PLANNING' ? 'ALL' : 'PLANNING')}
          active={statusFilter === 'PLANNING'}
        />

        <StatCard
          label="Đã Hoàn Thành"
          value={stats.completed}
          icon={<CheckCircle2 size={18} color="#0d9488" />}
          bgColor="#f0fdfa"
          borderColor="#99f6e4"
          onClick={() => setStatusFilter(statusFilter === 'COMPLETED' ? 'ALL' : 'COMPLETED')}
          active={statusFilter === 'COMPLETED'}
        />
      </div>

      {/* ─── Main Content Card ─── */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: 12,
          border: '1px solid #e2e8f0',
          boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
          overflow: 'hidden',
        }}
      >
        {/* ─── Filter & Search Toolbar ─── */}
        <div
          style={{
            padding: '16px 20px',
            borderBottom: '1px solid #f1f5f9',
            backgroundColor: '#ffffff',
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
          }}
        >
          {/* Top Row: Special Template Filter Button & Quick Mode */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 12,
            }}
          >
            {/* 🌟 Special Template Filter Buttons Group */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: '#334155', display: 'flex', alignItems: 'center', gap: 5 }}>
                <ListFilter size={15} color="#64748b" />
                <span>Chế độ hiển thị:</span>
              </span>

              <button
                type="button"
                onClick={() => setTemplateFilter('ALL')}
                style={{
                  padding: '6px 14px',
                  borderRadius: 20,
                  fontSize: 13,
                  fontWeight: templateFilter === 'ALL' ? 600 : 500,
                  backgroundColor: templateFilter === 'ALL' ? '#0f172a' : '#f1f5f9',
                  color: templateFilter === 'ALL' ? '#ffffff' : '#475569',
                  border: '1px solid transparent',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                Tất cả dự án ({stats.total})
              </button>

              {/* 🌟 THE SPECIAL TEMPLATE FILTER BUTTON 🌟 */}
              <button
                type="button"
                onClick={() => setTemplateFilter(templateFilter === 'TEMPLATE_ONLY' ? 'ALL' : 'TEMPLATE_ONLY')}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '6px 16px',
                  borderRadius: 20,
                  fontSize: 13,
                  fontWeight: 700,
                  cursor: 'pointer',
                  transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
                  backgroundColor:
                    templateFilter === 'TEMPLATE_ONLY'
                      ? '#f59e0b'
                      : '#fffbeb',
                  color:
                    templateFilter === 'TEMPLATE_ONLY'
                      ? '#ffffff'
                      : '#b45309',
                  border:
                    templateFilter === 'TEMPLATE_ONLY'
                      ? '1px solid #d97706'
                      : '1px solid #fde68a',
                  boxShadow:
                    templateFilter === 'TEMPLATE_ONLY'
                      ? '0 3px 10px rgba(245, 158, 11, 0.4)'
                      : '0 1px 2px rgba(245, 158, 11, 0.1)',
                  transform: templateFilter === 'TEMPLATE_ONLY' ? 'scale(1.03)' : 'scale(1)',
                }}
              >
                <Sparkles
                  size={15}
                  color={templateFilter === 'TEMPLATE_ONLY' ? '#ffffff' : '#d97706'}
                />
                <span>Chỉ Dự Án Mẫu (Templates)</span>
                <span
                  style={{
                    backgroundColor: templateFilter === 'TEMPLATE_ONLY' ? 'rgba(255,255,255,0.25)' : '#fef3c7',
                    color: templateFilter === 'TEMPLATE_ONLY' ? '#ffffff' : '#92400e',
                    fontSize: 11,
                    fontWeight: 800,
                    padding: '1px 7px',
                    borderRadius: 10,
                  }}
                >
                  {stats.templates}
                </span>
              </button>

              <button
                type="button"
                onClick={() => setTemplateFilter('STANDARD_ONLY')}
                style={{
                  padding: '6px 14px',
                  borderRadius: 20,
                  fontSize: 13,
                  fontWeight: templateFilter === 'STANDARD_ONLY' ? 600 : 500,
                  backgroundColor: templateFilter === 'STANDARD_ONLY' ? '#0284c7' : '#f1f5f9',
                  color: templateFilter === 'STANDARD_ONLY' ? '#ffffff' : '#475569',
                  border: '1px solid transparent',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                Dự án thông thường
              </button>
            </div>

            {/* Total Results Summary */}
            <div style={{ fontSize: 13, color: '#64748b' }}>
              Tìm thấy <strong style={{ color: '#0f172a' }}>{totalElements}</strong> kết quả phù hợp
            </div>
          </div>

          {/* Bottom Row: Search Box & Dropdown Filters */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 12,
            }}
          >
            {/* Search Input Form */}
            <form
              onSubmit={handleSearchSubmit}
              style={{
                position: 'relative',
                flex: '1 1 300px',
                minWidth: 260,
                maxWidth: 480,
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <Search
                size={16}
                color="#94a3b8"
                style={{ position: 'absolute', left: 12, pointerEvents: 'none' }}
              />
              <input
                type="text"
                placeholder="Tìm theo Tên, Mã Key, Mô tả dự án... (Nhấn Enter)"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                style={{
                  width: '100%',
                  height: 38,
                  padding: '0 36px 0 38px',
                  borderRadius: 8,
                  border: '1px solid #cbd5e1',
                  backgroundColor: '#f8fafc',
                  fontSize: 13,
                  color: '#0f172a',
                  outline: 'none',
                  transition: 'all 0.15s ease',
                }}
                onFocus={(e) => {
                  e.target.style.borderColor = '#0284c7';
                  e.target.style.backgroundColor = '#ffffff';
                  e.target.style.boxShadow = '0 0 0 3px rgba(2, 132, 199, 0.1)';
                }}
                onBlur={(e) => {
                  e.target.style.borderColor = '#cbd5e1';
                  e.target.style.backgroundColor = '#f8fafc';
                  e.target.style.boxShadow = 'none';
                }}
              />
              {searchKeyword && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchKeyword('');
                    setPage(0);
                    fetchProjects(0, pageSize);
                  }}
                  style={{
                    position: 'absolute',
                    right: 10,
                    background: 'none',
                    border: 'none',
                    color: '#94a3b8',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                  }}
                >
                  <X size={15} />
                </button>
              )}
            </form>

            {/* Dropdown Filters Group */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              {/* Status Filter */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: 12, color: '#64748b', fontWeight: 500 }}>Trạng thái:</span>
                <select
                  value={statusFilter}
                  onChange={(e) => {
                    setStatusFilter(e.target.value);
                    setPage(0);
                  }}
                  style={{
                    height: 38,
                    padding: '0 12px',
                    borderRadius: 8,
                    border: '1px solid #cbd5e1',
                    backgroundColor: '#ffffff',
                    fontSize: 13,
                    color: '#0f172a',
                    outline: 'none',
                    cursor: 'pointer',
                  }}
                >
                  <option value="ALL">Tất cả trạng thái</option>
                  <option value="ACTIVE">ACTIVE (Đang chạy)</option>
                  <option value="PLANNING">PLANNING (Kế hoạch)</option>
                  <option value="COMPLETED">COMPLETED (Hoàn thành)</option>
                  <option value="ON_HOLD">ON_HOLD (Tạm dừng)</option>
                </select>
              </div>

              {/* Team Filter */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: 12, color: '#64748b', fontWeight: 500 }}>Nhóm:</span>
                <select
                  value={teamFilter}
                  onChange={(e) => {
                    setTeamFilter(e.target.value);
                    setPage(0);
                  }}
                  style={{
                    height: 38,
                    padding: '0 12px',
                    borderRadius: 8,
                    border: '1px solid #cbd5e1',
                    backgroundColor: '#ffffff',
                    fontSize: 13,
                    color: '#0f172a',
                    outline: 'none',
                    cursor: 'pointer',
                    maxWidth: 180,
                  }}
                >
                  <option value="ALL">Tất cả các nhóm</option>
                  {teams.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </select>
              </div>

              {/* Reset Filter Button */}
              {(searchKeyword || templateFilter !== 'ALL' || statusFilter !== 'ALL' || teamFilter !== 'ALL') && (
                <button
                  type="button"
                  onClick={handleResetFilters}
                  style={{
                    height: 38,
                    padding: '0 12px',
                    borderRadius: 8,
                    border: '1px solid #e2e8f0',
                    backgroundColor: '#f1f5f9',
                    fontSize: 12,
                    fontWeight: 500,
                    color: '#64748b',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                  }}
                  title="Xóa tất cả bộ lọc"
                >
                  <X size={14} />
                  <span>Xóa bộ lọc</span>
                </button>
              )}
            </div>
          </div>
        </div>

        {/* ─── Table Content ─── */}
        {loading ? (
          <div style={{ padding: '80px 20px', textAlign: 'center', color: '#64748b' }}>
            <RefreshCw size={28} className="spin" style={{ margin: '0 auto 12px auto', color: '#0284c7' }} />
            <div style={{ fontSize: 14, fontWeight: 500 }}>Đang tải danh sách dự án...</div>
          </div>
        ) : projects.length === 0 ? (
          <div style={{ padding: '70px 20px', textAlign: 'center', color: '#64748b' }}>
            <FolderKanban size={44} color="#94a3b8" style={{ margin: '0 auto 12px auto' }} />
            <h3 style={{ fontSize: 16, fontWeight: 600, color: '#0f172a', marginBottom: 4 }}>
              Không tìm thấy dự án nào
            </h3>
            <p style={{ fontSize: 13, color: '#64748b', maxWidth: 400, margin: '0 auto 16px auto' }}>
              {searchKeyword || templateFilter !== 'ALL' || statusFilter !== 'ALL'
                ? 'Không có dự án nào thỏa mãn điều kiện lọc hiện tại. Thử xóa hoặc thay đổi bộ lọc.'
                : 'Hệ thống chưa có dự án nào. Bạn có thể bắt đầu tạo dự án hoặc quy trình mẫu mới ngay.'}
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: 10 }}>
              {(searchKeyword || templateFilter !== 'ALL' || statusFilter !== 'ALL') && (
                <button
                  onClick={handleResetFilters}
                  style={{
                    padding: '8px 16px',
                    borderRadius: 6,
                    border: '1px solid #cbd5e1',
                    backgroundColor: '#ffffff',
                    fontSize: 13,
                    fontWeight: 500,
                    cursor: 'pointer',
                  }}
                >
                  Xóa bộ lọc
                </button>
              )}
              <button
                onClick={() => setShowCreateModal(true)}
                style={{
                  padding: '8px 16px',
                  borderRadius: 6,
                  border: 'none',
                  backgroundColor: '#0284c7',
                  color: '#ffffff',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                + Tạo dự án mới
              </button>
            </div>
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="custom-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  <th style={{ width: '28%' }}>Mã & Tên Dự Án</th>
                  <th style={{ width: '14%' }}>Loại Dự Án</th>
                  <th style={{ width: '13%' }}>Trạng Thái</th>
                  <th style={{ width: '15%' }}>Nhóm / Phạm Vi</th>
                  <th style={{ width: '10%' }}>Tác Vụ</th>
                  <th style={{ width: '10%' }}>Ngày Tạo</th>
                  <th style={{ width: '10%', textAlign: 'right' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {projects.map((project) => {
                  const isTpl = project.isTemplate;
                  const statusStyle = getStatusBadgeStyle(project.status);

                  return (
                    <tr key={project.id} style={{ transition: 'background-color 0.15s ease' }}>
                      {/* Project Key, Name & Description */}
                      <td>
                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                          <div
                            style={{
                              width: 36,
                              height: 36,
                              borderRadius: 8,
                              backgroundColor: isTpl ? '#fef3c7' : '#e0f2fe',
                              border: isTpl ? '1px solid #fde68a' : '1px solid #bae6fd',
                              color: isTpl ? '#d97706' : '#0284c7',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              flexShrink: 0,
                              marginTop: 2,
                            }}
                          >
                            {isTpl ? <Sparkles size={18} /> : <FolderKanban size={18} />}
                          </div>

                          <div style={{ minWidth: 0 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                              <span
                                style={{
                                  fontSize: 11,
                                  fontWeight: 700,
                                  color: '#0284c7',
                                  backgroundColor: '#f0f9ff',
                                  border: '1px solid #e0f2fe',
                                  padding: '1px 6px',
                                  borderRadius: 4,
                                  fontFamily: 'monospace',
                                }}
                              >
                                {project.key || 'PRJ'}
                              </span>
                              <span
                                onClick={() => navigate(`/projects/${project.id}`)}
                                style={{
                                  fontWeight: 600,
                                  fontSize: 14,
                                  color: '#0f172a',
                                  cursor: 'pointer',
                                  textDecoration: 'none',
                                }}
                                onMouseEnter={(e) => (e.target.style.color = '#0284c7')}
                                onMouseLeave={(e) => (e.target.style.color = '#0f172a')}
                                title="Bấm để xem bảng công việc (Kanban)"
                              >
                                {project.name}
                              </span>
                            </div>

                            {project.description && (
                              <p
                                style={{
                                  fontSize: 12,
                                  color: '#64748b',
                                  margin: '3px 0 0 0',
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap',
                                  maxWidth: 320,
                                }}
                              >
                                {project.description}
                              </p>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* Project Type Badge (Template vs Standard) */}
                      <td>
                        {isTpl ? (
                          <span
                            style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 4,
                              fontSize: 11,
                              fontWeight: 700,
                              padding: '3px 9px',
                              borderRadius: 12,
                              backgroundColor: '#fef3c7',
                              color: '#b45309',
                              border: '1px solid #fde68a',
                              boxShadow: '0 1px 2px rgba(245, 158, 11, 0.1)',
                            }}
                          >
                            <Sparkles size={12} color="#d97706" />
                            <span>TEMPLATE</span>
                          </span>
                        ) : (
                          <span
                            style={{
                              fontSize: 11,
                              fontWeight: 600,
                              padding: '3px 8px',
                              borderRadius: 12,
                              backgroundColor: '#f1f5f9',
                              color: '#475569',
                              border: '1px solid #e2e8f0',
                            }}
                          >
                            STANDARD
                          </span>
                        )}
                      </td>

                      {/* Status */}
                      <td>
                        <span
                          style={{
                            fontSize: 11,
                            fontWeight: 600,
                            padding: '3px 10px',
                            borderRadius: 12,
                            backgroundColor: statusStyle.bg,
                            color: statusStyle.text,
                            border: `1px solid ${statusStyle.border}`,
                          }}
                        >
                          {project.status || 'ACTIVE'}
                        </span>
                      </td>

                      {/* Team & Visibility */}
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 13, color: '#334155' }}>
                            <Users size={13} color="#64748b" />
                            <span style={{ fontWeight: 500 }}>
                              {project.teamName || 'Cá nhân / Toàn cục'}
                            </span>
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: '#64748b' }}>
                            {project.isPublic ? (
                              <>
                                <Globe size={11} color="#16a34a" />
                                <span>Công khai</span>
                              </>
                            ) : (
                              <>
                                <Lock size={11} color="#94a3b8" />
                                <span>Nội bộ</span>
                              </>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* Tasks count */}
                      <td>
                        <span
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                            fontSize: 12,
                            fontWeight: 600,
                            color: '#334155',
                            backgroundColor: '#f8fafc',
                            padding: '3px 8px',
                            borderRadius: 6,
                            border: '1px solid #e2e8f0',
                          }}
                        >
                          <Layers size={13} color="#0284c7" />
                          <span>{project.taskCount || 0}</span>
                        </span>
                      </td>

                      {/* Created Date */}
                      <td>
                        <span style={{ fontSize: 12, color: '#64748b' }}>
                          {formatDate(project.createdAt)}
                        </span>
                      </td>

                      {/* Actions */}
                      <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                          {/* View Kanban */}
                          <button
                            type="button"
                            onClick={() => navigate(`/projects/${project.id}`)}
                            title="Mở bảng Kanban dự án"
                            style={{
                              padding: '5px 8px',
                              borderRadius: 6,
                              border: '1px solid #e2e8f0',
                              backgroundColor: '#ffffff',
                              color: '#0284c7',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: 4,
                              fontSize: 12,
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f0f9ff')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#ffffff')}
                          >
                            <ExternalLink size={13} />
                          </button>

                          {/* Clone */}
                          <button
                            type="button"
                            onClick={() => setCloningProject(project)}
                            title="Nhân bản (Clone) cấu trúc dự án này"
                            style={{
                              padding: '5px 8px',
                              borderRadius: 6,
                              border: '1px solid #e2e8f0',
                              backgroundColor: '#ffffff',
                              color: '#d97706',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: 4,
                              fontSize: 12,
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#fffbeb')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#ffffff')}
                          >
                            <Copy size={13} />
                          </button>

                          {/* Edit */}
                          <button
                            type="button"
                            onClick={() => setEditingProject({ ...project })}
                            title="Chỉnh sửa thông tin & Cấu hình Template"
                            style={{
                              padding: '5px 8px',
                              borderRadius: 6,
                              border: '1px solid #e2e8f0',
                              backgroundColor: '#ffffff',
                              color: '#475569',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: 4,
                              fontSize: 12,
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#f1f5f9')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#ffffff')}
                          >
                            <Edit3 size={13} />
                          </button>

                          {/* Delete */}
                          <button
                            type="button"
                            onClick={() => setDeletingProject(project)}
                            title="Xóa dự án"
                            style={{
                              padding: '5px 8px',
                              borderRadius: 6,
                              border: '1px solid #fecaca',
                              backgroundColor: '#ffffff',
                              color: '#dc2626',
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: 4,
                              fontSize: 12,
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#fef2f2')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#ffffff')}
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* ─── Pagination Footer ─── */}
        <div
          style={{
            padding: '14px 20px',
            borderTop: '1px solid #f1f5f9',
            backgroundColor: '#ffffff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 14,
            fontSize: 13,
            color: '#64748b',
          }}
        >
          {/* Page Info & Page Size Selector */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>

            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span>Số dòng:</span>
              <select
                value={pageSize}
                onChange={(e) => {
                  const newSize = Number(e.target.value);
                  setPageSize(newSize);
                  setPage(0);
                  fetchProjects(0, newSize);
                }}
                style={{
                  height: 28,
                  padding: '0 6px',
                  borderRadius: 4,
                  border: '1px solid #cbd5e1',
                  backgroundColor: '#ffffff',
                  fontSize: 12,
                  color: '#0f172a',
                  cursor: 'pointer',
                }}
              >
                <option value={5}>5</option>
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
            </div>
          </div>

          {/* Page Navigation Buttons */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <button
              onClick={() => {
                if (page > 0) {
                  setPage(page - 1);
                  fetchProjects(page - 1, pageSize);
                }
              }}
              disabled={page === 0}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                padding: '6px 12px',
                borderRadius: 6,
                border: '1px solid #cbd5e1',
                backgroundColor: page === 0 ? '#f8fafc' : '#ffffff',
                color: page === 0 ? '#94a3b8' : '#334155',
                cursor: page === 0 ? 'not-allowed' : 'pointer',
                fontWeight: 500,
              }}
            >
              <ChevronLeft size={15} />
              <span>Trước</span>
            </button>

            {/* Page number buttons */}
            {Array.from({ length: totalPages }, (_, i) => i)
              .filter((pIdx) => pIdx === 0 || pIdx === totalPages - 1 || Math.abs(pIdx - page) <= 1)
              .map((pIdx, idx, filteredArr) => {
                const prev = filteredArr[idx - 1];
                const showEllipsis = prev !== undefined && pIdx - prev > 1;

                return (
                  <React.Fragment key={`page-${pIdx}`}>
                    {showEllipsis && <span style={{ padding: '0 4px', color: '#94a3b8' }}>...</span>}
                    <button
                      onClick={() => {
                        setPage(pIdx);
                        fetchProjects(pIdx, pageSize);
                      }}
                      style={{
                        minWidth: 32,
                        height: 32,
                        borderRadius: 6,
                        border: pIdx === page ? '1px solid #0284c7' : '1px solid #cbd5e1',
                        backgroundColor: pIdx === page ? '#0284c7' : '#ffffff',
                        color: pIdx === page ? '#ffffff' : '#334155',
                        fontWeight: pIdx === page ? 700 : 500,
                        cursor: 'pointer',
                        fontSize: 12,
                      }}
                    >
                      {pIdx + 1}
                    </button>
                  </React.Fragment>
                );
              })}

            <button
              onClick={() => {
                if (page < totalPages - 1) {
                  setPage(page + 1);
                  fetchProjects(page + 1, pageSize);
                }
              }}
              disabled={page >= totalPages - 1}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                padding: '6px 12px',
                borderRadius: 6,
                border: '1px solid #cbd5e1',
                backgroundColor: page >= totalPages - 1 ? '#f8fafc' : '#ffffff',
                color: page >= totalPages - 1 ? '#94a3b8' : '#334155',
                cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                fontWeight: 500,
              }}
            >
              <span>Sau</span>
              <ChevronRight size={15} />
            </button>
          </div>
        </div>
      </div>

      {/* ─── MODAL 1: Create Project / Template Modal ─── */}
      {showCreateModal && (
        <CreateProjectModal
          teams={teams}
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            fetchProjects(0, pageSize);
            fetchStats();
          }}
        />
      )}

      {/* ─── MODAL 2: Edit Project Modal ─── */}
      {editingProject && (
        <div
          className="modal-overlay"
          onClick={() => setEditingProject(null)}
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: 20,
          }}
        >
          <div
            className="modal-card"
            onClick={(e) => e.stopPropagation()}
            style={{
              backgroundColor: '#ffffff',
              borderRadius: 12,
              width: '100%',
              maxWidth: 540,
              maxHeight: '90vh',
              overflowY: 'auto',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            }}
          >
            {/* Modal Header */}
            <div
              style={{
                padding: '18px 24px',
                borderBottom: '1px solid #f1f5f9',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Edit3 color="#0284c7" size={20} />
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0f172a' }}>
                  Chỉnh Sửa Dự Án & Cấu Hình
                </h3>
              </div>
              <button
                onClick={() => setEditingProject(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8' }}
              >
                <X size={20} />
              </button>
            </div>

            {/* Modal Form Body */}
            <form onSubmit={handleUpdateProject} style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* Project Key (Read-only) */}
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Mã Dự Án (Key - Cố định)
                </label>
                <input
                  type="text"
                  value={editingProject.key || ''}
                  disabled
                  style={{
                    width: '100%',
                    height: 40,
                    padding: '0 12px',
                    borderRadius: 8,
                    border: '1px solid #e2e8f0',
                    backgroundColor: '#f8fafc',
                    color: '#64748b',
                    fontWeight: 600,
                    fontFamily: 'monospace',
                  }}
                />
              </div>

              {/* Project Name */}
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Tên Dự Án <span style={{ color: '#dc2626' }}>*</span>
                </label>
                <input
                  type="text"
                  required
                  value={editingProject.name || ''}
                  onChange={(e) => setEditingProject({ ...editingProject, name: e.target.value })}
                  style={{
                    width: '100%',
                    height: 40,
                    padding: '0 12px',
                    borderRadius: 8,
                    border: '1px solid #cbd5e1',
                    fontSize: 14,
                    outline: 'none',
                  }}
                />
              </div>

              {/* Description */}
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Mô Tả Dự Án
                </label>
                <textarea
                  rows={3}
                  value={editingProject.description || ''}
                  onChange={(e) => setEditingProject({ ...editingProject, description: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '10px 12px',
                    borderRadius: 8,
                    border: '1px solid #cbd5e1',
                    fontSize: 13,
                    outline: 'none',
                    resize: 'vertical',
                  }}
                />
              </div>

              {/* Status & Team */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                    Trạng Thái
                  </label>
                  <select
                    value={editingProject.status || 'ACTIVE'}
                    onChange={(e) => setEditingProject({ ...editingProject, status: e.target.value })}
                    style={{
                      width: '100%',
                      height: 40,
                      padding: '0 12px',
                      borderRadius: 8,
                      border: '1px solid #cbd5e1',
                      fontSize: 13,
                    }}
                  >
                    <option value="PLANNING">PLANNING (Lập kế hoạch)</option>
                    <option value="ACTIVE">ACTIVE (Đang hoạt động)</option>
                    <option value="COMPLETED">COMPLETED (Hoàn thành)</option>
                    <option value="ON_HOLD">ON_HOLD (Tạm dừng)</option>
                  </select>
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                    Nhóm Sở Hữu (Team)
                  </label>
                  <select
                    value={editingProject.teamId || ''}
                    onChange={(e) => setEditingProject({ ...editingProject, teamId: e.target.value || null })}
                    style={{
                      width: '100%',
                      height: 40,
                      padding: '0 12px',
                      borderRadius: 8,
                      border: '1px solid #cbd5e1',
                      fontSize: 13,
                    }}
                  >
                    <option value="">Không gán nhóm (Cá nhân)</option>
                    {teams.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* 🌟 Template Checkbox Option */}
              <div
                style={{
                  padding: 12,
                  borderRadius: 8,
                  backgroundColor: editingProject.isTemplate ? '#fffbeb' : '#f8fafc',
                  border: editingProject.isTemplate ? '1px solid #fde68a' : '1px solid #e2e8f0',
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 10,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
                onClick={() => setEditingProject({ ...editingProject, isTemplate: !editingProject.isTemplate })}
              >
                <input
                  type="checkbox"
                  checked={!!editingProject.isTemplate}
                  onChange={() => { }}
                  style={{ marginTop: 2, cursor: 'pointer' }}
                />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: '#92400e', display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Sparkles size={14} color="#d97706" />
                    <span>Đặt làm Dự Án Mẫu (Template)</span>
                  </div>
                  <div style={{ fontSize: 12, color: '#64748b', marginTop: 2 }}>
                    Dự án này sẽ xuất hiện trong danh mục Template để các thành viên khác nhân bản nhanh quy trình và cột mốc.
                  </div>
                </div>
              </div>

              {/* Public Visibility Checkbox */}
              <div
                style={{
                  padding: 12,
                  borderRadius: 8,
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  cursor: 'pointer',
                }}
                onClick={() => setEditingProject({ ...editingProject, isPublic: !editingProject.isPublic })}
              >
                <input
                  type="checkbox"
                  checked={!!editingProject.isPublic}
                  onChange={() => { }}
                  style={{ cursor: 'pointer' }}
                />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: '#334155', display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Globe size={14} color="#16a34a" />
                    <span>Dự Án Công Khai (Public Workspace)</span>
                  </div>
                </div>
              </div>

              {/* Modal Footer Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 8 }}>
                <button
                  type="button"
                  onClick={() => setEditingProject(null)}
                  disabled={isSubmitting}
                  style={{
                    padding: '8px 16px',
                    borderRadius: 8,
                    border: '1px solid #cbd5e1',
                    backgroundColor: '#ffffff',
                    fontSize: 13,
                    fontWeight: 500,
                    cursor: 'pointer',
                  }}
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  style={{
                    padding: '8px 20px',
                    borderRadius: 8,
                    border: 'none',
                    backgroundColor: '#0284c7',
                    color: '#ffffff',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  {isSubmitting ? 'Đang lưu...' : 'Lưu Thay Đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── MODAL 3: Clone Project Modal ─── */}
      {cloningProject && (
        <CreateProjectModal
          teams={teams}
          sourceProject={cloningProject}
          onClose={() => setCloningProject(null)}
          onSuccess={() => {
            setCloningProject(null);
            fetchProjects(0, pageSize);
            fetchStats();
          }}
        />
      )}

      {/* ─── MODAL 4: Delete Confirmation Modal ─── */}
      {deletingProject && (
        <div
          className="modal-overlay"
          onClick={() => setDeletingProject(null)}
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: 20,
          }}
        >
          <div
            className="modal-card"
            onClick={(e) => e.stopPropagation()}
            style={{
              backgroundColor: '#ffffff',
              borderRadius: 12,
              width: '100%',
              maxWidth: 440,
              padding: 24,
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14 }}>
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 10,
                  backgroundColor: '#fee2e2',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#dc2626',
                }}
              >
                <AlertTriangle size={22} />
              </div>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0f172a' }}>
                Xác Nhận Xóa Dự Án
              </h3>
            </div>

            <p style={{ fontSize: 13, color: '#64748b', lineHeight: 1.5, marginBottom: 20 }}>
              Bạn có chắc chắn muốn xóa dự án <strong style={{ color: '#0f172a' }}>"{deletingProject.name}"</strong> (Mã:{' '}
              {deletingProject.key}) không? Toàn bộ cột mốc, nhãn và tác vụ bên trong dự án này sẽ bị xóa vĩnh viễn khỏi hệ thống.
            </p>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
              <button
                type="button"
                onClick={() => setDeletingProject(null)}
                disabled={isSubmitting}
                style={{
                  padding: '8px 16px',
                  borderRadius: 8,
                  border: '1px solid #cbd5e1',
                  backgroundColor: '#ffffff',
                  fontSize: 13,
                  fontWeight: 500,
                  cursor: 'pointer',
                }}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleDeleteConfirm}
                disabled={isSubmitting}
                style={{
                  padding: '8px 18px',
                  borderRadius: 8,
                  border: 'none',
                  backgroundColor: '#dc2626',
                  color: '#ffffff',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                {isSubmitting ? 'Đang xóa...' : 'Xác Nhận Xóa'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Stat Card Component ───
function StatCard({ label, value, icon, bgColor, borderColor, highlight, badgeText, active, onClick }) {
  return (
    <div
      onClick={onClick}
      style={{
        backgroundColor: active ? '#ffffff' : bgColor,
        borderRadius: 10,
        border: active ? `2px solid ${highlight ? '#f59e0b' : '#0284c7'}` : `1px solid ${borderColor}`,
        padding: '14px 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        cursor: 'pointer',
        boxShadow: active ? '0 4px 12px rgba(2, 132, 199, 0.12)' : '0 1px 2px rgba(0,0,0,0.02)',
        transition: 'all 0.15s ease',
      }}
    >
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
          <span style={{ fontSize: 12, fontWeight: 600, color: '#475569' }}>{label}</span>
          {badgeText && (
            <span
              style={{
                fontSize: 10,
                fontWeight: 700,
                backgroundColor: '#fef3c7',
                color: '#b45309',
                padding: '1px 5px',
                borderRadius: 4,
              }}
            >
              {badgeText}
            </span>
          )}
        </div>
        <div style={{ fontSize: 22, fontWeight: 800, color: '#0f172a', fontFamily: 'var(--font-heading)' }}>
          {value}
        </div>
      </div>

      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: 8,
          backgroundColor: '#ffffff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
        }}
      >
        {icon}
      </div>
    </div>
  );
}

// ─── Helper: Status Badge Style ───
function getStatusBadgeStyle(status) {
  switch (status) {
    case 'ACTIVE':
      return { bg: '#f0fdf4', text: '#16a34a', border: '#bbf7d0' };
    case 'PLANNING':
      return { bg: '#eef2ff', text: '#4f46e5', border: '#c7d2fe' };
    case 'COMPLETED':
      return { bg: '#f0fdfa', text: '#0d9488', border: '#99f6e4' };
    case 'ON_HOLD':
      return { bg: '#fffbeb', text: '#d97706', border: '#fde68a' };
    default:
      return { bg: '#f1f5f9', text: '#64748b', border: '#e2e8f0' };
  }
}

// ─── Helper: Date Formatter ───
function formatDate(dateStr) {
  if (!dateStr) return 'N/A';
  try {
    const d = new Date(dateStr);
    return d.toLocaleDateString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return dateStr;
  }
}
