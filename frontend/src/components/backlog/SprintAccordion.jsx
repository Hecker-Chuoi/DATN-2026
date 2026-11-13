import { useState, useMemo } from 'react';
import { ChevronDown, ChevronRight, Plus, Calendar, Bookmark, Pencil, Trash2 } from 'lucide-react';
import SprintStateBadge from '../sprints/SprintStateBadge.jsx';
import BacklogIssueRow from './BacklogIssueRow.jsx';
import InlineCreateIssueRow from './InlineCreateIssueRow.jsx';
import { SURFACE } from '../../constants/jiraTokens.js';

/**
 * Một khối gập/mở trên màn hình Backlog (Sprint hoặc Backlog chung).
 * Hỗ trợ hiển thị dạng cây phân cấp:
 * Hạng mục lớn (Epic Header) -> Các công việc con thụt lề (Indented child rows).
 */
export default function SprintAccordion({
  group,
  canManageSprint,
  types = [],
  busy = false,
  startBlockedReason,
  onSelectItem,
  onStart,
  onEdit,
  onDelete,
  onComplete,
  onQuickCreate,
  droppableProps = {},
  isOver = false,
  renderRow,
}) {
  const isBacklog = group.sprintId == null;
  const [open, setOpen] = useState(true);
  const [creating, setCreating] = useState(false);
  const [collapsedEpics, setCollapsedEpics] = useState({});

  // Gom nhóm các công việc theo Hạng mục cha (Epic)
  const groupedSections = useMemo(() => {
    const rawItems = (group.items || []).filter((i) => i.hierarchyLevel !== 1);
    if (!rawItems.length) return [];

    const hasAnyEpic = rawItems.some((i) => i.epicId || i.parentId || i.epicCode || i.parentCode);
    if (!hasAnyEpic) {
      return [{ key: '__flat__', epic: null, items: rawItems }];
    }

    const map = new Map();
    for (const item of rawItems) {
      const eId = item.epicId || item.parentId || '__no_epic__';
      if (!map.has(eId)) {
        map.set(eId, {
          key: eId,
          epic:
            eId === '__no_epic__'
              ? null
              : {
                  id: eId,
                  code: item.epicCode || item.parentCode,
                  summary: item.epicSummary || item.parentSummary,
                  color: item.epicColor || '#7C3AED',
                },
          items: [],
        });
      }
      map.get(eId).items.push(item);
    }
    return Array.from(map.values());
  }, [group.items]);

  const toggleEpicCollapse = (epicKey) => {
    setCollapsedEpics((prev) => ({ ...prev, [epicKey]: !prev[epicKey] }));
  };

  const hasEpics = groupedSections.some((s) => s.epic != null);

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
          ({groupedSections.reduce((acc, s) => acc + s.items.length, 0)} công việc)
        </span>

        {!isBacklog && (group.startDate || group.endDate) && (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: SURFACE.textMuted }}>
            <Calendar size={12} />
            {formatRange(group.startDate, group.endDate)}
          </span>
        )}

        {/* Phân bổ điểm theo 3 nhóm trạng thái */}
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, marginLeft: 'auto' }}>
          <PointChip value={group.todoStoryPoints} bg="#DFE1E6" color="#42526E" title="Cần làm" />
          <PointChip value={group.inProgressStoryPoints} bg="#DEEBFF" color="#0052CC" title="Đang làm" />
          <PointChip value={group.doneStoryPoints} bg="#E3FCEF" color="#00875A" title="Hoàn thành" />
        </span>

        {!isBacklog && canManageSprint && group.state === 'FUTURE' && (
          <button
            onClick={() => onEdit?.(group)}
            disabled={busy}
            title="Sửa tên, mục tiêu, ngày dự kiến (ngày bắt đầu quyết định thứ tự bắt đầu)"
            style={iconBtn(busy)}
          >
            <Pencil size={13} />
          </button>
        )}
        {!isBacklog && canManageSprint && group.state === 'FUTURE' && (
          <button
            onClick={() => onDelete?.(group)}
            disabled={busy}
            title="Xóa (công việc bên trong trở về Kho việc, không bị xóa)"
            style={{ ...iconBtn(busy), color: '#DC2626' }}
          >
            <Trash2 size={13} />
          </button>
        )}
        {!isBacklog && canManageSprint && group.state === 'FUTURE' && (
          <button
            disabled={busy || !group.canStart}
            title={group.canStart ? (group.name?.toLowerCase()?.includes('giai đoạn') ? 'Bắt đầu giai đoạn' : 'Bắt đầu chu kỳ') : startBlockedReason || 'Chưa thể bắt đầu lúc này'}
            onClick={() => onStart?.(group)}
            style={primaryBtn(busy || !group.canStart)}
          >
            {group.name?.toLowerCase()?.includes('giai đoạn') ? 'Bắt đầu giai đoạn' : 'Bắt đầu chu kỳ'}
          </button>
        )}
        {!isBacklog && canManageSprint && group.state === 'ACTIVE' && (
          <button disabled={busy} onClick={() => onComplete?.(group)} style={primaryBtn(busy)}>
            {group.name?.toLowerCase()?.includes('giai đoạn') ? 'Hoàn tất giai đoạn' : 'Hoàn tất chu kỳ'}
          </button>
        )}
      </div>

      {/* ─── Danh sách công việc dạng cây ─── */}
      {open && (
        <div>
          {groupedSections.length ? (
            groupedSections.map((section) => {
              // Nhóm phẳng không có Epic
              if (!section.epic) {
                return (
                  <div key={section.key}>
                    {hasEpics && (
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                          padding: '6px 14px',
                          backgroundColor: '#F8FAFC',
                          borderTop: `1px solid ${SURFACE.border}`,
                          borderBottom: `1px solid ${SURFACE.border}`,
                          fontSize: 11.5,
                          fontWeight: 600,
                          color: '#64748B',
                        }}
                      >
                        <span>Công việc chưa gán Hạng mục lớn</span>
                        <span style={{ fontSize: 11, color: '#94A3B8', marginLeft: 'auto' }}>
                          {section.items.length} việc
                        </span>
                      </div>
                    )}
                    {section.items.map((item) =>
                      renderRow ? (
                        renderRow(item, group, false)
                      ) : (
                        <BacklogIssueRow key={item.id} item={item} onSelect={onSelectItem} isChild={false} />
                      )
                    )}
                  </div>
                );
              }

              // Nhóm có Hạng mục cha (Epic)
              const isCollapsed = Boolean(collapsedEpics[section.key]);
              return (
                <div key={section.key} style={{ borderBottom: `1px solid ${SURFACE.border}` }}>
                  {/* Epic Header */}
                  <div
                    onClick={() => toggleEpicCollapse(section.key)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '7px 12px 7px 14px',
                      backgroundColor: '#FAF5FF',
                      borderTop: `1px solid #F3E8FF`,
                      borderBottom: `1px solid #F3E8FF`,
                      cursor: 'pointer',
                      userSelect: 'none',
                    }}
                  >
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleEpicCollapse(section.key);
                      }}
                      style={{
                        border: 'none',
                        background: 'transparent',
                        cursor: 'pointer',
                        color: '#7C3AED',
                        display: 'flex',
                        padding: 0,
                      }}
                    >
                      {isCollapsed ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
                    </button>

                    <span
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelectItem?.(section.epic.id);
                      }}
                      title="Bấm để mở chi tiết Hạng mục lớn"
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                        padding: '2px 7px',
                        borderRadius: 4,
                        backgroundColor: section.epic.color || '#7C3AED',
                        color: '#FFFFFF',
                        fontSize: 10.5,
                        fontWeight: 700,
                        letterSpacing: 0.3,
                        cursor: 'pointer',
                      }}
                    >
                      <Bookmark size={10} /> {section.epic.code || 'HẠNG MỤC'}
                    </span>

                    <span
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelectItem?.(section.epic.id);
                      }}
                      title="Bấm để mở chi tiết Hạng mục lớn"
                      style={{
                        fontWeight: 700,
                        fontSize: 12.5,
                        color: '#581C87',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        cursor: 'pointer',
                      }}
                    >
                      {section.epic.summary || 'Hạng mục lớn'}
                    </span>

                    <span style={{ fontSize: 11, fontWeight: 600, color: '#9333EA', marginLeft: 'auto' }}>
                      {section.items.length} việc
                    </span>
                  </div>

                  {/* Danh sách công việc con thụt lề */}
                  {!isCollapsed && (
                    <div style={{ paddingLeft: 18, borderLeft: '3px solid #DDD6FE', marginLeft: 12 }}>
                      {section.items.map((item) =>
                        renderRow ? (
                          renderRow(item, group, true)
                        ) : (
                          <BacklogIssueRow key={item.id} item={item} onSelect={onSelectItem} isChild={true} />
                        )
                      )}
                    </div>
                  )}
                </div>
              );
            })
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
                ? 'Kho công việc hiện đang trống. Hãy tạo công việc mới.'
                : 'Kéo công việc từ kho việc vào chu kỳ này.'}
            </div>
          )}

          {/* Tạo nhanh inline */}
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

function iconBtn(disabled) {
  return {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 26,
    height: 26,
    borderRadius: 6,
    border: `1px solid ${SURFACE.border}`,
    backgroundColor: '#FFFFFF',
    color: SURFACE.textMuted,
    cursor: disabled ? 'not-allowed' : 'pointer',
    padding: 0,
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
