import React, { useState } from 'react';
import {
  X,
  Circle,
  Clock,
  CheckCircle2,
  XCircle,
  Check,
  ArrowRight,
  Sparkles
} from 'lucide-react';

const STATUS_CONFIGS = [
  {
    id: 'TODO',
    label: 'Todo',
    subLabel: 'Việc cần làm',
    desc: 'Tác vụ đã lên kế hoạch và đang chờ thực hiện',
    icon: (s = 18) => <Circle size={s} color="#64748B" />,
    color: '#64748B',
    bgColor: '#F1F5F9',
    borderColor: '#CBD5E1',
    activeBg: '#F8FAFC',
    activeBorder: '#94A3B8',
  },
  {
    id: 'IN_PROGRESS',
    label: 'In Progress',
    subLabel: 'Đang tiến hành',
    desc: 'Tác vụ đang được các thành viên xử lý',
    icon: (s = 18) => <Clock size={s} color="#D97706" />,
    color: '#D97706',
    bgColor: '#FEF3C7',
    borderColor: '#FCD34D',
    activeBg: '#FFFBEB',
    activeBorder: '#F59E0B',
  },
  {
    id: 'DONE',
    label: 'Done',
    subLabel: 'Đã hoàn thành',
    desc: 'Tác vụ đã hoàn tất và được nghiệm thu',
    icon: (s = 18) => <CheckCircle2 size={s} color="#2563EB" />,
    color: '#2563EB',
    bgColor: '#DBEAFE',
    borderColor: '#93C5FD',
    activeBg: '#EFF6FF',
    activeBorder: '#3B82F6',
  },
  {
    id: 'CANCELLED',
    label: 'Canceled',
    subLabel: 'Đã hủy',
    desc: 'Tác vụ đã bị hủy bỏ hoặc không cần làm nữa',
    icon: (s = 18) => <XCircle size={s} color="#94A3B8" />,
    color: '#64748B',
    bgColor: '#F1F5F9',
    borderColor: '#E2E8F0',
    activeBg: '#F8FAFC',
    activeBorder: '#CBD5E1',
  },
];

