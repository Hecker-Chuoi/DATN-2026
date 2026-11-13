import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { workItemApi } from '../api.js';

/**
 * Trạng thái dùng chung của khung chat AI: mở/đóng panel, work item đang mở trên màn hình
 * (để trợ lý hiểu "việc này"), và cách mở WorkItemDetailModal từ một mã như AUTH-12.
 * ProjectDetailPage đăng ký hàm mở modal; khi chưa có trang dự án nào, link mã chỉ báo toast.
 */
const AiChatContext = createContext(null);

export function AiChatProvider({ children }) {
  const [open, setOpen] = useState(false);
  const [workItemId, setWorkItemId] = useState(null);
  const openerRef = useRef(null);

  const registerItemOpener = useCallback((fn) => {
    openerRef.current = fn;
    return () => {
      if (openerRef.current === fn) openerRef.current = null;
    };
  }, []);

  const openItemByCode = useCallback(async (code) => {
    if (!openerRef.current) {
      toast('Mở dự án chứa ' + code + ' để xem chi tiết công việc', { icon: 'ℹ️' });
      return;
    }
    try {
      const res = await workItemApi.getByCode(code);
      const id = res?.data?.id;
      if (id) openerRef.current(id);
    } catch {
      // interceptor đã toast lỗi
    }
  }, []);

  const value = useMemo(
    () => ({ open, setOpen, toggle: () => setOpen((v) => !v), workItemId, setWorkItemId, registerItemOpener, openItemByCode }),
    [open, workItemId, registerItemOpener, openItemByCode]
  );

  return <AiChatContext.Provider value={value}>{children}</AiChatContext.Provider>;
}

export function useAiPanel() {
  return useContext(AiChatContext);
}
