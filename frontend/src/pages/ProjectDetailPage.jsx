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
  Flame
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
          {mainSubTab === 'overview' && (
            <div style={{ maxWidth: 800, padding: 16 }}>
              <h2 style={{ fontSize: 20, fontWeight: 700, color: '#0F172A', marginBottom: 12 }}>
                {project?.name}
              </h2>
              <p style={{ fontSize: 14, color: '#475569', lineHeight: 1.6 }}>
                {project?.description || 'Chưa có mô tả dự án.'}
              </p>
            </div>
          )}

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
