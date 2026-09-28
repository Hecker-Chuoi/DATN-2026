import { Bookmark, Lock, AlertTriangle, Clock, MoreHorizontal } from 'lucide-react';
import { typeToken, priorityToken, SURFACE } from '../../constants/jiraTokens.js';
import { getAvatarColor, getInitials } from '../../utils/avatarColor.js';

/**
 * The cong viec tren Board - THUAN TRINH BAY, khong biet gi ve keo tha.
 * Giu nguyen bo cuc va cac huy hieu cua ProjectBoardView cu (BLOCKED, lech han,
 * thanh tien do Sub-task, chip han chot) theo 5_WORK_FRONTEND_UI_JIRA_SPEC.
 */
export default function BoardCard({ item, isOverlay = false, onSelect, dragHandleProps = {} }) {
  const type = typeToken(item.typeCode, item.typeName);
  const prio = priorityToken(item.priority);
  const progress = subtaskProgress(item.subtaskCount, item.subtaskDoneCount);

  return (
    <div
      className="bd-card"
      onClick={() => onSelect?.(item.id)}
      {...dragHandleProps}
      style={{
        backgroundColor: '#FFFFFF',
        border: `1px solid ${SURFACE.border}`,
        borderRadius: 8,
        padding: 10,
        cursor: 'pointer',
        boxShadow: isOverlay ? '0 12px 24px rgba(0,0,0,0.18)' : '0 1px 2px rgba(0,0,0,0.04)',
        display: 'flex',
        flexDirection: 'column',
        gap: 7,
      }}
    >
      {/* Hang tren: loai + ma ... uu tien + menu */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span
          title={type.label}
          style={{
            width: 15, height: 15, borderRadius: 3,
            backgroundColor: item.typeColor || type.color, flexShrink: 0,
          }}
        />
        <span style={{ fontSize: 11, fontWeight: 700, color: SURFACE.textMuted }}>{item.code}</span>
        <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 5 }}>
          <span title={prio.label} style={{ color: prio.color, fontSize: 14, lineHeight: 1 }}>
            {priorityGlyph(item.priority)}
          </span>
          <span className="bd-actions" style={{ color: SURFACE.textLight, display: 'flex' }}>
            <MoreHorizontal size={14} />
          </span>
        </span>
      </div>

      {/* Tieu de - toi da 2 dong */}
      <div
        style={{
          fontSize: 13,
          lineHeight: 1.35,
          color: SURFACE.text,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {item.summary}
      </div>

      {/* Nhan Epic cha + cac canh bao */}
      {(item.parentCode || item.isBlocked || item.isOverdue) && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 5, flexWrap: 'wrap' }}>
          {item.parentCode && (
            <span style={badge('#6554C0', '#F3F0FF', '#DDD6FE')}>
              <Bookmark size={10} /> {item.parentCode}
            </span>
          )}
          {item.isBlocked && (
            <span title="Bị công việc tiền đề cản trở" style={badge('#DC2626', '#FEF2F2', '#FECACA')}>
              <Lock size={10} /> Bị chặn
            </span>
          )}
          {item.isOverdue && (
            <span title="Đã quá hạn" style={badge('#D97706', '#FFFBEB', '#FCD34D')}>
              <AlertTriangle size={10} /> Quá hạn
            </span>
          )}
        </div>
      )}

      {/* Tien do Sub-task */}
      {progress && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <div style={{ flex: 1, height: 4, borderRadius: 999, backgroundColor: progress.trackBg }}>
            <div style={{ width: `${progress.percent}%`, height: '100%', borderRadius: 999, backgroundColor: progress.barColor }} />
          </div>
          <span style={{ fontSize: 10, fontWeight: 700, color: progress.textColor }}>
            {progress.done}/{progress.total}
          </span>
        </div>
      )}

      {/* Hang duoi: han chot + diem + avatar */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {item.dueDate && (
          <span
            title="Hạn chót"
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 10, fontWeight: 600,
              color: item.isOverdue ? '#DC2626' : SURFACE.textMuted,
            }}
          >
            <Clock size={10} /> {formatDate(item.dueDate)}
          </span>
        )}
        <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6 }}>
          {item.storyPoints != null && (
            <span
              title="Story points"
              style={{
                minWidth: 20, textAlign: 'center', padding: '1px 6px', borderRadius: 999,
                backgroundColor: SURFACE.track, color: SURFACE.textMuted, fontSize: 10, fontWeight: 700,
              }}
            >
              {Number(item.storyPoints)}
            </span>
          )}
          {item.assigneeId ? (
            <span
              title={item.assigneeName || 'Đã phân công'}
              style={{
                width: 22, height: 22, borderRadius: '50%',
                backgroundColor: getAvatarColor(item.assigneeName || item.assigneeId),
                color: '#FFFFFF', fontSize: 9, fontWeight: 700,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
            >
              {getInitials(item.assigneeName || '?')}
            </span>
          ) : (
            <span
              title="Chưa phân công"
              style={{ width: 22, height: 22, borderRadius: '50%', border: `1px dashed ${SURFACE.borderStrong}` }}
            />
          )}
        </span>
      </div>
    </div>
  );
}

function badge(color, bg, border) {
  return {
    display: 'inline-flex', alignItems: 'center', gap: 3,
    fontSize: 10, fontWeight: 700, color, backgroundColor: bg,
    border: `1px solid ${border}`, padding: '1px 5px', borderRadius: 4,
  };
}

/** Mau thanh tien do theo moc hoan thanh - giu nguyen quy uoc cua bang cu. */
function subtaskProgress(total, done) {
  const t = Number(total) || 0;
  if (t <= 0) return null;
  const d = Math.max(0, Number(done) || 0);
  const percent = Math.min(100, Math.round((d / t) * 100));
  if (percent === 100) return { total: t, done: d, percent, barColor: '#10B981', textColor: '#059669', trackBg: '#DCFCE7' };
  if (percent >= 70) return { total: t, done: d, percent, barColor: '#2563EB', textColor: '#1D4ED8', trackBg: '#DBEAFE' };
  if (percent >= 40) return { total: t, done: d, percent, barColor: '#F59E0B', textColor: '#B45309', trackBg: '#FEF3C7' };
  if (percent >= 1) return { total: t, done: d, percent, barColor: '#EA580C', textColor: '#C2410C', trackBg: '#FFEDD5' };
  return { total: t, done: d, percent, barColor: '#94A3B8', textColor: '#64748B', trackBg: '#F1F5F9' };
}

function priorityGlyph(priority) {
  switch (priority) {
    case 'HIGHEST': return '⇈';
    case 'HIGH': return '↑';
    case 'LOW': return '↓';
    case 'LOWEST': return '⇊';
    default: return '=';
  }
}

function formatDate(d) {
  const dt = new Date(d);
  if (Number.isNaN(dt.getTime())) return '';
  return `${String(dt.getDate()).padStart(2, '0')}/${String(dt.getMonth() + 1).padStart(2, '0')}`;
}
