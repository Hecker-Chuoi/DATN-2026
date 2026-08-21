import React from 'react';
import {
  CheckSquare,
  Bug,
  Bookmark,
  Zap,
  ChevronRight,
  ChevronDown,
  Clock,
  User,
  Plus,
  Trash2,
  Calendar,
  AlertTriangle
} from 'lucide-react';

export default function TaskRowItem({
  task,
  onStatusChange,
  onDelete,
  onAddSubtask,
  onTaskClick,
  isExpanded,
  onToggleExpand,
  level = 0,
}) {
  const getTypeIcon = (type) => {
    switch (type) {
      case 'BUG':
        return <Bug size={14} color="#EF4444" title="Lỗi (Bug)" />;
      case 'STORY':
        return <Bookmark size={14} color="#10B981" title="User Story" />;
      case 'EPIC':
        return <Zap size={14} color="#8B5CF6" title="Epic" />;
      case 'TASK':
      default:
        return <CheckSquare size={14} color="#3B82F6" title="Task" />;
    }
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

  const getStatusColor = (status) => {
    switch (status) {
      case 'DONE':
        return { bg: '#ECFDF5', text: '#059669', border: '#A7F3D0' };
      case 'IN_PROGRESS':
        return { bg: '#EFF6FF', text: '#2563EB', border: '#BFDBFE' };
      case 'CANCELLED':
        return { bg: '#FEF2F2', text: '#DC2626', border: '#FECACA' };
      case 'TODO':
      default:
        return { bg: '#F8FAFC', text: '#64748B', border: '#CBD5E1' };
    }
  };

  const pStyle = getPriorityStyle(task.priority);
  const sStyle = getStatusColor(task.status);

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '10px 16px',
        paddingLeft: level > 0 ? `${16 + level * 28}px` : '16px',
        backgroundColor: level > 0 ? '#F8FAFC' : '#FFFFFF',
        borderBottom: '1px solid #F1F5F9',
        fontSize: 13,
        transition: 'background-color 0.15s ease',
      }}
      onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = level > 0 ? '#F1F5F9' : '#F8FAFC')}
      onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = level > 0 ? '#F8FAFC' : '#FFFFFF')}
    >
      {/* ─── Left: Expand button, Type, Code, Title ─── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1, minWidth: 0, paddingRight: 16 }}>
        {/* Toggle subtasks if any */}
        {task.subtaskCount > 0 ? (
          <button
            onClick={() => onToggleExpand && onToggleExpand(task.id)}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: '#64748B',
              padding: 2,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            {isExpanded ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
          </button>
        ) : (
          <div style={{ width: 19 }} />
        )}

        {/* Task Type Icon */}
        <div style={{ display: 'flex', alignItems: 'center' }}>{getTypeIcon(task.type)}</div>

        {/* Task Code */}
        <span
          onClick={() => onTaskClick && onTaskClick(task.id)}
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: '#2563EB',
            backgroundColor: '#EFF6FF',
            padding: '2px 6px',
            borderRadius: 4,
            border: '1px solid #DBEAFE',
            flexShrink: 0,
            cursor: 'pointer',
          }}
        >
          {task.code}
        </span>

        {/* Task Title */}
        <span
          onClick={() => onTaskClick && onTaskClick(task.id)}
          style={{
            color: task.status === 'DONE' ? '#94A3B8' : '#0F172A',
            fontWeight: 500,
            textDecoration: task.status === 'DONE' ? 'line-through' : 'none',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            cursor: 'pointer',
          }}
        >
          {task.title}
        </span>

        {/* Subtask count badge */}
        {task.subtaskCount > 0 && (
          <span
            style={{
              fontSize: 11,
              color: '#64748B',
              backgroundColor: '#F1F5F9',
              padding: '1px 6px',
              borderRadius: 10,
              flexShrink: 0,
            }}
          >
            {task.completedSubtaskCount}/{task.subtaskCount} subtasks
          </span>
        )}
      </div>

      {/* ─── Right: Milestone, Priority, Status Dropdown, Estimate, Assignee, Actions ─── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0 }}>
        {/* Milestone Pill */}
        {task.milestoneName && (
          <span
            style={{
              fontSize: 11,
              color: '#475569',
              backgroundColor: '#F1F5F9',
              border: '1px solid #E2E8F0',
              padding: '2px 8px',
              borderRadius: 4,
              maxWidth: 140,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {task.milestoneName}
          </span>
        )}

        {/* Priority Badge */}
        <span
          style={{
            fontSize: 11,
            fontWeight: 600,
            color: pStyle.color,
            backgroundColor: pStyle.bg,
            border: `1px solid ${pStyle.border}`,
            padding: '2px 6px',
            borderRadius: 4,
          }}
        >
          {task.priority}
        </span>

        {/* Status Dropdown */}
        <select
          value={task.status}
          onChange={(e) => onStatusChange(task.id, e.target.value)}
          style={{
            fontSize: 12,
            fontWeight: 600,
            padding: '3px 8px',
            borderRadius: 6,
            backgroundColor: sStyle.bg,
            color: sStyle.text,
            border: `1px solid ${sStyle.border}`,
            outline: 'none',
            cursor: 'pointer',
          }}
        >
          <option value="TODO">TODO</option>
          <option value="IN_PROGRESS">IN_PROGRESS</option>
          <option value="DONE">DONE</option>
          <option value="CANCELLED">CANCELLED</option>
        </select>

        {/* Estimate */}
        {task.estimate != null && (
          <span style={{ display: 'flex', alignItems: 'center', gap: 3, fontSize: 12, color: '#64748B' }}>
            <Clock size={12} />
            <span>{task.estimate}h</span>
          </span>
        )}

        {/* Due Date Badge */}
        {task.dueDate && (() => {
          const isOverdue = new Date(task.dueDate) < new Date() && task.status !== 'DONE';
          const isOverMilestone = task.milestoneDueDate && new Date(task.dueDate) > new Date(task.milestoneDueDate) && task.status !== 'DONE';
          const badgeColor = isOverdue ? '#DC2626' : isOverMilestone ? '#D97706' : '#2563EB';
          const badgeBg = isOverdue ? '#FEF2F2' : isOverMilestone ? '#FFFBEB' : '#EFF6FF';
          const badgeBorder = isOverMilestone ? '1px solid #FDE68A' : 'none';
          const title = isOverMilestone
            ? `⚠️ Hạn chót (${new Date(task.dueDate).toLocaleDateString('vi-VN')}) trễ hơn Cột mốc (${new Date(task.milestoneDueDate).toLocaleDateString('vi-VN')})`
            : `Hạn chót: ${new Date(task.dueDate).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })}`;

          return (
            <span
              title={title}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                fontSize: 11,
                fontWeight: 600,
                color: badgeColor,
                backgroundColor: badgeBg,
                border: badgeBorder,
                padding: '2px 6px',
                borderRadius: 4,
              }}
            >
              {isOverMilestone ? <AlertTriangle size={11} color="#D97706" /> : <Calendar size={11} />}
              {new Date(task.dueDate).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })}
            </span>
          );
        })()}

        {/* Assignee Avatar */}
        <div
          title={task.assigneeName || task.assigneeUsername || 'Chưa gán người thực hiện'}
          style={{
            width: 26,
            height: 26,
            borderRadius: '50%',
            backgroundColor: task.assigneeName ? '#DBEAFE' : '#F1F5F9',
            color: task.assigneeName ? '#1E40AF' : '#94A3B8',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 11,
            fontWeight: 700,
          }}
        >
          {task.assigneeName ? task.assigneeName.charAt(0).toUpperCase() : <User size={13} />}
        </div>

        {/* Add Subtask Button */}
        {level === 0 && (
          <button
            onClick={() => onAddSubtask && onAddSubtask(task)}
            title="Thêm tác vụ con (Subtask)"
            style={{
              background: 'none',
              border: 'none',
              color: '#94A3B8',
              cursor: 'pointer',
              padding: 4,
              borderRadius: 4,
            }}
            onMouseEnter={(e) => (e.currentTarget.style.color = '#2563EB')}
            onMouseLeave={(e) => (e.currentTarget.style.color = '#94A3B8')}
          >
            <Plus size={15} />
          </button>
        )}

        {/* Delete button */}
        <button
          onClick={() => onDelete(task.id)}
          title="Xóa tác vụ"
          style={{
            background: 'none',
            border: 'none',
            color: '#94A3B8',
            cursor: 'pointer',
            padding: 4,
            borderRadius: 4,
          }}
          onMouseEnter={(e) => (e.currentTarget.style.color = '#DC2626')}
          onMouseLeave={(e) => (e.currentTarget.style.color = '#94A3B8')}
        >
          <Trash2 size={14} />
        </button>
      </div>
    </div>
  );
}
