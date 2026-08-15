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
  Smile
} from 'lucide-react';

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
}) {
  const [hiddenCols, setHiddenCols] = useState({});

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

  const formatDate = (dateStr) => {
    if (!dateStr) return null;
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('vi-VN', { month: 'short', year: 'numeric' });
    } catch {
      return dateStr;
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

                  return (
                    <div
                      key={task.id}
                      style={{
                        backgroundColor: '#FFFFFF',
                        borderRadius: 10,
                        border: '1px solid #E2E8F0',
                        padding: '14px',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
                        transition: 'all 0.15s ease',
                        cursor: 'pointer',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.borderColor = '#BFDBFE';
                        e.currentTarget.style.boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.05)';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.borderColor = '#E2E8F0';
                        e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.03)';
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

                      {/* Title & Status Icon */}
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 7, marginBottom: 12 }}>
                        <div style={{ marginTop: 2, flexShrink: 0 }}>
                          {task.status === 'DONE' ? (
                            <CheckCircle2 size={15} color="#3B82F6" />
                          ) : task.status === 'CANCELLED' ? (
                            <XCircle size={15} color="#94A3B8" />
                          ) : task.status === 'IN_PROGRESS' ? (
                            <Clock size={15} color="#EAB308" />
                          ) : (
                            <Circle size={15} color="#CBD5E1" />
                          )}
                        </div>

                        <span
                          style={{
                            fontSize: 13,
                            fontWeight: 600,
                            color: task.status === 'DONE' ? '#94A3B8' : '#0F172A',
                            textDecoration: task.status === 'DONE' ? 'line-through' : 'none',
                            lineHeight: 1.4,
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
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                          {/* Priority badge */}
                          <span
                            style={{
                              fontSize: 10,
                              fontWeight: 600,
                              color: pStyle.color,
                              backgroundColor: pStyle.bg,
                              border: `1px solid ${pStyle.border}`,
                              padding: '1px 5px',
                              borderRadius: 4,
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
                                padding: '1px 6px',
                                borderRadius: 4,
                                color: '#475569',
                                fontWeight: 500,
                              }}
                            >
                              <Layers size={11} color="#64748B" />
                              {task.completedSubtaskCount}/{task.subtaskCount}
                            </span>
                          )}

                          {/* Quick Status Mover Dropdown */}
                          <select
                            value={task.status}
                            onChange={(e) => onStatusChange(task.id, e.target.value)}
                            onClick={(e) => e.stopPropagation()}
                            style={{
                              fontSize: 10,
                              fontWeight: 600,
                              padding: '1px 4px',
                              borderRadius: 4,
                              border: '1px solid #E2E8F0',
                              backgroundColor: '#FFFFFF',
                              color: '#64748B',
                              outline: 'none',
                              cursor: 'pointer',
                            }}
                          >
                            <option value="TODO">TODO</option>
                            <option value="IN_PROGRESS">IN_PROGRESS</option>
                            <option value="DONE">DONE</option>
                            <option value="CANCELLED">CANCELLED</option>
                          </select>
                        </div>

                        {/* Created / Due Date */}
                        <span style={{ fontSize: 10, color: '#94A3B8', flexShrink: 0 }}>
                          {task.createdAt ? formatDate(task.createdAt) : ''}
                        </span>
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
    </div>
  );
}
