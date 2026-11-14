import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate, useParams } from 'react-router-dom';
import { Bell, Settings, X } from 'lucide-react';
import { useNotifications } from './NotificationContext.jsx';
import NotificationPanel from './NotificationPanel.jsx';
import { ACTION_COLOR } from './notificationFormat.jsx';
import { wsPath } from '../utils/workspacePath.js';

const PANEL_WIDTH = 420;

/**
 * Chuông thông báo ở đầu sidebar. Bấm để mở bảng nổi cạnh sidebar.
 * Số đỏ = chưa đọc; chấm tím = còn việc "Cần xử lý".
 */
export default function NotificationBell() {
  const { counts } = useNotifications();
  const { workspaceId } = useParams();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const buttonRef = useRef(null);
  const panelRef = useRef(null);
  const [pos, setPos] = useState({ top: 8, left: 264 });

  useEffect(() => {
    if (!open) return undefined;
    // Đặt bảng ngay bên phải sidebar (không che các mục và badge trong sidebar)
    const r = buttonRef.current?.getBoundingClientRect();
    const sidebarRight = buttonRef.current?.closest('aside')?.getBoundingClientRect().right;
    if (r) {
      const width = Math.min(PANEL_WIDTH, window.innerWidth - 24);
      const left = Math.min(Math.max(r.right, sidebarRight || 0) + 8, window.innerWidth - width - 12);
      setPos({ top: Math.max(8, r.top - 4), left: Math.max(12, left) });
    }
    const onDown = (e) => {
      if (panelRef.current?.contains(e.target) || buttonRef.current?.contains(e.target)) return;
      setOpen(false);
    };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const total = counts.total || 0;
  const label = total > 0 ? `Thông báo, ${total} chưa đọc` : 'Thông báo';

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        title={label}
        aria-label={label}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        style={{
          position: 'relative', width: 28, height: 28, borderRadius: 6, border: 'none',
          background: open ? '#EFF6FF' : 'transparent', color: open ? '#2563EB' : '#64748B',
          display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', flexShrink: 0,
        }}
        onMouseEnter={(e) => { if (!open) e.currentTarget.style.backgroundColor = '#F1F5F9'; }}
        onMouseLeave={(e) => { if (!open) e.currentTarget.style.backgroundColor = 'transparent'; }}
      >
        <Bell size={16} />
        {total > 0 && (
          <span style={{
            position: 'absolute', top: -4, right: -5, minWidth: 17, height: 17, padding: '0 4px', borderRadius: 999,
            background: '#DC2626', color: '#FFFFFF', fontSize: 10, fontWeight: 700, lineHeight: '17px', textAlign: 'center',
            border: '2px solid #FFFFFF', boxSizing: 'content-box',
          }}
          >
            {total > 99 ? '99+' : total}
          </span>
        )}
        {total === 0 && counts.action > 0 && (
          <span style={{ position: 'absolute', top: 2, right: 2, width: 7, height: 7, borderRadius: '50%', background: ACTION_COLOR.color }} />
        )}
      </button>

      {open && createPortal(
        <div
          ref={panelRef}
          role="dialog"
          aria-label="Thông báo"
          style={{
            position: 'fixed', top: pos.top, left: pos.left, width: PANEL_WIDTH, maxWidth: 'calc(100vw - 24px)',
            maxHeight: 'calc(100vh - 24px)', display: 'flex', flexDirection: 'column', background: '#FFFFFF',
            border: '1px solid #E2E8F0', borderRadius: 12, boxShadow: '0 16px 40px rgba(15, 23, 42, 0.16)', zIndex: 10040,
            overflow: 'hidden', fontFamily: 'inherit',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', padding: '12px 12px 8px 14px' }}>
            <span style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', flex: 1 }}>Thông báo</span>
            <button
              type="button"
              aria-label="Cài đặt thông báo"
              title="Cài đặt thông báo"
              onClick={() => { setOpen(false); navigate(wsPath(workspaceId, '/notifications?tab=settings')); }}
              style={{ border: 'none', background: 'none', color: '#94A3B8', cursor: 'pointer', display: 'flex', padding: 4, borderRadius: 6 }}
            >
              <Settings size={16} />
            </button>
            <button
              type="button"
              aria-label="Đóng"
              onClick={() => setOpen(false)}
              style={{ border: 'none', background: 'none', color: '#94A3B8', cursor: 'pointer', display: 'flex', padding: 4, borderRadius: 6 }}
            >
              <X size={16} />
            </button>
          </div>
          <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
            <NotificationPanel compact maxHeight="min(520px, calc(100vh - 150px))" onNavigate={() => setOpen(false)} />
          </div>
          <div style={{ borderTop: '1px solid #E2E8F0', padding: '8px 12px' }}>
            <button
              type="button"
              onClick={() => { setOpen(false); navigate(wsPath(workspaceId, '/notifications')); }}
              style={{ border: 'none', background: 'none', color: '#2563EB', fontSize: 12.5, fontWeight: 600, cursor: 'pointer', padding: 4 }}
            >
              Xem tất cả thông báo →
            </button>
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
