import React, { useState } from 'react';
import {
  CheckCircle2,
  Circle,
  Clock,
  XCircle,
  MoreHorizontal,
  Plus,
  Bookmark,
  Bug,
  CheckSquare,
  Zap,
  ChevronDown,
  ChevronRight,
  User,
  Layers,
  Calendar,
  AlertTriangle,
  AlertCircle,
  Flame,
  Smile
} from 'lucide-react';
import ChangeTaskStatusModal from '../modals/ChangeTaskStatusModal.jsx';

const COLUMNS = [
  {
    id: 'TODO',
    name: 'Todo',
    icon: (size = 14) => <Circle size={size} color="#64748B" />,
    badgeBg: '#F1F5F9',
    badgeText: '#475569',
  },
  {
    id: 'IN_PROGRESS',
    name: 'In Progress',
    icon: (size = 14) => <Clock size={size} color="#EAB308" />,
    badgeBg: '#FEF9C3',
    badgeText: '#854D0E',
  },
  {
    id: 'DONE',
    name: 'Done',
    icon: (size = 14) => <CheckCircle2 size={size} color="#3B82F6" />,
    badgeBg: '#EFF6FF',
    badgeText: '#1D4ED8',
  },
  {
    id: 'CANCELLED',
    name: 'Canceled',
    icon: (size = 14) => <XCircle size={size} color="#94A3B8" />,
    badgeBg: '#F8FAFC',
    badgeText: '#64748B',
  },
];

