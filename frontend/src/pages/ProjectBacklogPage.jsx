import { useEffect, useMemo, useState } from 'react';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MeasuringStrategy,
  PointerSensor,
  closestCorners,
  pointerWithin,
  useDroppable,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CheckCircle2, ChevronDown, ChevronRight, Plus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';

import { useProjectContext } from '../context/ProjectContext.jsx';
import { useProjectBacklog } from '../hooks/useProjectBacklog.js';
import { sprintApi, workItemApi } from '../api.js';
import { computeMove, revertMove } from '../utils/optimisticMove.js';
import { SURFACE } from '../constants/jiraTokens.js';
import BacklogToolbar from '../components/backlog/BacklogToolbar.jsx';
import SprintAccordion from '../components/backlog/SprintAccordion.jsx';
import BacklogIssueRow from '../components/backlog/BacklogIssueRow.jsx';
import SprintFormModal from '../components/sprints/SprintFormModal.jsx';
import CompleteSprintModal from '../components/sprints/CompleteSprintModal.jsx';
import ConfirmModal from '../components/modals/ConfirmModal.jsx';

const EMPTY_FILTERS = { q: '', epicId: null, assigneeIds: [], onlyMine: false, recentlyUpdated: false };

/** Khoa container: "backlog" hoac "sprint:<id>". */
const keyOf = (g) => (g.sprintId == null ? 'backlog' : `sprint:${g.sprintId}`);

/** Vung tha cua khu "da hoan tat": chi de chan tha nham, khong bao gio nhan cong viec. */
const CLOSED_AREA_ID = 'closed-area';

