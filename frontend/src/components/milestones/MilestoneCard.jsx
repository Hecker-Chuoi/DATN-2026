import React from 'react';
import { Calendar, CheckCircle2, Clock, MoreVertical, Edit2, Trash2, Layers } from 'lucide-react';

export default function MilestoneCard({ milestone, onEdit, onDelete, onSelectMilestone }) {
  const getStatusBadge = (status) => {
    switch (status) {
      case 'CLOSED':
        return { bg: '#ECFDF5', text: '#059669', border: '#A7F3D0', label: 'Đã hoàn thành' };
      case 'IN_PROGRESS':
        return { bg: '#EFF6FF', text: '#2563EB', border: '#BFDBFE', label: 'Đang triển khai' };
      case 'OPEN':
      default:
        return { bg: '#F8FAFC', text: '#64748B', border: '#E2E8F0', label: 'Đã lên kế hoạch' };
    }
  };

  const statusStyle = getStatusBadge(milestone.status);
  const percent = milestone.progressPercentage || 0;

  const formatDate = (dateStr) => {
    if (!dateStr) return 'Chưa đặt ngày';
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    } catch {
      return dateStr;
    }
  };

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        borderRadius: 12,
        border: '1px solid #E2E8F0',
        padding: '18px 20px',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
        transition: 'all 0.15s ease',
      }}
      onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#BFDBFE')}
      onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#E2E8F0')}
    >
      <div>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 8 }}>
          <div style={{ flex: 1, paddingRight: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              <h3 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                {milestone.name}
              </h3>
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 600,
                  padding: '2px 8px',
                  borderRadius: 10,
                  backgroundColor: statusStyle.bg,
                  color: statusStyle.text,
                  border: `1px solid ${statusStyle.border}`,
                }}
              >
                {statusStyle.label}
              </span>
            </div>
            <p style={{ fontSize: 13, color: '#64748B', margin: 0, lineHeight: 1.4 }}>
              {milestone.description || 'Chưa có mô tả chi tiết cho cột mốc này.'}
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <button
              onClick={() => onEdit(milestone)}
              title="Sửa cột mốc"
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
              <Edit2 size={14} />
            </button>
            <button
              onClick={() => onDelete(milestone.id)}
              title="Xóa cột mốc"
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

        {/* Progress Section */}
        <div style={{ marginTop: 14, marginBottom: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', fontSize: 12, marginBottom: 6 }}>
            <span style={{ color: '#64748B', fontWeight: 500 }}>Tiến độ hoàn thành</span>
            <span style={{ fontWeight: 700, color: percent === 100 ? '#059669' : '#2563EB' }}>
              {percent}%
            </span>
          </div>

          <div
            style={{
              width: '100%',
              height: 7,
              borderRadius: 4,
              backgroundColor: '#F1F5F9',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                height: '100%',
                width: `${percent}%`,
                background:
                  percent === 100
                    ? 'linear-gradient(90deg, #10B981 0%, #059669 100%)'
                    : 'linear-gradient(90deg, #3B82F6 0%, #2563EB 100%)',
                borderRadius: 4,
                transition: 'width 0.4s ease',
              }}
            />
          </div>
        </div>
      </div>

      {/* Footer */}
      <div
        style={{
          borderTop: '1px solid #F1F5F9',
          paddingTop: 10,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          fontSize: 12,
          color: '#64748B',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <Layers size={13} color="#94A3B8" />
          <span>
            <b>{milestone.completedTasks || 0}</b> / {milestone.totalTasks || 0} tác vụ
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <Calendar size={13} color="#94A3B8" />
          <span>Hạn chót: {formatDate(milestone.dueDate)}</span>
        </div>
      </div>
    </div>
  );
}
