import { AlertTriangle, Lock, MoreHorizontal, Hourglass } from 'lucide-react';
import { typeToken, priorityToken, statusToken, SURFACE } from '../../constants/jiraTokens.js';
import { getAvatarColor, getInitials } from '../../utils/avatarColor.js';

/**
 * Một hàng công việc trên màn hình Backlog (thuần trình bày, không biết gì về kéo thả).
 * Hỗ trợ hiển thị dạng cây phân cấp:
 * isChild: hiển thị ký hiệu nhánh '↳' thụt vào dưới Hạng mục lớn.
 */
export default function BacklogIssueRow({
  item,
  onSelect,
  isDragging = false,
  dragHandleProps = {},
  isChild = false,
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
      {/* Ký hiệu nhánh cây phân cấp con */}
      {isChild && (
        <span
          style={{
            flexShrink: 0,
            color: '#8B5CF6',
            fontSize: 14,
            fontWeight: 700,
            lineHeight: 1,
            userSelect: 'none',
          }}
          title="Công việc thuộc Hạng mục lớn"
        >
          ↳
        </span>
      )}

      {/* Loại công việc */}
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

      {/* Mã */}
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

      {/* Tiêu đề */}
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

      {/* Nhãn Epic (Lozenge) nếu có */}
      {(item.epicCode || item.parentCode) && (
        <span
          title={`Hạng mục lớn: ${item.epicSummary || item.parentSummary || ''}`}
          style={{
            flexShrink: 0,
            padding: '1px 6px',
            borderRadius: 3,
            backgroundColor: '#F3F0FF',
            color: '#6554C0',
            fontSize: 10,
            fontWeight: 700,
            letterSpacing: 0.2,
            border: '1px solid #DDD6FE',
          }}
        >
          {item.epicCode || item.parentCode}
        </span>
      )}

      {/* Cảnh báo */}
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
      {item.completionPending && (
        <span
          title="Tạm hoàn thành – đã báo xong, chờ Admin dự án xác nhận"
          style={{ flexShrink: 0, display: 'inline-flex', alignItems: 'center', gap: 3, color: '#B45309', fontSize: 11, fontWeight: 600 }}
        >
          <Hourglass size={12} /> Chờ duyệt HT
        </span>
      )}

      {/* Độ ưu tiên */}
      <span title={prio.label} style={{ flexShrink: 0, color: prio.color, fontSize: 15, lineHeight: 1 }}>
        {priorityGlyph(item.priority)}
      </span>

      {/* Điểm */}
      {item.storyPoints != null && (
        <span
          title="Điểm ước lượng"
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

      {/* Trạng thái */}
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

      {/* Người thực hiện */}
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