export default function ProjectBacklogPage() {
  const { projectId, project, members, canManageSprint, isAdmin, openItemDetail, bumpRefresh, refreshKey } =
    useProjectContext();
  const navigate = useNavigate();

  const backlog = useProjectBacklog(projectId);
  const { data, loading, error, busy, setData, reload } = backlog;

  // Dong bo lai khi cong viec duoc tao/sua o noi khac (modal chi tiet, doi sprint, tao nhanh...).
  useEffect(() => {
    if (refreshKey) reload({ silent: true });
  }, [refreshKey, reload]);

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [activeItem, setActiveItem] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [startTarget, setStartTarget] = useState(null);
  const [editTarget, setEditTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [completeTarget, setCompleteTarget] = useState(null);

  const canManage = canManageSprint || isAdmin;
  const currentUserId = members?.find?.((m) => m.isCurrentUser)?.userId || null;

  const sensors = useSensors(
    // distance 6px: hang cong viec co onClick mo modal, khong duoc nuot cu bam.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const groups = useMemo(() => {
    if (!data) return [];
    return [...(data.sprints || []), data.backlog].filter(Boolean);
  }, [data]);

  const visibleGroups = useMemo(
    () => groups.map((g) => filterGroup(g, filters, currentUserId)),
    [groups, filters, currentUserId]
  );

  // Chu ky / giai doan da hoan tat: giu lai de xem (BR-B08), chi doc, khong tham gia keo tha.
  const closedGroups = useMemo(
    () => (data?.closedSprints || []).map((g) => filterGroup(g, filters, currentUserId)),
    [data, filters, currentUserId]
  );

  const types = project?.workItemTypes || [];

  // Chu ky moi mac dinh bat dau SAU moc cuoi cung cua cac chu ky dang co, de no xep cuoi
  // ke hoach thay vi chen len truoc (thu tu bat dau = ngay bat dau du kien, BR-S15).
  const suggestedStartDate = useMemo(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    let latest = null;
    for (const s of data?.sprints || []) {
      const raw = s.endDate || s.startDate;
      const d = raw ? new Date(raw) : null;
      if (d && !Number.isNaN(d.getTime()) && (!latest || d > latest)) latest = d;
    }
    if (!latest || latest < today) return today;
    const next = new Date(latest);
    next.setDate(next.getDate() + 1);
    return next;
  }, [data]);

  // ───────────────────────── Kéo thả ─────────────────────────

  const collisionDetection = (args) => {
    // Khoi accordion rat cao nen closestCorners don thuan hay bat nham Sprint ben canh.
    const pointer = pointerWithin(args);
    return pointer.length > 0 ? pointer : closestCorners(args);
  };

  const findGroupKeyByItem = (itemId) => {
    const g = groups.find((x) => (x.items || []).some((i) => i.id === itemId));
    return g ? keyOf(g) : null;
  };

  const handleDragStart = ({ active }) => {
    const item = groups.flatMap((g) => g.items || []).find((i) => i.id === active.id);
    setActiveItem(item || null);
  };

  const handleDragEnd = async ({ active, over }) => {
    setActiveItem(null);
    if (!over) return;
    if (over.id === CLOSED_AREA_ID) {
      toast.error('Chu kỳ / giai đoạn đã hoàn tất chỉ để xem lại, không thể thêm công việc.');
      return;
    }

    const fromKey = findGroupKeyByItem(active.id);
    if (!fromKey) return;

    // over.id co the la container (co tien to) hoac mot the khac.
    const overIsContainer = String(over.id).startsWith('sprint:') || over.id === 'backlog';
    const toKey = overIsContainer ? String(over.id) : findGroupKeyByItem(over.id);
    if (!toKey) return;

    const toGroup = groups.find((g) => keyOf(g) === toKey);
    const toIndex = overIsContainer
      ? (toGroup?.items || []).length
      : (toGroup?.items || []).findIndex((i) => i.id === over.id);

    if (fromKey === toKey && toIndex === (toGroup?.items || []).findIndex((i) => i.id === active.id)) {
      return; // khong doi gi
    }

    const dragged = groups.flatMap((g) => g.items || []).find((i) => i.id === active.id);
    if (!dragged) return;

    // Sub-task luon di theo Sprint cua cong viec cha - chan ngay tren UI.
    if (dragged.isSubtask || dragged.hierarchyLevel === -1) {
      toast.error('Công việc con đi theo chu kỳ của công việc cha, không thể kéo riêng.');
      return;
    }
    if (!canManage) {
      toast.error('Bạn không có quyền thay đổi chu kỳ.');
      return;
    }

    const targetSprintId = toGroup?.sprintId ?? null;
    const result = computeMove(groups, {
      itemId: active.id,
      fromKey,
      toKey,
      toIndex,
      keyOf,
      patch: { sprintId: targetSprintId, sprintName: toGroup?.name },
    });
    if (!result) return;

    // 1. Ve ngay, khong cho server.
    applyGroups(result.containers);

    try {
      // 2. Doi Sprint (neu khac nhom) roi cap nhat thu tu.
      if (fromKey !== toKey) {
        if (targetSprintId) {
          await sprintApi.addItems(targetSprintId, [active.id]);
        } else {
          const fromSprintId = groups.find((g) => keyOf(g) === fromKey)?.sprintId;
          if (fromSprintId) await sprintApi.removeItems(fromSprintId, [active.id]);
        }
      }
      await workItemApi.updateRank(active.id, result.newRank);

      // 3. Nhan lai su that tu server, neu khong lan keo sau se gui version cu.
      await reload({ silent: true });
      bumpRefresh?.();
    } catch (err) {
      // 4. Truot ve cho cu. Interceptor da hien toast loi roi.
      applyGroups(revertMove(result.containers, result.snapshot, keyOf));
      console.warn('Không di chuyển được công việc:', err);
      if (err?.status === 409) {
        toast.error('Dữ liệu vừa được người khác thay đổi. Đang tải lại kho công việc.');
        reload({ silent: true });
      }
    }
  };

  const applyGroups = (containers) => {
    setData((prev) => {
      if (!prev) return prev;
      const backlogGroup = containers.find((c) => c.sprintId == null);
      const sprintGroups = containers.filter((c) => c.sprintId != null);
      return { ...prev, sprints: sprintGroups, backlog: backlogGroup };
    });
  };

  // ───────────────────────── Thao tác Sprint ─────────────────────────

  const handleQuickCreate = async ({ typeId, summary, sprintId }) => {
    await workItemApi.create(projectId, { typeId, summary, sprintId: sprintId ?? null });
    await reload({ silent: true });
    bumpRefresh?.();
  };

  // Dem ca chu ky da hoan tat, neu khong sau khi dong "Chu kỳ 1" se goi y trung ten.
  const suggestedName = `Chu kỳ ${(data?.sprints?.length || 0) + (data?.closedSprints?.length || 0) + 1}`;

  const openSprintReport = (g) =>
    navigate(`../reports?tab=progress&view=burndown&sprint=${g.sprintId}`);

  /**
   * Ly do nut "Bat dau" bi khoa, de hien tooltip. Quyet dinh khoa/mo van do backend (canStart);
   * o day chi giai thich. Backend tra cac Sprint FUTURE theo dung thu tu ke hoach (BR-S15),
   * nen Sprint FUTURE dau tien chinh la Sprint duy nhat duoc bat dau tiep.
   */
  const startBlockedReason = (group) => {
    if (group.state !== 'FUTURE' || group.canStart) return null;
    const sprintGroups = data?.sprints || [];
    const active = sprintGroups.find((s) => s.state === 'ACTIVE');
    if (active) return `Đang có "${active.name}" chạy — hãy hoàn tất nó trước.`;
    const next = sprintGroups.find((s) => s.state === 'FUTURE');
    if (next && next.sprintId !== group.sprintId) {
      // Khoi dung dau dang rong thi khong the bat dau duoc -> chi cach go.
      if (!next.itemCount) {
        return `"${next.name}" đứng trước nhưng chưa có công việc — hãy kéo việc vào, dời ngày bắt đầu của nó ra sau (bút chì) hoặc xóa nó (thùng rác).`;
      }
      return `Cần bắt đầu "${next.name}" trước — các chu kỳ/giai đoạn được bắt đầu lần lượt theo ngày bắt đầu dự kiến.`;
    }
    if (!group.itemCount) return 'Chưa có công việc nào — hãy kéo công việc vào trước.';
    return null;
  };

  // ───────────────────────── Render ─────────────────────────

  if (loading) {
    return <div style={centered}>Đang tải kho công việc & kế hoạch...</div>;
  }
  if (error) {
    return (
      <div style={centered}>
        Không tải được kho công việc & kế hoạch.{' '}
        <button onClick={() => reload()} style={linkBtn}>Thử lại</button>
      </div>
    );
  }

  const hasAnySprint = (data?.sprints || []).length + (data?.closedSprints || []).length > 0;

  const renderGroup = (group) => (
    <DroppableGroup key={keyOf(group)} group={group}>
      {(isOver, droppableProps) => (
        <SprintAccordion
          group={group}
          canManageSprint={canManage}
          types={types}
          busy={busy}
          startBlockedReason={startBlockedReason(group)}
          isOver={isOver}
          droppableProps={droppableProps}
          onSelectItem={openItemDetail}
          onStart={(g) => setStartTarget(g)}
          onEdit={(g) => setEditTarget({ ...g, id: g.sprintId })}
          onDelete={(g) => setDeleteTarget(g)}
          onComplete={(g) => setCompleteTarget(g)}
          onQuickCreate={handleQuickCreate}
          renderRow={(item, _g, isChild) => (
            <SortableRow key={item.id} item={item} onSelect={openItemDetail} isChild={isChild} />
          )}
        />
      )}
    </DroppableGroup>
  );

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '16px 24px 40px 24px' }}>
      <style>{`
        .blg-row .blg-actions { opacity: 0; transition: opacity .12s ease; }
        .blg-row:hover { background-color: #F8FAFC !important; }
        .blg-row:hover .blg-actions { opacity: 1; }
      `}</style>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <BacklogToolbar
          filters={filters}
          onChange={setFilters}
          epics={data?.epics || []}
          members={members || []}
          currentUserId={currentUserId}
        />
        {canManage && (
          <button onClick={() => setCreateOpen(true)} style={createSprintBtn}>
            <Plus size={14} /> Tạo chu kỳ
          </button>
        )}
      </div>

      {!hasAnySprint && (
        <div style={emptyHint}>
          Dự án chưa có chu kỳ nào. {canManage ? 'Hãy tạo chu kỳ đầu tiên để bắt đầu lập kế hoạch.' : ''}
        </div>
      )}

      <DndContext
        sensors={sensors}
        collisionDetection={collisionDetection}
        measuring={{ droppable: { strategy: MeasuringStrategy.Always } }}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
        onDragCancel={() => setActiveItem(null)}
      >
        {/* Thu tu: dang chay -> du kien -> da hoan tat (luu tru) -> Backlog (BR-B02, BR-B08) */}
        {visibleGroups.filter((g) => g.sprintId != null).map(renderGroup)}

        <ClosedSprintsSection
          groups={closedGroups}
          onSelectItem={openItemDetail}
          onOpenReport={openSprintReport}
        />

        {visibleGroups.filter((g) => g.sprintId == null).map(renderGroup)}

        <DragOverlay dropAnimation={{ duration: 180, easing: 'cubic-bezier(0.18,0.67,0.6,1.22)' }}>
          {activeItem ? (
            <div style={{ boxShadow: '0 10px 20px rgba(0,0,0,0.15)', borderRadius: 6, backgroundColor: '#FFFFFF' }}>
              <BacklogIssueRow item={activeItem} />
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>

      {/* ─── Modals ─── */}
      <SprintFormModal
        isOpen={createOpen}
        mode="create"
        suggestedName={suggestedName}
        suggestedStartDate={suggestedStartDate}
        loading={busy}
        onClose={() => setCreateOpen(false)}
        onSubmit={async (payload) => {
          await backlog.createSprint(payload);
          setCreateOpen(false);
          toast.success('Đã tạo chu kỳ');
        }}
      />

      <SprintFormModal
        isOpen={!!editTarget}
        mode="edit"
        sprint={editTarget}
        loading={busy}
        onClose={() => setEditTarget(null)}
        onSubmit={async (payload) => {
          await backlog.updateSprint(editTarget.sprintId, payload);
          setEditTarget(null);
          toast.success('Đã cập nhật chu kỳ');
        }}
      />

      {/* Chi xoa duoc chu ky Du kien (BR-S04); cong viec ben trong tro ve Kho viec (BR-S05). */}
      <ConfirmModal
        isOpen={!!deleteTarget}
        type="danger"
        title={`Xóa ${sprintTerm(deleteTarget)}`}
        message={
          deleteTarget ? (
            <>
              Xóa <b>{deleteTarget.name}</b>?{' '}
              {deleteTarget.itemCount
                ? <>{deleteTarget.itemCount} công việc bên trong sẽ <b>trở về Kho việc</b>, không bị xóa.</>
                : <>{capitalize(sprintTerm(deleteTarget))} này chưa có công việc nào.</>}
            </>
          ) : null
        }
        confirmText="Xóa"
        cancelText="Hủy"
        loading={busy}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={async () => {
          try {
            await backlog.deleteSprint(deleteTarget.sprintId);
            toast.success(`Đã xóa ${sprintTerm(deleteTarget)}`);
            setDeleteTarget(null);
            bumpRefresh?.();
          } catch (err) {
            // Interceptor da hien toast loi; giu hop thoai de nguoi dung tu dong.
            console.warn('Không xóa được chu kỳ:', err);
          }
        }}
      />

      <SprintFormModal
        isOpen={!!startTarget}
        mode="start"
        sprint={startTarget ? { ...startTarget, id: startTarget.sprintId } : null}
        itemCount={startTarget?.itemCount || 0}
        totalPoints={startTarget?.totalStoryPoints || 0}
        hasActiveSprint={(data?.sprints || []).some((s) => s.state === 'ACTIVE')}
        loading={busy}
        onClose={() => setStartTarget(null)}
        onSubmit={async (payload) => {
          await backlog.startSprint(startTarget.sprintId, payload);
          setStartTarget(null);
          toast.success('Chu kỳ đã bắt đầu');
        }}
      />

      <CompleteSprintModal
        isOpen={!!completeTarget}
        sprint={completeTarget}
        futureSprints={(data?.sprints || []).filter((s) => s.state === 'FUTURE')}
        loading={busy}
        onClose={() => setCompleteTarget(null)}
        onConfirm={async (payload) => {
          await backlog.completeSprint(completeTarget.sprintId, payload);
          setCompleteTarget(null);
          bumpRefresh?.();
          toast.success('Đã hoàn tất chu kỳ');
        }}
      />
    </div>
  );
}

