import { useMemo, useState } from 'react';
import {
  ChevronDown,
  ChevronRight,
  Layers,
  CheckCircle2,
  Clock,
  Calendar,
  User,
  ArrowRight,
  Maximize2,
  Minimize2,
  CheckSquare
} from 'lucide-react';
import { typeToken, priorityToken, statusToken, SURFACE } from '../../constants/jiraTokens.js';
import { getAvatarColor, getInitials } from '../../utils/avatarColor.js';
import { getStatusDisplayName } from '../../constants/workConstants.js';

const GRID_COLUMNS = 'minmax(320px, 1fr) 130px 160px 150px 90px';

export default function BoardListView({
  cards = [],
  onSelectItem,
  canTransition = false,
  onReload,
  projectId,
  isBusiness = false,
}) {
  const [collapsedIds, setCollapsedIds] = useState(new Set());

  const toggleCollapse = (id, e) => {
    e?.stopPropagation();
    setCollapsedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const expandAll = () => setCollapsedIds(new Set());
  const collapseAll = () => {
    const allParentIds = new Set();
    for (const c of cards) {
      if (c.hierarchyLevel === 1 || c.typeCode === 'EPIC') allParentIds.add(c.id);
      if (!c.isSubtask && c.hierarchyLevel !== 1) allParentIds.add(c.id);
    }
    setCollapsedIds(allParentIds);
  };

  // ─── Xây dựng cây phân cấp 3 tầng: Epic -> Task -> Subtask ───
  const { epicTree, orphanTasks, totalItemsCount } = useMemo(() => {
    const epicsMap = new Map();
    const tasksMap = new Map();
    const subtasks = [];

    // 1. Phân loại các cards
    for (const card of cards) {
      const isEpic = card.hierarchyLevel === 1 || card.typeCode === 'EPIC' || card.typeName?.toLowerCase()?.includes('hạng mục') || card.typeName?.toLowerCase()?.includes('giai đoạn');
      const isSub = Boolean(card.isSubtask || card.hierarchyLevel === 3);

      if (isEpic) {
        epicsMap.set(card.id, { ...card, tasks: [] });
      } else if (isSub) {
        subtasks.push(card);
      } else {
        tasksMap.set(card.id, { ...card, subtasks: [] });
      }
    }

    // 2. Tự động nhận diện và tạo Epic placeholder nếu task có epicId hoặc parentId trỏ tới Epic
    for (const task of tasksMap.values()) {
      const epicId = task.epicId || (task.parentId && !tasksMap.has(task.parentId) ? task.parentId : null);
      const epicCode = task.epicCode || task.parentCode;
      const epicSummary = task.epicSummary || task.parentSummary;
      const epicColor = task.epicColor || task.parentColor || '#8B5CF6';

      if (epicId && !epicsMap.has(epicId)) {
        epicsMap.set(epicId, {
          id: epicId,
          code: epicCode || 'EPIC',
          summary: epicSummary || (isBusiness ? 'Giai đoạn' : 'Hạng mục lớn'),
          typeColor: epicColor,
          typeCode: 'EPIC',
          typeName: isBusiness ? 'Giai đoạn' : 'Hạng mục lớn',
          hierarchyLevel: 1,
          isVirtual: true,
          tasks: [],
        });
      }
    }

    // 3. Ghép Subtask vào Task cha
    const orphanSubtasks = [];
    for (const sub of subtasks) {
      if (sub.parentId && tasksMap.has(sub.parentId)) {
        tasksMap.get(sub.parentId).subtasks.push(sub);
      } else {
        orphanSubtasks.push(sub);
      }
    }

    // 4. Ghép Task vào Epic
    const standalone = [];
    for (const task of tasksMap.values()) {
      const epicKey =
        (task.epicId && epicsMap.has(task.epicId) ? task.epicId : null) ||
        (task.parentId && epicsMap.has(task.parentId) ? task.parentId : null);

      if (epicKey && epicsMap.has(epicKey)) {
        epicsMap.get(epicKey).tasks.push(task);
      } else {
        standalone.push(task);
      }
    }

    // Nếu có subtask lẻ loi không tìm thấy task cha, đưa vào standalone
    if (orphanSubtasks.length > 0) {
      standalone.push(...orphanSubtasks);
    }

    return {
      epicTree: Array.from(epicsMap.values()),
      orphanTasks: standalone,
      totalItemsCount: cards.length,
    };
  }, [cards]);

  if (cards.length === 0) {
    return (
      <div
        style={{
          padding: '48px 24px',
          textAlign: 'center',
          backgroundColor: '#FFFFFF',
          borderRadius: 8,
          border: `1px solid ${SURFACE.border}`,
          color: SURFACE.textMuted,
          fontSize: 13,
        }}
      >
        Không có công việc nào phù hợp với bộ lọc hiện tại.
      </div>
    );
  }

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        borderRadius: 8,
        border: `1px solid ${SURFACE.border}`,
        overflow: 'hidden',
        boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
      }}
    >
      {/* ─── Toolbar Danh sách ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 16px',
          backgroundColor: '#F8FAFC',
          borderBottom: `1px solid ${SURFACE.border}`,
          fontSize: 12,
          color: SURFACE.textMuted,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontWeight: 600, color: SURFACE.text }}>
            Cấu trúc phân cấp công việc ({totalItemsCount} mục)
          </span>
          <span style={{ color: '#CBD5E1' }}>|</span>
          <span>{epicTree.length} {isBusiness ? 'Giai đoạn' : 'Hạng mục lớn'}</span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <button
            onClick={expandAll}
            style={toolBtn}
            title="Mở rộng tất cả các nhánh"
          >
            <Maximize2 size={12} /> Mở rộng
          </button>
          <button
            onClick={collapseAll}
            style={toolBtn}
            title="Thu gọn tất cả các nhánh"
          >
            <Minimize2 size={12} /> Thu gọn
          </button>
        </div>
      </div>

      {/* ─── Header Các Cột ─── */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: GRID_COLUMNS,
          padding: '8px 16px',
          backgroundColor: '#F1F5F9',
          borderBottom: `1px solid ${SURFACE.border}`,
          fontSize: 11,
          fontWeight: 700,
          color: '#475569',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}
      >
        <div>{isBusiness ? 'Công việc / Giai đoạn' : 'Công việc / Hạng mục'}</div>
        <div>Trạng thái</div>
        <div>Người thực hiện</div>
        <div>Thời gian đến hạn</div>
        <div style={{ textAlign: 'right' }}>Ước lượng</div>
      </div>

      {/* ─── Thân Danh Sách: Cây Phân Cấp ─── */}
      <div style={{ divideY: '1px solid #F1F5F9' }}>
        {/* Render từng Hạng mục lớn (Epic) */}
        {epicTree.map((epic) => {
          const isCollapsed = collapsedIds.has(epic.id);
          const tasks = epic.tasks || [];
          const totalTasks = tasks.length;
          const doneTasks = tasks.filter((t) => t.statusCategory === 'DONE').length;
          const percent = totalTasks > 0 ? Math.round((doneTasks / totalTasks) * 100) : 0;
          const epicDueDate = epic.dueDate || (tasks.length > 0
            ? tasks.map((t) => t.dueDate).filter(Boolean).sort().reverse()[0]
            : null);

          return (
            <div key={epic.id} style={{ borderBottom: `1px solid ${SURFACE.border}` }}>
              {/* ─── LEVEL 1: Hạng mục lớn (Epic Row) ─── */}
              <div
                onClick={() => !epic.isVirtual && onSelectItem?.(epic.id)}
                style={{
                  display: 'grid',
                  gridTemplateColumns: GRID_COLUMNS,
                  alignItems: 'center',
                  padding: '9px 16px 9px 12px',
                  backgroundColor: '#FAF5FF',
                  borderLeft: `4px solid ${epic.typeColor || '#8B5CF6'}`,
                  cursor: epic.isVirtual ? 'default' : 'pointer',
                  transition: 'background-color 0.15s ease',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F5EDFD')}
                onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FAF5FF')}
              >
                {/* Cột 1: Expand icon + Epic icon + Code + Summary + Progress */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, paddingRight: 12 }}>
                  <button
                    type="button"
                    onClick={(e) => toggleCollapse(epic.id, e)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: '#7E22CE',
                      cursor: 'pointer',
                      padding: 2,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      borderRadius: 4,
                    }}
                  >
                    {isCollapsed ? <ChevronRight size={15} /> : <ChevronDown size={15} />}
                  </button>

                  {/* Icon Epic */}
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      fontSize: 10,
                      fontWeight: 700,
                      color: '#7E22CE',
                      backgroundColor: '#F3E8FF',
                      border: '1px solid #E9D5FF',
                      padding: '2px 6px',
                      borderRadius: 4,
                      flexShrink: 0,
                    }}
                  >
                    <Layers size={11} /> {isBusiness ? 'GIAI ĐOẠN' : 'HẠNG MỤC LỚN'}
                  </span>

                  {/* Mã code */}
                  <span
                    style={{
                      fontSize: 12,
                      fontWeight: 700,
                      color: '#6B21A8',
                      flexShrink: 0,
                    }}
                  >
                    {epic.code}
                  </span>

                  {/* Tên tóm tắt */}
                  <span
                    title={epic.summary}
                    style={{
                      fontSize: 13,
                      fontWeight: 700,
                      color: '#0F172A',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      flex: 1,
                    }}
                  >
                    {epic.summary}
                  </span>

                  {/* Tiến độ hoàn thành */}
                  {totalTasks > 0 && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                        flexShrink: 0,
                        fontSize: 11,
                        color: '#6B21A8',
                        fontWeight: 600,
                      }}
                      title={`${doneTasks}/${totalTasks} công việc đã xong (${percent}%)`}
                    >
                      <div
                        style={{
                          width: 44,
                          height: 5,
                          borderRadius: 3,
                          backgroundColor: '#E9D5FF',
                          overflow: 'hidden',
                        }}
                      >
                        <div
                          style={{
                            width: `${percent}%`,
                            height: '100%',
                            backgroundColor: '#10B981',
                            borderRadius: 3,
                          }}
                        />
                      </div>
                      <span>{doneTasks}/{totalTasks}</span>
                    </div>
                  )}
                </div>

                {/* Cột 2: Trạng thái */}
                <div>
                  {epic.statusName ? (
                    <StatusBadge statusName={epic.statusName} category={epic.statusCategory} />
                  ) : (
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>—</span>
                  )}
                </div>

                {/* Cột 3: Người thực hiện */}
                <div>
                  <AssigneeCell name={epic.assigneeName} />
                </div>

                {/* Cột 4: Thời gian đến hạn */}
                <div>
                  <DueDateCell
                    dueDate={epicDueDate}
                    isOverdue={epic.isOverdue}
                    isDone={epic.statusCategory === 'DONE'}
                  />
                </div>

                {/* Cột 5: Ước lượng */}
                <div style={{ textAlign: 'right', fontSize: 12, fontWeight: 600, color: '#64748B' }}>
                  {epic.storyPoints != null ? `${epic.storyPoints} SP` : '—'}
                </div>
              </div>

              {/* ─── LEVEL 2: Các công việc thuộc Hạng mục lớn (LÙI 1 CHÚT) ─── */}
              {!isCollapsed && (
                <div style={{ backgroundColor: '#FFFFFF' }}>
                  {tasks.length === 0 ? (
                    <div
                      style={{
                        padding: '8px 16px 8px 46px',
                        fontSize: 12,
                        color: '#94A3B8',
                        fontStyle: 'italic',
                      }}
                    >
                      Chưa có công việc nào trong chu kỳ thuộc ${isBusiness ? 'giai đoạn' : 'hạng mục lớn'} này.
                    </div>
                  ) : (
                    tasks.map((task) => (
                      <TaskTreeRow
                        key={task.id}
                        task={task}
                        collapsedIds={collapsedIds}
                        toggleCollapse={toggleCollapse}
                        onSelectItem={onSelectItem}
                      />
                    ))
                  )}
                </div>
              )}
            </div>
          );
        })}

        {/* ─── Render các công việc độc lập (Không thuộc Epic nào) ─── */}
        {orphanTasks.length > 0 && (
          <div style={{ borderBottom: `1px solid ${SURFACE.border}` }}>
            <div
              style={{
                padding: '8px 16px',
                backgroundColor: '#F8FAFC',
                borderLeft: '4px solid #94A3B8',
                fontSize: 11,
                fontWeight: 700,
                color: '#64748B',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
              }}
            >
              Công việc độc lập ({orphanTasks.length})
            </div>
            {orphanTasks.map((task) => (
              <TaskTreeRow
                key={task.id}
                task={task}
                collapsedIds={collapsedIds}
                toggleCollapse={toggleCollapse}
                onSelectItem={onSelectItem}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Row hiển thị một Công việc (Task/Story/Bug - Level 2) kèm các công việc con (Subtasks - Level 3)
 */
function TaskTreeRow({ task, collapsedIds, toggleCollapse, onSelectItem }) {
  const subtasks = task.subtasks || [];
  const hasSubtasks = subtasks.length > 0;
  const isCollapsed = collapsedIds.has(task.id);
  const type = typeToken(task.typeCode, task.typeName);
  const isDone = task.statusCategory === 'DONE';

  return (
    <div>
      {/* ─── LEVEL 2: Công việc (Lùi 36px) ─── */}
      <div
        onClick={() => onSelectItem?.(task.id)}
        style={{
          display: 'grid',
          gridTemplateColumns: GRID_COLUMNS,
          alignItems: 'center',
          padding: '8px 16px 8px 36px', // Lùi 1 chút dưới Hạng mục lớn
          borderBottom: '1px solid #F1F5F9',
          cursor: 'pointer',
          backgroundColor: '#FFFFFF',
          transition: 'background-color 0.15s ease',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
        onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
      >
        {/* Cột 1: Nhánh cây + Expand + Loại icon + Mã + Tên */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, paddingRight: 12 }}>
          {/* Ký hiệu nhánh cây */}
          <span
            style={{
              color: '#94A3B8',
              fontSize: 13,
              fontWeight: 700,
              userSelect: 'none',
              flexShrink: 0,
            }}
          >
            ↳
          </span>

          {/* Nút collapse nếu có subtasks */}
          {hasSubtasks ? (
            <button
              type="button"
              onClick={(e) => toggleCollapse(task.id, e)}
              style={{
                background: 'none',
                border: 'none',
                color: '#64748B',
                cursor: 'pointer',
                padding: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {isCollapsed ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
            </button>
          ) : (
            <span style={{ width: 14 }} />
          )}

          {/* Icon loại công việc */}
          <span
            title={type.label}
            style={{
              width: 16,
              height: 16,
              borderRadius: 3,
              backgroundColor: task.typeColor || type.color,
              display: 'inline-block',
              flexShrink: 0,
            }}
          />

          {/* Mã công việc */}
          <span
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: SURFACE.textMuted,
              flexShrink: 0,
              minWidth: 70,
              textDecoration: isDone ? 'line-through' : 'none',
            }}
          >
            {task.code}
          </span>

          {/* Tiêu đề */}
          <span
            title={task.summary}
            style={{
              fontSize: 13,
              color: SURFACE.text,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              flex: 1,
              fontWeight: 500,
              textDecoration: isDone ? 'line-through' : 'none',
            }}
          >
            {task.summary}
          </span>

          {/* Huy hiệu số lượng subtasks */}
          {hasSubtasks && (
            <span
              style={{
                fontSize: 10,
                color: '#64748B',
                backgroundColor: '#F1F5F9',
                padding: '1px 5px',
                borderRadius: 4,
                flexShrink: 0,
                fontWeight: 600,
              }}
            >
              {subtasks.filter((s) => s.statusCategory === 'DONE').length}/{subtasks.length} con
            </span>
          )}
        </div>

        {/* Cột 2: Trạng thái */}
        <div>
          <StatusBadge statusName={task.statusName} category={task.statusCategory} />
        </div>

        {/* Cột 3: Người thực hiện */}
        <div>
          <AssigneeCell name={task.assigneeName} />
        </div>

        {/* Cột 4: Thời gian đến hạn */}
        <div>
          <DueDateCell
            dueDate={task.dueDate}
            isOverdue={task.isOverdue}
            isDone={task.statusCategory === 'DONE'}
          />
        </div>

        {/* Cột 5: Ước lượng */}
        <div style={{ textAlign: 'right', fontSize: 12, fontWeight: 600, color: '#64748B' }}>
          {task.storyPoints != null ? `${task.storyPoints} SP` : '—'}
        </div>
      </div>

      {/* ─── LEVEL 3: Các công việc con (Subtasks - LÙI TIẾP 1 CHÚT: 68px) ─── */}
      {!isCollapsed &&
        subtasks.map((sub) => {
          const subDone = sub.statusCategory === 'DONE';
          const subType = typeToken(sub.typeCode, sub.typeName);

          return (
            <div
              key={sub.id}
              onClick={() => onSelectItem?.(sub.id)}
              style={{
                display: 'grid',
                gridTemplateColumns: GRID_COLUMNS,
                alignItems: 'center',
                padding: '7px 16px 7px 68px', // Lùi sâu hơn cho công việc con
                borderBottom: '1px solid #F8FAFC',
                backgroundColor: '#FAFAFC',
                cursor: 'pointer',
                transition: 'background-color 0.15s ease',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FAFAFC')}
            >
              {/* Cột 1: Ký hiệu nhánh ↳ + Icon subtask + Code + Summary */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, paddingRight: 12 }}>
                <span
                  style={{
                    color: '#0284C7',
                    fontSize: 12,
                    fontWeight: 700,
                    userSelect: 'none',
                    flexShrink: 0,
                  }}
                >
                  ↳
                </span>

                <span
                  title={subType.label}
                  style={{
                    width: 14,
                    height: 14,
                    borderRadius: 3,
                    backgroundColor: sub.typeColor || '#0284C7',
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    color: '#FFFFFF',
                  }}
                >
                  <CheckSquare size={10} />
                </span>

                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    color: SURFACE.textMuted,
                    flexShrink: 0,
                    minWidth: 70,
                    textDecoration: subDone ? 'line-through' : 'none',
                  }}
                >
                  {sub.code}
                </span>

                <span
                  title={sub.summary}
                  style={{
                    fontSize: 12,
                    color: subDone ? '#94A3B8' : '#334155',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    flex: 1,
                    textDecoration: subDone ? 'line-through' : 'none',
                  }}
                >
                  {sub.summary}
                </span>
              </div>

              {/* Cột 2: Trạng thái */}
              <div>
                <StatusBadge statusName={sub.statusName} category={sub.statusCategory} isSmall />
              </div>

              {/* Cột 3: Người thực hiện */}
              <div>
                <AssigneeCell name={sub.assigneeName} isSmall />
              </div>

              {/* Cột 4: Thời gian đến hạn */}
              <div>
                <DueDateCell
                  dueDate={sub.dueDate}
                  isOverdue={sub.isOverdue}
                  isDone={sub.statusCategory === 'DONE'}
                  isSmall
                />
              </div>

              {/* Cột 5: Ước lượng */}
              <div style={{ textAlign: 'right', fontSize: 11, color: '#94A3B8' }}>
                {sub.storyPoints != null ? `${sub.storyPoints} SP` : '—'}
              </div>
            </div>
          );
        })}
    </div>
  );
}

function StatusBadge({ statusName, category, isSmall = false }) {
  const token = statusToken(category);
  return (
    <span
      style={{
        display: 'inline-block',
        padding: isSmall ? '1px 6px' : '2px 8px',
        borderRadius: 4,
        fontSize: isSmall ? 10 : 11,
        fontWeight: 700,
        textTransform: 'uppercase',
        backgroundColor: token.bg,
        color: token.text,
        border: `1px solid ${token.border || 'transparent'}`,
        whiteSpace: 'nowrap',
      }}
    >
      {getStatusDisplayName(statusName)}
    </span>
  );
}

function AssigneeCell({ name, isSmall = false }) {
  if (!name) {
    return <span style={{ fontSize: 12, color: '#94A3B8', fontStyle: 'italic' }}>Chưa phân công</span>;
  }
  const color = getAvatarColor(name);
  const initials = getInitials(name);

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span
        style={{
          width: isSmall ? 18 : 22,
          height: isSmall ? 18 : 22,
          borderRadius: '50%',
          backgroundColor: color.bg,
          color: color.text,
          fontSize: isSmall ? 9 : 10,
          fontWeight: 700,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        {initials}
      </span>
      <span
        title={name}
        style={{
          fontSize: isSmall ? 11 : 12,
          color: SURFACE.text,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          maxWidth: 130,
        }}
      >
        {name}
      </span>
    </div>
  );
}

const toolBtn = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 4,
  padding: '4px 8px',
  borderRadius: 4,
  border: '1px solid #CBD5E1',
  backgroundColor: '#FFFFFF',
  color: '#475569',
  fontSize: 11,
  fontWeight: 600,
  cursor: 'pointer',
};

function formatDueDate(dateStr) {
  if (!dateStr) return null;
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return null;

  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();

  return `${day}/${month}/${year}`;
}

function DueDateCell({ dueDate, isOverdue = false, isDone = false, isSmall = false }) {
  if (!dueDate) {
    return <span style={{ fontSize: isSmall ? 11 : 12, color: '#94A3B8' }}>—</span>;
  }

  const formatted = formatDueDate(dueDate);
  if (!formatted) {
    return <span style={{ fontSize: isSmall ? 11 : 12, color: '#94A3B8' }}>—</span>;
  }

  // So sánh ngày hôm nay để nhận diện quá hạn / hôm nay / ngày mai
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(dueDate);
  target.setHours(0, 0, 0, 0);

  const diffTime = target.getTime() - today.getTime();
  const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));

  const overdue = !isDone && (isOverdue || diffDays < 0);
  const isToday = !isDone && diffDays === 0;
  const isTomorrow = !isDone && diffDays === 1;

  let textColor = '#475569';
  let bgColor = 'transparent';
  let badgeText = null;

  if (isDone) {
    textColor = '#94A3B8';
  } else if (overdue) {
    textColor = '#DC2626';
    bgColor = '#FEF2F2';
    badgeText = 'Quá hạn';
  } else if (isToday) {
    textColor = '#D97706';
    bgColor = '#FFFBEB';
    badgeText = 'Hôm nay';
  } else if (isTomorrow) {
    textColor = '#2563EB';
    bgColor = '#EFF6FF';
    badgeText = 'Ngày mai';
  }

  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 5 }}>
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          fontSize: isSmall ? 11 : 12,
          fontWeight: overdue || isToday ? 600 : 500,
          color: textColor,
          backgroundColor: bgColor,
          padding: bgColor !== 'transparent' ? '1px 6px' : '0',
          borderRadius: 4,
          border: overdue ? '1px solid #FECACA' : isToday ? '1px solid #FDE68A' : 'none',
        }}
        title={`Hạn chót: ${formatted}${overdue ? ' (Đã quá hạn)' : ''}`}
      >
        <Calendar size={isSmall ? 11 : 12} />
        <span>{formatted}</span>
      </span>
      {badgeText && !isSmall && (
        <span
          style={{
            fontSize: 9,
            fontWeight: 700,
            padding: '1px 5px',
            borderRadius: 3,
            backgroundColor: overdue ? '#EF4444' : isToday ? '#F59E0B' : '#3B82F6',
            color: '#FFFFFF',
            letterSpacing: '0.02em',
          }}
        >
          {badgeText}
        </span>
      )}
    </div>
  );
}
