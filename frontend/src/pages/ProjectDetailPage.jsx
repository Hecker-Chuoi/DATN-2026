import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useOutletContext, Link } from 'react-router-dom';
import {
  FolderKanban,
  CheckSquare,
  Flag,
  Plus,
  Search,
  Filter,
  Layers,
  Calendar,
  RefreshCw,
  Clock,
  LayoutList,
  Kanban,
  SlidersHorizontal,
  Star,
  MoreHorizontal,
  Link as LinkIcon,
  Bell,
  ChevronDown,
  ChevronRight,
  Circle,
  CheckCircle2,
  XCircle,
  User,
  Tag,
  Users,
  Check,
  AlertTriangle,
  AlertCircle,
  Flame,
  TrendingUp,
  ArrowRight,
  Target,
  Sparkles
} from 'lucide-react';
import { projectApi, milestoneApi, taskApi, teamApi, userApi } from '../api.js';
import TaskBoardView from '../components/tasks/TaskBoardView.jsx';
import TaskDetailView from '../components/tasks/TaskDetailView.jsx';
import CreateTaskModal from '../components/modals/CreateTaskModal.jsx';
import CreateMilestoneModal from '../components/modals/CreateMilestoneModal.jsx';

const STATUS_GROUPS = [
  {
    id: 'IN_PROGRESS',
    title: 'In Progress',
    color: '#EAB308',
    icon: (s = 14) => <Clock size={s} color="#EAB308" />,
    bg: '#FEFCE8',
  },
  {
    id: 'TODO',
    title: 'Todo',
    color: '#64748B',
    icon: (s = 14) => <Circle size={s} color="#94A3B8" />,
    bg: '#F8FAFC',
  },
  {
    id: 'DONE',
    title: 'Done',
    color: '#3B82F6',
    icon: (s = 14) => <CheckCircle2 size={s} color="#3B82F6" />,
    bg: '#EFF6FF',
  },
  {
    id: 'CANCELLED',
    title: 'Canceled',
    color: '#94A3B8',
    icon: (s = 14) => <XCircle size={s} color="#94A3B8" />,
    bg: '#F1F5F9',
  },
];

const PRIORITY_STYLES = {
  URGENT: { label: 'Urgent', color: '#DC2626' },
  HIGH: { label: 'High', color: '#EA580C' },
  MEDIUM: { label: 'Medium', color: '#2563EB' },
  LOW: { label: 'Low', color: '#64748B' },
};