export default function TaskBoardView({
  tasks,
  onStatusChange,
  onOpenCreateTask,
  onDeleteTask,
  onTaskClick,
}) {
  const [hiddenCols, setHiddenCols] = useState({});
  const [statusModalTask, setStatusModalTask] = useState(null);

  const toggleHideColumn = (colId) => {
    setHiddenCols((prev) => ({ ...prev, [colId]: !prev[colId] }));
  };

  const getPriorityStyle = (priority) => {
    switch (priority) {
      case 'URGENT':
        return { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA' };
      case 'HIGH':
        return { color: '#EA580C', bg: '#FFF7ED', border: '#FFEDD5' };
      case 'MEDIUM':
        return { color: '#2563EB', bg: '#EFF6FF', border: '#DBEAFE' };
      case 'LOW':
      default:
        return { color: '#64748B', bg: '#F8FAFC', border: '#E2E8F0' };
    }
  };

  const getTaskUrgency = (task) => {
    if (!task.dueDate) return { type: 'NONE' };

    // Chỉ áp dụng cảnh báo background cho TODO và IN_PROGRESS
    if (task.status === 'DONE' || task.status === 'CANCELLED') {
      const d = new Date(task.dueDate);
      return {
        type: 'COMPLETED',
        text: d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }),
        cardBg: '#FFFFFF',
        cardBorder: '#E2E8F0',
        badgeColor: '#64748B',
        badgeBg: '#F1F5F9',
        badgeBorder: '#E2E8F0',
        title: `Hạn chót: ${d.toLocaleDateString('vi-VN')}`,
      };
    }

    try {
      const due = new Date(task.dueDate);
      const now = new Date();
      const diffHours = (due.getTime() - now.getTime()) / (1000 * 60 * 60);
      const formatted = due.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });

      const isOverMilestone = task.milestoneDueDate && due > new Date(task.milestoneDueDate);

      if (diffHours < 0) {
        // 1. ĐÃ HẾT HẠN (Overdue) -> Background đỏ hồng rất nhẹ
        return {
          type: 'OVERDUE',
          text: formatted,
          cardBg: '#FFF1F2',
          cardBorder: '#FDA4AF',
          cardShadow: '0 2px 6px rgba(225, 29, 72, 0.08)',
          badgeColor: '#BE123C',
          badgeBg: '#FFE4E6',
          badgeBorder: '#FDA4AF',
          title: `⚠️ Đã hết hạn: ${formatted}`,
          isOverdue: true,
        };
      } else if (isOverMilestone) {
        // 2. VƯỢT HẠN CỘT MỐC (Over Milestone)
        return {
          type: 'OVER_MILESTONE',
          text: formatted,
          cardBg: '#FFFBEB',
          cardBorder: '#FDE68A',
          cardShadow: '0 2px 6px rgba(217, 119, 6, 0.08)',
          badgeColor: '#B45309',
          badgeBg: '#FEF3C7',
          badgeBorder: '#FCD34D',
          title: `⚠️ Hạn chót (${formatted}) trễ hơn Cột mốc (${new Date(task.milestoneDueDate).toLocaleDateString('vi-VN')})!`,
          isOverMilestone: true,
        };
      } else if (diffHours <= 48) {
        // 3. SẮP HẾT HẠN (trong 48h) -> Background cam cảnh báo nhẹ, icon warning animation
        return {
          type: 'DUE_SOON',
          text: formatted,
          cardBg: '#FFFBEB',
          cardBorder: '#FCD34D',
          cardShadow: '0 3px 8px rgba(217, 119, 6, 0.12)',
          badgeColor: '#B45309',
          badgeBg: '#FEF3C7',
          badgeBorder: '#FCD34D',
          title: `🔥 CẢNH BÁO: Sắp hết hạn trong ${Math.round(diffHours)}h (${formatted})!`,
          isDueSoon: true,
          hoursLeft: Math.round(diffHours),
        };
      } else {
        // 4. CÒN HẠN BÌNH THƯỜNG (> 48h)
        return {
          type: 'ON_TRACK',
          text: formatted,
          cardBg: '#FFFFFF',
          cardBorder: '#E2E8F0',
          cardShadow: '0 1px 3px rgba(0, 0, 0, 0.03)',
          badgeColor: '#16A34A',
          badgeBg: '#F0FDF4',
          badgeBorder: '#DCFCE7',
          title: `Hạn chót: ${formatted}`,
          isOnTrack: true,
        };
      }
    } catch {
      return { type: 'NONE' };
    }
  };

  const visibleColumns = COLUMNS.filter((col) => !hiddenCols[col.id]);
  const hiddenColumnsList = COLUMNS.filter((col) => !!hiddenCols[col.id]);

  return (
    <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start', overflowX: 'auto', paddingBottom: 24 }}>
      {/* ─── Visible Columns ─── */}
      {visibleColumns.map((col) => {
        const colTasks = tasks.filter((t) => (t.status || 'TODO') === col.id);

        return (
          <div
            key={col.id}
            style={{
              width: 320,
              minWidth: 300,
              flexShrink: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
            }}
          >
            {/* Column Header */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '4px 6px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {col.icon(16)}
                <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
                  {col.name}
                </span>
                <span
                  style={{
                    fontSize: 12,
                    fontWeight: 600,
                    color: '#64748B',
                    padding: '1px 7px',
                    borderRadius: 10,
                    backgroundColor: '#F1F5F9',
                  }}
                >
                  {colTasks.length}
                </span>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                <button
                  onClick={() => toggleHideColumn(col.id)}
                  title="Ẩn cột này"
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#94A3B8',
                    cursor: 'pointer',
                    padding: 3,
                    borderRadius: 4,
                  }}
                >
                  <MoreHorizontal size={15} />
                </button>
                <button
                  onClick={() => onOpenCreateTask && onOpenCreateTask(col.id)}
                  title="Thêm tác vụ vào cột này"
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#94A3B8',
                    cursor: 'pointer',
                    padding: 3,
                    borderRadius: 4,
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.color = '#2563EB')}
                  onMouseLeave={(e) => (e.currentTarget.style.color = '#94A3B8')}
                >
                  <Plus size={16} />
                </button>
              </div>
            </div>

            {/* Cards List */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {colTasks.length === 0 ? (
                <div
                  style={{
                    padding: '24px 16px',
                    textAlign: 'center',
                    border: '1px dashed #E2E8F0',
                    borderRadius: 10,
                    backgroundColor: '#F8FAFC',
                    color: '#94A3B8',
                    fontSize: 12,
                  }}
                >
                  Chưa có thẻ nào trong cột
                </div>
              ) : (
                colTasks.map((task) => {
                  const pStyle = getPriorityStyle(task.priority);
                  const urgency = getTaskUrgency(task);

                  return (
                    <div
                      key={task.id}
                      onClick={() => onTaskClick && onTaskClick(task.id)}
                      style={{
                        backgroundColor: urgency.cardBg || '#FFFFFF',
                        borderRadius: 10,
                        border: `1px solid ${urgency.cardBorder || '#E2E8F0'}`,
                        padding: '14px',
                        boxShadow: urgency.cardShadow || '0 1px 3px rgba(0,0,0,0.03)',
                        transition: 'all 0.15s ease',
                        cursor: 'pointer',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.borderColor = urgency.isDueSoon ? '#F59E0B' : urgency.isOverdue ? '#F43F5E' : '#BFDBFE';
                        e.currentTarget.style.boxShadow = urgency.isDueSoon ? '0 4px 12px rgba(217, 119, 6, 0.2)' : '0 4px 6px -1px rgba(0, 0, 0, 0.05)';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.borderColor = urgency.cardBorder || '#E2E8F0';
                        e.currentTarget.style.boxShadow = urgency.cardShadow || '0 1px 3px rgba(0,0,0,0.03)';
                      }}
                    >
                      {/* Top: Code breadcrumb & Assignee Avatar */}
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          marginBottom: 8,
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 5, overflow: 'hidden' }}>
                          <span
                            title={task.code || ''}
                            style={{
                              fontSize: 11,
                              fontWeight: 700,
                              color: '#475569',
                              backgroundColor: '#F1F5F9',
                              padding: '1px 6px',
                              borderRadius: 4,
                              flexShrink: 0,
                              maxWidth: 80,
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              display: 'inline-block',
                            }}
                          >
                            {task.code || `#${task.id || ''}`}
                          </span>
                          {task.milestoneName && (
                            <>
                              <span style={{ fontSize: 11, color: '#CBD5E1' }}>›</span>
                              <span
                                style={{
                                  fontSize: 11,
                                  color: '#64748B',
                                  whiteSpace: 'nowrap',
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  maxWidth: 140,
                                }}
                              >
                                {task.milestoneName}
                              </span>
                            </>
                          )}
                        </div>

                        {/* Assignee Avatar */}
                        <div
                          title={task.assigneeName || task.assigneeUsername || 'Chưa gán'}
                          style={{
                            width: 22,
                            height: 22,
                            borderRadius: '50%',
                            backgroundColor: task.assigneeName ? '#DBEAFE' : '#F1F5F9',
                            color: task.assigneeName ? '#1E40AF' : '#94A3B8',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: 10,
                            fontWeight: 700,
                            flexShrink: 0,
                          }}
                        >
                          {task.assigneeName ? task.assigneeName.charAt(0).toUpperCase() : <User size={11} />}
                        </div>
                      </div>

                      {/* Title & Status Icon (Nút tương tác mở modal chuyển trạng thái - Không chiếm diện tích & không đẩy rớt dòng) */}
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 7, marginBottom: 12 }}>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setStatusModalTask(task);
                          }}
                          title={`Trạng thái: ${task.status || 'TODO'} (Bấm để chuyển trạng thái)`}
                          style={{
                            marginTop: 1,
                            flexShrink: 0,
                            background: 'none',
                            border: 'none',
                            padding: 2,
                            borderRadius: '50%',
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: 'pointer',
                            transition: 'all 0.15s ease',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.backgroundColor = '#E2E8F0';
                            e.currentTarget.style.transform = 'scale(1.15)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.backgroundColor = 'transparent';
                            e.currentTarget.style.transform = 'scale(1)';
                          }}
                        >
                          {task.status === 'DONE' ? (
                            <CheckCircle2 size={16} color="#3B82F6" />
                          ) : task.status === 'CANCELLED' ? (
                            <XCircle size={16} color="#94A3B8" />
                          ) : task.status === 'IN_PROGRESS' ? (
                            <Clock size={16} color="#EAB308" />
                          ) : (
                            <Circle size={16} color="#94A3B8" />
                          )}
                        </button>

                        <span
                          style={{
                            fontSize: 13,
                            fontWeight: 600,
                            color: task.status === 'DONE' ? '#94A3B8' : '#0F172A',
                            textDecoration: task.status === 'DONE' ? 'line-through' : 'none',
                            lineHeight: 1.4,
                            flex: 1,
                            wordBreak: 'break-word',
                          }}
                        >
                          {task.title}
                        </span>
                      </div>

                      {/* Bottom Pills (Badges & Dates) */}
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          gap: 6,
                          fontSize: 11,
                          color: '#64748B',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'nowrap', minWidth: 0 }}>
                          {/* Priority badge */}
                          <span
                            style={{
                              fontSize: 10,
                              fontWeight: 600,
                              color: pStyle.color,
                              backgroundColor: pStyle.bg,
                              border: `1px solid ${pStyle.border}`,
                              padding: '2px 6px',
                              borderRadius: 4,
                              flexShrink: 0,
                            }}
                          >
                            {task.priority}
                          </span>

                          {/* Subtasks Count Badge */}
                          {task.subtaskCount > 0 && (
                            <span
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 3,
                                backgroundColor: '#F8FAFC',
                                border: '1px solid #E2E8F0',
                                padding: '2px 6px',
                                borderRadius: 4,
                                color: '#475569',
                                fontWeight: 500,
                                fontSize: 10,
                                flexShrink: 0,
                              }}
                            >
                              <Layers size={11} color="#64748B" />
                              {task.completedSubtaskCount}/{task.subtaskCount}
                            </span>
                          )}
                        </div>

                        {/* Created / Due Date */}
                        {(() => {
                          if (urgency.type === 'NONE') return null;
                          return (
                            <span
                              title={urgency.title}
                              style={{
                                fontSize: 10,
                                color: urgency.badgeColor,
                                backgroundColor: urgency.badgeBg,
                                border: `1px solid ${urgency.badgeBorder}`,
                                padding: '2px 6px',
                                borderRadius: 4,
                                fontWeight: 600,
                                display: 'flex',
                                alignItems: 'center',
                                gap: 4,
                                flexShrink: 0,
                              }}
                            >
                              {urgency.isDueSoon ? (
                                <span className="animate-pulse" style={{ display: 'inline-flex', alignItems: 'center' }}>
                                  <Flame size={11} color="#D97706" />
                                </span>
                              ) : urgency.isOverdue ? (
                                <AlertCircle size={10} color="#BE123C" />
                              ) : urgency.isOverMilestone ? (
                                <AlertTriangle size={10} color="#D97706" />
                              ) : (
                                <Calendar size={10} />
                              )}
                              <span>{urgency.text}</span>
                            </span>
                          );
                        })()}
                      </div>

                    </div>
                  );
                })
              )}
            </div>
          </div>
        );
      })}

      {/* ─── Hidden Columns Sidebar (Phần thu gọn bên phải như ui-1.png) ─── */}
      {hiddenColumnsList.length > 0 && (
        <div
          style={{
            width: 240,
            flexShrink: 0,
            backgroundColor: '#FAFAFA',
            border: '1px dashed #E2E8F0',
            borderRadius: 12,
            padding: '14px',
          }}
        >
          <div
            style={{
              fontSize: 12,
              fontWeight: 700,
              color: '#64748B',
              marginBottom: 10,
              display: 'flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <ChevronDown size={14} />
            <span>Hidden columns ({hiddenColumnsList.length})</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {hiddenColumnsList.map((col) => {
              const count = tasks.filter((t) => (t.status || 'TODO') === col.id).length;
              return (
                <div
                  key={col.id}
                  onClick={() => toggleHideColumn(col.id)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '7px 10px',
                    borderRadius: 6,
                    backgroundColor: '#FFFFFF',
                    border: '1px solid #E2E8F0',
                    fontSize: 12,
                    color: '#334155',
                    cursor: 'pointer',
                    boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#93C5FD')}
                  onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#E2E8F0')}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    {col.icon(14)}
                    <span style={{ fontWeight: 600 }}>{col.name}</span>
                  </div>
                  <span style={{ fontSize: 11, color: '#94A3B8' }}>{count}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Modal Chuyển trạng thái tác vụ */}
      <ChangeTaskStatusModal
        task={statusModalTask}
        isOpen={Boolean(statusModalTask)}
        onClose={() => setStatusModalTask(null)}
        onStatusChange={onStatusChange}
      />
    </div>
  );
}
