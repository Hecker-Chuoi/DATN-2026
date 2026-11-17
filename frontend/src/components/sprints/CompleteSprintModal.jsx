import { useEffect, useState } from 'react';
import { X, CheckCircle2, CircleDashed, AlertTriangle, AlertCircle } from 'lucide-react';
import { SURFACE, FONT_STACK } from '../../constants/jiraTokens.js';
import { useModalBackdrop } from '../../hooks/useModalBackdrop.js';

/**
 * Modal hoàn tất Sprint / Chu kỳ.
 *
 * Chế độ Cảnh báo (Warning State):
 * - Khi còn công việc chưa hoàn thành (incomplete > 0), modal hiển thị giao diện Warning với:
 *   + Dải màu và icon cảnh báo (AlertTriangle - Amber/Orange).
 *   + Hộp thông tin cảnh báo nêu rõ hậu quả (chốt velocity, chuyển việc dở dang).
 *   + Nút xác nhận mang màu cảnh báo (#D97706) kèm icon để người dùng ý thức tính nghiêm trọng.
 * - Khi toàn bộ công việc đã hoàn thành (incomplete === 0), modal ở trạng thái Success/Neutral an toàn.
 */
export default function CompleteSprintModal({
  isOpen,
  sprint,
  futureSprints = [],
  loading = false,
  onConfirm,
  onClose,
}) {
  const backdropProps = useModalBackdrop(onClose);
  const [target, setTarget] = useState('BACKLOG');

  useEffect(() => {
    if (isOpen) setTarget('BACKLOG');
  }, [isOpen]);

  if (!isOpen || !sprint) return null;

  const total = sprint.itemCount ?? sprint.totalItems ?? sprint.items?.length ?? 0;
  const done = sprint.doneCount ?? sprint.doneItems ?? (sprint.items ? sprint.items.filter((i) => i.statusCategory === 'DONE' || i.status === 'DONE').length : 0);
  const incomplete = Math.max(0, total - done);
  const isWarning = incomplete > 0;

  return (
    <div style={overlay} {...backdropProps}>
      <style>{`
        @keyframes modalFadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes modalScaleUp {
          from { transform: scale(0.96); opacity: 0; }
          to { transform: scale(1); opacity: 1; }
        }
      `}</style>

      <div style={panel} onClick={(e) => e.stopPropagation()}>
        {/* Accent Bar biểu thị trạng thái */}
        <div
          style={{
            height: 4,
            width: '100%',
            background: isWarning
              ? 'linear-gradient(90deg, #F59E0B 0%, #D97706 100%)'
              : 'linear-gradient(90deg, #10B981 0%, #059669 100%)',
          }}
        />

        {/* Modal Header */}
        <div style={header}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
            <div
              style={{
                width: 38,
                height: 38,
                borderRadius: 10,
                backgroundColor: isWarning ? '#FEF3C7' : '#DCFCE7',
                color: isWarning ? '#D97706' : '#16A34A',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              {isWarning ? <AlertTriangle size={20} /> : <CheckCircle2 size={20} />}
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <h3
                  style={{
                    margin: 0,
                    fontSize: 16,
                    fontWeight: 700,
                    color: SURFACE.text,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  Hoàn tất {sprint.name}
                </h3>
                {isWarning && (
                  <span
                    style={{
                      fontSize: 11,
                      fontWeight: 600,
                      color: '#B45309',
                      backgroundColor: '#FEF3C7',
                      border: '1px solid #FDE68A',
                      padding: '1px 8px',
                      borderRadius: 9999,
                    }}
                  >
                    Cảnh báo dở dang
                  </span>
                )}
              </div>
              <div style={{ fontSize: 12, color: SURFACE.textMuted, marginTop: 2 }}>
                Đóng chu kỳ làm việc và điều phối các công việc liên quan
              </div>
            </div>
          </div>
          <button onClick={onClose} style={iconBtn} title="Đóng">
            <X size={18} />
          </button>
        </div>

        {/* Modal Body */}
        <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          {/* Warning Banner nếu còn công việc chưa hoàn thành */}
          {isWarning ? (
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: 10,
                padding: '11px 14px',
                backgroundColor: '#FFFBEB',
                border: '1px solid #FDE68A',
                borderLeft: '4px solid #F59E0B',
                borderRadius: 8,
              }}
            >
              <AlertCircle size={18} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
              <div style={{ fontSize: 13, lineHeight: 1.5, color: '#92400E' }}>
                <div style={{ fontWeight: 600 }}>
                  Cảnh báo: Chu kỳ còn {incomplete} công việc chưa hoàn thành!
                </div>
                <div style={{ fontSize: 12, color: '#B45309', marginTop: 2 }}>
                  Việc đóng chu kỳ sẽ chốt báo cáo tiến độ và vận tốc (velocity). Bạn cần chọn nơi để di dời các công việc dở dang này.
                </div>
              </div>
            </div>
          ) : (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                fontSize: 13,
                color: '#065F46',
                backgroundColor: '#ECFDF5',
                border: '1px solid #A7F3D0',
                padding: '10px 14px',
                borderRadius: 8,
              }}
            >
              <CheckCircle2 size={18} color="#059669" style={{ flexShrink: 0 }} />
              <span>Tuyệt vời! Toàn bộ {done} công việc đã hoàn thành 100%. Chu kỳ sẵn sàng để đóng.</span>
            </div>
          )}

          {/* Thống kê hoàn thành vs chưa xong */}
          <div style={{ display: 'flex', gap: 10 }}>
            <StatCard
              icon={<CheckCircle2 size={16} color={done > 0 ? '#059669' : '#64748B'} />}
              value={done}
              label="công việc đã hoàn thành"
              bg={done > 0 ? '#ECFDF5' : '#F8FAFC'}
              border={done > 0 ? '1px solid #A7F3D0' : '1px solid #E2E8F0'}
              valueColor={done > 0 ? '#065F46' : SURFACE.textMuted}
            />
            <StatCard
              icon={
                isWarning ? (
                  <AlertTriangle size={16} color="#D97706" />
                ) : (
                  <CircleDashed size={16} color="#64748B" />
                )
              }
              value={incomplete}
              label="công việc chưa xong"
              bg={isWarning ? '#FEF3C7' : '#F8FAFC'}
              border={isWarning ? '1px solid #FCD34D' : '1px solid #E2E8F0'}
              valueColor={isWarning ? '#92400E' : SURFACE.textMuted}
            />
          </div>

          {/* Chọn nơi chuyển công việc chưa xong */}
          {incomplete > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: SURFACE.text }}>
                Chuyển {incomplete} công việc chưa xong sang:
              </span>

              <Radio
                checked={target === 'BACKLOG'}
                onChange={() => setTarget('BACKLOG')}
                label="Kho việc chờ phân bổ (Backlog)"
                hint="Đưa về kho việc chờ phân bổ để lập kế hoạch lại sau"
              />

              {futureSprints
                .filter((s) => (s.sprintId || s.id) !== (sprint.sprintId || sprint.id))
                .map((s) => {
                  const sId = s.sprintId || s.id;
                  const sCount = s.itemCount ?? s.totalItems ?? s.items?.length ?? 0;
                  return (
                    <Radio
                      key={sId}
                      checked={target === sId}
                      onChange={() => setTarget(sId)}
                      label={s.name}
                      hint={`Chu kỳ chưa bắt đầu — hiện có ${sCount} công việc`}
                    />
                  );
                })}
            </div>
          )}

          <div style={{ fontSize: 12, color: SURFACE.textMuted, display: 'flex', alignItems: 'center', gap: 6 }}>
            <span>ℹ️ Số liệu của chu kỳ sẽ được lưu lại vĩnh viễn để dựng báo cáo tiến độ và vận tốc.</span>
          </div>
        </div>

        {/* Modal Footer */}
        <div style={footer}>
          <button onClick={onClose} style={secondaryBtn} disabled={loading}>
            Quay lại
          </button>
          <button
            onClick={() => onConfirm?.({ targetSprintIdForIncomplete: target })}
            disabled={loading}
            style={actionBtn(loading, isWarning)}
          >
            {isWarning && <AlertTriangle size={15} />}
            <span>
              {loading
                ? 'Đang đóng...'
                : isWarning
                ? 'Xác nhận đóng chu kỳ'
                : 'Hoàn tất chu kỳ'}
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}

