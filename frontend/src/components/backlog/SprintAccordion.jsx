import { useState } from 'react';
import { ChevronDown, ChevronRight, Plus, Calendar } from 'lucide-react';
import SprintStateBadge from '../sprints/SprintStateBadge.jsx';
import BacklogIssueRow from './BacklogIssueRow.jsx';
import InlineCreateIssueRow from './InlineCreateIssueRow.jsx';
import { SURFACE } from '../../constants/jiraTokens.js';

/**
 * Mot khoi gap/mo tren man hinh Backlog.
 *
 * Dung chung cho CA hai truong hop: mot Sprint (group.sprintId != null) va khoi
 * Backlog (group.sprintId == null) - hai khoi nay giong het nhau ve bo cuc va deu
 * la vung tha khi keo cong viec, nen tach lam hai component se sinh trung lap.
 */
export default function SprintAccordion({
  group,
  canManageSprint,
  types = [],
  busy = false,
  onSelectItem,
  onStart,
  onComplete,
  onQuickCreate,
  droppableProps = {},
  isOver = false,
  renderRow,
}) {
  const isBacklog = group.sprintId == null;
  const [open, setOpen] = useState(true);
  const [creating, setCreating] = useState(false);

  return (
    <div
      {...droppableProps}
      style={{
        border: `1px solid ${isOver ? '#93C5FD' : SURFACE.border}`,
        borderRadius: 8,
        marginBottom: 14,
        backgroundColor: isOver ? '#F0F7FF' : '#FFFFFF',
        overflow: 'hidden',
        transition: 'border-color .15s ease, background-color .15s ease',
      }}
    >
      {/* ─── Header ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '10px 12px',
          backgroundColor: SURFACE.subtle,
          borderBottom: open ? `1px solid ${SURFACE.border}` : 'none',
          flexWrap: 'wrap',
        }}
      >
        <button
          onClick={() => setOpen((o) => !o)}
          title={open ? 'Thu gọn' : 'Mở rộng'}
          style={{
            border: 'none',
            background: 'transparent',
            cursor: 'pointer',
            color: SURFACE.textMuted,
            display: 'flex',
            padding: 0,
          }}
        >
          {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
        </button>

        <span style={{ fontWeight: 700, fontSize: 14, color: SURFACE.text }}>{group.name}</span>

        {!isBacklog && <SprintStateBadge state={group.state} />}

        <span style={{ fontSize: 12, color: SURFACE.textMuted }}>
          ({group.itemCount} công việc)
        </span>

        {!isBacklog && (group.startDate || group.endDate) && (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: SURFACE.textMuted }}>
            <Calendar size={12} />
            {formatRange(group.startDate, group.endDate)}
          </span>
        )}

        {/* Phan bo diem theo 3 nhom trang thai */}
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, marginLeft: 'auto' }}>
          <PointChip value={group.todoStoryPoints} bg="#DFE1E6" color="#42526E" title="Cần làm" />
          <PointChip value={group.inProgressStoryPoints} bg="#DEEBFF" color="#0052CC" title="Đang làm" />
          <PointChip value={group.doneStoryPoints} bg="#E3FCEF" color="#00875A" title="Hoàn thành" />
        </span>

        {!isBacklog && canManageSprint && group.state === 'FUTURE' && (
          <button
            disabled={busy || !group.canStart}
            title={group.canStart ? 'Bắt đầu Sprint' : 'Cần có công việc và không có Sprint nào đang chạy'}
            onClick={() => onStart?.(group)}
            style={primaryBtn(busy || !group.canStart)}
          >
            Bắt đầu Sprint
          </button>
        )}
        {!isBacklog && canManageSprint && group.state === 'ACTIVE' && (
          <button disabled={busy} onClick={() => onComplete?.(group)} style={primaryBtn(busy)}>
            Hoàn tất Sprint
          </button>
        )}
      </div>

      {/* ─── Danh sach cong viec ─── */}
      {open && (
        <div>
          {group.items?.length ? (
            group.items.map((item) =>
              renderRow ? (
                renderRow(item, group)
              ) : (
                <BacklogIssueRow key={item.id} item={item} onSelect={onSelectItem} />
              )
            )
          ) : (
            <div
              style={{
                padding: '18px 12px',
                textAlign: 'center',
                fontSize: 12,
                fontStyle: 'italic',
                color: SURFACE.textLight,
              }}
            >
              {isBacklog
                ? 'Backlog trống. Hãy tạo công việc mới.'
                : 'Kéo công việc từ Backlog vào Sprint này.'}
            </div>
          )}

          {/* Tao nhanh inline */}
          {creating ? (
            <InlineCreateIssueRow
              types={types}
              onCancel={() => setCreating(false)}
              onSubmit={async (payload) => {
                await onQuickCreate?.({ ...payload, sprintId: group.sprintId ?? null });
                setCreating(false);
              }}
            />
          ) : (
            <button
              onClick={() => setCreating(true)}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '8px 12px',
                border: 'none',
                background: 'transparent',
                color: SURFACE.textMuted,
                fontSize: 13,
                cursor: 'pointer',
                textAlign: 'left',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = SURFACE.subtle)}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <Plus size={14} /> Tạo công việc
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function PointChip({ value, bg, color, title }) {
  const n = Number(value || 0);
  return (
    <span
      title={title}
      style={{
        minWidth: 22,
        textAlign: 'center',
        padding: '1px 7px',
        borderRadius: 999,
        backgroundColor: bg,
        color,
        fontSize: 11,
        fontWeight: 700,
      }}
    >
      {n}
    </span>
  );
}

function primaryBtn(disabled) {
  return {
    padding: '5px 12px',
    borderRadius: 6,
    border: 'none',
    fontSize: 12,
    fontWeight: 600,
    cursor: disabled ? 'not-allowed' : 'pointer',
    backgroundColor: disabled ? '#E2E8F0' : '#2563EB',
    color: disabled ? '#94A3B8' : '#FFFFFF',
  };
}

function formatRange(start, end) {
  const f = (d) => {
    if (!d) return '';
    const dt = new Date(d);
    return `${String(dt.getDate()).padStart(2, '0')}/${String(dt.getMonth() + 1).padStart(2, '0')}`;
  };
  if (start && end) return `${f(start)} → ${f(end)}`;
  return f(start) || f(end);
}
