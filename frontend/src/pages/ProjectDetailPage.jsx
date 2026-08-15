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
  Sparkles,
  ArrowLeft,
  RefreshCw,
  Clock,
  LayoutList,
  Kanban,
  LayoutGrid
} from 'lucide-react';
import { projectApi, milestoneApi, taskApi } from '../api.js';
import MilestoneCard from '../components/milestones/MilestoneCard.jsx';
import CreateMilestoneModal from '../components/modals/CreateMilestoneModal.jsx';
import TaskRowItem from '../components/tasks/TaskRowItem.jsx';
import TaskBoardView from '../components/tasks/TaskBoardView.jsx';
import CreateTaskModal from '../components/modals/CreateTaskModal.jsx';

export default function ProjectDetailPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const { teams } = useOutletContext();

  const [project, setProject] = useState(null);
  const [milestones, setMilestones] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);

  // Filter & View states
  const [activeTab, setActiveTab] = useState('tasks'); // 'tasks' | 'milestones'
  const [viewType, setViewType] = useState('board'); // 'list' | 'board' (Mặc định board theo ui-1.png)
  const [quickSubFilter, setQuickSubFilter] = useState('all'); // 'all' | 'active' | 'backlog'
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [milestoneFilter, setMilestoneFilter] = useState('');

  // Modals state
  const [isCreateMilestoneOpen, setIsCreateMilestoneOpen] = useState(false);
  const [milestoneToEdit, setMilestoneToEdit] = useState(null);
  const [isCreateTaskOpen, setIsCreateTaskOpen] = useState(false);
  const [parentTaskForSubtask, setParentTaskForSubtask] = useState(null);
  const [defaultStatusForNewTask, setDefaultStatusForNewTask] = useState('TODO');

  // Expanded subtasks map
  const [expandedTasks, setExpandedTasks] = useState({});
  const [subtasksMap, setSubtasksMap] = useState({});

  const fetchProjectData = async () => {
    if (!projectId || projectId === 'undefined') {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      const [pRes, mRes, tRes] = await Promise.all([
        projectApi.getById(projectId),
        milestoneApi.listByProject(projectId),
        taskApi.listByProject(projectId, {
          status: statusFilter || null,
          milestoneId: milestoneFilter || null,
          search: searchTerm || null,
        }),
      ]);

      if (pRes && pRes.data) setProject(pRes.data);
      if (mRes && mRes.data) setMilestones(mRes.data);
      if (tRes && tRes.data) setTasks(tRes.data);
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
  }, [projectId, statusFilter, milestoneFilter, searchTerm]);

  // Handle task status update
  const handleTaskStatusChange = async (taskId, newStatus) => {
    try {
      await taskApi.update(taskId, { status: newStatus });
      // Refresh tasks and milestones (for progress updates)
      fetchProjectData();
    } catch (err) {
      alert(err.message || 'Cập nhật trạng thái thất bại');
    }
  };

  // Handle task delete
  const handleTaskDelete = async (taskId) => {
    if (!window.confirm('Bạn có chắc muốn xóa tác vụ này?')) return;
    try {
      await taskApi.delete(taskId);
      fetchProjectData();
    } catch (err) {
      alert(err.message || 'Xóa tác vụ thất bại');
    }
  };

  // Handle toggle subtasks
  const handleToggleExpand = async (taskId) => {
    const isCurrentlyExpanded = !!expandedTasks[taskId];
    setExpandedTasks((prev) => ({ ...prev, [taskId]: !isCurrentlyExpanded }));

    if (!isCurrentlyExpanded && !subtasksMap[taskId]) {
      try {
        const res = await taskApi.getSubtasks(taskId);
        if (res && res.data) {
          setSubtasksMap((prev) => ({ ...prev, [taskId]: res.data }));
        }
      } catch (err) {
        console.error('Failed to fetch subtasks:', err);
      }
    }
  };

  // Handle milestone delete
  const handleMilestoneDelete = async (milestoneId) => {
    if (!window.confirm('Bạn có chắc muốn xóa cột mốc này?')) return;
    try {
      await milestoneApi.delete(milestoneId);
      fetchProjectData();
    } catch (err) {
      alert(err.message || 'Xóa cột mốc thất bại');
    }
  };

  if (loading && !project) {
    return (
      <div style={{ padding: '60px 0', textAlign: 'center', color: '#64748B' }}>
        Đang tải thông tin dự án...
      </div>
    );
  }

  if (!project) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <h3>Không tìm thấy dự án</h3>
        <button onClick={() => navigate('/projects')} style={{ marginTop: 12, padding: '6px 14px' }}>
          Quay lại danh sách dự án
        </button>
      </div>
    );
  }

  // Filter root tasks (not subtasks) for main list
  const rootTasks = tasks.filter((t) => !t.parentTaskId);

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      {/* ─── Top Navigation & Project Title ─── */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <button
            onClick={() => navigate('/projects')}
            style={{
              background: 'none',
              border: '1px solid #E2E8F0',
              borderRadius: 6,
              padding: '5px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              fontSize: 12,
              color: '#64748B',
              cursor: 'pointer',
              backgroundColor: '#FFFFFF',
            }}
          >
            <ArrowLeft size={13} />
            <span>Danh sách dự án</span>
          </button>
        </div>

        {/* Project Header Card */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 14,
            border: '1px solid #E2E8F0',
            padding: '20px 24px',
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
              <h1 style={{ fontSize: 22, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                {project.name}
              </h1>
            </div>

            <p style={{ fontSize: 13, color: '#64748B', margin: 0, maxWidth: 700, lineHeight: 1.4 }}>
              {project.description || 'Chưa có mô tả cho dự án này.'}
            </p>
          </div>

          {/* Top Quick Actions */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <button
              onClick={() => {
                setMilestoneToEdit(null);
                setIsCreateMilestoneOpen(true);
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                backgroundColor: '#FFFFFF',
                color: '#334155',
                border: '1px solid #CBD5E1',
                borderRadius: 7,
                padding: '7px 12px',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              <Flag size={14} color="#2563EB" />
              <span>+ Cột mốc</span>
            </button>

            <button
              onClick={() => {
                setParentTaskForSubtask(null);
                setIsCreateTaskOpen(true);
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                backgroundColor: '#2563EB',
                color: '#FFFFFF',
                border: 'none',
                borderRadius: 7,
                padding: '7px 14px',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                boxShadow: '0 2px 4px rgba(37,99,235,0.2)',
              }}
            >
              <Plus size={15} />
              <span>+ Tạo tác vụ</span>
            </button>
          </div>
        </div>
      </div>

      {/* ─── Navigation Tabs ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          borderBottom: '1px solid #E2E8F0',
          marginBottom: 20,
        }}
      >
        <button
          onClick={() => setActiveTab('tasks')}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '10px 16px',
            border: 'none',
            backgroundColor: 'transparent',
            borderBottom: activeTab === 'tasks' ? '2px solid #2563EB' : '2px solid transparent',
            color: activeTab === 'tasks' ? '#2563EB' : '#64748B',
            fontWeight: activeTab === 'tasks' ? 700 : 500,
            fontSize: 14,
            cursor: 'pointer',
          }}
        >
          <CheckSquare size={16} />
          <span>Danh sách Tác vụ</span>
          <span
            style={{
              fontSize: 11,
              padding: '1px 6px',
              borderRadius: 10,
              backgroundColor: activeTab === 'tasks' ? '#EFF6FF' : '#F1F5F9',
              color: activeTab === 'tasks' ? '#2563EB' : '#64748B',
              fontWeight: 600,
            }}
          >
            {tasks.length}
          </span>
        </button>

        <button
          onClick={() => setActiveTab('milestones')}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '10px 16px',
            border: 'none',
            backgroundColor: 'transparent',
            borderBottom: activeTab === 'milestones' ? '2px solid #2563EB' : '2px solid transparent',
            color: activeTab === 'milestones' ? '#2563EB' : '#64748B',
            fontWeight: activeTab === 'milestones' ? 700 : 500,
            fontSize: 14,
            cursor: 'pointer',
          }}
        >
          <Flag size={16} />
          <span>Cột mốc & Sprints (Milestones)</span>
          <span
            style={{
              fontSize: 11,
              padding: '1px 6px',
              borderRadius: 10,
              backgroundColor: activeTab === 'milestones' ? '#EFF6FF' : '#F1F5F9',
              color: activeTab === 'milestones' ? '#2563EB' : '#64748B',
              fontWeight: 600,
            }}
          >
            {milestones.length}
          </span>
        </button>
      </div>

      {/* ─── TAB 1: Tasks View (List / Kanban Board theo ui-1.png) ─── */}
      {activeTab === 'tasks' && (
        <div>
          {/* Sub-Filters & View Mode Switcher Header Bar */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              marginBottom: 16,
            }}
          >
            {/* Left: Sub-filter Pills (Active, Backlog, All issues như ui-1.png) */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              {[
                { id: 'all', label: 'All issues' },
                { id: 'active', label: 'Active' },
                { id: 'backlog', label: 'Backlog' },
              ].map((sub) => {
                const isActive = quickSubFilter === sub.id;
                return (
                  <button
                    key={sub.id}
                    onClick={() => setQuickSubFilter(sub.id)}
                    style={{
                      padding: '5px 12px',
                      borderRadius: 6,
                      border: isActive ? '1px solid #BFDBFE' : '1px solid transparent',
                      backgroundColor: isActive ? '#EFF6FF' : '#FFFFFF',
                      color: isActive ? '#2563EB' : '#64748B',
                      fontSize: 13,
                      fontWeight: isActive ? 700 : 500,
                      cursor: 'pointer',
                      boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
                    }}
                  >
                    {sub.label}
                  </button>
                );
              })}
            </div>

            {/* Right: Search, Status/Milestone Dropdown & View Mode Switcher */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {/* Search */}
              <div style={{ position: 'relative', width: 220, display: 'flex', alignItems: 'center' }}>
                <Search size={14} color="#94A3B8" style={{ position: 'absolute', left: 10 }} />
                <input
                  type="text"
                  placeholder="Tìm tác vụ..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '5px 10px 5px 30px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                    backgroundColor: '#FFFFFF',
                  }}
                />
              </div>

              {/* Milestone Filter */}
              <select
                value={milestoneFilter}
                onChange={(e) => setMilestoneFilter(e.target.value)}
                style={{
                  padding: '5px 10px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  backgroundColor: '#FFFFFF',
                  outline: 'none',
                }}
              >
                <option value="">Tất cả Cột mốc</option>
                {milestones && milestones.map((m, idx) => (
                  <option key={m.id || `filter-milestone-${idx}`} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>

              {/* View Mode Toggle: List vs Board (ui-1.png) */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  backgroundColor: '#FFFFFF',
                  border: '1px solid #CBD5E1',
                  borderRadius: 6,
                  padding: 2,
                }}
              >
                <button
                  onClick={() => setViewType('list')}
                  title="Xem dạng Danh sách (List View)"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '4px 8px',
                    borderRadius: 4,
                    border: 'none',
                    backgroundColor: viewType === 'list' ? '#EFF6FF' : 'transparent',
                    color: viewType === 'list' ? '#2563EB' : '#64748B',
                    cursor: 'pointer',
                  }}
                >
                  <LayoutList size={15} />
                </button>
                <button
                  onClick={() => setViewType('board')}
                  title="Xem dạng Bảng cột (Kanban Board - ui-1.png)"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: '4px 8px',
                    borderRadius: 4,
                    border: 'none',
                    backgroundColor: viewType === 'board' ? '#EFF6FF' : 'transparent',
                    color: viewType === 'board' ? '#2563EB' : '#64748B',
                    cursor: 'pointer',
                  }}
                >
                  <Kanban size={15} />
                </button>
              </div>

              {/* Reload Button */}
              <button
                onClick={fetchProjectData}
                title="Làm mới"
                style={{
                  background: '#FFFFFF',
                  border: '1px solid #CBD5E1',
                  borderRadius: 6,
                  padding: '5px 8px',
                  color: '#64748B',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  fontSize: 12,
                }}
              >
                <RefreshCw size={13} />
              </button>
            </div>
          </div>

          {/* ─── DISPLAY MODE: Board View (Kanban theo ui-1.png) ─── */}
          {viewType === 'board' ? (
            <TaskBoardView
              tasks={tasks.filter((t) => {
                if (quickSubFilter === 'active') return t.status === 'TODO' || t.status === 'IN_PROGRESS';
                if (quickSubFilter === 'backlog') return !t.milestoneId || t.status === 'TODO';
                return true;
              })}
              onStatusChange={handleTaskStatusChange}
              onDeleteTask={handleTaskDelete}
              onOpenCreateTask={(colStatus) => {
                setParentTaskForSubtask(null);
                setDefaultStatusForNewTask(colStatus || 'TODO');
                setIsCreateTaskOpen(true);
              }}
            />
          ) : (
            /* ─── DISPLAY MODE: List View (Danh sách hàng ngang) ─── */
            <div
              style={{
                backgroundColor: '#FFFFFF',
                borderRadius: 12,
                border: '1px solid #E2E8F0',
                overflow: 'hidden',
                boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
              }}
            >
              {rootTasks.length === 0 ? (
                <div style={{ padding: '50px 20px', textAlign: 'center', color: '#64748B' }}>
                  <CheckSquare size={36} color="#CBD5E1" style={{ margin: '0 auto 10px auto' }} />
                  <h4 style={{ color: '#0F172A', marginBottom: 4 }}>Chưa có tác vụ nào</h4>
                  <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 14px 0' }}>
                    Hãy tạo tác vụ đầu tiên để bắt đầu triển khai dự án.
                  </p>
                  <button
                    onClick={() => setIsCreateTaskOpen(true)}
                    style={{
                      backgroundColor: '#2563EB',
                      color: '#FFFFFF',
                      border: 'none',
                      borderRadius: 6,
                      padding: '6px 14px',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: 'pointer',
                    }}
                  >
                    + Tạo tác vụ mới
                  </button>
                </div>
              ) : (
                <div>
                  {rootTasks
                    .filter((t) => {
                      if (quickSubFilter === 'active') return t.status === 'TODO' || t.status === 'IN_PROGRESS';
                      if (quickSubFilter === 'backlog') return !t.milestoneId || t.status === 'TODO';
                      return true;
                    })
                    .map((task) => {
                      const isExpanded = !!expandedTasks[task.id];
                      const subtasks = subtasksMap[task.id] || [];

                      return (
                        <React.Fragment key={task.id}>
                          <TaskRowItem
                            task={task}
                            isExpanded={isExpanded}
                            onToggleExpand={handleToggleExpand}
                            onStatusChange={handleTaskStatusChange}
                            onDelete={handleTaskDelete}
                            onAddSubtask={(pTask) => {
                              setParentTaskForSubtask(pTask);
                              setIsCreateTaskOpen(true);
                            }}
                          />

                          {/* Render Expanded Subtasks Tree */}
                          {isExpanded &&
                            subtasks.map((subtask) => (
                              <TaskRowItem
                                key={subtask.id}
                                task={subtask}
                                level={1}
                                onStatusChange={handleTaskStatusChange}
                                onDelete={handleTaskDelete}
                              />
                            ))}
                        </React.Fragment>
                      );
                    })}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ─── TAB 2: Milestones & Sprints ─── */}
      {activeTab === 'milestones' && (
        <div>
          {milestones.length === 0 ? (
            <div
              style={{
                padding: '50px 20px',
                textAlign: 'center',
                backgroundColor: '#FFFFFF',
                borderRadius: 12,
                border: '1px dashed #CBD5E1',
              }}
            >
              <Flag size={36} color="#CBD5E1" style={{ margin: '0 auto 10px auto' }} />
              <h4 style={{ color: '#0F172A', marginBottom: 4 }}>Chưa có Cột mốc (Milestone) nào</h4>
              <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 14px 0' }}>
                Cột mốc giúp bạn phân chia dự án thành các giai đoạn (Sprints/Phases) và theo dõi % tiến độ hoàn thành.
              </p>
              <button
                onClick={() => {
                  setMilestoneToEdit(null);
                  setIsCreateMilestoneOpen(true);
                }}
                style={{
                  backgroundColor: '#2563EB',
                  color: '#FFFFFF',
                  border: 'none',
                  borderRadius: 6,
                  padding: '6px 14px',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                + Tạo cột mốc mới
              </button>
            </div>
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))',
                gap: 16,
              }}
            >
              {milestones.map((m) => (
                <MilestoneCard
                  key={m.id}
                  milestone={m}
                  onEdit={(mItem) => {
                    setMilestoneToEdit(mItem);
                    setIsCreateMilestoneOpen(true);
                  }}
                  onDelete={handleMilestoneDelete}
                />
              ))}
            </div>
          )}
        </div>
      )}

      {/* ─── Modals ─── */}
      {isCreateMilestoneOpen && (
        <CreateMilestoneModal
          projectId={projectId}
          milestoneToEdit={milestoneToEdit}
          onClose={() => setIsCreateMilestoneOpen(false)}
          onSuccess={() => {
            setIsCreateMilestoneOpen(false);
            fetchProjectData();
          }}
        />
      )}

      {isCreateTaskOpen && (
        <CreateTaskModal
          projectId={projectId}
          milestones={milestones}
          parentTask={parentTaskForSubtask}
          onClose={() => setIsCreateTaskOpen(false)}
          onSuccess={() => {
            setIsCreateTaskOpen(false);
            setParentTaskForSubtask(null);
            fetchProjectData();
          }}
        />
      )}
    </div>
  );
}