function StatCard({ icon, value, label, bg, border, valueColor }) {
  return (
    <div
      style={{
        flex: 1,
        backgroundColor: bg,
        border: border || 'none',
        borderRadius: 8,
        padding: '10px 12px',
        boxSizing: 'border-box',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {icon}
        <span style={{ fontSize: 20, fontWeight: 700, color: valueColor || SURFACE.text }}>
          {value}
        </span>
      </div>
      <div style={{ fontSize: 11, color: SURFACE.textMuted, marginTop: 2 }}>{label}</div>
    </div>
  );
}

function Radio({ checked, onChange, label, hint }) {
  return (
    <label
      onClick={onChange}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        padding: '10px 12px',
        borderRadius: 8,
        cursor: 'pointer',
        border: `1.5px solid ${checked ? '#F59E0B' : SURFACE.border}`,
        backgroundColor: checked ? '#FFFDF5' : '#FFFFFF',
        transition: 'all 0.15s ease',
      }}
    >
      <input
        type="radio"
        checked={checked}
        onChange={onChange}
        style={{
          marginTop: 2,
          cursor: 'pointer',
          accentColor: '#D97706',
        }}
      />
      <div>
        <span
          style={{
            display: 'block',
            fontSize: 13,
            fontWeight: 600,
            color: checked ? '#92400E' : SURFACE.text,
          }}
        >
          {label}
        </span>
        <span style={{ display: 'block', fontSize: 11, color: SURFACE.textMuted, marginTop: 1 }}>
          {hint}
        </span>
      </div>
    </label>
  );
}

