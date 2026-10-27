import { useCallback, useEffect, useMemo, useState } from 'react';
import { boardApi, workflowApi } from '../api.js';

/**
 * Nạp dữ liệu Board + bản đồ bước chuyển của quy trình.
 * Tự động đảm bảo các Hạng mục lớn (Epic) liên quan đến các công việc
 * trong chu kỳ xuất hiện đầy đủ trên Bảng.
 */
export function useBoardData(projectId, boardId, sprintId) {
  const [data, setData] = useState(null);
  const [transitions, setTransitions] = useState(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const load = useCallback(
    async (opts = {}) => {
      if (!boardId) return;
      try {
        if (!opts.silent) setLoading(true);
        setError(false);
        const [boardRes, workflowRes] = await Promise.all([
          boardApi.getBoardData(boardId, sprintId ? { sprintId } : undefined),
          workflowApi.getProjectWorkflow(projectId).catch(() => null),
        ]);
        const enriched = enrichBoardDataWithEpics(boardRes?.data ?? null);
        setData(enriched);
        setTransitions(buildTransitionMap(workflowRes?.data));
      } catch (err) {
        console.warn('Không tải được dữ liệu bảng:', err);
        setError(true);
      } finally {
        if (!opts.silent) setLoading(false);
      }
    },
    [projectId, boardId, sprintId]
  );

  useEffect(() => {
    load();
  }, [load]);

  /** Gom thẻ theo cột, giữ đúng thứ tự cột và rank. */
  const columns = useMemo(() => {
    if (!data) return [];
    const byColumn = new Map();
    for (const card of data.cards || []) {
      if (!byColumn.has(card.columnId)) byColumn.set(card.columnId, []);
      byColumn.get(card.columnId).push(card);
    }
    return (data.columns || []).map((col) => ({
      ...col,
      items: byColumn.get(col.id) || [],
    }));
  }, [data]);

  /** Các cột mà thẻ đang kéo KHÔNG thể chuyển tới. */
  const forbiddenColumnIds = useCallback(
    (card) => {
      if (!card || transitions.size === 0) return new Set();
      const reachable = transitions.get(card.statusId) || new Set();
      const forbidden = new Set();
      for (const col of data?.columns || []) {
        if (col.id === card.columnId) continue;
        const ok = (col.statuses || []).some((s) => reachable.has(s.id));
        if (!ok) forbidden.add(col.id);
      }
      return forbidden;
    },
    [transitions, data]
  );

  return { data, setData, columns, loading, error, reload: load, forbiddenColumnIds, transitions };
}

/**
 * Đảm bảo các Hạng mục lớn (Epic) mà các công việc trong Sprint thuộc về
 * đều có mặt trong cards của Board, giúp hiển thị thẻ Epic trên cột Kanban,
 * banner Hạng mục lớn và cấu trúc cây trong Chế độ Danh sách.
 */
function enrichBoardDataWithEpics(boardData) {
  if (!boardData || !boardData.cards || !boardData.columns || boardData.columns.length === 0) {
    return boardData;
  }

  const existingEpicIds = new Set(
    boardData.cards
      .filter((c) => c.hierarchyLevel === 1 || c.typeCode === 'EPIC')
      .map((c) => c.id)
  );

  const firstColumnId = boardData.columns[0]?.id;
  const newCards = [...boardData.cards];
  const epicsToPrepend = [];
  const cardEpicMap = new Map();

  // 1. Duyệt qua các thẻ công việc (Level 2: Task/Story/Bug) để truy tìm Epic cha
  for (let i = 0; i < newCards.length; i++) {
    const card = newCards[i];
    const isSub = Boolean(card.isSubtask || card.hierarchyLevel === 3);
    const parentId = card.epicId || card.parentId;
    const parentCode = card.epicCode || card.parentCode;
    const parentSummary = card.epicSummary || card.parentSummary;
    const parentColor = card.epicColor || '#8B5CF6';

    if (!isSub && parentId && parentCode) {
      // Đảm bảo card task có đầy đủ thông tin epic
      if (!card.epicId) {
        newCards[i] = {
          ...card,
          epicId: parentId,
          epicCode: parentCode,
          epicSummary: parentSummary,
          epicColor: parentColor,
        };
      }

      cardEpicMap.set(card.id, {
        id: parentId,
        code: parentCode,
        summary: parentSummary,
        color: parentColor,
      });

      // Nếu Epic chưa có thẻ trên Board thì tạo thẻ Epic
      if (!existingEpicIds.has(parentId)) {
        existingEpicIds.add(parentId);
        epicsToPrepend.push({
          id: parentId,
          code: parentCode,
          summary: parentSummary || 'Hạng mục lớn',
          typeCode: 'EPIC',
          typeName: 'Hạng mục lớn',
          typeColor: parentColor,
          hierarchyLevel: 1,
          isSubtask: false,
          columnId: firstColumnId,
          statusName: 'CẦN LÀM',
          statusCategory: 'TO_DO',
          projectId: card.projectId,
          projectKey: card.projectKey,
          isVirtual: false,
        });
      }
    }
  }

  // 2. Điền epicId cho các subtask nếu task cha của chúng thuộc Epic
  for (let i = 0; i < newCards.length; i++) {
    const card = newCards[i];
    const isSub = Boolean(card.isSubtask || card.hierarchyLevel === 3);
    if (isSub && card.parentId && cardEpicMap.has(card.parentId) && !card.epicId) {
      const ep = cardEpicMap.get(card.parentId);
      newCards[i] = {
        ...card,
        epicId: ep.id,
        epicCode: ep.code,
        epicSummary: ep.summary,
        epicColor: ep.color,
      };
    }
  }

  return {
    ...boardData,
    cards: [...epicsToPrepend, ...newCards],
  };
}

/** statusId hiện tại -> tập statusId có thể tới. */
function buildTransitionMap(workflow) {
  const map = new Map();
  const list = workflow?.transitions || workflow?.workflowTransitions || [];
  for (const t of list) {
    const from = t.fromStatusId;
    const to = t.toStatusId;
    if (!to) continue;
    if (from == null) continue; // bước "Create" không dùng cho kéo thả
    if (!map.has(from)) map.set(from, new Set());
    map.get(from).add(to);
  }
  return map;
}

export default useBoardData;
