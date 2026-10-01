import { useEffect, useState } from 'react';
import { X, CheckCircle2, CircleDashed } from 'lucide-react';
import { SURFACE, FONT_STACK } from '../../constants/jiraTokens.js';
import { useModalBackdrop } from '../../hooks/useModalBackdrop.js';

/**
 * Modal hoan tat Sprint.
 *
 * Diem mau chot: nguoi dung phai chon noi chuyen cac cong viec CHUA XONG - ve Backlog
 * hoac sang mot Sprint chua bat dau. Backend nhan "BACKLOG" hoac UUID.
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
  const done = sprint.doneCount ?? 0;
  const incomplete = (sprint.itemCount ?? 0) - done;

  return (
    <div style={overlay} {...backdropProps}>
      <div style={panel} onClick={(e) => e.stopPropagation()}>
        <div style={header}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: SURFACE.text }}>
            Hoàn tất {sprint.name}
          </h3>
          <button onClick={onClose} style={iconBtn}><X size={18} /></button>
        </div>

        <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', gap: 10 }}>
            <StatCard
              icon={<CheckCircle2 size={16} color="#00875A" />}
              value={done}
              label="công việc đã hoàn thành"
              bg="#E3FCEF"
            />
            <StatCard
              icon={<CircleDashed size={16} color="#B45309" />}
              value={incomplete}
              label="công việc chưa xong"
              bg="#FEF3C7"
            />
          </div>

          {incomplete > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: SURFACE.text }}>
                Chuyển {incomplete} công việc chưa xong sang:
              </span>

              <Radio
                checked={target === 'BACKLOG'}
                onChange={() => setTarget('BACKLOG')}
                label="Backlog"
                hint="Đưa về danh sách tồn đọng để lập kế hoạch lại"
              />

              {futureSprints
                .filter((s) => s.sprintId !== sprint.sprintId)
                .map((s) => (
                  <Radio
                    key={s.sprintId}
                    checked={target === s.sprintId}
                    onChange={() => setTarget(s.sprintId)}
                    label={s.name}
                    hint={`Sprint chưa bắt đầu — hiện có ${s.itemCount} công việc`}
                  />
                ))}
            </div>
          ) : (
            <div style={{ fontSize: 13, color: '#00875A', backgroundColor: '#E3FCEF', padding: '10px 12px', borderRadius: 6 }}>
              Tất cả công việc đã hoàn thành. Sprint sẽ được đóng ngay.
            </div>
          )}

          <div style={{ fontSize: 12, color: SURFACE.textMuted }}>
            Số liệu của Sprint sẽ được lưu lại để dựng báo cáo Burndown và Velocity.
          </div>
        </div>

        <div style={footer}>
          <button onClick={onClose} style={secondaryBtn}>Quay lại</button>
          <button
            onClick={() => onConfirm?.({ targetSprintIdForIncomplete: target })}
            disabled={loading}
            style={primaryBtn(loading)}
          >
            {loading ? 'Đang đóng...' : 'Hoàn tất Sprint'}
          </button>
        </div>
      </div>
    </div>
  );
}

function StatCard({ icon, value, label, bg }) {
  return (
    <div style={{ flex: 1, backgroundColor: bg, borderRadius: 8, padding: '10px 12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {icon}
        <span style={{ fontSize: 20, fontWeight: 700, color: SURFACE.text }}>{value}</span>
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
        gap: 9,
        padding: '9px 11px',
        borderRadius: 8,
        cursor: 'pointer',
        border: `1px solid ${checked ? '#93C5FD' : SURFACE.border}`,
        backgroundColor: checked ? '#EFF6FF' : '#FFFFFF',
      }}
    >
      <input type="radio" checked={checked} onChange={onChange} style={{ marginTop: 2, cursor: 'pointer' }} />
      <span>
        <span style={{ display: 'block', fontSize: 13, fontWeight: 600, color: SURFACE.text }}>{label}</span>
        <span style={{ display: 'block', fontSize: 11, color: SURFACE.textMuted }}>{hint}</span>
      </span>
    </label>
  );
}

const overlay = {
  position: 'fixed', inset: 0, backgroundColor: 'rgba(15,23,42,0.6)', backdropFilter: 'blur(4px)',
  zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20,
  fontFamily: FONT_STACK,
};
const panel = {
  width: '100%', maxWidth: 520, backgroundColor: '#FFFFFF', borderRadius: 12,
  boxShadow: '0 20px 40px rgba(0,0,0,0.18)', overflow: 'hidden',
};
const header = {
  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
  padding: '14px 20px', borderBottom: `1px solid ${SURFACE.border}`,
};
const footer = {
  display: 'flex', justifyContent: 'flex-end', gap: 8,
  padding: '12px 20px', borderTop: `1px solid ${SURFACE.border}`, backgroundColor: SURFACE.subtle,
};
const iconBtn = { border: 'none', background: 'transparent', cursor: 'pointer', color: SURFACE.textMuted, display: 'flex' };
const secondaryBtn = {
  padding: '7px 14px', borderRadius: 6, border: `1px solid ${SURFACE.border}`,
  backgroundColor: '#FFFFFF', color: SURFACE.textMuted, fontSize: 13, cursor: 'pointer',
};
function primaryBtn(disabled) {
  return {
    padding: '7px 16px', borderRadius: 6, border: 'none', fontSize: 13, fontWeight: 600,
    cursor: disabled ? 'not-allowed' : 'pointer',
    backgroundColor: disabled ? '#E2E8F0' : '#2563EB',
    color: disabled ? '#94A3B8' : '#FFFFFF',
  };
}
