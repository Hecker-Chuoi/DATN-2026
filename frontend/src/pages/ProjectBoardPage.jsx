import { useEffect, useMemo, useState } from 'react';
import { Search, Layers, CalendarClock, X, Kanban, ListTree, Zap, CheckSquare } from 'lucide-react';
import toast from 'react-hot-toast';

import { useProjectContext } from '../context/ProjectContext.jsx';
import { useBoardData } from '../hooks/useBoardData.js';
import { boardApi, sprintApi } from '../api.js';
import { SURFACE } from '../constants/jiraTokens.js';
import { getTypeDisplayName, getStatusDisplayName } from '../constants/workConstants.js';
import { boardTypeOf } from '../utils/projectType.js';
import InteractiveBoard from '../components/boards/InteractiveBoard.jsx';
import BoardListView from '../components/boards/BoardListView.jsx';
import SprintStateBadge from '../components/sprints/SprintStateBadge.jsx';
import CompleteSprintModal from '../components/sprints/CompleteSprintModal.jsx';

/**
 * Route /board. Hỗ trợ 2 chế độ hiển thị:
 * - Bảng (Kanban Board): kéo thả thẻ theo các cột trạng thái
 * - Danh sách (List View): cấu trúc phân cấp cây Hạng mục lớn -> Công việc -> Công việc con
 */
