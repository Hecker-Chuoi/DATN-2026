import { AlertTriangle, Lock, MoreHorizontal } from 'lucide-react';
import { typeToken, priorityToken, statusToken, SURFACE } from '../../constants/jiraTokens.js';
import { getAvatarColor, getInitials } from '../../utils/avatarColor.js';

/**
 * Mot hang cong viec tren man hinh Backlog (thuan trinh bay, khong biet gi ve keo tha).
 * Bo cuc theo 5_WORK_FRONTEND_UI_JIRA_SPEC: icon loai - ma - do uu tien - tieu de
 * - diem - trang thai - avatar.
 */
export default function BacklogIssueRow({
  item,
  onSelect,
  isDragging = false,
  dragHandleProps = {},
}) {
  const type = typeToken(item.typeCode, item.typeName);
  const prio = priorityToken(item.priority);
  const status = statusToken(item.statusCategory);
  const isDone = item.statusCategory === 'DONE';

  return (
    <div
      className="blg-row"
      onClick={() => onSelect?.(item.id)}
      {...dragHandleProps}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '7px 12px',
        backgroundColor: isDragging ? '#EFF6FF' : '#FFFFFF',
        borderBottom: `1px solid ${SURFACE.border}`,
        cursor: 'pointer',
        fontSize: 13,
        opacity: isDragging ? 0.6 : 1,
      }}
    >
      {/* Loai cong viec */}
      <span
        title={type.label}
        style={{
          flexShrink: 0,
          width: 18,
          height: 18,
          borderRadius: 4,
          backgroundColor: item.typeColor || type.color,
          display: 'inline-block',
        }}
      />

      {/* Ma */}
      <span
        style={{
          flexShrink: 0,
          fontWeight: 600,
          color: SURFACE.textMuted,
          fontSize: 12,
          minWidth: 68,
          textDecoration: isDone ? 'line-through' : 'none',
        }}
      >
        {item.code}
      </span>

      {/* Tieu de */}
      <span
        style={{
          flex: 1,
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          color: SURFACE.text,
          textDecoration: isDone ? 'line-through' : 'none',
        }}
      >
        {item.summary}
      </span>

      {/* Canh bao */}
      {item.isBlocked && (
        <span
          title="Đang bị công việc khác cản trở"
          style={{ flexShrink: 0, display: 'inline-flex', alignItems: 'center', gap: 3, color: '#DE350B', fontSize: 11, fontWeight: 600 }}
        >
          <Lock size={12} /> Bị chặn
        </span>
      )}
      {item.isOverdue && (
        <span title="Đã quá hạn" style={{ flexShrink: 0, color: '#EA580C', display: 'inline-flex' }}>
          <AlertTriangle size={13} />
        </span>
      )}

      {/* Do uu tien */}
      <span title={prio.label} style={{ flexShrink: 0, color: prio.color, fontSize: 15, lineHeight: 1 }}>
        {priorityGlyph(item.priority)}
      </span>

      {/* Diem */}
      {item.storyPoints != null && (
        <span
          title="Story points"
          style={{
            flexShrink: 0,
            minWidth: 22,
            textAlign: 'center',
            padding: '1px 7px',
            borderRadius: 999,
            backgroundColor: SURFACE.track,
            color: SURFACE.textMuted,
            fontSize: 11,
            fontWeight: 700,
          }}
        >
          {Number(item.storyPoints)}
        </span>
      )}

      {/* Trang thai */}
      <span
        style={{
          flexShrink: 0,
          padding: '2px 8px',
          borderRadius: 4,
          fontSize: 10,
          fontWeight: 700,
          letterSpacing: 0.3,
          color: status.color,
          backgroundColor: status.bg,
          textTransform: 'uppercase',
          maxWidth: 110,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {item.statusName || status.label}
      </span>

      {/* Nguoi thuc hien */}
      <span style={{ flexShrink: 0, width: 24, height: 24 }}>
        {item.assigneeId ? (
          <span
            title={item.assigneeName || 'Đã phân công'}
            style={{
              width: 24,
              height: 24,
              borderRadius: '50%',
              backgroundColor: getAvatarColor(item.assigneeName || item.assigneeId),
              color: '#FFFFFF',
              fontSize: 10,
              fontWeight: 700,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {getInitials(item.assigneeName || '?')}
          </span>
        ) : (
          <span
            title="Chưa phân công"
            style={{
              width: 24,
              height: 24,
              borderRadius: '50%',
              border: `1px dashed ${SURFACE.borderStrong}`,
              display: 'block',
            }}
          />
        )}
      </span>

      <span className="blg-actions" style={{ flexShrink: 0, color: SURFACE.textLight, display: 'flex' }}>
        <MoreHorizontal size={15} />
      </span>
    </div>
  );
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