/** "giai đoạn" hay "chu kỳ" theo tên, giống cách SprintAccordion đặt nhãn nút. */
function sprintTerm(group) {
  return group?.name?.toLowerCase()?.includes('giai đoạn') ? 'giai đoạn' : 'chu kỳ';
}

function capitalize(s) {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}

/**
 * Khu luu tru cac chu ky / giai doan da hoan tat (BR-B08), nam ngay tren khoi Backlog.
 * Khong xoa di sau khi hoan tat: cong viec DONE van giu sprintId cua chung va la nguon cua
 * Bao cao (tien do, nang suat, dong gop). Moi khoi mac dinh thu gon de trang gon.
 */
function ClosedSprintsSection({ groups, onSelectItem, onOpenReport }) {
  const [open, setOpen] = useState(true);
  // Dang ky vung tha de tha nham vao day thi bi chan (xem handleDragEnd), khong roi sang khoi ben canh.
  const { setNodeRef } = useDroppable({ id: CLOSED_AREA_ID });
  if (!groups.length) return null;

  const phases = groups.filter((g) => sprintTerm(g) === 'giai đoạn').length;
  const title =
    phases === groups.length ? 'Giai đoạn đã hoàn tất'
      : phases === 0 ? 'Chu kỳ đã hoàn tất'
        : 'Chu kỳ / giai đoạn đã hoàn tất';

  return (
    <section ref={setNodeRef} style={{ marginBottom: 14 }}>
      <button type="button" onClick={() => setOpen((o) => !o)} style={closedHeaderBtn} aria-expanded={open}>
        {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
        <CheckCircle2 size={15} color="#00875A" />
        <span style={{ fontWeight: 700, fontSize: 14, color: SURFACE.text }}>{title}</span>
        <span style={{ fontSize: 12, color: SURFACE.textMuted }}>({groups.length})</span>
        <span style={{ fontSize: 12, color: SURFACE.textLight, fontWeight: 400 }}>
          — chỉ để xem lại; công việc và số liệu được giữ nguyên cho Báo cáo
        </span>
      </button>
      {open && (
        <div style={{ marginLeft: 7, paddingLeft: 12, borderLeft: '2px solid #ABF5D1' }}>
          {groups.map((g) => (
            <SprintAccordion
              key={g.sprintId}
              group={g}
              readOnly
              defaultOpen={false}
              canManageSprint={false}
              onSelectItem={onSelectItem}
              onOpenReport={onOpenReport}
            />
          ))}
        </div>
      )}
    </section>
  );
}

/** Vung tha cho ca khoi (tha vao cho trong = them vao cuoi danh sach). */
function DroppableGroup({ group, children }) {
  const { setNodeRef, isOver } = useDroppable({ id: keyOf(group) });
  const items = (group.items || []).map((i) => i.id);
  return (
    <SortableContext items={items} strategy={verticalListSortingStrategy}>
      {children(isOver, { ref: setNodeRef })}
    </SortableContext>
  );
}

/** Boc useSortable quanh hang trinh bay, giu logic keo tha tach khoi phan hien thi. */
function SortableRow({ item, onSelect, isChild }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
  });
  const style = {
    transform: transform ? `translate3d(${transform.x}px, ${transform.y}px, 0)` : undefined,
    transition,
    // Giu nguyen o trong de khoang cach khong giat khi keo.
    opacity: isDragging ? 0 : 1,
  };
  return (
    <div ref={setNodeRef} style={style}>
      <BacklogIssueRow
        item={item}
        onSelect={onSelect}
        isChild={isChild}
        dragHandleProps={{ ...attributes, ...listeners }}
      />
    </div>
  );
}