export default function ProjectBoardPage() {
  const { projectId, project, canTransitionIssue, canManageSprint, isAdmin, openItemDetail, refreshKey, bumpRefresh } =
    useProjectContext();

  const [boardId, setBoardId] = useState(null);
  const [boardsLoading, setBoardsLoading] = useState(true);
  const [q, setQ] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [completeOpen, setCompleteOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [futureSprints, setFutureSprints] = useState([]);

  // Lưu chế độ hiển thị (board / list) vào localStorage
  const [viewMode, setViewMode] = useState(() => {
    try {
      return localStorage.getItem('board_view_mode') || 'board';
    } catch {
      return 'board';
    }
  });

  const handleViewModeChange = (mode) => {
    setViewMode(mode);
    try {
      localStorage.setItem('board_view_mode', mode);
    } catch {}
  };

  // Dự án hiện chỉ có một bảng (do seed tự động khi tạo dự án).
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        setBoardsLoading(true);
        const res = await boardApi.listByProject(projectId);
        const boards = res?.data || [];
        if (!cancelled) setBoardId(boards[0]?.id ?? null);
      } catch (err) {
        console.warn('Không tải được danh sách bảng:', err);
      } finally {
        if (!cancelled) setBoardsLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId]);

  const board = useBoardData(projectId, boardId);
  const { data, columns, loading, error, reload, forbiddenColumnIds, setData } = board;

  // Đồng bộ lại khi công việc được tạo/sửa ở nơi khác (modal chi tiết, tạo nhanh...).
  useEffect(() => {
    if (boardId && refreshKey) reload({ silent: true });
  }, [refreshKey, boardId, reload]);

  const filteredColumns = useMemo(
    () =>
      columns.map((c) => ({
        ...c,
        items: c.items.filter((i) => matches(i, q, typeFilter, statusFilter)),
      })),
    [columns, q, typeFilter, statusFilter]
  );

  const allFilteredCards = useMemo(() => {
    return (data?.cards || []).filter((i) =>
      matches(i, q, typeFilter, statusFilter)
    );
  }, [data?.cards, q, typeFilter, statusFilter]);

  // Nhóm các loại công việc theo Cấp bậc (Hierarchy Level):
  // Cấp 1 (Hạng mục lớn: Epic, CTF...), Cấp 0 (Tiêu chuẩn: Task, Story, Bug...), Cấp -1 (Công việc con: Subtask...)
  const groupedTypes = useMemo(() => {
    const types = project?.workItemTypes || [];
    const level1 = [];
    const level0 = [];
    const levelMinus1 = [];

    for (const t of types) {
      const code = String(t.code || t.name || '').toUpperCase().replace('-', '_');
      const item = {
        key: code,
        label: getTypeDisplayName(t.name || t.code),
        code: t.code,
        name: t.name,
        color: t.colorHex || '#3B82F6',
        hierarchyLevel: t.hierarchyLevel,
      };

      if (t.hierarchyLevel === 1) {
        level1.push(item);
      } else if (t.hierarchyLevel === -1 || t.isSubtask) {
        levelMinus1.push(item);
      } else {
        level0.push(item);
      }
    }

    // Sắp xếp trong nhóm: EPIC lên đầu nhóm 1, TASK lên đầu nhóm 0, SUBTASK lên đầu nhóm -1
    level1.sort((a, b) => (a.code === 'EPIC' ? -1 : b.code === 'EPIC' ? 1 : a.label.localeCompare(b.label)));
    level0.sort((a, b) => (a.code === 'TASK' ? -1 : b.code === 'TASK' ? 1 : a.label.localeCompare(b.label)));
    levelMinus1.sort((a, b) => (a.code === 'SUBTASK' ? -1 : b.code === 'SUBTASK' ? 1 : a.label.localeCompare(b.label)));

    return { level1, level0, levelMinus1 };
  }, [project?.workItemTypes]);

  // Số thẻ theo từng trạng thái
  const statusCounts = useMemo(() => {
    const counts = new Map();
    for (const card of data?.cards || []) {
      if (card.statusName) {
        counts.set(card.statusName, (counts.get(card.statusName) || 0) + 1);
      }
    }
    return counts;
  }, [data?.cards]);

  // Nhóm các trạng thái theo Category: Chuẩn bị / Cần làm (TODO), Đang triển khai (IN_PROGRESS), Hoàn thành / Nghiệm thu (DONE)
  const groupedStatuses = useMemo(() => {
    const categoryMap = new Map();
    for (const card of data?.cards || []) {
      if (card.statusName && card.statusCategory) {
        categoryMap.set(card.statusName, card.statusCategory);
      }
    }
    for (const col of data?.columns || []) {
      for (const s of col.statuses || []) {
        if (s.name && s.category) categoryMap.set(s.name, s.category);
      }
    }

    const todo = [];
    const inProgress = [];
    const done = [];

    for (const [name, count] of statusCounts.entries()) {
      const cat = categoryMap.get(name) || 'TODO';
      if (cat === 'DONE') done.push({ name, count });
      else if (cat === 'IN_PROGRESS') inProgress.push({ name, count });
      else todo.push({ name, count });
    }

    return { todo, inProgress, done };
  }, [data?.cards, data?.columns, statusCounts]);

  /** InteractiveBoard trả về mảng cột đã cập nhật; ghi ngược vào `cards` phẳng. */
  const applyColumns = (nextColumns) => {
    setData((prev) => {
      if (!prev) return prev;
      return { ...prev, cards: nextColumns.flatMap((c) => c.items) };
    });
  };

  const openComplete = async () => {
    try {
      const res = await sprintApi.listByProject(projectId, { state: 'FUTURE' });
      setFutureSprints((res?.data || []).map((s) => ({ ...s, sprintId: s.id })));
    } catch {
      setFutureSprints([]);
    }
    setCompleteOpen(true);
  };

  if (boardsLoading || loading) return <div style={centered}>Đang tải bảng công việc...</div>;
  if (!boardId) return <div style={centered}>Dự án chưa có bảng công việc nào.</div>;
  if (error) {
    return (
      <div style={centered}>
        Không tải được bảng.{' '}
        <button onClick={() => reload()} style={linkBtn}>Thử lại</button>
      </div>
    );
  }

  const hasFilter = q || typeFilter !== 'ALL' || statusFilter !== 'ALL';

  const isScrum = boardTypeOf(project, data) === 'SCRUM' || data?.boardType === 'SCRUM';
  const sprint = data?.activeSprint;

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '14px 24px 32px 24px' }}>
      {/* ─── Thanh Sprint (chỉ với bảng Scrum) ─── */}
      {isScrum && (
        sprint ? (
          <div style={sprintBar}>
            <span style={{ fontSize: 14, fontWeight: 700, color: SURFACE.text }}>{sprint.name}</span>
            <SprintStateBadge state={sprint.state} />
            {sprint.goal && (
              <span style={{ fontSize: 12, color: SURFACE.textMuted, maxWidth: 380, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {sprint.goal}
              </span>
            )}
            {sprint.daysRemaining != null && (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: sprint.isOverdue ? '#DC2626' : SURFACE.textMuted }}>
                <CalendarClock size={13} />
                {sprint.daysRemaining >= 0 ? `Còn ${sprint.daysRemaining} ngày` : `Quá hạn ${-sprint.daysRemaining} ngày`}
              </span>
            )}
            {(canManageSprint || isAdmin) && (
              <button onClick={openComplete} disabled={busy} style={primaryBtn(busy)}>
                Hoàn tất chu kỳ
              </button>
            )}
          </div>
        ) : (
          <div style={emptyHint}>
            Chưa có chu kỳ nào đang chạy. Hãy sang tab <b>Kho việc & Kế hoạch</b> để bắt đầu một chu kỳ.
          </div>
        )
      )}

      {/* ─── Thanh Bộ Lọc & Switcher Chế độ hiển thị ─── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14, flexWrap: 'wrap' }}>
        <div style={{ position: 'relative', width: 200 }}>
          <Search size={14} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: SURFACE.textLight }} />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Tìm trên bảng..."
            style={{ width: '100%', padding: '6px 10px 6px 28px', borderRadius: 6, border: `1px solid ${SURFACE.border}`, fontSize: 13, outline: 'none', color: SURFACE.text }}
          />
        </div>

        {/* Nút Tất cả */}
        <button
          type="button"
          onClick={() => setTypeFilter('ALL')}
          style={{
            padding: '5px 12px',
            borderRadius: 6,
            fontSize: 12,
            fontWeight: typeFilter === 'ALL' ? 700 : 500,
            cursor: 'pointer',
            border: `1px solid ${typeFilter === 'ALL' ? '#93C5FD' : SURFACE.border}`,
            backgroundColor: typeFilter === 'ALL' ? '#EFF6FF' : '#FFFFFF',
            color: typeFilter === 'ALL' ? '#1D4ED8' : SURFACE.text,
            transition: 'all 0.15s ease',
          }}
        >
          Tất cả
        </button>

        {/* ── Nhóm Cấp 1 (Hạng mục lớn: Epic, CTF...) ── */}
        {groupedTypes.level1.length > 0 && (
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              backgroundColor: '#FAF5FF',
              border: `1px solid ${typeFilter === 'LEVEL_1' || groupedTypes.level1.some((t) => typeFilter === t.key) ? '#C084FC' : '#E9D5FF'}`,
              borderRadius: 8,
              padding: '2px 4px',
              gap: 4,
            }}
          >
            <button
              type="button"
              onClick={() => setTypeFilter(typeFilter === 'LEVEL_1' ? 'ALL' : 'LEVEL_1')}
              title="Lọc tất cả công việc Cấp 1 (Hạng mục lớn)"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
                padding: '3px 8px',
                borderRadius: 5,
                border: 'none',
                backgroundColor: typeFilter === 'LEVEL_1' ? '#7E22CE' : 'transparent',
                color: typeFilter === 'LEVEL_1' ? '#FFFFFF' : '#7E22CE',
                fontSize: 11,
                fontWeight: 700,
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              <Zap size={11} /> Cấp 1
            </button>

            {groupedTypes.level1.map((t) => {
              const active = typeFilter === t.key;
              return (
                <button
                  key={t.key}
                  type="button"
                  onClick={() => setTypeFilter(active ? 'ALL' : t.key)}
                  style={{
                    padding: '3px 9px',
                    borderRadius: 5,
                    fontSize: 12,
                    fontWeight: active ? 700 : 500,
                    cursor: 'pointer',
                    border: active ? '1px solid #7E22CE' : '1px solid transparent',
                    backgroundColor: active ? '#7E22CE' : '#FFFFFF',
                    color: active ? '#FFFFFF' : '#581C87',
                    boxShadow: active ? '0 1px 2px rgba(126,34,206,0.2)' : '0 1px 1px rgba(0,0,0,0.04)',
                    transition: 'all 0.15s ease',
                  }}
                >
                  {t.label}
                </button>
              );
            })}
          </div>
        )}

        {/* ── Nhóm Cấp 0 (Tiêu chuẩn: Task, Story, Bug...) ── */}
        {groupedTypes.level0.length > 0 && (
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              backgroundColor: '#EFF6FF',
              border: `1px solid ${typeFilter === 'LEVEL_0' || groupedTypes.level0.some((t) => typeFilter === t.key) ? '#93C5FD' : '#BFDBFE'}`,
              borderRadius: 8,
              padding: '2px 4px',
              gap: 4,
            }}
          >
            <button
              type="button"
              onClick={() => setTypeFilter(typeFilter === 'LEVEL_0' ? 'ALL' : 'LEVEL_0')}
              title="Lọc tất cả công việc Cấp 0 (Tiêu chuẩn)"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
                padding: '3px 8px',
                borderRadius: 5,
                border: 'none',
                backgroundColor: typeFilter === 'LEVEL_0' ? '#2563EB' : 'transparent',
                color: typeFilter === 'LEVEL_0' ? '#FFFFFF' : '#1D4ED8',
                fontSize: 11,
                fontWeight: 700,
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              <CheckSquare size={11} /> Cấp 0
            </button>

            {groupedTypes.level0.map((t) => {
              const active = typeFilter === t.key;
              return (
                <button
                  key={t.key}
                  type="button"
                  onClick={() => setTypeFilter(active ? 'ALL' : t.key)}
                  style={{
                    padding: '3px 9px',
                    borderRadius: 5,
                    fontSize: 12,
                    fontWeight: active ? 700 : 500,
                    cursor: 'pointer',
                    border: active ? '1px solid #2563EB' : '1px solid transparent',
                    backgroundColor: active ? '#2563EB' : '#FFFFFF',
                    color: active ? '#FFFFFF' : '#1E40AF',
                    boxShadow: active ? '0 1px 2px rgba(37,99,235,0.2)' : '0 1px 1px rgba(0,0,0,0.04)',
                    transition: 'all 0.15s ease',
                  }}
                >
                  {t.label}
                </button>
              );
            })}
          </div>
        )}

        {/* ── Nhóm Cấp -1 (Công việc con: Subtasks...) ── */}
        {groupedTypes.levelMinus1.length > 0 && (
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              backgroundColor: '#ECFEFF',
              border: `1px solid ${typeFilter === 'LEVEL_-1' || groupedTypes.levelMinus1.some((t) => typeFilter === t.key) ? '#67E8F9' : '#A5F3FC'}`,
              borderRadius: 8,
              padding: '2px 4px',
              gap: 4,
            }}
          >
            <button
              type="button"
              onClick={() => setTypeFilter(typeFilter === 'LEVEL_-1' ? 'ALL' : 'LEVEL_-1')}
              title="Lọc tất cả công việc Cấp -1 (Công việc con)"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
                padding: '3px 8px',
                borderRadius: 5,
                border: 'none',
                backgroundColor: typeFilter === 'LEVEL_-1' ? '#0891B2' : 'transparent',
                color: typeFilter === 'LEVEL_-1' ? '#FFFFFF' : '#0E7490',
                fontSize: 11,
                fontWeight: 700,
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              <Layers size={11} /> Cấp -1
            </button>

            {groupedTypes.levelMinus1.map((t) => {
              const active = typeFilter === t.key;
              return (
                <button
                  key={t.key}
                  type="button"
                  onClick={() => setTypeFilter(active ? 'ALL' : t.key)}
                  style={{
                    padding: '3px 9px',
                    borderRadius: 5,
                    fontSize: 12,
                    fontWeight: active ? 700 : 500,
                    cursor: 'pointer',
                    border: active ? '1px solid #0891B2' : '1px solid transparent',
                    backgroundColor: active ? '#0891B2' : '#FFFFFF',
                    color: active ? '#FFFFFF' : '#155E75',
                    boxShadow: active ? '0 1px 2px rgba(8,145,178,0.2)' : '0 1px 1px rgba(0,0,0,0.04)',
                    transition: 'all 0.15s ease',
                  }}
                >
                  {t.label}
                </button>
              );
            })}
          </div>
        )}

        {/* Dropdown Trạng thái được nhóm theo Category */}
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{
            padding: '6px 10px',
            borderRadius: 6,
            border: `1px solid ${SURFACE.border}`,
            fontSize: 12,
            backgroundColor: '#FFFFFF',
            color: SURFACE.text,
            cursor: 'pointer',
          }}
        >
          <option value="ALL">Mọi trạng thái ({data?.cards?.length || 0})</option>
          {groupedStatuses.todo.length > 0 && (
            <optgroup label="── Chuẩn bị / Cần làm ──">
              {groupedStatuses.todo.map((s) => (
                <option key={s.name} value={s.name}>
                  {getStatusDisplayName(s.name)} ({s.count})
                </option>
              ))}
            </optgroup>
          )}
          {groupedStatuses.inProgress.length > 0 && (
            <optgroup label="── Đang triển khai ──">
              {groupedStatuses.inProgress.map((s) => (
                <option key={s.name} value={s.name}>
                  {getStatusDisplayName(s.name)} ({s.count})
                </option>
              ))}
            </optgroup>
          )}
          {groupedStatuses.done.length > 0 && (
            <optgroup label="── Hoàn thành / Nghiệm thu ──">
              {groupedStatuses.done.map((s) => (
                <option key={s.name} value={s.name}>
                  {getStatusDisplayName(s.name)} ({s.count})
                </option>
              ))}
            </optgroup>
          )}
        </select>

        {hasFilter && (
          <button
            onClick={() => { setQ(''); setTypeFilter('ALL'); setStatusFilter('ALL'); }}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              padding: '5px 10px', borderRadius: 6, border: `1px solid ${SURFACE.border}`,
              backgroundColor: '#FFFFFF', color: SURFACE.textMuted, fontSize: 12, cursor: 'pointer',
            }}
          >
            <X size={12} /> Xoá lọc
          </button>
        )}

        {/* ─── Switcher: Chuyển đổi Bảng (Board) / Danh sách (List) ─── */}
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 10 }}>
          {data?.swimlaneStrategy && viewMode === 'board' && (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12, color: SURFACE.textMuted }}>
              <Layers size={13} /> Phân làn: {data.swimlaneStrategy === 'BY_EPIC' ? 'Theo Hạng mục lớn' : data.swimlaneStrategy === 'BY_ASSIGNEE' ? 'Theo Người thực hiện' : data.swimlaneStrategy === 'BY_PRIORITY' ? 'Theo Mức độ ưu tiên' : data.swimlaneStrategy === 'NONE' ? 'Không phân làn' : data.swimlaneStrategy}
            </span>
          )}

          <div
            style={{
              display: 'flex',
              backgroundColor: '#F1F5F9',
              padding: 2,
              borderRadius: 6,
              border: '1px solid #E2E8F0',
            }}
          >
            <button
              type="button"
              onClick={() => handleViewModeChange('board')}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 5,
                padding: '4px 10px',
                borderRadius: 5,
                border: 'none',
                backgroundColor: viewMode === 'board' ? '#FFFFFF' : 'transparent',
                color: viewMode === 'board' ? '#1D4ED8' : '#64748B',
                fontWeight: viewMode === 'board' ? 700 : 500,
                fontSize: 12,
                cursor: 'pointer',
                boxShadow: viewMode === 'board' ? '0 1px 2px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.15s ease',
              }}
              title="Xem dạng Bảng Kanban"
            >
              <Kanban size={13} />
              <span>Bảng</span>
            </button>

            <button
              type="button"
              onClick={() => handleViewModeChange('list')}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 5,
                padding: '4px 10px',
                borderRadius: 5,
                border: 'none',
                backgroundColor: viewMode === 'list' ? '#FFFFFF' : 'transparent',
                color: viewMode === 'list' ? '#1D4ED8' : '#64748B',
                fontWeight: viewMode === 'list' ? 700 : 500,
                fontSize: 12,
                cursor: 'pointer',
                boxShadow: viewMode === 'list' ? '0 1px 2px rgba(0,0,0,0.06)' : 'none',
                transition: 'all 0.15s ease',
              }}
              title="Xem dạng Danh sách phân cấp (Hạng mục lớn -> Công việc -> Công việc con)"
            >
              <ListTree size={13} />
              <span>Danh sách</span>
            </button>
          </div>
        </div>
      </div>

      {/* ─── Nội Dung: Render theo Chế độ Bảng hoặc Danh sách ─── */}
      {viewMode === 'board' ? (
        <InteractiveBoard
          boardId={boardId}
          columns={filteredColumns}
          swimlanes={data?.swimlanes || []}
          unmappedStatuses={data?.unmappedStatuses || []}
          canTransition={canTransitionIssue || isAdmin}
          forbiddenColumnIds={forbiddenColumnIds}
          onSelectItem={openItemDetail}
          onApplyColumns={applyColumns}
          onReload={reload}
        />
      ) : (
        <BoardListView
          cards={allFilteredCards}
          columns={columns}
          canTransition={canTransitionIssue || isAdmin}
          onSelectItem={openItemDetail}
          onReload={reload}
          projectId={projectId}
        />
      )}

      <CompleteSprintModal
        isOpen={completeOpen}
        sprint={sprint ? { ...sprint, sprintId: sprint.id, itemCount: sprint.totalItems, doneCount: sprint.doneItems } : null}
        futureSprints={futureSprints}
        loading={busy}
        onClose={() => setCompleteOpen(false)}
        onConfirm={async (payload) => {
          try {
            setBusy(true);
            await sprintApi.close(sprint.id, payload);
            setCompleteOpen(false);
            toast.success('Đã hoàn tất chu kỳ');
            await reload({ silent: true });
            bumpRefresh?.();
          } catch (err) {
            console.warn('Không đóng được Sprint:', err);
          } finally {
            setBusy(false);
          }
        }}
      />
    </div>
  );
}

