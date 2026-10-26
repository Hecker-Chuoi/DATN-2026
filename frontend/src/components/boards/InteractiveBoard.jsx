import { useMemo, useState } from 'react';
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MeasuringStrategy,
  PointerSensor,
  closestCorners,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { sortableKeyboardCoordinates, useSortable } from '@dnd-kit/sortable';
import toast from 'react-hot-toast';

import { boardApi } from '../../api.js';
import { computeMove, revertMove, patchItem } from '../../utils/optimisticMove.js';
import BoardCard from './BoardCard.jsx';
import BoardColumn from './BoardColumn.jsx';
import BoardSwimlane from './BoardSwimlane.jsx';

const keyOf = (c) => `col:${c.id}`;

/**
 * Bo may Board dung chung cho ca Scrum va Kanban.
 *
 * Trang thai duoc luu theo NHOM (`columns[].items`) chu khong phang: dung mo hinh
 * nhieu container cua dnd-kit, va tranh phai gom lai o moi khung hinh khi keo.
 */
export default function InteractiveBoard({
  boardId,
  columns,
  swimlanes = [],
  unmappedStatuses = [],
  canTransition,
  forbiddenColumnIds,
  onSelectItem,
  onApplyColumns,
  onReload,
  onResolveSubtasks,
}) {
  const [activeCard, setActiveCard] = useState(null);

  const sensors = useSensors(
    // 6px: the co onClick mo modal chi tiet, khong duoc nuot cu bam.
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const forbidden = useMemo(
    () => (activeCard ? forbiddenColumnIds(activeCard) : new Set()),
    [activeCard, forbiddenColumnIds]
  );

  const findColumnKeyByItem = (itemId) => {
    const col = columns.find((c) => c.items.some((i) => i.id === itemId));
    return col ? keyOf(col) : null;
  };

  const handleDragEnd = async ({ active, over }) => {
    setActiveCard(null);
    if (!over) return;

    const fromKey = findColumnKeyByItem(active.id);
    if (!fromKey) return;

    const overStr = String(over.id);
    const isContainer = overStr.startsWith('col:');
    const toKey = isContainer ? overStr.split('|')[0] : findColumnKeyByItem(over.id);
    if (!toKey) return;

    const toColumn = columns.find((c) => keyOf(c) === toKey);
    if (!toColumn) return;

    if (forbidden.has(toColumn.id)) {
      toast.error(`Không thể chuyển sang cột "${toColumn.name}" theo quy trình hiện tại.`);
      return;
    }
    if (!canTransition) {
      toast.error('Bạn không có quyền chuyển trạng thái công việc.');
      return;
    }

    const toIndex = isContainer
      ? toColumn.items.length
      : toColumn.items.findIndex((i) => i.id === over.id);
    const currentIndex = toColumn.items.findIndex((i) => i.id === active.id);
    if (fromKey === toKey && toIndex === currentIndex) return;

    const dragged = columns.flatMap((c) => c.items).find((i) => i.id === active.id);
    if (!dragged) return;

    const result = computeMove(columns, {
      itemId: active.id,
      fromKey,
      toKey,
      toIndex,
      keyOf,
      patch: { columnId: toColumn.id },
    });
    if (!result) return;

    // 1. Ve ngay lap tuc.
    onApplyColumns(result.containers);

    try {
      const res = await boardApi.moveCard(
        boardId,
        active.id,
        {
          targetColumnId: toColumn.id,
          prevItemRank: result.prevItemRank,
          nextItemRank: result.nextItemRank,
          clientVersion: dragged.version,
        },
        { skipErrorToast: true }
      );

      // 2. Nhan su that tu server. Neu khong cap nhat version, lan keo ke tiep se gui
      //    version cu va bi 409.
      const d = res?.data || {};
      onApplyColumns(
        patchItem(result.containers, active.id, {
          rank: d.newRank ?? result.newRank,
          version: d.version ?? (dragged.version ?? 0) + 1,
          statusId: d.statusId ?? dragged.statusId,
          statusName: d.statusName ?? dragged.statusName,
          statusCategory: d.statusCategory ?? dragged.statusCategory,
          columnId: d.columnId ?? toColumn.id,
        })
      );
    } catch (err) {
      // 3. Truot ve cho cu - chi hoan tac RIENG the nay.
      onApplyColumns(revertMove(result.containers, result.snapshot, keyOf));
      handleMoveError(err, dragged, toColumn, onReload, onResolveSubtasks);
    }
  };

  const renderCard = (item, onSelect) => (
    <SortableCard key={item.id} item={item} onSelect={onSelect} />
  );

  const hasLanes = swimlanes.length > 1 || (swimlanes.length === 1 && swimlanes[0].swimlaneKey != null);

  return (
    <>
      <style>{`
        .bd-card .bd-actions { opacity: 0; transition: opacity .12s ease; }
        .bd-card:hover .bd-actions { opacity: 1; }
        .bd-scroll::-webkit-scrollbar { height: 9px; width: 9px; }
        .bd-scroll::-webkit-scrollbar-thumb { background: #CBD5E1; border-radius: 9px; }
      `}</style>

      {unmappedStatuses.length > 0 && (
        <div style={warnBox}>
          {unmappedStatuses.length} trạng thái chưa được gán cột nào (
          {unmappedStatuses.map((s) => s.name).join(', ')}) — công việc ở các trạng thái này không
          hiển thị trên bảng. Hãy cấu hình lại cột.
        </div>
      )}

      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        // Always: cot rong di va lan co lai ngay giua luc keo; chien luoc mac dinh
        // se do sai vi tri sau khi mot cot vua trong ra.
        measuring={{ droppable: { strategy: MeasuringStrategy.Always } }}
        onDragStart={({ active }) =>
          setActiveCard(columns.flatMap((c) => c.items).find((i) => i.id === active.id) || null)
        }
        onDragEnd={handleDragEnd}
        onDragCancel={() => setActiveCard(null)}
      >
        {hasLanes ? (
          swimlanes.map((lane) => (
            <BoardSwimlane key={lane.swimlaneKey ?? 'all'} lane={lane}>
              <BoardGrid
                columns={columns.map((c) => ({
                  ...c,
                  items: c.items.filter((i) => i.swimlaneKey === lane.swimlaneKey),
                }))}
                laneKey={lane.swimlaneKey}
                forbidden={forbidden}
                onSelectItem={onSelectItem}
                renderCard={renderCard}
              />
            </BoardSwimlane>
          ))
        ) : (
          <BoardGrid
            columns={columns}
            forbidden={forbidden}
            onSelectItem={onSelectItem}
            renderCard={renderCard}
          />
        )}

        <DragOverlay dropAnimation={{ duration: 180, easing: 'cubic-bezier(0.18,0.67,0.6,1.22)' }}>
          {activeCard ? <BoardCard item={activeCard} isOverlay /> : null}
        </DragOverlay>
      </DndContext>
    </>
  );
}

function BoardGrid({ columns, laneKey, forbidden, onSelectItem, renderCard }) {
  return (
    <div
      className="bd-scroll"
      style={{
        display: 'grid',
        gridAutoFlow: 'column',
        gridAutoColumns: 'minmax(280px, 1fr)',
        gap: 12,
        overflowX: 'auto',
        paddingBottom: 8,
        alignItems: 'stretch',
      }}
    >
      {columns.map((col) => (
        <BoardColumn
          key={col.id}
          column={col}
          items={col.items}
          laneKey={laneKey}
          forbidden={forbidden.has(col.id)}
          onSelectItem={onSelectItem}
          renderCard={renderCard}
        />
      ))}
    </div>
  );
}

/** Boc useSortable quanh the trinh bay. */
function SortableCard({ item, onSelect }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: item.id,
  });
  const style = {
    transform: transform ? `translate3d(${transform.x}px, ${transform.y}px, 0)` : undefined,
    transition,
    opacity: isDragging ? 0 : 1,
  };
  return (
    <div ref={setNodeRef} style={style}>
      <BoardCard item={item} onSelect={onSelect} dragHandleProps={{ ...attributes, ...listeners }} />
    </div>
  );
}

/**
 * Phan loai loi tu server. `skipErrorToast` khien interceptor khong tu hien thong bao,
 * nen o day phai tu toast.
 */
function handleMoveError(err, item, column, onReload, onResolveSubtasks) {
  const msg = err?.message || 'Không di chuyển được thẻ';
  const status = err?.status;

  if (status === 409) {
    toast.error('Thẻ này vừa được người khác di chuyển. Đang tải lại bảng.');
    onReload?.({ silent: true });
    return;
  }

  // Cha con dang mo -> mo modal dong loat thay vi bat nguoi dung tu lam tung cai.
  if (/sub-?task|công việc con|nhiệm vụ con/i.test(msg) && onResolveSubtasks) {
    toast.error(msg);
    onResolveSubtasks(item, column);
    return;
  }

  toast.error(msg);
}

const warnBox = {
  padding: '10px 12px',
  borderRadius: 8,
  backgroundColor: '#FFFBEB',
  border: '1px solid #FCD34D',
  color: '#B45309',
  fontSize: 12,
  marginBottom: 12,
};
