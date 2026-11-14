import { useOutletContext } from 'react-router-dom';
import { Bell, Trash2 } from 'lucide-react';
import { useNotifications } from '../notifications/NotificationContext.jsx';
import NotificationPanel from '../notifications/NotificationPanel.jsx';
import { CATEGORY_META } from '../notifications/notificationFormat.jsx';
import CustomSelect from '../components/common/CustomSelect.jsx';

const CATEGORY_ORDER = ['TASK', 'APPROVAL', 'COMMENT', 'REMINDER', 'DEPENDENCY', 'SPRINT', 'MEMBER', 'PROJECT', 'CONTRIBUTION'];

/**
 * Trang thông báo đầy đủ: lọc theo trạng thái (tab), nhóm và dự án; đánh dấu đã đọc / xoá từng cái,
 * đánh dấu tất cả đã đọc, xoá mọi thông báo đã đọc.
 */
export default function NotificationsPage() {
  const { projects = [] } = useOutletContext() || {};
  const { counts, category, projectId, load, removeAllRead } = useNotifications();

  const projectOptions = [
    { value: '', label: 'Tất cả dự án' },
    ...projects.map((p) => ({ value: p.id, label: p.name, sublabel: p.key })),
  ];

  return (
    <div style={{ backgroundColor: '#FFFFFF', minHeight: '100%', display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden' }}>
      <div style={{ height: 52, minHeight: 52, padding: '0 32px', borderBottom: '1px solid #E2E8F0', display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
        <Bell size={16} color="#2563EB" />
        <span>Thông báo</span>
        {counts.total > 0 && (
          <span style={{ fontSize: 11, fontWeight: 700, backgroundColor: '#DBEAFE', color: '#1D4ED8', padding: '1px 7px', borderRadius: 10 }}>
            {counts.total > 99 ? '99+' : counts.total} chưa đọc
          </span>
        )}
        <button
          type="button"
          onClick={() => {
            if (window.confirm('Xoá tất cả thông báo đã đọc? Thông báo chưa đọc vẫn được giữ lại.')) removeAllRead();
          }}
          title="Xoá mọi thông báo đã đọc"
          style={{
            marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 6, border: '1px solid #E2E8F0', background: '#FFFFFF',
            color: '#475569', fontSize: 12.5, fontWeight: 600, padding: '6px 12px', borderRadius: 7, cursor: 'pointer',
          }}
          onMouseEnter={(e) => { e.currentTarget.style.borderColor = '#FCA5A5'; e.currentTarget.style.color = '#B91C1C'; }}
          onMouseLeave={(e) => { e.currentTarget.style.borderColor = '#E2E8F0'; e.currentTarget.style.color = '#475569'; }}
        >
          <Trash2 size={14} /> Xoá tất cả đã đọc
        </button>
      </div>

      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
        <div style={{ width: '100%', maxWidth: 880, margin: '0 auto', padding: '20px 32px 32px', display: 'flex', flexDirection: 'column', gap: 14, boxSizing: 'border-box' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <div role="group" aria-label="Lọc theo nhóm" style={{ display: 'flex', gap: 6, flexWrap: 'wrap', flex: 1, minWidth: 0 }}>
              <Chip active={!category} onClick={() => load({ category: '' })}>Mọi nhóm</Chip>
              {CATEGORY_ORDER.map((c) => {
                const meta = CATEGORY_META[c];
                const Icon = meta.Icon;
                return (
                  <Chip key={c} active={category === c} onClick={() => load({ category: category === c ? '' : c })}>
                    <Icon size={13} /> {meta.label}
                  </Chip>
                );
              })}
            </div>
            {projects.length > 0 && (
              <CustomSelect
                options={projectOptions}
                value={projectId || ''}
                onChange={(v) => load({ projectId: v || '' })}
                width={220}
                placeholder="Tất cả dự án"
              />
            )}
          </div>

          <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden', paddingTop: 10, background: '#FFFFFF' }}>
            <NotificationPanel />
          </div>
        </div>
      </div>
    </div>
  );
}

function Chip({ active, onClick, children }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 5, padding: '5px 10px', borderRadius: 999, fontSize: 12, fontWeight: active ? 600 : 500,
        border: `1px solid ${active ? '#2563EB' : '#E2E8F0'}`, background: active ? '#EFF6FF' : '#FFFFFF', color: active ? '#1D4ED8' : '#475569',
        cursor: 'pointer',
      }}
    >
      {children}
    </button>
  );
}
