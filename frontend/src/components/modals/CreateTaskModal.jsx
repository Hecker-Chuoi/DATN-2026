import React, { useState, useRef, useEffect } from 'react';
import {
  X,
  Maximize2,
  Minimize2,
  Circle,
  Clock,
  CheckCircle2,
  XCircle,
  HelpCircle,
  Paperclip,
  Calendar,
  User,
  Tag,
  FolderKanban,
  ChevronRight,
  Sparkles,
  Layers,
  Repeat,
  Link as LinkIcon,
  Plus
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

export default function CreateTaskModal({
  projectId,
  projectKey = 'PRJ',
  milestones = [],
  members = [],
  parentTask,
  defaultStatus = 'TODO',
  onClose,
  onSuccess,
}) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState(defaultStatus || 'TODO');
  const [priority, setPriority] = useState('MEDIUM');
  const [assigneeId, setAssigneeId] = useState('');
  const [milestoneId, setMilestoneId] = useState(parentTask?.milestoneId || '');
  const [dueDate, setDueDate] = useState('');
  const [estimate, setEstimate] = useState('');
  const [createMore, setCreateMore] = useState(false);
  const [isMaximized, setIsMaximized] = useState(false);

  const [activePopover, setActivePopover] = useState(null); // 'status' | 'priority' | 'assignee' | 'milestone' | 'more' | 'dueDate'
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const titleInputRef = useRef(null);
  const popoverRef = useRef(null);

  useEffect(() => {
    if (titleInputRef.current) {
      titleInputRef.current.focus();
    }
  }, []);

  // Close popovers on outside click
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target)) {
        setActivePopover(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Quick Due Date calculators
  const setQuickDueDate = (type) => {
    const today = new Date();
    let target = new Date();

    if (type === 'tomorrow') {
      target.setDate(today.getDate() + 1);
    } else if (type === 'endOfWeek') {
      const day = today.getDay(); // 0 is Sunday
      const diff = 5 - day; // Friday
      target.setDate(today.getDate() + (diff > 0 ? diff : diff + 7));
    } else if (type === 'nextWeek') {
      target.setDate(today.getDate() + 7);
    }

    const yyyy = target.getFullYear();
    const mm = String(target.getMonth() + 1).padStart(2, '0');
    const dd = String(target.getDate()).padStart(2, '0');
    setDueDate(`${yyyy}-${mm}-${dd}`);
    setActivePopover(null);
  };

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();
    if (!title.trim()) return;

    try {
      setLoading(true);
      setErrorMsg('');

      await taskApi.create({
        projectId,
        parentTaskId: parentTask ? parentTask.id : null,
        milestoneId: milestoneId || null,
        assigneeId: assigneeId || null,
        title: title.trim(),
        description: description.trim() || null,
        type: 'TASK',
        status,
        priority,
        estimate: estimate ? parseInt(estimate, 10) : null,
        dueDate: dueDate ? `${dueDate}T23:59:59` : null,
      });

      if (onSuccess) onSuccess();

      if (createMore) {
        setTitle('');
        setDescription('');
        if (titleInputRef.current) titleInputRef.current.focus();
      } else {
        if (onClose) onClose();
      }
    } catch (err) {
      setErrorMsg(err.message || 'Lỗi khi tạo tác vụ. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  };

  const selectedStatus = STATUS_OPTIONS.find((s) => s.id === status) || STATUS_OPTIONS[0];
  const selectedPriority = PRIORITY_OPTIONS.find((p) => p.id === priority);
  const selectedMilestone = milestones.find((m) => m.id === milestoneId);
  const selectedAssignee = members.find((u) => (u.id || u.userId) === assigneeId);

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
        padding: 20,
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: '100%',
          maxWidth: isMaximized ? '900px' : '680px',
          minHeight: isMaximized ? '550px' : '380px',
          backgroundColor: '#FFFFFF',
          borderRadius: 16,
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.15)',
          border: '1px solid #E2E8F0',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          transition: 'all 0.2s ease',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* ─── Header: Breadcrumb & Actions (create_task_modal.png) ─── */}
        <div
          style={{
            padding: '14px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #F1F5F9',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                padding: '2px 8px',
                borderRadius: 6,
                backgroundColor: '#F1F5F9',
                fontSize: 12,
                fontWeight: 700,
                color: '#334155',
              }}
            >
              <FolderKanban size={13} color="#2563EB" />
              <span>{projectKey}</span>
            </div>
            <span style={{ fontSize: 13, color: '#94A3B8' }}>›</span>
            <span style={{ fontSize: 13, fontWeight: 500, color: '#64748B' }}>
              {parentTask ? `Sub-task for ${parentTask.code}` : 'New task'}
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <button
              type="button"
              onClick={() => setIsMaximized(!isMaximized)}
              style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', padding: 4 }}
              title={isMaximized ? 'Thu nhỏ' : 'Phóng to'}
            >
              {isMaximized ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
            </button>
            <button
              type="button"
              onClick={onClose}
              style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', padding: 4 }}
              title="Đóng"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* ─── Form Body ─── */}
        <form
          onSubmit={handleSubmit}
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            padding: '20px 24px',
            position: 'relative',
          }}
        >
          {errorMsg && (
            <div
              style={{
                padding: '8px 12px',
                borderRadius: 6,
                backgroundColor: '#FEF2F2',
                color: '#DC2626',
                fontSize: 12,
                marginBottom: 12,
              }}
            >
              {errorMsg}
            </div>
          )}

          {/* Title Input (Borderless Linear Heading) */}
          <input
            ref={titleInputRef}
            type="text"
            placeholder="Task title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                handleSubmit();
              }
            }}
            required
            style={{
              width: '100%',
              fontSize: 18,
              fontWeight: 600,
              color: '#0F172A',
              border: 'none',
              outline: 'none',
              marginBottom: 12,
              fontFamily: 'inherit',
            }}
          />

          {/* Description Textarea */}
          <textarea
            placeholder="Add description..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                handleSubmit();
              }
            }}
            style={{
              width: '100%',
              flex: 1,
              minHeight: isMaximized ? '260px' : '140px',
              border: 'none',
              outline: 'none',
              fontSize: 14,
              color: '#334155',
              resize: 'none',
              fontFamily: 'inherit',
              lineHeight: 1.5,
              marginBottom: 16,
            }}
          />

          {/* ─── Property Pills Row (create_task_modal.png & create_task_modal_2.png) ─── */}
          <div
            ref={popoverRef}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              flexWrap: 'wrap',
              position: 'relative',
              paddingTop: 12,
              borderTop: '1px solid #F8FAFC',
            }}
          >
            {/* Status Pill */}
            <div style={{ position: 'relative' }}>
              <button
                type="button"
                onClick={() => setActivePopover(activePopover === 'status' ? null : 'status')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '5px 10px',
                  borderRadius: 20,
                  border: '1px solid #E2E8F0',
                  backgroundColor: '#FFFFFF',
                  fontSize: 12,
                  fontWeight: 600,
                  color: '#334155',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                {selectedStatus.icon(14)}
                <span>{selectedStatus.label}</span>
              </button>

              {activePopover === 'status' && (
                <div
                  style={{
                    position: 'absolute',
                    bottom: '120%',
                    left: 0,
                    zIndex: 100,
                    backgroundColor: '#FFFFFF',
                    borderRadius: 10,
                    boxShadow: '0 10px 20px -3px rgba(0,0,0,0.12)',
                    border: '1px solid #E2E8F0',
                    padding: 4,
                    minWidth: 150,
                  }}
                >
                  {STATUS_OPTIONS.map((st) => (
                    <div
                      key={st.id}
                      onClick={() => {
                        setStatus(st.id);
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
                        color: '#1E293B',
                        backgroundColor: status === st.id ? '#F1F5F9' : 'transparent',
                      }}
                    >
                      {st.icon(15)}
                      <span>{st.label}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Priority Pill */}
            <div style={{ position: 'relative' }}>
              <button
                type="button"
                onClick={() => setActivePopover(activePopover === 'priority' ? null : 'priority')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '5px 10px',
                  borderRadius: 20,
                  border: '1px solid #E2E8F0',
                  backgroundColor: '#FFFFFF',
                  fontSize: 12,
                  fontWeight: 600,
                  color: selectedPriority ? selectedPriority.color : '#64748B',
                  cursor: 'pointer',
                }}
              >
                <span>···</span>
                <span>{selectedPriority ? selectedPriority.label : 'Priority'}</span>
              </button>

              {activePopover === 'priority' && (
                <div
                  style={{
                    position: 'absolute',
                    bottom: '120%',
                    left: 0,
                    zIndex: 100,
                    backgroundColor: '#FFFFFF',
                    borderRadius: 10,
                    boxShadow: '0 10px 20px -3px rgba(0,0,0,0.12)',
                    border: '1px solid #E2E8F0',
                    padding: 4,
                    minWidth: 140,
                  }}
                >
                  {PRIORITY_OPTIONS.map((pr) => (
                    <div
                      key={pr.id}
                      onClick={() => {
                        setPriority(pr.id);
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
                        backgroundColor: priority === pr.id ? '#F1F5F9' : 'transparent',
                      }}
                    >
                      <span>···</span>
                      <span>{pr.label}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Assignee Pill */}
            <div style={{ position: 'relative' }}>
              <button
                type="button"
                onClick={() => setActivePopover(activePopover === 'assignee' ? null : 'assignee')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '5px 10px',
                  borderRadius: 20,
                  border: '1px solid #E2E8F0',
                  backgroundColor: selectedAssignee ? '#EFF6FF' : '#FFFFFF',
                  fontSize: 12,
                  fontWeight: 600,
                  color: selectedAssignee ? '#1D4ED8' : '#64748B',
                  cursor: 'pointer',
                }}
              >
                <User size={13} />
                <span>{selectedAssignee ? (selectedAssignee.fullName || selectedAssignee.username) : 'Assignee'}</span>
              </button>

              {activePopover === 'assignee' && (
                <div
                  style={{
                    position: 'absolute',
                    bottom: '120%',
                    left: 0,
                    zIndex: 100,
                    backgroundColor: '#FFFFFF',
                    borderRadius: 10,
                    boxShadow: '0 10px 20px -3px rgba(0,0,0,0.12)',
                    border: '1px solid #E2E8F0',
                    padding: 4,
                    minWidth: 180,
                    maxHeight: 200,
                    overflowY: 'auto',
                  }}
                >
                  <div
                    onClick={() => {
                      setAssigneeId('');
                      setActivePopover(null);
                    }}
                    style={{
                      padding: '7px 10px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 12,
                      color: '#64748B',
                    }}
                  >
                    Unassigned
                  </div>
                  {members.map((u) => {
                    const uId = u.id || u.userId;
                    const uName = u.fullName || u.username || u.email;
                    return (
                      <div
                        key={uId}
                        onClick={() => {
                          setAssigneeId(uId);
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
                          backgroundColor: assigneeId === uId ? '#F1F5F9' : 'transparent',
                        }}
                      >
                        <User size={13} color="#2563EB" />
                        <span>{uName}</span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Milestone Pill */}
            <div style={{ position: 'relative' }}>
              <button
                type="button"
                onClick={() => setActivePopover(activePopover === 'milestone' ? null : 'milestone')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '5px 10px',
                  borderRadius: 20,
                  border: '1px solid #E2E8F0',
                  backgroundColor: selectedMilestone ? '#F5F3FF' : '#FFFFFF',
                  fontSize: 12,
                  fontWeight: 600,
                  color: selectedMilestone ? '#7C3AED' : '#64748B',
                  cursor: 'pointer',
                }}
              >
                <Layers size={13} />
                <span>{selectedMilestone ? selectedMilestone.name : 'Milestone'}</span>
              </button>

              {activePopover === 'milestone' && (
                <div
                  style={{
                    position: 'absolute',
                    bottom: '120%',
                    left: 0,
                    zIndex: 100,
                    backgroundColor: '#FFFFFF',
                    borderRadius: 10,
                    boxShadow: '0 10px 20px -3px rgba(0,0,0,0.12)',
                    border: '1px solid #E2E8F0',
                    padding: 4,
                    minWidth: 180,
                  }}
                >
                  <div
                    onClick={() => {
                      setMilestoneId('');
                      setActivePopover(null);
                    }}
                    style={{
                      padding: '7px 10px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 12,
                      color: '#64748B',
                    }}
                  >
                    No milestone
                  </div>
                  {milestones.map((m) => (
                    <div
                      key={m.id}
                      onClick={() => {
                        setMilestoneId(m.id);
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
                        backgroundColor: milestoneId === m.id ? '#F1F5F9' : 'transparent',
                      }}
                    >
                      <Layers size={13} color="#7C3AED" />
                      <span>{m.name}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* More Menu Pill (···) (create_task_modal_2.png) */}
            <div style={{ position: 'relative' }}>
              <button
                type="button"
                onClick={() => setActivePopover(activePopover === 'more' ? null : 'more')}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 28,
                  height: 28,
                  borderRadius: 20,
                  border: '1px solid #E2E8F0',
                  backgroundColor: '#FFFFFF',
                  fontSize: 12,
                  fontWeight: 700,
                  color: '#64748B',
                  cursor: 'pointer',
                }}
              >
                ···
              </button>

              {activePopover === 'more' && (
                <div
                  style={{
                    position: 'absolute',
                    bottom: '120%',
                    left: 0,
                    zIndex: 100,
                    backgroundColor: '#FFFFFF',
                    borderRadius: 10,
                    boxShadow: '0 10px 20px -3px rgba(0,0,0,0.15)',
                    border: '1px solid #E2E8F0',
                    padding: 6,
                    minWidth: 200,
                  }}
                >
                  {/* Set due date option */}
                  <div
                    onClick={() => setActivePopover('dueDate')}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '8px 10px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 12,
                      color: '#1E293B',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Calendar size={14} color="#64748B" />
                      <span>Set due date</span>
                    </div>
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>⇧ D ›</span>
                  </div>

                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '8px 10px',
                      borderRadius: 6,
                      fontSize: 12,
                      color: '#64748B',
                    }}
                  >
                    <Repeat size={14} />
                    <span>Make recurring...</span>
                  </div>
                </div>
              )}

              {/* Due date sub-menu (create_task_modal_2.png) */}
              {activePopover === 'dueDate' && (
                <div
                  style={{
                    position: 'absolute',
                    bottom: '120%',
                    left: 0,
                    zIndex: 110,
                    backgroundColor: '#FFFFFF',
                    borderRadius: 10,
                    boxShadow: '0 10px 20px -3px rgba(0,0,0,0.15)',
                    border: '1px solid #E2E8F0',
                    padding: 8,
                    minWidth: 240,
                  }}
                >
                  <div style={{ fontSize: 11, color: '#94A3B8', padding: '4px 8px', marginBottom: 4 }}>
                    Try: 24h, 7 days, Feb 9
                  </div>

                  <div
                    onClick={() => {
                      const input = prompt('Nhập ngày hết hạn (YYYY-MM-DD):', dueDate);
                      if (input) setDueDate(input);
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
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <Calendar size={14} />
                    <span>Custom date...</span>
                  </div>

                  <div
                    onClick={() => setQuickDueDate('tomorrow')}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '7px 10px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 12,
                      color: '#1E293B',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Calendar size={14} />
                      <span>Tomorrow</span>
                    </div>
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>+1 day</span>
                  </div>

                  <div
                    onClick={() => setQuickDueDate('endOfWeek')}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '7px 10px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 12,
                      color: '#1E293B',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Calendar size={14} />
                      <span>End of this week</span>
                    </div>
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>Fri</span>
                  </div>

                  <div
                    onClick={() => setQuickDueDate('nextWeek')}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '7px 10px',
                      borderRadius: 6,
                      cursor: 'pointer',
                      fontSize: 12,
                      color: '#1E293B',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Calendar size={14} />
                      <span>In one week</span>
                    </div>
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>+7 days</span>
                  </div>
                </div>
              )}
            </div>

            {/* Display Due Date badge if set */}
            {dueDate && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  padding: '4px 8px',
                  borderRadius: 14,
                  backgroundColor: '#FEF3C7',
                  color: '#92400E',
                  fontSize: 11,
                  fontWeight: 600,
                }}
              >
                <Calendar size={12} />
                <span>Due: {dueDate}</span>
                <X
                  size={12}
                  style={{ cursor: 'pointer', marginLeft: 2 }}
                  onClick={() => setDueDate('')}
                />
              </div>
            )}
          </div>

          {/* ─── Bottom Actions Bar (create_task_modal.png) ─── */}
          <div
            style={{
              marginTop: 20,
              paddingTop: 12,
              borderTop: '1px solid #F1F5F9',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            {/* Attachment Button */}
            <button
              type="button"
              title="Đính kèm tệp tin"
              style={{
                background: 'none',
                border: 'none',
                color: '#94A3B8',
                cursor: 'pointer',
                padding: 6,
                borderRadius: 6,
                display: 'flex',
                alignItems: 'center',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <Paperclip size={16} />
            </button>

            {/* Create more toggle + Submit Button */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  fontSize: 13,
                  color: '#64748B',
                  cursor: 'pointer',
                  userSelect: 'none',
                }}
              >
                <input
                  type="checkbox"
                  checked={createMore}
                  onChange={(e) => setCreateMore(e.target.checked)}
                  style={{ cursor: 'pointer' }}
                />
                <span>Create more</span>
              </label>

              <button
                type="submit"
                disabled={!title.trim() || loading}
                style={{
                  padding: '7px 18px',
                  borderRadius: 8,
                  border: 'none',
                  backgroundColor: title.trim() ? '#5E6AD2' : '#E2E8F0',
                  color: title.trim() ? '#FFFFFF' : '#94A3B8',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: title.trim() ? 'pointer' : 'default',
                  transition: 'all 0.15s ease',
                  boxShadow: title.trim() ? '0 1px 2px rgba(94, 106, 210, 0.2)' : 'none',
                }}
              >
                {loading ? 'Creating...' : 'Create task'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
