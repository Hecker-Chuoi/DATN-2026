import { useCallback, useEffect, useMemo, useState } from 'react';
import { boardApi, workflowApi } from '../api.js';

/**
 * Nap du lieu Board + ban do buoc chuyen cua quy trinh.
 *
 * Ban do buoc chuyen cho phep biet TRUOC khi keo cot nao la hop le, nho vay UI vo hieu
 * hoa o tha thay vi de nguoi dung tha roi moi nhan loi tu server. Vi du thuc te: quy trinh
 * mac dinh cua du an KHONG co buoc DONE -> TO DO.
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
        setData(boardRes?.data ?? null);
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

  /** Gom the theo cot, giu dung thu tu cot va rank. */
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

  /** Cac cot ma the dang keo KHONG the chuyen toi. */
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

/** statusId hien tai -> tap statusId co the toi. */
function buildTransitionMap(workflow) {
  const map = new Map();
  const list = workflow?.transitions || workflow?.workflowTransitions || [];
  for (const t of list) {
    const from = t.fromStatusId;
    const to = t.toStatusId;
    if (!to) continue;
    if (from == null) continue; // buoc "Create" khong dung cho keo tha
    if (!map.has(from)) map.set(from, new Set());
    map.get(from).add(to);
  }
  return map;
}

export default useBoardData;
