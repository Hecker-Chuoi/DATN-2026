import React, { useState, useEffect, useRef } from 'react';
import {
  X,
  Circle,
  Clock,
  CheckCircle2,
  XCircle,
  User,
  Calendar,
  Layers,
  Paperclip,
  Smile,
  Plus,
  ArrowUp,
  Link as LinkIcon,
  Copy,
  MoreHorizontal,
  FolderKanban,
  CheckSquare,
  Trash2,
  Sparkles,
  ChevronDown,
  Tag
} from 'lucide-react';
import { taskApi } from '../../api.js';

const STATUS_OPTIONS = [
  { id: 'TODO', label: 'Todo', icon: (s = 14) => <Circle size={s} color="#64748B" /> },
  { id: 'IN_PROGRESS', label: 'In Progress', icon: (s = 14) => <Clock size={s} color="#EAB308" /> },
  { id: 'DONE', label: 'Done', icon: (s = 14) => <CheckCircle2 size={s} color="#3B82F6" /> },
  { id: 'CANCELLED', label: 'Canceled', icon: (s = 14) => <XCircle size={s} color="#94A3B8" /> },
];

const PRIORITY_OPTIONS = [
  { id: 'URGENT', label: 'Urgent', color: '#DC2626' },
  { id: 'HIGH', label: 'High', color: '#EA580C' },
  { id: 'MEDIUM', label: 'Medium', color: '#2563EB' },
  { id: 'LOW', label: 'Low', color: '#64748B' },
];

