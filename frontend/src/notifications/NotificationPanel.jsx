import { useEffect } from 'react';
import { CheckCheck, Inbox, Loader2, WifiOff } from 'lucide-react';
import { useNotifications } from './NotificationContext.jsx';
import NotificationItem from './NotificationItem.jsx';
import { ACTION_COLOR, FILTERS } from './notificationFormat.jsx';

/**
 * Danh sách thông báo kèm tab Tất cả / Chưa đọc / Cần xử lý.
 * Dùng cho popover ở chuông (compact) và trang /notifications.
 */
export default function NotificationPanel({ compact = false, onNavigate, maxHeight }) {
  const {
    items, filter, category, counts, loading, loaded, nextCursor, connected,
    load, loadMore, markRead, markAllRead, remove, open,
  } = useNotifications();

  // Mở bảng là tải lại trang đầu (số liệu mới nhất). Bảng nổi ở chuông luôn xem mọi nhóm, mọi dự án;
  // trang /notifications giữ nguyên bộ lọc nhóm / dự án đang chọn.
  useEffect(() => {
    if (compact) load({ filter, category: '', projectId: '' });
    else load({ filter, category });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const hasUnreadVisible = items.some((n) => !n.read);
  const emptyText = filter === 'unread'
    ? 'Bạn đã đọc hết thông báo.'
    : filter === 'action' ? 'Không còn việc nào cần bạn xử lý.' : 'Chưa có thông báo nào.';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 0, height: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: compact ? '0 10px 8px' : '0 14px 10px', borderBottom: '1px solid #E2E8F0', flexWrap: 'wrap' }}>
        <div role="tablist" aria-label="Lọc thông báo" style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {FILTERS.map((f) => {
            const active = filter === f.id;
            const isAction = f.id === 'action';
            const count = f.id === 'unread' ? counts.total : f.id === 'action' ? counts.action : null;
            const accent = isAction ? ACTION_COLOR : { soft: '#EFF6FF', ink: '#1D4ED8', color: '#2563EB' };
            return (
              <button
                key={f.id}
                type="button"
                role="tab"
                aria-selected={active}
                onClick={() => load({ filter: f.id })}
                style={{
                  display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 10px', borderRadius: 7,
                  border: `1px solid ${active ? accent.color : 'transparent'}`,
                  background: active ? accent.soft : 'transparent',
                  color: active ? accent.ink : '#475569', fontSize: 12.5, fontWeight: active ? 600 : 500, cursor: 'pointer',
                }}
              >
                {f.label}
                {count != null && (
                  <span style={{
                    fontSize: 11, fontWeight: 700, minWidth: 18, padding: '0 5px', borderRadius: 999, textAlign: 'center',
                    background: active ? accent.color : '#F1F5F9', color: active ? '#FFFFFF' : '#64748B',
                  }}
                  >
                    {count > 99 ? '99+' : count}
                  </span>
                )}
              </button>
            );
          })}
        </div>
        <button
          type="button"
          onClick={markAllRead}
          disabled={!hasUnreadVisible}
          title="Đánh dấu tất cả đã đọc"
          style={{
            marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 5, border: 'none', background: 'none',
            color: hasUnreadVisible ? '#2563EB' : '#94A3B8', fontSize: 12.5, fontWeight: 600,
            cursor: hasUnreadVisible ? 'pointer' : 'default', padding: '4px 6px', borderRadius: 6,
          }}
        >
          <CheckCheck size={14} /> {compact ? 'Đọc tất cả' : 'Đánh dấu tất cả đã đọc'}
        </button>
      </div>

      {!connected && loaded && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11.5, color: '#92400E', background: '#FFFBEB', padding: '5px 12px' }}>
          <WifiOff size={12} /> Đang kết nối lại để nhận thông báo tức thì…
        </div>
      )}

      <div role="list" style={{ overflowY: 'auto', flex: 1, maxHeight: maxHeight || undefined }}>
        {items.map((n) => (
          <NotificationItem
            key={n.id}
            n={n}
            compact={compact}
            onOpen={(x) => { open(x); onNavigate?.(); }}
            onToggleRead={(x, read) => markRead(x, read)}
            onDelete={remove}
          />
        ))}
        {!loading && loaded && items.length === 0 && (
          <div style={{ padding: '40px 16px', textAlign: 'center', color: '#94A3B8', fontSize: 13 }}>
            <Inbox size={28} style={{ display: 'block', margin: '0 auto 8px', opacity: 0.6 }} />
            {emptyText}
          </div>
        )}
        {loading && (
          <div style={{ padding: 16, textAlign: 'center', color: '#94A3B8', fontSize: 12.5 }}>
            <Loader2 size={16} style={{ verticalAlign: -3, marginRight: 6, animation: 'spin 1s linear infinite' }} />
            Đang tải…
          </div>
        )}
        {!loading && nextCursor && (
          <div style={{ padding: 10, textAlign: 'center' }}>
            <button
              type="button"
              onClick={loadMore}
              style={{ border: '1px solid #E2E8F0', background: '#FFFFFF', borderRadius: 7, padding: '6px 14px', fontSize: 12.5, color: '#334155', cursor: 'pointer' }}
            >
              Tải thêm
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