export default function ProjectDetailPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const { teams } = useOutletContext();

  const [project, setProject] = useState(null);
  const [milestones, setMilestones] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [members, setMembers] = useState([]);
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Linear Top Sub-tabs ('overview' | 'activity' | 'tasks')
  const [mainSubTab, setMainSubTab] = useState('tasks');
  const [viewType, setViewType] = useState('board'); // 'list' | 'board'
  const [searchTerm, setSearchTerm] = useState('');
  const [milestoneFilter, setMilestoneFilter] = useState('');

  // Collapsed status groups map
  const [collapsedGroups, setCollapsedGroups] = useState({});

  // Modals & Popovers
  const [isCreateTaskOpen, setIsCreateTaskOpen] = useState(false);
  const [defaultStatusForNewTask, setDefaultStatusForNewTask] = useState('TODO');
  const [selectedTaskId, setSelectedTaskId] = useState(null);

  // Sidebar Inline Milestone creation & Modal Edit
  const [isAddingMilestone, setIsAddingMilestone] = useState(false);
  const [newMilestoneName, setNewMilestoneName] = useState('');
  const [newMilestoneDate, setNewMilestoneDate] = useState('');
  const [milestoneSubmitting, setMilestoneSubmitting] = useState(false);
  const [isMilestoneModalOpen, setIsMilestoneModalOpen] = useState(false);
  const [selectedMilestoneToEdit, setSelectedMilestoneToEdit] = useState(null);

  const fetchProjectData = async () => {
    if (!projectId || projectId === 'undefined') {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      const [pRes, mRes, tRes, meRes] = await Promise.all([
        projectApi.getById(projectId),
        milestoneApi.listByProject(projectId),
        taskApi.listByProject(projectId, {
          milestoneId: milestoneFilter === 'NONE' ? null : (milestoneFilter || null),
          search: searchTerm || null,
        }),
        userApi.me().catch(() => null),
      ]);

      if (pRes && pRes.data) {
        setProject(pRes.data);
        if (pRes.data.teamId) {
          try {
            const teamRes = await teamApi.getById(pRes.data.teamId);
            if (teamRes && teamRes.data && teamRes.data.members) {
              setMembers(teamRes.data.members);
            }
          } catch (e) {
            console.warn('Could not load team members:', e);
          }
        }
      }
      if (mRes && mRes.data) setMilestones(mRes.data);
      if (tRes && tRes.data) setTasks(tRes.data);
      if (meRes && meRes.data) setCurrentUser(meRes.data);
    } catch (err) {
      console.error('Failed to load project details:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (projectId && projectId !== 'undefined') {
      fetchProjectData();
    } else {
      setLoading(false);
    }
  }, [projectId, milestoneFilter, searchTerm]);

  const toggleGroupCollapse = (groupId) => {
    setCollapsedGroups((prev) => ({ ...prev, [groupId]: !prev[groupId] }));
  };

  const handleTaskStatusChange = async (taskId, newStatus) => {
    try {
      await taskApi.update(taskId, { status: newStatus });
      fetchProjectData();
    } catch (err) {
      alert(err.message || 'Cập nhật trạng thái thất bại');
    }
  };

  const handleCreateQuickMilestone = async (e) => {
    e.preventDefault();
    if (!newMilestoneName.trim()) return;
    try {
      setMilestoneSubmitting(true);
      await milestoneApi.create({
        projectId,
        name: newMilestoneName.trim(),
        targetDate: newMilestoneDate ? `${newMilestoneDate}T23:59:59` : null,
      });
      setNewMilestoneName('');
      setNewMilestoneDate('');
      setIsAddingMilestone(false);
      fetchProjectData();
    } catch (err) {
      alert(err.message || 'Tạo cột mốc thất bại');
    } finally {
      setMilestoneSubmitting(false);
    }
  };

  const formatTaskDeadline = (task) => {
    if (!task.dueDate) return null;

    if (task.status === 'DONE' || task.status === 'CANCELLED') {
      const d = new Date(task.dueDate);
      return {
        text: d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }),
        isDeadline: true,
        color: '#64748B',
        bg: '#F1F5F9',
        border: '#E2E8F0',
        rowBg: 'transparent',
        rowBorder: '#F8FAFC',
        title: `Hạn chót: ${d.toLocaleDateString('vi-VN')}`,
      };
    }

    try {
      const due = new Date(task.dueDate);
      const now = new Date();
      const diffHours = (due.getTime() - now.getTime()) / (1000 * 60 * 60);
      const formatted = due.toLocaleDateString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      });

      if (diffHours < 0) {
        // 1. ĐÃ HẾT HẠN (Overdue)
        return {
          text: formatted,
          isDeadline: true,
          isOverdue: true,
          color: '#BE123C',
          bg: '#FFE4E6',
          border: '#FDA4AF',
          rowBg: '#FFF1F2',
          rowBorder: '#FECDD3',
          title: `⚠️ Đã hết hạn: ${formatted}`,
        };
      } else if (diffHours <= 48) {
        // 2. SẮP HẾT HẠN (Due soon trong 48h)
        return {
          text: formatted,
          isDeadline: true,
          isDueSoon: true,
          color: '#B45309',
          bg: '#FEF3C7',
          border: '#FCD34D',
          rowBg: '#FFFBEB',
          rowBorder: '#FDE68A',
          title: `🔥 CẢNH BÁO: Sắp hết hạn trong ${Math.round(diffHours)}h (${formatted})!`,
        };
      } else {
        // 3. CÒN HẠN BÌNH THƯỜNG (> 48h)
        return {
          text: formatted,
          isDeadline: true,
          color: '#16A34A',
          bg: '#F0FDF4',
          border: '#DCFCE7',
          rowBg: 'transparent',
          rowBorder: '#F8FAFC',
          title: `Hạn chót: ${formatted}`,
        };
      }
    } catch {
      return null;
    }
  };

  const formatDateShort = (dateStr) => {
    if (!dateStr) return null;
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch {
      return dateStr;
    }
  };

  const currentTeam = teams?.find((t) => t.id === project?.teamId);

  if (loading && !project) return <div style={{ padding: 40, textAlign: 'center' }}>Loading...</div>;

  return (
    <div style={{ backgroundColor: '#FFFFFF', minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* ════════ TOP BREADCRUMB & HEADER ════════ */}
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
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, color: '#64748B' }}>
          <FolderKanban size={15} color="#2563EB" />
          <span style={{ fontWeight: 500, color: '#475569' }}>
            {currentTeam?.name || 'My Workspace'}
          </span>
          <span style={{ color: '#94A3B8' }}>›</span>
          <Link to="/projects" style={{ color: '#64748B', textDecoration: 'none', fontWeight: 500 }}>
            Projects
          </Link>
          <span style={{ color: '#94A3B8' }}>›</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#0F172A', fontWeight: 600 }}>
            <span>{project?.name || 'Loading project...'}</span>
          </div>
          <Star size={14} color="#94A3B8" style={{ cursor: 'pointer', marginLeft: 4 }} />
          <MoreHorizontal size={14} color="#94A3B8" style={{ cursor: 'pointer' }} />
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button
            onClick={() => {
              navigator.clipboard.writeText(window.location.href);
              alert('Đã sao chép link dự án!');
            }}
            title="Copy link"
            style={{ background: 'none', border: 'none', color: '#64748B', cursor: 'pointer', padding: 4 }}
          >
            <LinkIcon size={15} />
          </button>
          <button
            title="Notifications"
            style={{ background: 'none', border: 'none', color: '#64748B', cursor: 'pointer', padding: 4 }}
          >
            <Bell size={15} />
          </button>
        </div>
      </div>

      {/* ════════ SUBTABS & VIEW CONTROLS ════════ */}
      <div
        style={{
          padding: '0 24px',
          borderBottom: '1px solid #F1F5F9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', gap: 20 }}>
          {[
            { id: 'overview', label: 'Overview' },
            { id: 'activity', label: 'Activity' },
            { id: 'tasks', label: 'Tasks', icon: CheckSquare },
          ].map((tab) => {
            const isActive = mainSubTab === tab.id;
            const TabIcon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setMainSubTab(tab.id)}
                style={{
                  background: 'none',
                  border: 'none',
                  borderBottom: isActive ? '2px solid #5E6AD2' : '2px solid transparent',
                  padding: '10px 0',
                  color: isActive ? '#0F172A' : '#64748B',
                  fontWeight: isActive ? 600 : 500,
                  fontSize: 13,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                {TabIcon && <TabIcon size={14} color={isActive ? '#5E6AD2' : '#64748B'} />}
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {/* ─── Deadline Color Legend Note ─── */}
          {mainSubTab === 'tasks' && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginRight: 4 }}>
              {/* Sắp đến hạn note (Nền vàng) */}
              <div
                title="Tác vụ sắp đến hạn chót trong 48h (Nền vàng)"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  padding: '3px 8px',
                  borderRadius: 6,
                  backgroundColor: '#FFFBEB',
                  border: '1px solid #FCD34D',
                  color: '#B45309',
                  fontSize: 11,
                  fontWeight: 600,
                }}
              >
                <Flame size={12} color="#D97706" />
                <span>Sắp đến hạn</span>
              </div>

              {/* Đã hết hạn note (Nền đỏ) */}
              <div
                title="Tác vụ đã quá thời hạn chót (Nền đỏ)"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  padding: '3px 8px',
                  borderRadius: 6,
                  backgroundColor: '#FFF1F2',
                  border: '1px solid #FDA4AF',
                  color: '#BE123C',
                  fontSize: 11,
                  fontWeight: 600,
                }}
              >
                <AlertCircle size={12} color="#BE123C" />
                <span>Đã hết hạn</span>
              </div>
            </div>
          )}

          <div style={{ display: 'flex', backgroundColor: '#F1F5F9', padding: 2, borderRadius: 6 }}>
            <button
              onClick={() => setViewType('list')}
              title="Danh sách hàng ngang"
              style={{
                background: viewType === 'list' ? '#FFFFFF' : 'none',
                border: 'none',
                borderRadius: 4,
                padding: '4px 8px',
                color: viewType === 'list' ? '#0F172A' : '#64748B',
                cursor: 'pointer',
                boxShadow: viewType === 'list' ? '0 1px 2px rgba(0,0,0,0.05)' : 'none',
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <LayoutList size={14} />
            </button>
            <button
              onClick={() => setViewType('board')}
              title="Bảng Kanban"
              style={{
                background: viewType === 'board' ? '#FFFFFF' : 'none',
                border: 'none',
                borderRadius: 4,
                padding: '4px 8px',
                color: viewType === 'board' ? '#0F172A' : '#64748B',
                cursor: 'pointer',
                boxShadow: viewType === 'board' ? '0 1px 2px rgba(0,0,0,0.05)' : 'none',
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <Kanban size={14} />
            </button>
          </div>

          <button
            onClick={() => {
              setDefaultStatusForNewTask('TODO');
              setIsCreateTaskOpen(true);
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 5,
              backgroundColor: '#5E6AD2',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: 6,
              padding: '5px 12px',
              fontSize: 12,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Plus size={14} />
            <span>New task</span>
          </button>
        </div>
      </div>

      {/* ════════ MAIN 2-COLUMN LAYOUT ════════ */}
      <div style={{ display: 'flex', flex: 1, minHeight: 'calc(100vh - 100px)' }}>
        <div style={{ flex: 1, padding: '16px 24px', overflowY: 'auto' }}>
          {mainSubTab === 'overview' && (() => {
            const totalTasksCount = tasks.length;
            const inProgressTasks = tasks.filter((t) => t.status === 'IN_PROGRESS');
            const doneTasks = tasks.filter((t) => t.status === 'DONE');
            const todoTasks = tasks.filter((t) => (t.status || 'TODO') === 'TODO');
            const cancelledTasks = tasks.filter((t) => t.status === 'CANCELLED');

            const now = new Date().getTime();
            const dueSoonTasks = tasks.filter((t) => {
              if (t.status === 'DONE' || t.status === 'CANCELLED' || !t.dueDate) return false;
              const diffHours = (new Date(t.dueDate).getTime() - now) / (1000 * 60 * 60);
              return diffHours >= 0 && diffHours <= 48;
            });

            const overdueTasks = tasks.filter((t) => {
              if (t.status === 'DONE' || t.status === 'CANCELLED' || !t.dueDate) return false;
              const diffHours = (new Date(t.dueDate).getTime() - now) / (1000 * 60 * 60);
              return diffHours < 0;
            });

            const completionRate = totalTasksCount > 0 ? Math.round((doneTasks.length / totalTasksCount) * 100) : 0;
            const attentionTasks = [...overdueTasks, ...dueSoonTasks];

            return (
              <div style={{ maxWidth: 980, display: 'flex', flexDirection: 'column', gap: 24, paddingBottom: 40 }}>
                {/* ─── 1. Project Header Banner ─── */}
                <div
                  style={{
                    backgroundColor: '#FFFFFF',
                    borderRadius: 12,
                    border: '1px solid #E2E8F0',
                    padding: '24px',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                    <div style={{ flex: 1, minWidth: 260 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                        <span
                          style={{
                            fontSize: 12,
                            fontWeight: 700,
                            color: '#5E6AD2',
                            backgroundColor: '#EEF2FF',
                            padding: '2px 8px',
                            borderRadius: 4,
                          }}
                        >
                          {project?.key || 'PRJ'}
                        </span>
                        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: '#0F172A' }}>
                          {project?.name}
                        </h1>
                      </div>
                      <p style={{ margin: 0, fontSize: 14, color: '#475569', lineHeight: 1.6, maxWidth: 680 }}>
                        {project?.description || 'Chưa có mô tả chi tiết cho dự án.'}
                      </p>
                    </div>

                    <div style={{ display: 'flex', gap: 8 }}>
                      <button
                        onClick={() => setMainSubTab('tasks')}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                          padding: '8px 14px',
                          backgroundColor: '#F8FAFC',
                          border: '1px solid #E2E8F0',
                          borderRadius: 8,
                          fontSize: 13,
                          fontWeight: 600,
                          color: '#334155',
                          cursor: 'pointer',
                          transition: 'all 0.15s ease',
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                        onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                      >
                        <CheckSquare size={15} color="#5E6AD2" />
                        <span>Xem tất cả tác vụ</span>
                        <ArrowRight size={14} color="#64748B" />
                      </button>

                      <button
                        onClick={() => {
                          setDefaultStatusForNewTask('TODO');
                          setIsCreateTaskOpen(true);
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                          padding: '8px 14px',
                          backgroundColor: '#5E6AD2',
                          color: '#FFFFFF',
                          border: 'none',
                          borderRadius: 8,
                          fontSize: 13,
                          fontWeight: 600,
                          cursor: 'pointer',
                        }}
                      >
                        <Plus size={15} />
                        <span>Thêm tác vụ</span>
                      </button>
                    </div>
                  </div>
                </div>

                {/* ─── 2. Key Metrics Cards (Tổng hợp 4 chỉ số yêu cầu) ─── */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
                  {/* Card 1: Đang làm (In Progress) */}
                  <div
                    onClick={() => setMainSubTab('tasks')}
                    style={{
                      backgroundColor: '#FFFFFF',
                      borderRadius: 12,
                      border: '1px solid #E2E8F0',
                      padding: '18px 20px',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.transform = 'translateY(-2px)';
                      e.currentTarget.style.borderColor = '#FCD34D';
                      e.currentTarget.style.boxShadow = '0 6px 16px rgba(217, 119, 6, 0.08)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'translateY(0)';
                      e.currentTarget.style.borderColor = '#E2E8F0';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.02)';
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: '#64748B' }}>Đang thực hiện</span>
                      <div
                        style={{
                          width: 32,
                          height: 32,
                          borderRadius: 8,
                          backgroundColor: '#FEF3C7',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Clock size={18} color="#D97706" />
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                      <span style={{ fontSize: 28, fontWeight: 800, color: '#B45309' }}>
                        {inProgressTasks.length}
                      </span>
                      <span style={{ fontSize: 12, color: '#94A3B8' }}>
                        / {totalTasksCount} tác vụ
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: '#B45309', marginTop: 6, fontWeight: 500 }}>
                      {totalTasksCount > 0 ? Math.round((inProgressTasks.length / totalTasksCount) * 100) : 0}% tổng khối lượng
                    </div>
                  </div>

                  {/* Card 2: Sắp hết hạn (Due Soon) */}
                  <div
                    onClick={() => setMainSubTab('tasks')}
                    style={{
                      backgroundColor: dueSoonTasks.length > 0 ? '#FFFBEB' : '#FFFFFF',
                      borderRadius: 12,
                      border: `1px solid ${dueSoonTasks.length > 0 ? '#FCD34D' : '#E2E8F0'}`,
                      padding: '18px 20px',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.transform = 'translateY(-2px)';
                      e.currentTarget.style.borderColor = '#F59E0B';
                      e.currentTarget.style.boxShadow = '0 6px 16px rgba(217, 119, 6, 0.12)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'translateY(0)';
                      e.currentTarget.style.borderColor = dueSoonTasks.length > 0 ? '#FCD34D' : '#E2E8F0';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.02)';
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: '#B45309' }}>Sắp hết hạn</span>
                      <div
                        style={{
                          width: 32,
                          height: 32,
                          borderRadius: 8,
                          backgroundColor: '#FEF3C7',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <Flame size={18} color="#D97706" />
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                      <span style={{ fontSize: 28, fontWeight: 800, color: '#B45309' }}>
                        {dueSoonTasks.length}
                      </span>
                      <span style={{ fontSize: 12, color: '#B45309' }}>
                        tác vụ
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: '#92400E', marginTop: 6, fontWeight: 500 }}>
                      {dueSoonTasks.length > 0 ? '🔥 Cần xử lý trong 48h tới' : 'Không có tác vụ gấp'}
                    </div>
                  </div>

                  {/* Card 3: Quá hạn (Overdue) */}
                  <div
                    onClick={() => setMainSubTab('tasks')}
                    style={{
                      backgroundColor: overdueTasks.length > 0 ? '#FFF1F2' : '#FFFFFF',
                      borderRadius: 12,
                      border: `1px solid ${overdueTasks.length > 0 ? '#FDA4AF' : '#E2E8F0'}`,
                      padding: '18px 20px',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.transform = 'translateY(-2px)';
                      e.currentTarget.style.borderColor = '#F43F5E';
                      e.currentTarget.style.boxShadow = '0 6px 16px rgba(225, 29, 72, 0.12)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'translateY(0)';
                      e.currentTarget.style.borderColor = overdueTasks.length > 0 ? '#FDA4AF' : '#E2E8F0';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.02)';
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: overdueTasks.length > 0 ? '#BE123C' : '#64748B' }}>
                        Đã quá hạn
                      </span>
                      <div
                        style={{
                          width: 32,
                          height: 32,
                          borderRadius: 8,
                          backgroundColor: '#FFE4E6',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <AlertCircle size={18} color="#BE123C" />
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                      <span style={{ fontSize: 28, fontWeight: 800, color: '#BE123C' }}>
                        {overdueTasks.length}
                      </span>
                      <span style={{ fontSize: 12, color: '#BE123C' }}>
                        tác vụ
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: '#9F1239', marginTop: 6, fontWeight: 500 }}>
                      {overdueTasks.length > 0 ? '⚠️ Cần kiểm tra & điều chỉnh' : 'Không có tác vụ trễ hạn'}
                    </div>
                  </div>

                  {/* Card 4: Đã hoàn thành (Done) */}
                  <div
                    onClick={() => setMainSubTab('tasks')}
                    style={{
                      backgroundColor: '#FFFFFF',
                      borderRadius: 12,
                      border: '1px solid #E2E8F0',
                      padding: '18px 20px',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.transform = 'translateY(-2px)';
                      e.currentTarget.style.borderColor = '#93C5FD';
                      e.currentTarget.style.boxShadow = '0 6px 16px rgba(37, 99, 235, 0.08)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.transform = 'translateY(0)';
                      e.currentTarget.style.borderColor = '#E2E8F0';
                      e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.02)';
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: '#64748B' }}>Đã hoàn thành</span>
                      <div
                        style={{
                          width: 32,
                          height: 32,
                          borderRadius: 8,
                          backgroundColor: '#DBEAFE',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <CheckCircle2 size={18} color="#2563EB" />
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                      <span style={{ fontSize: 28, fontWeight: 800, color: '#1D4ED8' }}>
                        {doneTasks.length}
                      </span>
                      <span style={{ fontSize: 12, color: '#94A3B8' }}>
                        / {totalTasksCount} tác vụ
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: '#1E40AF', marginTop: 6, fontWeight: 500 }}>
                      Tỷ lệ hoàn thành: {completionRate}%
                    </div>
                  </div>
                </div>

                {/* ─── 3. Overall Project Progress Bar ─── */}
                <div
                  style={{
                    backgroundColor: '#FFFFFF',
                    borderRadius: 12,
                    border: '1px solid #E2E8F0',
                    padding: '20px 24px',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <TrendingUp size={18} color="#5E6AD2" />
                      <span style={{ fontSize: 15, fontWeight: 700, color: '#0F172A' }}>
                        Tiến độ hoàn thành dự án
                      </span>
                    </div>
                    <span style={{ fontSize: 16, fontWeight: 800, color: '#5E6AD2' }}>
                      {completionRate}%
                    </span>
                  </div>

                  {/* Multi-segment Progress Bar */}
                  <div
                    style={{
                      height: 10,
                      width: '100%',
                      backgroundColor: '#F1F5F9',
                      borderRadius: 6,
                      overflow: 'hidden',
                      display: 'flex',
                      marginBottom: 14,
                    }}
                  >
                    {doneTasks.length > 0 && (
                      <div
                        title={`Đã hoàn thành: ${doneTasks.length}`}
                        style={{
                          width: `${(doneTasks.length / (totalTasksCount || 1)) * 100}%`,
                          backgroundColor: '#3B82F6',
                          transition: 'width 0.4s ease',
                        }}
                      />
                    )}
                    {inProgressTasks.length > 0 && (
                      <div
                        title={`Đang tiến hành: ${inProgressTasks.length}`}
                        style={{
                          width: `${(inProgressTasks.length / (totalTasksCount || 1)) * 100}%`,
                          backgroundColor: '#EAB308',
                          transition: 'width 0.4s ease',
                        }}
                      />
                    )}
                    {todoTasks.length > 0 && (
                      <div
                        title={`Cần làm (Todo): ${todoTasks.length}`}
                        style={{
                          width: `${(todoTasks.length / (totalTasksCount || 1)) * 100}%`,
                          backgroundColor: '#CBD5E1',
                          transition: 'width 0.4s ease',
                        }}
                      />
                    )}
                    {cancelledTasks.length > 0 && (
                      <div
                        title={`Đã hủy: ${cancelledTasks.length}`}
                        style={{
                          width: `${(cancelledTasks.length / (totalTasksCount || 1)) * 100}%`,
                          backgroundColor: '#94A3B8',
                          transition: 'width 0.4s ease',
                        }}
                      />
                    )}
                  </div>

                  {/* Breakdown Legend */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap', fontSize: 12, color: '#64748B' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: '#3B82F6' }} />
                      <span>Đã xong: <b>{doneTasks.length}</b></span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: '#EAB308' }} />
                      <span>Đang làm: <b>{inProgressTasks.length}</b></span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: '#CBD5E1' }} />
                      <span>Chờ xử lý: <b>{todoTasks.length}</b></span>
                    </div>
                    {cancelledTasks.length > 0 && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: '#94A3B8' }} />
                        <span>Đã hủy: <b>{cancelledTasks.length}</b></span>
                      </div>
                    )}
                  </div>
                </div>

                {/* ─── 4. Attention Required Tasks (Tác vụ quá hạn & Sắp đến hạn) ─── */}
                <div
                  style={{
                    backgroundColor: '#FFFFFF',
                    borderRadius: 12,
                    border: '1px solid #E2E8F0',
                    padding: '20px 24px',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <AlertCircle size={18} color="#BE123C" />
                      <span style={{ fontSize: 15, fontWeight: 700, color: '#0F172A' }}>
                        Tác vụ cần chú ý ({attentionTasks.length})
                      </span>
                    </div>
                    {attentionTasks.length > 0 && (
                      <button
                        onClick={() => setMainSubTab('tasks')}
                        style={{
                          background: 'none',
                          border: 'none',
                          color: '#5E6AD2',
                          fontSize: 12,
                          fontWeight: 600,
                          cursor: 'pointer',
                        }}
                      >
                        Xem tất cả trên bảng →
                      </button>
                    )}
                  </div>

                  {attentionTasks.length === 0 ? (
                    <div
                      style={{
                        padding: '24px 16px',
                        textAlign: 'center',
                        backgroundColor: '#F8FAFC',
                        borderRadius: 8,
                        color: '#64748B',
                        fontSize: 13,
                        border: '1px dashed #E2E8F0',
                      }}
                    >
                      🎉 Tuyệt vời! Không có tác vụ nào bị quá hạn hoặc sắp đến hạn cần gấp.
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {attentionTasks.slice(0, 5).map((t) => {
                        const isOver = new Date(t.dueDate).getTime() < now;
                        const formattedDate = new Date(t.dueDate).toLocaleDateString('vi-VN');

                        return (
                          <div
                            key={t.id}
                            onClick={() => setSelectedTaskId(t.id)}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              padding: '10px 14px',
                              borderRadius: 8,
                              backgroundColor: isOver ? '#FFF1F2' : '#FFFBEB',
                              border: `1px solid ${isOver ? '#FDA4AF' : '#FCD34D'}`,
                              cursor: 'pointer',
                              transition: 'all 0.15s ease',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.transform = 'translateX(2px)')}
                            onMouseLeave={(e) => (e.currentTarget.style.transform = 'translateX(0)')}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1, minWidth: 0 }}>
                              <span
                                style={{
                                  fontSize: 11,
                                  fontWeight: 700,
                                  color: isOver ? '#BE123C' : '#B45309',
                                  backgroundColor: isOver ? '#FFE4E6' : '#FEF3C7',
                                  padding: '2px 6px',
                                  borderRadius: 4,
                                  flexShrink: 0,
                                }}
                              >
                                {t.code || `#${t.id}`}
                              </span>
                              <span
                                style={{
                                  fontSize: 13,
                                  fontWeight: 600,
                                  color: '#0F172A',
                                  whiteSpace: 'nowrap',
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                }}
                              >
                                {t.title}
                              </span>
                            </div>

                            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}>
                              <span
                                style={{
                                  fontSize: 11,
                                  fontWeight: 600,
                                  color: isOver ? '#BE123C' : '#B45309',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 4,
                                }}
                              >
                                {isOver ? <AlertCircle size={12} /> : <Flame size={12} />}
                                {isOver ? `Quá hạn (${formattedDate})` : `Sắp đến hạn (${formattedDate})`}
                              </span>

                              <div
                                title={t.assigneeName || 'Chưa gán'}
                                style={{
                                  width: 20,
                                  height: 20,
                                  borderRadius: '50%',
                                  backgroundColor: t.assigneeName ? '#DBEAFE' : '#F1F5F9',
                                  color: t.assigneeName ? '#1E40AF' : '#94A3B8',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: 10,
                                  fontWeight: 700,
                                }}
                              >
                                {t.assigneeName ? t.assigneeName.charAt(0).toUpperCase() : <User size={10} />}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* ─── 5. Milestone Progress Section ─── */}
                {milestones.length > 0 && (
                  <div
                    style={{
                      backgroundColor: '#FFFFFF',
                      borderRadius: 12,
                      border: '1px solid #E2E8F0',
                      padding: '20px 24px',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Target size={18} color="#5E6AD2" />
                        <span style={{ fontSize: 15, fontWeight: 700, color: '#0F172A' }}>
                          Tiến độ theo Giai đoạn / Milestones ({milestones.length})
                        </span>
                      </div>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                      {milestones.map((m) => {
                        const mTasks = tasks.filter((t) => t.milestoneId === m.id);
                        const mDone = mTasks.filter((t) => t.status === 'DONE').length;
                        const mProgress = mTasks.length > 0 ? Math.round((mDone / mTasks.length) * 100) : 0;

                        return (
                          <div
                            key={m.id}
                            style={{
                              padding: '12px 14px',
                              backgroundColor: '#F8FAFC',
                              borderRadius: 8,
                              border: '1px solid #E2E8F0',
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                <span style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: '#5E6AD2' }} />
                                <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>
                                  {m.title || m.name}
                                </span>
                              </div>
                              <span style={{ fontSize: 12, fontWeight: 700, color: '#5E6AD2' }}>
                                {mDone}/{mTasks.length} tasks ({mProgress}%)
                              </span>
                            </div>

                            <div
                              style={{
                                height: 6,
                                width: '100%',
                                backgroundColor: '#E2E8F0',
                                borderRadius: 3,
                                overflow: 'hidden',
                              }}
                            >
                              <div
                                style={{
                                  height: '100%',
                                  width: `${mProgress}%`,
                                  backgroundColor: mProgress === 100 ? '#10B981' : '#5E6AD2',
                                  transition: 'width 0.3s ease',
                                }}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            );
          })()}

          {mainSubTab === 'activity' && (
            <div style={{ maxWidth: 800, padding: 16, color: '#64748B', fontSize: 13 }}>
              Timeline hoạt động của dự án sẽ được hiển thị tại đây.
            </div>
          )}

          {mainSubTab === 'tasks' && (
            <div>
              {viewType === 'board' ? (
                <TaskBoardView
                  tasks={tasks}
                  onStatusChange={handleTaskStatusChange}
                  onDeleteTask={async (tId) => {
                    if (window.confirm('Xóa tác vụ?')) {
                      await taskApi.delete(tId);
                      fetchProjectData();
                    }
                  }}
                  onTaskClick={(tId) => setSelectedTaskId(tId)}
                  onOpenCreateTask={(st) => {
                    setDefaultStatusForNewTask(st || 'TODO');
                    setIsCreateTaskOpen(true);
                  }}
                />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {STATUS_GROUPS.map((group) => {
                    const groupTasks = tasks.filter((t) => (t.status || 'TODO') === group.id);
                    const isCollapsed = !!collapsedGroups[group.id];

                    return (
                      <div key={group.id} style={{ display: 'flex', flexDirection: 'column' }}>
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '6px 8px',
                            borderRadius: 6,
                            cursor: 'pointer',
                          }}
                        >
                          <div
                            onClick={() => toggleGroupCollapse(group.id)}
                            style={{ display: 'flex', alignItems: 'center', gap: 8 }}
                          >
                            {isCollapsed ? (
                              <ChevronRight size={14} color="#94A3B8" />
                            ) : (
                              <ChevronDown size={14} color="#94A3B8" />
                            )}
                            {group.icon(14)}
                            <span style={{ fontSize: 13, fontWeight: 600, color: '#1E293B' }}>
                              {group.title}
                            </span>
                            <span style={{ fontSize: 12, color: '#94A3B8', fontWeight: 500 }}>
                              {groupTasks.length}
                            </span>
                          </div>

                          <button
                            onClick={() => {
                              setDefaultStatusForNewTask(group.id);
                              setIsCreateTaskOpen(true);
                            }}
                            title={`Add task to ${group.title}`}
                            style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer' }}
                          >
                            <Plus size={14} />
                          </button>
                        </div>

                        {!isCollapsed && (
                          <div style={{ display: 'flex', flexDirection: 'column' }}>
                            {groupTasks.map((task) => {
                              const pInfo = PRIORITY_STYLES[task.priority] || PRIORITY_STYLES.LOW;
                              const dInfo = formatTaskDeadline(task);

                              return (
                                <div
                                  key={task.id}
                                  onClick={() => setSelectedTaskId(task.id)}
                                  style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    padding: '8px 12px',
                                    backgroundColor: dInfo?.rowBg || 'transparent',
                                    borderBottom: `1px solid ${dInfo?.rowBorder || '#F8FAFC'}`,
                                    borderRadius: 6,
                                    cursor: 'pointer',
                                    transition: 'all 0.15s ease',
                                    marginBottom: dInfo?.isDueSoon || dInfo?.isOverdue ? 2 : 0,
                                  }}
                                  onMouseEnter={(e) => {
                                    if (!dInfo?.rowBg || dInfo.rowBg === 'transparent') {
                                      e.currentTarget.style.backgroundColor = '#F8FAFC';
                                    }
                                  }}
                                  onMouseLeave={(e) => {
                                    e.currentTarget.style.backgroundColor = dInfo?.rowBg || 'transparent';
                                  }}
                                >
                                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1, minWidth: 0 }}>
                                    <span style={{ fontSize: 12, color: pInfo.color, fontWeight: 600 }}>···</span>
                                    <span style={{ fontSize: 12, fontWeight: 700, color: '#475569' }}>
                                      {task.code || `${project?.key || 'PRJ'}-${task.id?.substring(0, 4)}`}
                                    </span>
                                    <div style={{ display: 'flex', alignItems: 'center' }}>{group.icon(14)}</div>
                                    <span style={{ fontSize: 13, color: task.status === 'DONE' ? '#94A3B8' : '#0F172A', textDecoration: task.status === 'DONE' ? 'line-through' : 'none' }}>
                                      {task.title}
                                    </span>
                                  </div>

                                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                                    <div style={{ width: 20, height: 20, borderRadius: '50%', backgroundColor: task.assigneeName ? '#DBEAFE' : '#F1F5F9', color: task.assigneeName ? '#1D4ED8' : '#94A3B8', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700 }}>
                                      {task.assigneeName ? task.assigneeName.charAt(0).toUpperCase() : <User size={11} />}
                                    </div>

                                    {/* Deadline Badge (Red if overdue, Amber if due soon, Green if valid) */}
                                    {dInfo && (
                                      <span
                                        title={dInfo.title}
                                        style={{
                                          display: 'inline-flex',
                                          alignItems: 'center',
                                          gap: 4,
                                          fontSize: 11,
                                          fontWeight: 600,
                                          color: dInfo.color,
                                          backgroundColor: dInfo.bg,
                                          border: `1px solid ${dInfo.border}`,
                                          padding: '2px 8px',
                                          borderRadius: 4,
                                          minWidth: 75,
                                          justifyContent: 'center',
                                        }}
                                      >
                                        {dInfo.isDueSoon ? (
                                          <span className="animate-pulse" style={{ display: 'inline-flex', alignItems: 'center' }}>
                                            <Flame size={11} color="#D97706" />
                                          </span>
                                        ) : dInfo.isOverdue ? (
                                          <AlertCircle size={11} color="#BE123C" />
                                        ) : (
                                          <Calendar size={11} />
                                        )}
                                        <span>{dInfo.text}</span>
                                      </span>
                                    )}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        <div style={{ width: 320, borderLeft: '1px solid #F1F5F9', backgroundColor: '#FFFFFF', padding: '20px 20px', display: 'flex', flexDirection: 'column', gap: 28, overflowY: 'auto' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>Properties</span>
                <ChevronDown size={14} color="#64748B" />
              </div>
              <Plus size={14} color="#94A3B8" style={{ cursor: 'pointer' }} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 13 }}>
                <span style={{ color: '#64748B' }}>Status</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 500, color: '#0F172A' }}>
                  <Clock size={14} color="#EAB308" />
                  <span>In Progress</span>
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 13 }}>
                <span style={{ color: '#64748B' }}>Priority</span>
                <span style={{ color: '#64748B' }}>No priority</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 13 }}>
                <span style={{ color: '#64748B' }}>Dates</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#0F172A' }}>
                  <Calendar size={13} color="#64748B" />
                  <span>{project?.startDate ? formatDateShort(project.startDate) : 'Set start'} → {project?.targetDate ? formatDateShort(project.targetDate) : 'Set target'}</span>
                </div>
              </div>
            </div>
          </div>

          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>Milestones</span>
                <ChevronDown size={14} color="#64748B" />
              </div>
              <Plus size={14} color="#94A3B8" onClick={() => setIsAddingMilestone(true)} style={{ cursor: 'pointer' }} />
            </div>

            {isAddingMilestone && (
              <form onSubmit={handleCreateQuickMilestone} style={{ padding: 8, borderRadius: 6, backgroundColor: '#F8FAFC', border: '1px solid #E2E8F0', marginBottom: 10 }}>
                <input type="text" placeholder="Milestone name..." value={newMilestoneName} onChange={(e) => setNewMilestoneName(e.target.value)} autoFocus style={{ width: '100%', marginBottom: 6, padding: '4px 6px', fontSize: 12, border: '1px solid #CBD5E1', borderRadius: 4 }} />
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <input type="date" value={newMilestoneDate} onChange={(e) => setNewMilestoneDate(e.target.value)} style={{ fontSize: 11, border: 'none', backgroundColor: 'transparent' }} />
                  <button type="submit" disabled={milestoneSubmitting || !newMilestoneName.trim()} style={{ backgroundColor: '#5E6AD2', color: '#FFFFFF', border: 'none', borderRadius: 4, padding: '2px 8px', fontSize: 11, cursor: 'pointer' }}>Add</button>
                </div>
              </form>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {milestones.length === 0 ? (
                <div style={{ fontSize: 12, color: '#94A3B8', fontStyle: 'italic' }}>No milestones yet</div>
              ) : (
                milestones.map((m) => (
                  <div
                    key={m.id}
                    onClick={() => setMilestoneFilter(milestoneFilter === m.id ? '' : m.id)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '6px 8px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 12,
                      backgroundColor: milestoneFilter === m.id ? '#F5F3FF' : 'transparent',
                      color: milestoneFilter === m.id ? '#7C3AED' : '#334155',
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => {
                      if (milestoneFilter !== m.id) e.currentTarget.style.backgroundColor = '#F8FAFC';
                    }}
                    onMouseLeave={(e) => {
                      if (milestoneFilter !== m.id) e.currentTarget.style.backgroundColor = 'transparent';
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ color: '#8B5CF6', fontSize: 12 }}>◆</span>
                      <span style={{ fontWeight: 500 }}>{m.name}</span>
                    </div>

                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedMilestoneToEdit(m);
                        setIsMilestoneModalOpen(true);
                      }}
                      title="Xem & Sửa Cột mốc"
                      style={{
                        background: 'none',
                        border: 'none',
                        color: '#94A3B8',
                        cursor: 'pointer',
                        padding: '2px 4px',
                        borderRadius: 4,
                        display: 'flex',
                        alignItems: 'center',
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.color = '#0F172A')}
                      onMouseLeave={(e) => (e.currentTarget.style.color = '#94A3B8')}
                    >
                      <MoreHorizontal size={14} />
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>

      {selectedTaskId && (
        <TaskDetailView
          taskId={selectedTaskId}
          project={project}
          milestones={milestones}
          members={members}
          currentUser={currentUser}
          onClose={() => setSelectedTaskId(null)}
          onTaskUpdated={fetchProjectData}
        />
      )}

      {isMilestoneModalOpen && (
        <CreateMilestoneModal
          projectId={projectId}
          milestoneToEdit={selectedMilestoneToEdit}
          onClose={() => {
            setIsMilestoneModalOpen(false);
            setSelectedMilestoneToEdit(null);
          }}
          onSuccess={() => {
            setIsMilestoneModalOpen(false);
            setSelectedMilestoneToEdit(null);
            fetchProjectData();
          }}
        />
      )}
    </div>
  );
}