const overlay = {
  position: 'fixed',
  inset: 0,
  backgroundColor: 'rgba(15,23,42,0.6)',
  backdropFilter: 'blur(4px)',
  zIndex: 9999,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 20,
  fontFamily: FONT_STACK,
  animation: 'modalFadeIn 0.15s ease-out',
};

const panel = {
  width: '100%',
  maxWidth: 520,
  backgroundColor: '#FFFFFF',
  borderRadius: 12,
  boxShadow: '0 20px 40px rgba(0,0,0,0.18)',
  overflow: 'hidden',
  animation: 'modalScaleUp 0.15s ease-out',
};

const header = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  padding: '14px 20px',
  borderBottom: `1px solid ${SURFACE.border}`,
};

const footer = {
  display: 'flex',
  justifyContent: 'flex-end',
  gap: 8,
  padding: '12px 20px',
  borderTop: `1px solid ${SURFACE.border}`,
  backgroundColor: SURFACE.subtle,
};

const iconBtn = {
  border: 'none',
  background: 'transparent',
  cursor: 'pointer',
  color: SURFACE.textMuted,
  display: 'flex',
  padding: 4,
  borderRadius: 6,
};

const secondaryBtn = {
  padding: '8px 16px',
  borderRadius: 6,
  border: `1px solid ${SURFACE.border}`,
  backgroundColor: '#FFFFFF',
  color: SURFACE.textMuted,
  fontSize: 13,
  fontWeight: 500,
  cursor: 'pointer',
};

function actionBtn(disabled, isWarning) {
  if (disabled) {
    return {
      padding: '8px 18px',
      borderRadius: 6,
      border: 'none',
      fontSize: 13,
      fontWeight: 600,
      cursor: 'not-allowed',
      backgroundColor: '#E2E8F0',
      color: '#94A3B8',
      display: 'inline-flex',
      alignItems: 'center',
      gap: 6,
    };
  }

  if (isWarning) {
    return {
      padding: '8px 18px',
      borderRadius: 6,
      border: 'none',
      fontSize: 13,
      fontWeight: 600,
      cursor: 'pointer',
      backgroundColor: '#D97706',
      color: '#FFFFFF',
      display: 'inline-flex',
      alignItems: 'center',
      gap: 6,
      boxShadow: '0 1px 2px rgba(217, 119, 6, 0.25)',
    };
  }

  return {
    padding: '8px 18px',
    borderRadius: 6,
    border: 'none',
    fontSize: 13,
    fontWeight: 600,
    cursor: 'pointer',
    backgroundColor: '#2563EB',
    color: '#FFFFFF',
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    boxShadow: '0 1px 2px rgba(37, 99, 235, 0.25)',
  };
}
