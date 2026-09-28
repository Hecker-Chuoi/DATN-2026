import { useCallback, useEffect, useState } from 'react';
import { boardApi, sprintApi } from '../api.js';

/**
 * Nap va thao tac du lieu man hinh Backlog.
 *
 * Toan bo man hinh lay tu MOT endpoint (`/projects/{id}/backlog`) de khong bi nhay
 * khi tai. Cac ham mutate deu reload lai sau khi thanh cong; rieng thao tac keo tha
 * dung setData truc tiep (optimistic) nen khong goi reload.
 *
 * Luu y: interceptor trong api.js da tu hien toast loi, nen cac catch o day chi log.
 */
export function useProjectBacklog(projectId) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(
    async (opts = {}) => {
      if (!projectId) return;
      try {
        if (!opts.silent) setLoading(true);
        setError(false);
        const res = await boardApi.getBacklog(projectId);
        setData(res?.data ?? null);
      } catch (err) {
        console.warn('Không tải được Backlog:', err);
        setError(true);
      } finally {
        if (!opts.silent) setLoading(false);
      }
    },
    [projectId]
  );

  useEffect(() => {
    load();
  }, [load]);

  /** Bao boc cac thao tac ghi: khoa nut, reload im lang sau khi xong. */
  const run = useCallback(
    async (fn) => {
      setBusy(true);
      try {
        const result = await fn();
        await load({ silent: true });
        return result;
      } finally {
        setBusy(false);
      }
    },
    [load]
  );

  const createSprint = useCallback(
    (payload) => run(() => sprintApi.create(projectId, payload)),
    [projectId, run]
  );

  const startSprint = useCallback(
    (sprintId, payload) => run(() => sprintApi.start(sprintId, payload)),
    [run]
  );

  const completeSprint = useCallback(
    (sprintId, payload) => run(() => sprintApi.close(sprintId, payload)),
    [run]
  );

  const updateSprint = useCallback(
    (sprintId, payload) => run(() => sprintApi.update(sprintId, payload)),
    [run]
  );

  const deleteSprint = useCallback(
    (sprintId) => run(() => sprintApi.delete(sprintId)),
    [run]
  );

  const groups = data
    ? [...(data.sprints || []), data.backlog].filter(Boolean)
    : [];

  /** Sprint co the nhan cong viec khi dong Sprint hien tai (FUTURE, khong phai chinh no). */
  const futureSprints = (data?.sprints || []).filter((s) => s.state === 'FUTURE');

  return {
    data,
    groups,
    futureSprints,
    epics: data?.epics || [],
    statuses: data?.statuses || [],
    loading,
    error,
    busy,
    reload: load,
    setData,
    createSprint,
    startSprint,
    completeSprint,
    updateSprint,
    deleteSprint,
  };
}

export default useProjectBacklog;
