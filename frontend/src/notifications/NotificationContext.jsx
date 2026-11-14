/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { notificationApi } from '../api.js';
import { connectNotificationSocket } from './notificationSocket.js';
import { parseBrowseLink, resolveItemPath } from './browseLink.js';
import { matchesFilter, plainTitle, SEVERITY_META, ACTION_COLOR } from './notificationFormat.jsx';

const NotificationContext = createContext(null);

const PAGE_SIZE = 20;
const POLL_MS = 30000;

/**
 * Nguồn dữ liệu chung cho chuông, bảng thông báo và trang /notifications.
 *
 * - Số chưa đọc / cần xử lý: tải khi vào, sau đó cập nhật qua WebSocket (mỗi thông điệp kèm số mới).
 * - Mất WebSocket thì hỏi lại mỗi 30 giây; quay lại tab thì đồng bộ ngay.
 * - Đọc / xoá cập nhật giao diện ngay (lạc quan), lỗi thì hoàn nguyên. Xoá có "Hoàn tác" 5 giây.
 * - Thông báo mới Khẩn cấp hoặc Cần xử lý thì hiện toast.
 */
export function NotificationProvider({ children }) {
  const navigate = useNavigate();
  const [counts, setCounts] = useState({ total: 0, action: 0 });
  const [items, setItems] = useState([]);
  const [filter, setFilterState] = useState('all');
  const [category, setCategoryState] = useState('');
  const [projectId, setProjectIdState] = useState('');
  const [nextCursor, setNextCursor] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [connected, setConnected] = useState(false);

  // Dùng ref để handler WebSocket luôn thấy bộ lọc mới nhất
  const viewRef = useRef({ filter: 'all', category: '', projectId: '' });
  const requestSeq = useRef(0);

  const applyCounts = useCallback((m) => {
    if (m && typeof m.unreadCount === 'number') {
      setCounts({ total: m.unreadCount, action: typeof m.actionCount === 'number' ? m.actionCount : 0 });
    }
  }, []);

  const refreshCounts = useCallback(async () => {
    try {
      const res = await notificationApi.counts();
      if (res?.data) setCounts({ total: res.data.total || 0, action: res.data.action || 0 });
    } catch {
      // im lặng: chuông giữ số cũ
    }
  }, []);

  const load = useCallback(async ({ filter: f, category: c, projectId: p, append = false } = {}) => {
    const view = {
      filter: f ?? viewRef.current.filter,
      category: c ?? viewRef.current.category,
      projectId: p ?? viewRef.current.projectId,
    };
    viewRef.current = view;
    setFilterState(view.filter);
    setCategoryState(view.category);
    setProjectIdState(view.projectId);
    const seq = ++requestSeq.current;
    setLoading(true);
    try {
      const res = await notificationApi.list(
        {
          filter: view.filter,
          category: view.category || undefined,
          projectId: view.projectId || undefined,
          size: PAGE_SIZE,
          cursor: append ? nextCursor || undefined : undefined,
        },
        { skipErrorToast: true },
      );
      if (seq !== requestSeq.current) return; // kết quả cũ về muộn
      const page = res?.data || {};
      const rows = Array.isArray(page.items) ? page.items : [];
      setItems((prev) => {
        if (!append) return rows;
        const seen = new Set(prev.map((n) => n.id));
        return [...prev, ...rows.filter((n) => !seen.has(n.id))];
      });
      setNextCursor(page.nextCursor || null);
      setCounts({ total: page.unreadCount || 0, action: page.actionCount || 0 });
      setLoaded(true);
    } catch {
      if (seq === requestSeq.current) setLoaded(true);
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  }, [nextCursor]);

  const loadMore = useCallback(() => {
    if (nextCursor && !loading) load({ append: true });
  }, [nextCursor, loading, load]);

  // Nơi khác (VD: badge Lời mời, danh sách dự án ở sidebar) nghe thông điệp để tự làm mới
  const listenersRef = useRef(new Set());
  const subscribe = useCallback((fn) => {
    listenersRef.current.add(fn);
    return () => listenersRef.current.delete(fn);
  }, []);

  // ─── WebSocket ───
  const handleMessage = useCallback((m) => {
    applyCounts(m);
    listenersRef.current.forEach((fn) => {
      try {
        fn(m);
      } catch {
        // một listener lỗi không được chặn các listener khác
      }
    });
    const { filter: f, category: c, projectId: p } = viewRef.current;
    switch (m.kind) {
      case 'CREATED':
      case 'RESTORED': {
        const n = m.notification;
        if (!n) break;
        if (matchesFilter(n, f, c, p)) {
          setItems((prev) => [n, ...prev.filter((x) => x.id !== n.id)]);
        }
        if (m.kind === 'CREATED' && (n.severity === 'URGENT' || (n.actionRequired && !n.actionResolved))) {
          const urgent = n.severity === 'URGENT';
          toast(plainTitle(n.title), {
            id: `notif-${n.id}`,
            duration: 6000,
            icon: urgent ? '⛔' : '📌',
            style: { borderLeft: `4px solid ${urgent ? SEVERITY_META.URGENT.color : ACTION_COLOR.color}`, fontSize: 13 },
          });
        }
        break;
      }
      case 'UPDATED': {
        const n = m.notification;
        if (!n) break;
        setItems((prev) => {
          const others = prev.filter((x) => x.id !== n.id);
          if (!matchesFilter(n, f, c, p)) return others;
          const old = prev.find((x) => x.id === n.id);
          // Gộp (updatedAt đổi) thì nổi lên đầu; chỉ đổi trạng thái "đã xử lý" thì giữ chỗ
          if (old && old.updatedAt === n.updatedAt) return prev.map((x) => (x.id === n.id ? n : x));
          return [n, ...others];
        });
        break;
      }
      case 'READ':
      case 'UNREAD': {
        const ids = new Set(m.ids || []);
        const read = m.kind === 'READ';
        setItems((prev) => prev
          .map((x) => (ids.has(x.id) ? { ...x, read } : x))
          .filter((x) => matchesFilter(x, f, c, p) || !ids.has(x.id)));
        break;
      }
      case 'DELETED': {
        const ids = new Set(m.ids || []);
        setItems((prev) => prev.filter((x) => !ids.has(x.id)));
        break;
      }
      default:
        break;
    }
  }, [applyCounts]);

  useEffect(() => {
    let alive = true;
    notificationApi.counts()
      .then((res) => {
        if (alive && res?.data) setCounts({ total: res.data.total || 0, action: res.data.action || 0 });
      })
      .catch(() => {});
    const disconnect = connectNotificationSocket({
      onMessage: handleMessage,
      onStatus: (s) => setConnected(s === 'connected'),
    });
    return () => {
      alive = false;
      disconnect();
    };
  }, [handleMessage]);

  // Lưới an toàn: mất WebSocket thì hỏi lại định kỳ; quay lại tab thì đồng bộ ngay
  useEffect(() => {
    if (connected) return undefined;
    const t = setInterval(refreshCounts, POLL_MS);
    return () => clearInterval(t);
  }, [connected, refreshCounts]);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') refreshCounts();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [refreshCounts]);

  // ─── Thao tác ───
  const markRead = useCallback(async (n, read = true) => {
    if (!n || n.read === read) return;
    setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read } : x)));
    setCounts((c) => ({ ...c, total: Math.max(0, c.total + (read ? -1 : 1)) }));
    try {
      await notificationApi.setRead(n.id, read);
    } catch {
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: !read } : x)));
      refreshCounts();
    }
  }, [refreshCounts]);

  const restore = useCallback(async (n) => {
    try {
      const res = await notificationApi.restore(n.id);
      const restored = res?.data || n;
      const v = viewRef.current;
      if (matchesFilter(restored, v.filter, v.category, v.projectId)) {
        setItems((prev) => {
          if (prev.some((x) => x.id === restored.id)) return prev;
          const next = [...prev, restored];
          return next.sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)));
        });
      }
      refreshCounts();
    } catch {
      // interceptor đã báo lỗi
    }
  }, [refreshCounts]);

  const remove = useCallback(async (n) => {
    if (!n) return;
    setItems((prev) => prev.filter((x) => x.id !== n.id));
    try {
      const res = await notificationApi.remove(n.id);
      if (res?.data) setCounts({ total: res.data.total || 0, action: res.data.action || 0 });
      toast((t) => (
        <span style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 13 }}>
          Đã xoá thông báo
          <button
            type="button"
            onClick={() => { toast.dismiss(t.id); restore(n); }}
            style={{ border: 'none', background: 'none', color: '#2563EB', fontWeight: 700, cursor: 'pointer', padding: 0, fontSize: 13 }}
          >
            Hoàn tác
          </button>
        </span>
      ), { id: `notif-del-${n.id}`, duration: 5000 });
    } catch {
      setItems((prev) => [n, ...prev]);
    }
  }, [restore]);

  const markAllRead = useCallback(async () => {
    const f = viewRef.current.filter;
    // Đang ở tab "Chưa đọc" thì danh sách trống luôn; tab khác giữ nguyên, chỉ đổi sang đã đọc
    setItems((prev) => (f === 'unread' ? [] : prev.map((x) => ({ ...x, read: true }))));
    try {
      const res = await notificationApi.readAll({
        filter: f === 'action' ? 'action' : undefined,
        category: viewRef.current.category || undefined,
        projectId: viewRef.current.projectId || undefined,
      });
      const c = res?.data?.counts;
      if (c) setCounts({ total: c.total || 0, action: c.action || 0 });
    } catch {
      load({});
    }
  }, [load]);

  const removeAllRead = useCallback(async () => {
    try {
      const res = await notificationApi.removeAllRead();
      const n = res?.data?.deleted || 0;
      setItems((prev) => prev.filter((x) => !x.read));
      toast.success(n > 0 ? `Đã xoá ${n} thông báo đã đọc` : 'Không có thông báo đã đọc để xoá');
    } catch {
      // interceptor đã báo lỗi
    }
  }, []);

  /**
   * Bấm vào thông báo: đánh dấu đã đọc rồi mở đường dẫn.
   * Link "/browse/{mã}" được tra ngay tại đây rồi chuyển thẳng tới trang dự án, để không phải
   * đi qua trang trung gian (tránh dựng lại toàn bộ khung trang).
   */
  const open = useCallback(async (n) => {
    if (!n) return;
    if (!n.read) markRead(n, true);
    if (!n.link) return;
    const browse = parseBrowseLink(n.link);
    if (!browse) {
      navigate(n.link);
      return;
    }
    try {
      navigate(await resolveItemPath(browse.code, { commentId: browse.commentId }));
    } catch (err) {
      toast.error(err.friendlyMessage || 'Không mở được công việc', { id: `notif-open-${n.id}` });
    }
  }, [markRead, navigate]);

  const value = useMemo(() => ({
    counts, items, filter, category, projectId, nextCursor, loading, loaded, connected,
    load, loadMore, refreshCounts, markRead, markAllRead, remove, removeAllRead, open, subscribe,
  }), [counts, items, filter, category, projectId, nextCursor, loading, loaded, connected,
    load, loadMore, refreshCounts, markRead, markAllRead, remove, removeAllRead, open, subscribe]);

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}

export function useNotifications() {
  const ctx = useContext(NotificationContext);
  if (!ctx) throw new Error('useNotifications phải nằm trong <NotificationProvider>');
  return ctx;
}
