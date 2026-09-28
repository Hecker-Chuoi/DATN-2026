import { useEffect, useMemo, useState } from 'react';
import { Search, Layers, CalendarClock } from 'lucide-react';
import toast from 'react-hot-toast';

import { useProjectContext } from '../context/ProjectContext.jsx';
import { useBoardData } from '../hooks/useBoardData.js';
import { boardApi, sprintApi } from '../api.js';
import { SURFACE, typeToken } from '../constants/jiraTokens.js';
import { boardTypeOf } from '../utils/projectType.js';
import InteractiveBoard from '../components/boards/InteractiveBoard.jsx';
import SprintStateBadge from '../components/sprints/SprintStateBadge.jsx';
import CompleteSprintModal from '../components/sprints/CompleteSprintModal.jsx';

const TYPE_FILTERS = ['ALL', 'EPIC', 'STORY', 'TASK', 'BUG', 'SUBTASK'];

/**
 * Route /board. Tu chon bo cuc Scrum hay Kanban dua tren loai bang:
 * - SCRUM: co thanh Sprint dang chay + nut Hoan tat Sprint
 * - KANBAN: khong co khai niem Sprint
 */
export default function ProjectBoardPage() {
  const { projectId, project, canTransitionIssue, canManageSprint, isAdmin, openItemDetail, refreshKey, bumpRefresh } =
    useProjectContext();

  const [boardId, setBoardId] = useState(null);
  const [boardsLoading, setBoardsLoading] = useState(true);
  const [q, setQ] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [completeOpen, setCompleteOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [futureSprints, setFutureSprints] = useState([]);

  // Du an hien chi co mot bang (do seed tu dong khi tao du an).
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

  // Dong bo lai khi cong viec duoc tao/sua o noi khac (modal chi tiet, tao nhanh...).
  useEffect(() => {
    if (boardId && refreshKey) reload({ silent: true });
  }, [refreshKey, boardId, reload]);

  const filteredColumns = useMemo(
    () => columns.map((c) => ({ ...c, items: c.items.filter((i) => matches(i, q, typeFilter)) })),
    [columns, q, typeFilter]
  );

  /** InteractiveBoard tra ve mang cot da cap nhat; ghi nguoc vao `cards` phang. */
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

  const isScrum = boardTypeOf(project, data) === 'SCRUM' || data?.boardType === 'SCRUM';
  const sprint = data?.activeSprint;

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '14px 24px 32px 24px' }}>
      {/* Thanh Sprint (chi voi bang Scrum) */}
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
                Hoàn tất Sprint
              </button>
            )}
          </div>
        ) : (
          <div style={emptyHint}>
            Chưa có Sprint nào đang chạy. Hãy sang tab <b>Backlog</b> để bắt đầu một Sprint.
          </div>
        )
      )}

      {/* Bo loc */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14, flexWrap: 'wrap' }}>
        <div style={{ position: 'relative', width: 220 }}>
          <Search size={14} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: SURFACE.textLight }} />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Tìm trên bảng..."
            style={{ width: '100%', padding: '6px 10px 6px 28px', borderRadius: 6, border: `1px solid ${SURFACE.border}`, fontSize: 13, outline: 'none', color: SURFACE.text }}
          />
        </div>
        {TYPE_FILTERS.map((t) => {
          const active = typeFilter === t;
          return (
            <button
              key={t}
              onClick={() => setTypeFilter(t)}
              style={{
                padding: '5px 12px', borderRadius: 999, fontSize: 12, fontWeight: active ? 600 : 500,
                cursor: 'pointer',
                border: `1px solid ${active ? '#93C5FD' : SURFACE.border}`,
                backgroundColor: active ? '#EFF6FF' : '#FFFFFF',
                color: active ? '#1D4ED8' : SURFACE.textMuted,
              }}
            >
              {t === 'ALL' ? 'Tất cả' : typeToken(t).label}
            </button>
          );
        })}
        {data?.swimlaneStrategy && (
          <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12, color: SURFACE.textMuted }}>
            <Layers size={13} /> Phân luồng: {data.swimlaneStrategy}
          </span>
        )}
      </div>

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
            toast.success('Đã hoàn tất Sprint');
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

function matches(item, q, typeFilter) {
  if (typeFilter !== 'ALL') {
    const code = String(item.typeCode || item.typeName || '').toUpperCase().replace('-', '_');
    if (code !== typeFilter && code.replace('_', '') !== typeFilter) return false;
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
