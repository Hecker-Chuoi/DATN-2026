import { useEffect, useState } from 'react';
import { X, Calendar } from 'lucide-react';
import { SURFACE, FONT_STACK } from '../../constants/jiraTokens.js';
import { useModalBackdrop } from '../../hooks/useModalBackdrop.js';

const DURATIONS = [
  { label: '1 tuần', days: 7 },
  { label: '2 tuần', days: 14 },
  { label: '3 tuần', days: 21 },
  { label: '4 tuần', days: 28 },
];

/**
 * Modal dung chung cho "Tao Sprint", "Sua Sprint" va "Bat dau Sprint".
 *
 * Ba man hinh nay chi khac nhau o tieu de, nut bam va viec ngay ket thuc co bat buoc
 * hay khong, nen gop lam mot de khong lap bo cuc form.
 *
 * Ngay bat dau du kien quyet dinh THU TU cac chu ky (BR-S15): chu ky nao co ngay som hon
 * thi phai bat dau truoc. Vi vay khi tao moi, ngay mac dinh la sau chu ky cuoi cung
 * (suggestedStartDate) thay vi hom nay - neu khong chu ky moi se chen len truoc ke hoach.
 *
 * Theo quy uoc san co cua du an: khong dung portal, tu render overlay fixed.
 */
