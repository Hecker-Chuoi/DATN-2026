import { useDroppable } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { statusToken, SURFACE } from '../../constants/jiraTokens.js';

/**
 * Mot cot tren Board: header co dem WIP + vung tha.
 *
 * `forbidden` = the dang keo khong the chuyen sang cot nay (quy trinh khong cho phep).
 * Khi do vung tha bi VO HIEU HOA han - trung thuc hon la de nguoi dung tha roi moi bao loi.
 */
export default function BoardColumn({
  column,
  items,
  laneKey = null,
  forbidden = false,
  onSelectItem,
  renderCard,
}) {
  const droppableId = laneKey ? `col:${column.id}|lane:${laneKey}` : `col:${column.id}`;
  const { setNodeRef, isOver } = useDroppable({ id: droppableId, disabled: forbidden });

  const category = column.statuses?.[0]?.category || 'TODO';
  const token = statusToken(category);
  const overMax = column.maxWipLimit != null && items.length > column.maxWipLimit;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        minWidth: 280,
        backgroundColor: isOver ? '#F0F7FF' : SURFACE.subtle,
        border: `1px solid ${overMax ? '#FCA5A5' : isOver ? '#93C5FD' : 'transparent'}`,
        borderRadius: 8,
        transition: 'background-color .15s ease, border-color .15s ease',
        opacity: forbidden ? 0.45 : 1,
      }}
    >
      {/* Header */}
      <div style={{ padding: '10px 12px 8px 12px', display: 'flex', alignItems: 'center', gap: 8 }}>
        <span
          style={{
            fontSize: 11, fontWeight: 700, letterSpacing: 0.4, textTransform: 'uppercase',
            color: token.color, backgroundColor: token.bg,
            padding: '2px 8px', borderRadius: 4,
          }}
        >
          {column.name}
        </span>
        <WipBadge current={items.length} max={column.maxWipLimit} min={column.minWipLimit} />
      </div>

      {/* Vung tha */}
      <div
        ref={setNodeRef}
        style={{
          flex: 1,
          minHeight: 80,
          padding: '0 8px 10px 8px',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          overflowY: 'auto',
        }}
      >
        <SortableContext items={items.map((i) => i.id)} strategy={verticalListSortingStrategy}>
          {items.length === 0 ? (
            <div
              style={{
                padding: '18px 10px', textAlign: 'center', fontSize: 11, fontStyle: 'italic',
                color: SURFACE.textLight, border: `1px dashed ${SURFACE.borderStrong}`, borderRadius: 6,
              }}
            >
              {forbidden ? 'Không thể chuyển sang cột này' : 'Chưa có công việc'}
            </div>
          ) : (
            items.map((item) => renderCard(item, onSelectItem))
          )}
        </SortableContext>
      </div>
    </div>
  );
}

/** Hien "(3)" hoac "(2/4 WIP Limit)" theo dung mo ta trong UI spec. */
export function WipBadge({ current, max, min }) {
  const overMax = max != null && current > max;
  const belowMin = min != null && current < min;

  let color = SURFACE.textMuted;
  let bg = 'transparent';
  if (overMax) { color = '#DC2626'; bg = '#FEF2F2'; }
  else if (belowMin) { color = '#B45309'; bg = '#FFFBEB'; }

  return (
    <span
      title={
        max != null
          ? `Giới hạn việc đang làm tối đa: ${max}`
          : belowMin
            ? `Dưới giới hạn việc đang làm tối thiểu: ${min}`
            : undefined
      }
      style={{
        fontSize: 11, fontWeight: 600, color, backgroundColor: bg,
        padding: bg === 'transparent' ? 0 : '1px 6px', borderRadius: 4,
      }}
    >
      {max != null ? `(${current}/${max} Giới hạn)` : `(${current})`}
    </span>
  );
}