function matches(item, q, typeFilter, statusFilter) {
  if (typeFilter !== 'ALL') {
    if (typeFilter === 'LEVEL_1') {
      const isLevel1 = item.hierarchyLevel === 1 || item.typeCode === 'EPIC' || item.typeCode === 'CTF' || item.typeName?.toLowerCase()?.includes('hạng mục');
      if (!isLevel1) return false;
    } else if (typeFilter === 'LEVEL_0') {
      const isLevel0 = item.hierarchyLevel === 0 || (!item.isSubtask && item.hierarchyLevel !== 1 && item.hierarchyLevel !== -1);
      if (!isLevel0) return false;
    } else if (typeFilter === 'LEVEL_-1') {
      const isLevelSub = Boolean(item.isSubtask || item.hierarchyLevel === -1 || item.hierarchyLevel === 3);
      if (!isLevelSub) return false;
    } else {
      const code = String(item.typeCode || item.typeName || '').toUpperCase().replace('-', '_');
      if (code !== typeFilter && code.replace('_', '') !== typeFilter) return false;
    }
  }
  if (statusFilter && statusFilter !== 'ALL' && item.statusName !== statusFilter) {
    return false;
  }
  if (q) {
    const n = q.trim().toLowerCase();
    if (!(item.code || '').toLowerCase().includes(n) && !(item.summary || '').toLowerCase().includes(n)) {
      return false;
    }
  }
  return true;
}

const centered = { padding: 48, textAlign: 'center', color: SURFACE.textMuted, fontSize: 13 };
const linkBtn = { border: 'none', background: 'transparent', color: '#2563EB', cursor: 'pointer', fontSize: 13 };
const sprintBar = {
  display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap',
  padding: '10px 14px', borderRadius: 8, backgroundColor: SURFACE.subtle,
  border: `1px solid ${SURFACE.border}`, marginBottom: 12,
};
const emptyHint = {
  padding: '10px 14px', borderRadius: 8, backgroundColor: '#EFF6FF',
  color: '#1D4ED8', fontSize: 13, marginBottom: 12,
};
function primaryBtn(disabled) {
  return {
    marginLeft: 'auto', padding: '6px 14px', borderRadius: 6, border: 'none',
    fontSize: 12, fontWeight: 600, cursor: disabled ? 'not-allowed' : 'pointer',
    backgroundColor: disabled ? '#E2E8F0' : '#2563EB', color: disabled ? '#94A3B8' : '#FFFFFF',
  };
}