export default function SprintFormModal({
  isOpen,
  mode = 'create', // 'create' | 'edit' | 'start'
  sprint,
  suggestedName,
  suggestedStartDate,
  itemCount = 0,
  totalPoints = 0,
  hasActiveSprint = false,
  loading = false,
  onSubmit,
  onClose,
}) {
  const backdropProps = useModalBackdrop(onClose);
  const isStart = mode === 'start';
  const isEdit = mode === 'edit';
  const [name, setName] = useState('');
  const [goal, setGoal] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    setError('');
    setName(sprint?.name || suggestedName || 'Chu kỳ 1');
    setGoal(sprint?.goal || '');
    const start = sprint?.startDate
      ? toInput(sprint.startDate)
      : toInput(suggestedStartDate || new Date());
    setStartDate(start);
    setEndDate(sprint?.endDate ? toInput(sprint.endDate) : isStart ? addDays(start, 14) : '');
  }, [isOpen, sprint, suggestedName, suggestedStartDate, isStart]);

  if (!isOpen) return null;

  const applyDuration = (days) => setEndDate(addDays(startDate, days));

  const submit = async () => {
    setError('');
    if (!name.trim()) { setError('Tên chu kỳ là bắt buộc'); return; }
    if (isStart && !endDate) { setError('Ngày kết thúc là bắt buộc để theo dõi biểu đồ tiến độ'); return; }
    if (startDate && endDate && new Date(endDate) <= new Date(startDate)) {
      setError('Ngày kết thúc phải sau ngày bắt đầu');
      return;
    }
    const payload = isStart
      ? { startDate: toIso(startDate), endDate: toIso(endDate), goal: goal.trim() || null }
      : {
          name: name.trim(),
          goal: goal.trim() || null,
          // Khi sua ma khong doi ngay thi giu nguyen gio cu, tranh xao tron thu tu cac chu ky trung ngay.
          startDate: keepOrIso(startDate, isEdit ? sprint?.startDate : null),
          endDate: keepOrIso(endDate, isEdit ? sprint?.endDate : null),
        };
    try {
      await onSubmit?.(payload);
    } catch (err) {
      console.warn('Thao tác chu kỳ thất bại:', err);
    }
  };

  return (
    <div style={overlay} {...backdropProps}>
      <div style={panel} onClick={(e) => e.stopPropagation()}>
        <div style={header}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: SURFACE.text }}>
            {isStart ? 'Bắt đầu chu kỳ' : isEdit ? 'Sửa chu kỳ' : 'Tạo chu kỳ mới'}
          </h3>
          <button onClick={onClose} style={iconBtn}><X size={18} /></button>
        </div>

        <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          {isStart && (
            <div style={infoBox}>
              Chu kỳ này gồm <b>{itemCount}</b> công việc, tổng <b>{Number(totalPoints || 0)}</b> điểm ước lượng.
              {hasActiveSprint && (
                <div style={{ marginTop: 6, color: '#B45309' }}>
                  Dự án đang có một chu kỳ đang chạy — cần hoàn tất chu kỳ đó trước.
                </div>
              )}
            </div>
          )}
          {!isStart && (
            <div style={infoBox}>
              Các chu kỳ được bắt đầu <b>lần lượt theo ngày bắt đầu dự kiến</b>: chu kỳ có ngày sớm hơn phải bắt
              đầu trước. Muốn chen một chu kỳ lên trước, hãy đặt ngày bắt đầu sớm hơn chu kỳ đó.
            </div>
          )}

          <Field label="Tên chu kỳ">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isStart}
              style={{ ...input, backgroundColor: isStart ? SURFACE.track : '#FFFFFF' }}
            />
          </Field>

          <Field label="Mục tiêu chu kỳ">
            <textarea
              value={goal}
              onChange={(e) => setGoal(e.target.value)}
              rows={2}
              placeholder="Chu kỳ này hướng tới điều gì?"
              style={{ ...input, resize: 'vertical', fontFamily: FONT_STACK }}
            />
          </Field>

          <div style={{ display: 'flex', gap: 12 }}>
            <Field label="Ngày bắt đầu" style={{ flex: 1 }}>
              <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} style={input} />
            </Field>
            <Field label={isStart ? 'Ngày kết thúc *' : 'Ngày kết thúc'} style={{ flex: 1 }}>
              <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} style={input} />
            </Field>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 12, color: SURFACE.textMuted, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              <Calendar size={12} /> Thời lượng:
            </span>
            {DURATIONS.map((d) => (
              <button
                key={d.days}
                onClick={() => applyDuration(d.days)}
                style={{
                  padding: '4px 10px',
                  borderRadius: 999,
                  border: `1px solid ${SURFACE.border}`,
                  backgroundColor: '#FFFFFF',
                  color: SURFACE.textMuted,
                  fontSize: 12,
                  cursor: 'pointer',
                }}
              >
                {d.label}
              </button>
            ))}
          </div>

          {error && (
            <div style={{ fontSize: 12, color: '#DC2626', backgroundColor: '#FEF2F2', padding: '8px 10px', borderRadius: 6 }}>
              {error}
            </div>
          )}
        </div>

        <div style={footer}>
          <button onClick={onClose} style={secondaryBtn}>Hủy</button>
          <button onClick={submit} disabled={loading} style={primaryBtn(loading)}>
            {loading ? 'Đang xử lý...' : isStart ? 'Bắt đầu' : isEdit ? 'Lưu thay đổi' : 'Tạo chu kỳ'}
          </button>
        </div>
      </div>
    </div>
  );
}

function Field({ label, children, style }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 5, ...style }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: SURFACE.textMuted }}>{label}</span>
      {children}
    </label>
  );
}

function toInput(d) {
  const dt = new Date(d);
  if (Number.isNaN(dt.getTime())) return '';
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
}
function addDays(dateStr, days) {
  const base = dateStr ? new Date(dateStr) : new Date();
  base.setDate(base.getDate() + days);
  return toInput(base);
}
function toIso(dateStr) {
  return dateStr ? `${dateStr}T09:00:00` : null;
}
function keepOrIso(dateStr, original) {
  if (!dateStr) return null;
  return original && toInput(original) === dateStr ? original : toIso(dateStr);
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
const input = {
  padding: '7px 10px', borderRadius: 6, border: `1px solid ${SURFACE.border}`,
  fontSize: 13, outline: 'none', color: SURFACE.text, width: '100%', boxSizing: 'border-box',
};
const iconBtn = { border: 'none', background: 'transparent', cursor: 'pointer', color: SURFACE.textMuted, display: 'flex' };
const infoBox = { fontSize: 12, color: SURFACE.textMuted, backgroundColor: SURFACE.subtle, padding: '10px 12px', borderRadius: 6 };
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
