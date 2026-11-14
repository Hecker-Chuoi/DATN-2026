import { Check, Circle, Trash2 } from 'lucide-react';
import { ACTION_COLOR, CATEGORY_META, formatRelative, plainTitle, renderTitle, SEVERITY_META } from './notificationFormat.jsx';

/**
 * Một dòng thông báo: dải màu theo mức độ, biểu tượng theo nhóm, tiêu đề (đậm khi chưa đọc),
 * dòng phụ, dự án · thời gian, nhãn mức độ / "Cần xử lý", nút đọc–chưa đọc và xoá.
 */
export default function NotificationItem({ n, onOpen, onToggleRead, onDelete, compact = false }) {
  const sev = SEVERITY_META[n.severity] || SEVERITY_META.INFO;
  const cat = CATEGORY_META[n.category] || CATEGORY_META.TASK;
  const CatIcon = cat.Icon;
  const SevIcon = sev.Icon;
  const unread = !n.read;
  const pendingAction = n.actionRequired && !n.actionResolved;
  const context = n.projectName || n.workspaceName;

  const stop = (fn) => (e) => {
    e.stopPropagation();
    fn();
  };

  return (
    <div
      role="button"
      tabIndex={0}
      aria-label={plainTitle(n.title)}
      onClick={() => onOpen?.(n)}
      onKeyDown={(e) => {
        if (e.target !== e.currentTarget) return;
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onOpen?.(n);
        }
      }}
      style={{
        position: 'relative',
        display: 'flex',
        gap: 10,
        padding: compact ? '10px 10px 10px 14px' : '12px 14px 12px 18px',
        background: unread ? '#F5F9FF' : '#FFFFFF',
        borderBottom: '1px solid #F1F5F9',
        cursor: n.link ? 'pointer' : 'default',
        opacity: n.actionRequired && n.actionResolved ? 0.75 : 1,
        outlineOffset: -2,
      }}
      onMouseEnter={(e) => { e.currentTarget.style.background = unread ? '#EEF5FF' : '#F8FAFC'; }}
      onMouseLeave={(e) => { e.currentTarget.style.background = unread ? '#F5F9FF' : '#FFFFFF'; }}
    >
      <span style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 3, background: sev.color }} />
      <span
        title={cat.label}
        style={{
          width: 30, height: 30, borderRadius: '50%', background: sev.soft, color: sev.ink,
          display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
        }}
      >
        <CatIcon size={15} />
      </span>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, lineHeight: 1.45, color: unread ? '#0F172A' : '#334155', fontWeight: unread ? 500 : 400, overflowWrap: 'anywhere' }}>
          {renderTitle(n.title)}
        </div>
        {n.body && (
          <div style={{ fontSize: 12, color: '#64748B', marginTop: 2, overflowWrap: 'anywhere', lineHeight: 1.4 }}>{n.body}</div>
        )}
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '4px 8px', marginTop: 5, fontSize: 11.5, color: '#94A3B8' }}>
          <span>{context ? `${context} · ` : ''}{formatRelative(n.updatedAt || n.createdAt)}</span>
          {n.severity !== 'INFO' && (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontWeight: 600, fontSize: 11, padding: '1px 7px', borderRadius: 999, background: sev.soft, color: sev.ink }}>
              <SevIcon size={11} /> {sev.label}
            </span>
          )}
          {n.actionRequired && (
            pendingAction ? (
              <span style={{ fontWeight: 600, fontSize: 11, padding: '1px 7px', borderRadius: 999, background: ACTION_COLOR.soft, color: ACTION_COLOR.ink }}>
                Cần xử lý
              </span>
            ) : (
              <span style={{ fontWeight: 600, fontSize: 11, padding: '1px 7px', borderRadius: 999, background: '#F1F5F9', color: '#64748B', border: '1px solid #E2E8F0' }}>
                Đã xử lý
              </span>
            )
          )}
          {n.actorCount > 1 && <span title="Đã gộp các cập nhật">· {n.actorCount} người</span>}
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, flexShrink: 0 }}>
        <IconButton
          title={unread ? 'Đánh dấu đã đọc' : 'Đánh dấu chưa đọc'}
          onClick={stop(() => onToggleRead?.(n, unread))}
        >
          {unread ? <Check size={14} /> : <Circle size={13} />}
        </IconButton>
        <IconButton title="Xoá thông báo" danger onClick={stop(() => onDelete?.(n))}>
          <Trash2 size={14} />
        </IconButton>
      </div>
      {unread && (
        <span aria-hidden style={{ position: 'absolute', top: 14, right: 46, width: 7, height: 7, borderRadius: '50%', background: '#2563EB' }} />
      )}
    </div>
  );
}

function IconButton({ title, onClick, danger = false, children }) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      onClick={onClick}
      style={{
        width: 26, height: 26, borderRadius: 6, border: '1px solid transparent', background: 'transparent',
        color: '#94A3B8', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.background = danger ? '#FEF2F2' : '#F1F5F9';
        e.currentTarget.style.color = danger ? '#B91C1C' : '#0F172A';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = 'transparent';
        e.currentTarget.style.color = '#94A3B8';
      }}
    >
      {children}
    </button>
  );
}