export default function TaskDetailView({
  taskId,
  project,
  milestones = [],
  members = [],
  currentUser,
  onClose,
  onTaskUpdated,
}) {
  const [task, setTask] = useState(null);
  const [subtasks, setSubtasks] = useState([]);
  const [loading, setLoading] = useState(true);

  // Editable fields
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [isEditingDesc, setIsEditingDesc] = useState(false);

  // Subtask Inline Creator state (task_detail_create_subtask.png)
  const [isAddingSubtask, setIsAddingSubtask] = useState(false);
  const [subtaskTitle, setSubtaskTitle] = useState('');
  const [subtaskDesc, setSubtaskDesc] = useState('');
  const [subtaskPriority, setSubtaskPriority] = useState('MEDIUM');
  const [subtaskAssigneeId, setSubtaskAssigneeId] = useState('');
  const [subtaskLoading, setSubtaskLoading] = useState(false);

  // Activity / Comments state
  const [commentText, setCommentText] = useState('');
  const [comments, setComments] = useState([]);

  // Property Popovers
  const [activePopover, setActivePopover] = useState(null);

  const fetchTaskDetails = async () => {
    if (!taskId) return;
    try {
      setLoading(true);
      const [tRes, sRes] = await Promise.all([
        taskApi.getById(taskId),
        taskApi.getSubtasks(taskId),
      ]);
      if (tRes && tRes.data) {
        setTask(tRes.data);
        setTitle(tRes.data.title || '');
        setDescription(tRes.data.description || '');
      }
      if (sRes && sRes.data) {
        setSubtasks(sRes.data);
      }
    } catch (err) {
      console.error('Failed to load task details:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTaskDetails();
  }, [taskId]);

  const handleUpdateField = async (fields) => {
    try {
      const res = await taskApi.update(taskId, fields);
      if (res && res.data) {
        setTask(res.data);
      }
      if (onTaskUpdated) onTaskUpdated();
    } catch (err) {
      alert(err.message || 'Lỗi khi cập nhật tác vụ');
    }
  };

  const handleSaveTitle = () => {
    setIsEditingTitle(false);
    if (title.trim() && title !== task?.title) {
      handleUpdateField({ title: title.trim() });
    }
  };

  const handleSaveDesc = () => {
    setIsEditingDesc(false);
    if (description !== task?.description) {
      handleUpdateField({ description: description.trim() || null });
    }
  };

  const handleCreateSubtaskSubmit = async (e) => {
    if (e) e.preventDefault();
    if (!subtaskTitle.trim()) return;

    try {
      setSubtaskLoading(true);
      await taskApi.create({
        projectId: task.projectId,
        parentTaskId: task.id,
        milestoneId: task.milestoneId || null,
        assigneeId: subtaskAssigneeId || null,
        title: subtaskTitle.trim(),
        description: subtaskDesc.trim() || null,
        type: 'TASK',
        status: 'TODO',
        priority: subtaskPriority,
      });

      setSubtaskTitle('');
      setSubtaskDesc('');
      setIsAddingSubtask(false);
      fetchTaskDetails();
      if (onTaskUpdated) onTaskUpdated();
    } catch (err) {
      alert(err.message || 'Lỗi khi tạo subtask');
    } finally {
      setSubtaskLoading(false);
    }
  };

  const handleToggleSubtaskStatus = async (sub) => {
    const nextStatus = sub.status === 'DONE' ? 'TODO' : 'DONE';
    try {
      await taskApi.update(sub.id, { status: nextStatus });
      fetchTaskDetails();
      if (onTaskUpdated) onTaskUpdated();
    } catch (err) {
      alert(err.message || 'Lỗi khi đổi trạng thái subtask');
    }
  };

  const handleDeleteSubtask = async (subId) => {
    if (!window.confirm('Xóa subtask này?')) return;
    try {
      await taskApi.delete(subId);
      fetchTaskDetails();
      if (onTaskUpdated) onTaskUpdated();
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa subtask');
    }
  };

  const handleAddComment = (e) => {
    if (e) e.preventDefault();
    if (!commentText.trim()) return;

    const newComment = {
      id: Date.now(),
      author: currentUser?.username || 'You',
      text: commentText.trim(),
      createdAt: 'Just now',
    };
    setComments((prev) => [...prev, newComment]);
    setCommentText('');
  };

  const copyTaskLink = () => {
    navigator.clipboard.writeText(window.location.href);
    alert('Đã sao chép liên kết tác vụ!');
  };

  if (loading && !task) {
    return (
      <div
        style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.45)',
          backdropFilter: 'blur(3px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          color: '#FFFFFF',
        }}
      >
        Đang tải chi tiết tác vụ...
      </div>
    );
  }

  if (!task) return null;

  const currentStatusObj = STATUS_OPTIONS.find((s) => s.id === task.status) || STATUS_OPTIONS[0];
  const currentPriorityObj = PRIORITY_OPTIONS.find((p) => p.id === task.priority);
  const currentMilestoneObj = milestones.find((m) => m.id === task.milestoneId);
  const currentAssigneeObj = members.find((m) => (m.id || m.userId) === task.assigneeId);

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.45)',
        backdropFilter: 'blur(3px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: '24px 20px',
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '1120px',
          height: '90vh',
          maxHeight: '850px',
          backgroundColor: '#FFFFFF',
          borderRadius: 16,
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.2)',
          border: '1px solid #E2E8F0',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* ─── Top Bar (task_detail_create_subtask.png) ─── */}
        <div
          style={{
            padding: '12px 24px',
            borderBottom: '1px solid #F1F5F9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#2563EB', backgroundColor: '#EFF6FF', padding: '2px 8px', borderRadius: 4 }}>
              {task.code}
            </span>
            <span style={{ fontSize: 13, color: '#94A3B8' }}>›</span>
            <span style={{ fontSize: 13, color: '#64748B', fontWeight: 500 }}>
              {project?.name || 'Project'}
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <button
              onClick={copyTaskLink}
              title="Sao chép liên kết"
              style={{ background: 'none', border: 'none', color: '#64748B', cursor: 'pointer', padding: 4 }}
            >
              <LinkIcon size={16} />
            </button>
            <button
              onClick={onClose}
              title="Đóng"
              style={{ background: 'none', border: 'none', color: '#64748B', cursor: 'pointer', padding: 4 }}
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* ─── 2-Column Main Layout (task_detail_create_subtask.png) ─── */}
        <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
          {/* ════════ LEFT COLUMN: Main Content & Subtasks ════════ */}
          <div
            style={{
              flex: 1,
              padding: '24px 32px',
              overflowY: 'auto',
              borderRight: '1px solid #F1F5F9',
            }}
          >
            {/* Title (Inline Edit) */}
            {isEditingTitle ? (
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                onBlur={handleSaveTitle}
                onKeyDown={(e) => e.key === 'Enter' && handleSaveTitle()}
                autoFocus
                style={{
                  width: '100%',
                  fontSize: 22,
                  fontWeight: 700,
                  color: '#0F172A',
                  border: '1px solid #3B82F6',
                  borderRadius: 6,
                  padding: '4px 8px',
                  outline: 'none',
                  marginBottom: 12,
                }}
              />
            ) : (
              <h1
                onClick={() => setIsEditingTitle(true)}
                title="Nhấp để sửa tiêu đề"
                style={{
                  fontSize: 22,
                  fontWeight: 700,
                  color: '#0F172A',
                  margin: '0 0 12px 0',
                  cursor: 'text',
                  padding: '4px 0',
                  borderRadius: 4,
                }}
              >
                {title || 'Untitled Task'}
              </h1>
            )}

            {/* Description (Inline Edit) */}
            {isEditingDesc ? (
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                onBlur={handleSaveDesc}
                autoFocus
                rows={4}
                style={{
                  width: '100%',
                  fontSize: 14,
                  color: '#334155',
                  border: '1px solid #3B82F6',
                  borderRadius: 6,
                  padding: '8px',
                  outline: 'none',
                  resize: 'vertical',
                  lineHeight: 1.5,
                  marginBottom: 16,
                }}
              />
            ) : (
              <div
                onClick={() => setIsEditingDesc(true)}
                title="Nhấp để sửa mô tả"
                style={{
                  fontSize: 14,
                  color: description ? '#334155' : '#94A3B8',
                  lineHeight: 1.6,
                  minHeight: 40,
                  cursor: 'text',
                  padding: '6px 0',
                  marginBottom: 16,
                  whiteSpace: 'pre-wrap',
                }}
              >
                {description || 'Add description...'}
              </div>
            )}

            {/* Actions row under description */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
              <button
                type="button"
                style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', padding: 4 }}
              >
                <Smile size={16} />
              </button>
              <button
                type="button"
                style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', padding: 4 }}
              >
                <Paperclip size={16} />
              </button>
            </div>

            {/* ─── Sub-issues Section (task_detail_create_subtask.png) ─── */}
            <div style={{ marginBottom: 32 }}>
              {!isAddingSubtask ? (
                <button
                  type="button"
                  onClick={() => setIsAddingSubtask(true)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    padding: '6px 12px',
                    borderRadius: 6,
                    border: '1px solid #E2E8F0',
                    backgroundColor: '#FFFFFF',
                    color: '#334155',
                    fontSize: 12,
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
                >
                  <Plus size={14} />
                  <span>Add sub-tasks</span>
                </button>
              ) : (
                /* Inline Subtask Box (task_detail_create_subtask.png) */
                <form
                  onSubmit={handleCreateSubtaskSubmit}
                  style={{
                    backgroundColor: '#FFFFFF',
                    borderRadius: 12,
                    border: '1px solid #CBD5E1',
                    boxShadow: '0 4px 6px -1px rgba(0,0,0,0.05)',
                    padding: '14px 16px',
                    marginBottom: 16,
                  }}
                >
                  <input
                    type="text"
                    placeholder="Task title"
                    value={subtaskTitle}
                    onChange={(e) => setSubtaskTitle(e.target.value)}
                    autoFocus
                    required
                    style={{
                      width: '100%',
                      fontSize: 14,
                      fontWeight: 600,
                      color: '#0F172A',
                      border: 'none',
                      outline: 'none',
                      marginBottom: 8,
                    }}
                  />

                  <textarea
                    placeholder="Add description..."
                    value={subtaskDesc}
                    onChange={(e) => setSubtaskDesc(e.target.value)}
                    rows={2}
                    style={{
                      width: '100%',
                      fontSize: 13,
                      color: '#475569',
                      border: 'none',
                      outline: 'none',
                      resize: 'none',
                      marginBottom: 12,
                    }}
                  />

                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span
                        style={{
                          fontSize: 11,
                          fontWeight: 700,
                          backgroundColor: '#EFF6FF',
                          color: '#2563EB',
                          padding: '3px 8px',
                          borderRadius: 4,
                        }}
                      >
                        {project?.key || 'PRJ'}
                      </span>

                      {/* Subtask Priority Pill */}
                      <div style={{ position: 'relative' }}>
                        <div
                          onClick={() =>
                            setActivePopover(
                              activePopover === 'subtask-priority' ? null : 'subtask-priority'
                            )
                          }
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 5,
                            padding: '3px 8px',
                            borderRadius: 14,
                            border: '1px solid #E2E8F0',
                            backgroundColor: '#FFFFFF',
                            color:
                              PRIORITY_OPTIONS.find((p) => p.id === subtaskPriority)?.color ||
                              '#64748B',
                            fontSize: 11,
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          <span>···</span>
                          <span>
                            {PRIORITY_OPTIONS.find((p) => p.id === subtaskPriority)?.label ||
                              'Priority'}
                          </span>
                        </div>

                        {activePopover === 'subtask-priority' && (
                          <div
                            style={{
                              position: 'absolute',
                              top: '110%',
                              left: 0,
                              zIndex: 100,
                              backgroundColor: '#FFFFFF',
                              borderRadius: 8,
                              boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                              border: '1px solid #E2E8F0',
                              padding: 4,
                              minWidth: 130,
                            }}
                          >
                            {PRIORITY_OPTIONS.map((pr) => (
                              <div
                                key={pr.id}
                                onClick={() => {
                                  setSubtaskPriority(pr.id);
                                  setActivePopover(null);
                                }}
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 8,
                                  padding: '6px 8px',
                                  borderRadius: 4,
                                  cursor: 'pointer',
                                  fontSize: 11,
                                  fontWeight: 600,
                                  color: pr.color,
                                  backgroundColor:
                                    subtaskPriority === pr.id ? '#F1F5F9' : 'transparent',
                                }}
                                onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                                onMouseLeave={(e) =>
                                  (e.currentTarget.style.backgroundColor =
                                    subtaskPriority === pr.id ? '#F1F5F9' : 'transparent')
                                }
                              >
                                <span>···</span>
                                <span>{pr.label}</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>

                      {/* Subtask Assignee Pill */}
                      <div style={{ position: 'relative' }}>
                        {(() => {
                          const subAssignee = members.find(
                            (m) => (m.id || m.userId) === subtaskAssigneeId
                          );
                          return (
                            <>
                              <div
                                onClick={() =>
                                  setActivePopover(
                                    activePopover === 'subtask-assignee' ? null : 'subtask-assignee'
                                  )
                                }
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 5,
                                  padding: '3px 8px',
                                  borderRadius: 14,
                                  border: '1px solid #E2E8F0',
                                  backgroundColor: subAssignee ? '#EFF6FF' : '#FFFFFF',
                                  color: subAssignee ? '#1D4ED8' : '#64748B',
                                  fontSize: 11,
                                  fontWeight: 600,
                                  cursor: 'pointer',
                                }}
                              >
                                <User size={12} />
                                <span>
                                  {subAssignee
                                    ? subAssignee.fullName || subAssignee.username
                                    : 'Assignee'}
                                </span>
                              </div>

                              {activePopover === 'subtask-assignee' && (
                                <div
                                  style={{
                                    position: 'absolute',
                                    top: '110%',
                                    left: 0,
                                    zIndex: 100,
                                    backgroundColor: '#FFFFFF',
                                    borderRadius: 8,
                                    boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                                    border: '1px solid #E2E8F0',
                                    padding: 4,
                                    minWidth: 170,
                                    maxHeight: 180,
                                    overflowY: 'auto',
                                  }}
                                >
                                  <div
                                    onClick={() => {
                                      setSubtaskAssigneeId('');
                                      setActivePopover(null);
                                    }}
                                    style={{
                                      padding: '6px 8px',
                                      borderRadius: 4,
                                      cursor: 'pointer',
                                      fontSize: 11,
                                      color: '#64748B',
                                    }}
                                  >
                                    Unassigned
                                  </div>
                                  {members.map((m) => {
                                    const mId = m.id || m.userId;
                                    const mName = m.fullName || m.username || m.email;
                                    return (
                                      <div
                                        key={mId}
                                        onClick={() => {
                                          setSubtaskAssigneeId(mId);
                                          setActivePopover(null);
                                        }}
                                        style={{
                                          display: 'flex',
                                          alignItems: 'center',
                                          gap: 6,
                                          padding: '6px 8px',
                                          borderRadius: 4,
                                          cursor: 'pointer',
                                          fontSize: 11,
                                          color: '#1E293B',
                                          backgroundColor:
                                            subtaskAssigneeId === mId ? '#F1F5F9' : 'transparent',
                                        }}
                                      >
                                        <User size={12} color="#2563EB" />
                                        <span>{mName}</span>
                                      </div>
                                    );
                                  })}
                                </div>
                              )}
                            </>
                          );
                        })()}
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <button
                        type="button"
                        onClick={() => setIsAddingSubtask(false)}
                        style={{
                          background: 'none',
                          border: 'none',
                          fontSize: 12,
                          color: '#64748B',
                          cursor: 'pointer',
                          padding: '4px 8px',
                        }}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        disabled={!subtaskTitle.trim() || subtaskLoading}
                        style={{
                          padding: '5px 14px',
                          borderRadius: 6,
                          border: 'none',
                          backgroundColor: subtaskTitle.trim() ? '#5E6AD2' : '#E2E8F0',
                          color: subtaskTitle.trim() ? '#FFFFFF' : '#94A3B8',
                          fontSize: 12,
                          fontWeight: 600,
                          cursor: subtaskTitle.trim() ? 'pointer' : 'default',
                        }}
                      >
                        {subtaskLoading ? 'Creating...' : 'Create'}
                      </button>
                    </div>
                  </div>
                </form>
              )}

              {/* Subtasks List */}
              {subtasks.length > 0 && (
                <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {subtasks.map((sub) => (
                    <div
                      key={sub.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '8px 12px',
                        borderRadius: 8,
                        backgroundColor: '#F8FAFC',
                        border: '1px solid #F1F5F9',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <input
                          type="checkbox"
                          checked={sub.status === 'DONE'}
                          onChange={() => handleToggleSubtaskStatus(sub)}
                          style={{ cursor: 'pointer' }}
                        />
                        <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B' }}>
                          {sub.code}
                        </span>
                        <span
                          style={{
                            fontSize: 13,
                            color: sub.status === 'DONE' ? '#94A3B8' : '#1E293B',
                            textDecoration: sub.status === 'DONE' ? 'line-through' : 'none',
                          }}
                        >
                          {sub.title}
                        </span>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <span style={{ fontSize: 11, color: '#64748B' }}>
                          {sub.assigneeName || 'Unassigned'}
                        </span>
                        <button
                          onClick={() => handleDeleteSubtask(sub.id)}
                          style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', padding: 2 }}
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* ─── Activity & Comment Section (task_detail_create_subtask.png) ─── */}
            <div>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
                <h3 style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', margin: 0 }}>Activity</h3>
                <button
                  style={{
                    background: 'none',
                    border: 'none',
                    fontSize: 12,
                    color: '#64748B',
                    cursor: 'pointer',
                  }}
                >
                  Unsubscribe
                </button>
              </div>

              {/* Timeline Item */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: '#64748B', marginBottom: 16 }}>
                <div
                  style={{
                    width: 20,
                    height: 20,
                    borderRadius: '50%',
                    backgroundColor: '#F59E0B',
                    color: '#FFFFFF',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 10,
                    fontWeight: 700,
                  }}
                >
                  {currentUser?.username ? currentUser.username.charAt(0).toUpperCase() : 'U'}
                </div>
                <span>
                  <strong>{currentUser?.username || 'User'}</strong> created the task · {task.createdAt ? new Date(task.createdAt).toLocaleDateString('vi-VN') : 'just now'}
                </span>
              </div>

              {/* Comments Stream */}
              {comments.map((c) => (
                <div key={c.id} style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
                  <div
                    style={{
                      width: 24,
                      height: 24,
                      borderRadius: '50%',
                      backgroundColor: '#DBEAFE',
                      color: '#1E40AF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 11,
                      fontWeight: 700,
                      flexShrink: 0,
                    }}
                  >
                    {c.author.charAt(0).toUpperCase()}
                  </div>
                  <div style={{ backgroundColor: '#F8FAFC', padding: '8px 12px', borderRadius: 8, flex: 1, border: '1px solid #E2E8F0' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600, color: '#1E293B' }}>{c.author}</span>
                      <span style={{ fontSize: 10, color: '#94A3B8' }}>{c.createdAt}</span>
                    </div>
                    <div style={{ fontSize: 13, color: '#334155' }}>{c.text}</div>
                  </div>
                </div>
              ))}

              {/* Leave a comment box (task_detail_create_subtask.png) */}
              <form
                onSubmit={handleAddComment}
                style={{
                  backgroundColor: '#FFFFFF',
                  borderRadius: 10,
                  border: '1px solid #E2E8F0',
                  padding: '10px 14px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <input
                  type="text"
                  placeholder="Leave a comment..."
                  value={commentText}
                  onChange={(e) => setCommentText(e.target.value)}
                  style={{
                    flex: 1,
                    border: 'none',
                    outline: 'none',
                    fontSize: 13,
                    color: '#1E293B',
                  }}
                />
                <button
                  type="button"
                  style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', padding: 4 }}
                >
                  <Paperclip size={15} />
                </button>
                <button
                  type="submit"
                  disabled={!commentText.trim()}
                  style={{
                    width: 26,
                    height: 26,
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: commentText.trim() ? '#5E6AD2' : '#F1F5F9',
                    color: commentText.trim() ? '#FFFFFF' : '#94A3B8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: commentText.trim() ? 'pointer' : 'default',
                  }}
                >
                  <ArrowUp size={14} />
                </button>
              </form>
            </div>
          </div>

          {/* ════════ RIGHT COLUMN: Properties Sidebar (Linear Style task_detail_create_subtask.png) ════════ */}
          <div
            style={{
              width: 280,
              padding: '24px 20px',
              backgroundColor: '#FAFAFA',
              display: 'flex',
              flexDirection: 'column',
              gap: 24,
              overflowY: 'auto',
              borderLeft: '1px solid #F1F5F9',
            }}
          >
            {/* Properties Block */}
            <div>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: '#64748B',
                  display: 'block',
                  marginBottom: 8,
                }}
              >
                Properties
              </span>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {/* 1. Status Row */}
                <div style={{ position: 'relative' }}>
                  <div
                    onClick={() => setActivePopover(activePopover === 'sidebar-status' ? null : 'sidebar-status')}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '6px 8px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 13,
                      color: '#1E293B',
                      fontWeight: 500,
                      transition: 'background-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    {currentStatusObj.icon(14)}
                    <span>{currentStatusObj.label}</span>
                  </div>

                  {activePopover === 'sidebar-status' && (
                    <div
                      style={{
                        position: 'absolute',
                        top: '100%',
                        left: 0,
                        zIndex: 100,
                        backgroundColor: '#FFFFFF',
                        borderRadius: 8,
                        boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                        border: '1px solid #E2E8F0',
                        padding: 4,
                        minWidth: 150,
                      }}
                    >
                      {STATUS_OPTIONS.map((st) => (
                        <div
                          key={st.id}
                          onClick={() => {
                            handleUpdateField({ status: st.id });
                            setActivePopover(null);
                          }}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            padding: '7px 10px',
                            borderRadius: 6,
                            cursor: 'pointer',
                            fontSize: 12,
                            color: '#1E293B',
                            backgroundColor: task.status === st.id ? '#F1F5F9' : 'transparent',
                          }}
                          onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                          onMouseLeave={(e) =>
                            (e.currentTarget.style.backgroundColor =
                              task.status === st.id ? '#F1F5F9' : 'transparent')
                          }
                        >
                          {st.icon(14)}
                          <span>{st.label}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* 2. Priority Row */}
                <div style={{ position: 'relative' }}>
                  <div
                    onClick={() => setActivePopover(activePopover === 'sidebar-priority' ? null : 'sidebar-priority')}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '6px 8px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 13,
                      color: currentPriorityObj ? currentPriorityObj.color : '#64748B',
                      fontWeight: 500,
                      transition: 'background-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <span>···</span>
                    <span>{currentPriorityObj ? currentPriorityObj.label : 'Set priority'}</span>
                  </div>

                  {activePopover === 'sidebar-priority' && (
                    <div
                      style={{
                        position: 'absolute',
                        top: '100%',
                        left: 0,
                        zIndex: 100,
                        backgroundColor: '#FFFFFF',
                        borderRadius: 8,
                        boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                        border: '1px solid #E2E8F0',
                        padding: 4,
                        minWidth: 140,
                      }}
                    >
                      {PRIORITY_OPTIONS.map((pr) => (
                        <div
                          key={pr.id}
                          onClick={() => {
                            handleUpdateField({ priority: pr.id });
                            setActivePopover(null);
                          }}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            padding: '7px 10px',
                            borderRadius: 6,
                            cursor: 'pointer',
                            fontSize: 12,
                            fontWeight: 500,
                            color: pr.color,
                            backgroundColor: task.priority === pr.id ? '#F1F5F9' : 'transparent',
                          }}
                        >
                          <span>···</span>
                          <span>{pr.label}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* 3. Assignee Row */}
                <div style={{ position: 'relative' }}>
                  <div
                    onClick={() => setActivePopover(activePopover === 'sidebar-assignee' ? null : 'sidebar-assignee')}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '6px 8px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 13,
                      color: currentAssigneeObj ? '#1E293B' : '#64748B',
                      fontWeight: 500,
                      transition: 'background-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <User size={14} color="#64748B" />
                    <span>{currentAssigneeObj ? (currentAssigneeObj.fullName || currentAssigneeObj.username) : 'Assign'}</span>
                  </div>

                  {activePopover === 'sidebar-assignee' && (
                    <div
                      style={{
                        position: 'absolute',
                        top: '100%',
                        left: 0,
                        zIndex: 100,
                        backgroundColor: '#FFFFFF',
                        borderRadius: 8,
                        boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                        border: '1px solid #E2E8F0',
                        padding: 4,
                        minWidth: 180,
                        maxHeight: 200,
                        overflowY: 'auto',
                      }}
                    >
                      <div
                        onClick={() => {
                          handleUpdateField({ assigneeId: null });
                          setActivePopover(null);
                        }}
                        style={{
                          padding: '7px 10px',
                          borderRadius: 6,
                          cursor: 'pointer',
                          fontSize: 12,
                          color: '#64748B',
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                        onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                      >
                        Unassigned
                      </div>
                      {members.map((m) => {
                        const mId = m.id || m.userId;
                        const mName = m.fullName || m.username || m.email;
                        return (
                          <div
                            key={mId}
                            onClick={() => {
                              handleUpdateField({ assigneeId: mId });
                              setActivePopover(null);
                            }}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 8,
                              padding: '7px 10px',
                              borderRadius: 6,
                              cursor: 'pointer',
                              fontSize: 12,
                              color: '#1E293B',
                              backgroundColor: task.assigneeId === mId ? '#F1F5F9' : 'transparent',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                            onMouseLeave={(e) =>
                              (e.currentTarget.style.backgroundColor =
                                task.assigneeId === mId ? '#F1F5F9' : 'transparent')
                            }
                          >
                            <User size={13} color="#2563EB" />
                            <span>{mName}</span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* 4. Due Date Row */}
                <div style={{ position: 'relative' }}>
                  <div
                    onClick={() => setActivePopover(activePopover === 'sidebar-due' ? null : 'sidebar-due')}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '6px 8px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 13,
                      color: task.dueDate ? '#1E293B' : '#64748B',
                      fontWeight: 500,
                      transition: 'background-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <Calendar size={14} color="#64748B" />
                    <span>
                      {task.dueDate
                        ? new Date(task.dueDate).toLocaleDateString('vi-VN', {
                            day: '2-digit',
                            month: '2-digit',
                            year: 'numeric',
                          })
                        : 'Due date'}
                    </span>
                  </div>

                  {activePopover === 'sidebar-due' && (
                    <div
                      style={{
                        position: 'absolute',
                        top: '100%',
                        left: 0,
                        zIndex: 100,
                        backgroundColor: '#FFFFFF',
                        borderRadius: 8,
                        boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                        border: '1px solid #E2E8F0',
                        padding: 10,
                        minWidth: 200,
                      }}
                    >
                      <input
                        type="date"
                        value={task.dueDate ? task.dueDate.substring(0, 10) : ''}
                        onChange={(e) => {
                          handleUpdateField({
                            dueDate: e.target.value ? `${e.target.value}T23:59:59` : null,
                          });
                          setActivePopover(null);
                        }}
                        style={{
                          width: '100%',
                          padding: '6px 8px',
                          borderRadius: 6,
                          border: '1px solid #CBD5E1',
                          fontSize: 12,
                          outline: 'none',
                        }}
                      />
                      {task.dueDate && (
                        <button
                          type="button"
                          onClick={() => {
                            handleUpdateField({ dueDate: null });
                            setActivePopover(null);
                          }}
                          style={{
                            marginTop: 8,
                            width: '100%',
                            padding: '4px 8px',
                            background: 'none',
                            border: '1px solid #FCA5A5',
                            borderRadius: 4,
                            color: '#DC2626',
                            fontSize: 11,
                            cursor: 'pointer',
                          }}
                        >
                          Xóa ngày hạn chót
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Labels Block */}
            <div>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: '#64748B',
                  display: 'block',
                  marginBottom: 8,
                }}
              >
                Labels
              </span>
              <div
                onClick={() => alert('Tính năng nhãn dán tùy chỉnh sẽ có mặt trong Phase tiếp theo')}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '5px 10px',
                  borderRadius: 14,
                  border: '1px solid #E2E8F0',
                  backgroundColor: '#FFFFFF',
                  fontSize: 12,
                  color: '#64748B',
                  cursor: 'pointer',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
              >
                <Tag size={12} />
                <span>Add label</span>
              </div>
            </div>

            {/* Milestone Block */}
            <div style={{ position: 'relative' }}>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: '#64748B',
                  display: 'block',
                  marginBottom: 8,
                }}
              >
                Milestone
              </span>

              <div
                onClick={() => setActivePopover(activePopover === 'sidebar-milestone' ? null : 'sidebar-milestone')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '6px 8px',
                  borderRadius: 6,
                  backgroundColor: currentMilestoneObj ? '#F5F3FF' : 'transparent',
                  color: currentMilestoneObj ? '#7C3AED' : '#64748B',
                  fontSize: 13,
                  fontWeight: 500,
                  cursor: 'pointer',
                  transition: 'background-color 0.15s ease',
                }}
                onMouseEnter={(e) => {
                  if (!currentMilestoneObj) e.currentTarget.style.backgroundColor = '#F1F5F9';
                }}
                onMouseLeave={(e) => {
                  if (!currentMilestoneObj) e.currentTarget.style.backgroundColor = 'transparent';
                }}
              >
                <Layers size={14} color={currentMilestoneObj ? '#7C3AED' : '#64748B'} />
                <span>{currentMilestoneObj ? currentMilestoneObj.name : 'Set milestone'}</span>
              </div>

              {activePopover === 'sidebar-milestone' && (
                <div
                  style={{
                    position: 'absolute',
                    top: '100%',
                    left: 0,
                    zIndex: 100,
                    backgroundColor: '#FFFFFF',
                    borderRadius: 8,
                    boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                    border: '1px solid #E2E8F0',
                    padding: 4,
                    minWidth: 180,
                  }}
                >
                  <div
                    onClick={() => {
                      handleUpdateField({ milestoneId: null });
                      setActivePopover(null);
                    }}
                    style={{
                      padding: '7px 10px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 12,
                      color: '#64748B',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    No milestone
                  </div>
                  {milestones.map((m) => (
                    <div
                      key={m.id}
                      onClick={() => {
                        handleUpdateField({ milestoneId: m.id });
                        setActivePopover(null);
                      }}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '7px 10px',
                        borderRadius: 6,
                        cursor: 'pointer',
                        fontSize: 12,
                        color: '#1E293B',
                        backgroundColor: task.milestoneId === m.id ? '#F1F5F9' : 'transparent',
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                      onMouseLeave={(e) =>
                        (e.currentTarget.style.backgroundColor =
                          task.milestoneId === m.id ? '#F1F5F9' : 'transparent')
                      }
                    >
                      <Layers size={13} color="#7C3AED" />
                      <span>{m.name}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Project Block */}
            <div>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: '#64748B',
                  display: 'block',
                  marginBottom: 8,
                }}
              >
                Project
              </span>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '6px 8px',
                  borderRadius: 6,
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#1E293B',
                }}
              >
                <FolderKanban size={14} color="#2563EB" />
                <span>{project?.name || project?.key || 'Project'}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