/** Bo hang Epic va ap bo loc (BR-B05: chi thu hep hien thi). */
function filterGroup(g, f, currentUserId) {
  return {
    ...g,
    items: (g.items || []).filter((i) => i.hierarchyLevel !== 1).filter((i) => matches(i, f, currentUserId)),
  };
}

function matches(item, f, currentUserId) {
  if (f.q) {
    const n = f.q.trim().toLowerCase();
    const code = (item.code || '').toLowerCase();
    const sum = (item.summary || '').toLowerCase();
    if (!code.includes(n) && !sum.includes(n)) return false;
  }
  if (f.epicId && item.epicId !== f.epicId) return false;
  if (f.assigneeIds?.length && !f.assigneeIds.includes(item.assigneeId)) return false;
  if (f.onlyMine && currentUserId && item.assigneeId !== currentUserId) return false;
  if (f.recentlyUpdated) {
    const d = item.updatedAt ? new Date(item.updatedAt) : null;
    if (!d || Date.now() - d.getTime() > 7 * 24 * 3600 * 1000) return false;
  }
  return true;
}

const centered = { padding: 48, textAlign: 'center', color: SURFACE.textMuted, fontSize: 13 };
const linkBtn = { border: 'none', background: 'transparent', color: '#2563EB', cursor: 'pointer', fontSize: 13 };
const createSprintBtn = {
  display: 'inline-flex', alignItems: 'center', gap: 6, padding: '7px 14px', borderRadius: 6,
  border: 'none', backgroundColor: '#2563EB', color: '#FFFFFF', fontSize: 13, fontWeight: 600, cursor: 'pointer',
};
const closedHeaderBtn = {
  display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', width: '100%',
  padding: '6px 2px', marginBottom: 8, border: 'none', background: 'transparent',
  color: SURFACE.textMuted, cursor: 'pointer', textAlign: 'left',
};
const emptyHint = {
  padding: '12px 14px', borderRadius: 8, backgroundColor: '#EFF6FF',
  color: '#1D4ED8', fontSize: 13, marginBottom: 14,
};