export default function ChangeTaskStatusModal({
  task,
  isOpen,
  onClose,
  onStatusChange,
}) {
  const [selectedStatus, setSelectedStatus] = useState(task?.status || 'TODO');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Sync selected status when task changes
  React.useEffect(() => {
    if (task) {
      setSelectedStatus(task.status || 'TODO');
    }
  }, [task]);

  if (!isOpen || !task) return null;

  const currentStatusConfig = STATUS_CONFIGS.find((s) => s.id === (task.status || 'TODO')) || STATUS_CONFIGS[0];

  const handleSelectAndApply = async (newStatusId) => {
    setSelectedStatus(newStatusId);
    if (newStatusId === task.status) {
      onClose();
      return;
    }
    setIsSubmitting(true);
    try {
      if (onStatusChange) {
        await onStatusChange(task.id, newStatusId);
      }
      onClose();
    } catch (err) {
      console.error('Lỗi khi cập nhật trạng thái:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.5)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: 16,
        animation: 'fadeIn 0.15s ease',
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 480,
          backgroundColor: '#FFFFFF',
          borderRadius: 14,
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
          border: '1px solid #E2E8F0',
          overflow: 'hidden',
          animation: 'scaleUp 0.18s ease-out',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '16px 20px',
            borderBottom: '1px solid #F1F5F9',
            backgroundColor: '#FAFAFA',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                backgroundColor: currentStatusConfig.bgColor,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {currentStatusConfig.icon(18)}
            </div>
            <div>
              <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: '#0F172A' }}>
                Chuyển trạng thái tác vụ
              </h3>
              <p style={{ margin: 0, fontSize: 12, color: '#64748B' }}>
                Cập nhật tiến độ trực tiếp cho tác vụ
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              color: '#94A3B8',
              cursor: 'pointer',
              padding: 6,
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'background-color 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
          >
            <X size={18} />
          </button>
        </div>

        {/* Task Preview Card */}
        <div style={{ padding: '16px 20px 8px 20px' }}>
          <div
            style={{
              padding: '12px 14px',
              backgroundColor: '#F8FAFC',
              borderRadius: 8,
              border: '1px solid #E2E8F0',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 700,
                  color: '#475569',
                  backgroundColor: '#E2E8F0',
                  padding: '1px 6px',
                  borderRadius: 4,
                }}
              >
                {task.code || `#${task.id}`}
              </span>
              {task.milestoneName && (
                <>
                  <span style={{ fontSize: 11, color: '#94A3B8' }}>›</span>
                  <span style={{ fontSize: 11, color: '#64748B', fontWeight: 500 }}>
                    {task.milestoneName}
                  </span>
                </>
              )}
            </div>
            <div
              style={{
                fontSize: 13,
                fontWeight: 600,
                color: '#1E293B',
                lineHeight: 1.4,
              }}
            >
              {task.title}
            </div>
          </div>
        </div>

        {/* Status Selection List */}
        <div style={{ padding: '8px 20px 20px 20px', display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: '#64748B', marginBottom: 2 }}>
            Chọn trạng thái mới:
          </div>

          {STATUS_CONFIGS.map((st) => {
            const isCurrent = (task.status || 'TODO') === st.id;
            const isSelected = selectedStatus === st.id;

            return (
              <button
                key={st.id}
                type="button"
                disabled={isSubmitting}
                onClick={() => handleSelectAndApply(st.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 14px',
                  borderRadius: 10,
                  border: `1.5px solid ${isCurrent ? st.activeBorder : '#E2E8F0'}`,
                  backgroundColor: isCurrent ? st.activeBg : '#FFFFFF',
                  cursor: isSubmitting ? 'not-allowed' : 'pointer',
                  textAlign: 'left',
                  transition: 'all 0.15s ease',
                  opacity: isSubmitting ? 0.7 : 1,
                  boxShadow: isCurrent ? `0 2px 6px ${st.bgColor}` : 'none',
                }}
                onMouseEnter={(e) => {
                  if (!isSubmitting) {
                    e.currentTarget.style.borderColor = st.activeBorder;
                    e.currentTarget.style.backgroundColor = st.activeBg;
                    e.currentTarget.style.transform = 'translateY(-1px)';
                  }
                }}
                onMouseLeave={(e) => {
                  if (!isSubmitting) {
                    e.currentTarget.style.borderColor = isCurrent ? st.activeBorder : '#E2E8F0';
                    e.currentTarget.style.backgroundColor = isCurrent ? st.activeBg : '#FFFFFF';
                    e.currentTarget.style.transform = 'translateY(0)';
                  }
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div
                    style={{
                      width: 36,
                      height: 36,
                      borderRadius: '50%',
                      backgroundColor: st.bgColor,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    {st.icon(18)}
                  </div>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>
                        {st.label}
                      </span>
                      <span style={{ fontSize: 11, color: '#64748B', fontWeight: 500 }}>
                        ({st.subLabel})
                      </span>
                    </div>
                    <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>
                      {st.desc}
                    </div>
                  </div>
                </div>

                {isCurrent && (
                  <span
                    style={{
                      fontSize: 11,
                      fontWeight: 600,
                      color: st.color,
                      backgroundColor: st.bgColor,
                      border: `1px solid ${st.borderColor}`,
                      padding: '3px 8px',
                      borderRadius: 12,
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                      flexShrink: 0,
                    }}
                  >
                    <Check size={12} />
                    Hiện tại
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* Footer */}
        <div
          style={{
            padding: '12px 20px',
            backgroundColor: '#F8FAFC',
            borderTop: '1px solid #F1F5F9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            fontSize: 12,
            color: '#64748B',
          }}
        >
          <span>💡 Nhấp vào một trạng thái để chuyển ngay</span>
          <button
            onClick={onClose}
            style={{
              padding: '6px 14px',
              backgroundColor: '#FFFFFF',
              border: '1px solid #CBD5E1',
              borderRadius: 6,
              fontSize: 12,
              fontWeight: 600,
              color: '#475569',
              cursor: 'pointer',
            }}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
