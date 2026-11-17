import { useEffect } from 'react';
import { BellRing, Loader2, Lock, Mail } from 'lucide-react';
import { useNotifications } from './NotificationContext.jsx';
import { CATEGORY_META } from './notificationFormat.jsx';

const CATEGORY_ORDER = ['TASK', 'APPROVAL', 'COMMENT', 'REMINDER', 'DEPENDENCY', 'SPRINT', 'MEMBER', 'PROJECT', 'CONTRIBUTION'];

/**
 * Cài đặt thông báo: bật / tắt từng nhóm ở hai kênh (Trong ứng dụng, Email) và danh sách dự án đang tắt tiếng.
 * Đổi là lưu ngay. Một số loại quan trọng luôn hiện trong chuông (ghi rõ dưới từng nhóm).
 */
export default function NotificationSettings({ projectNames = {} }) {
  const { preferences, loadPreferences, savePreference, unmuteProject } = useNotifications();

  useEffect(() => {
    loadPreferences(true);
  }, [loadPreferences]);

  if (!preferences) {
    return (
      <div style={{ padding: 32, textAlign: 'center', color: '#94A3B8', fontSize: 13 }}>
        <Loader2 size={16} style={{ verticalAlign: -3, marginRight: 6, animation: 'spin 1s linear infinite' }} />
        Đang tải cài đặt…
      </div>
    );
  }

  const byCategory = Object.fromEntries((preferences.categories || []).map((c) => [c.category, c]));
  const muted = preferences.mutedProjects || [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <section style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 132px 96px', alignItems: 'center', padding: '10px 16px', background: '#F8FAFC', borderBottom: '1px solid #E2E8F0', fontSize: 12, fontWeight: 600, color: '#475569' }}>
          <span>Nhóm thông báo</span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, justifyContent: 'center' }}><BellRing size={13} /> Trong ứng dụng</span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, justifyContent: 'center' }}><Mail size={13} /> Email</span>
        </div>
        {CATEGORY_ORDER.filter((c) => byCategory[c]).map((c) => {
          const pref = byCategory[c];
          const meta = CATEGORY_META[c];
          const Icon = meta.Icon;
          return (
            <div key={c} data-category={c} style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 132px 96px', alignItems: 'center', padding: '12px 16px', borderBottom: '1px solid #F1F5F9' }}>
              <div style={{ display: 'flex', gap: 10, minWidth: 0 }}>
                <span style={{ width: 28, height: 28, borderRadius: 8, background: '#F1F5F9', color: '#334155', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Icon size={15} />
                </span>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>{meta.label}</div>
                  {pref.inAppLocked ? (
                    <div style={{ fontSize: 11.5, color: '#64748B', marginTop: 2 }}>
                      Luôn hiện trong chuông vì liên quan quyền truy cập của bạn
                    </div>
                  ) : pref.alwaysInApp?.length > 0 && (
                    <div style={{ fontSize: 11.5, color: '#64748B', marginTop: 2 }}>
                      Vẫn luôn hiện: {pref.alwaysInApp.join(', ')}
                    </div>
                  )}
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'center' }}>
                {pref.inAppLocked ? (
                  <span title="Không tắt được" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: '#94A3B8', fontSize: 11.5 }}>
                    <Lock size={13} /> Luôn bật
                  </span>
                ) : (
                  <Switch
                    checked={pref.inApp}
                    label={`${meta.label}: trong ứng dụng`}
                    onChange={(v) => savePreference(c, { inApp: v })}
                  />
                )}
              </div>
              <div style={{ display: 'flex', justifyContent: 'center' }}>
                {pref.emailAvailable ? (
                  <Switch
                    checked={pref.email}
                    label={`${meta.label}: email`}
                    onChange={(v) => savePreference(c, { email: v })}
                  />
                ) : (
                  <span title="Nhóm này không gửi email" style={{ color: '#CBD5E1', fontSize: 13 }}>—</span>
                )}
              </div>
            </div>
          );
        })}
      </section>

      <section style={{ border: '1px solid #E2E8F0', borderRadius: 12, padding: '14px 16px' }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', marginBottom: 4 }}>Dự án đã tắt thông báo</div>
        <div style={{ fontSize: 12, color: '#64748B', marginBottom: 10 }}>
          Vẫn nhận thông báo gửi đích danh cho bạn (được giao việc, được nhắc tên, trả lời bình luận của bạn…) và việc cần bạn xử lý.
          Tắt / bật bằng biểu tượng chuông ở đầu trang dự án.
        </div>
        {muted.length === 0 ? (
          <div data-testid="no-muted-projects" style={{ fontSize: 12.5, color: '#94A3B8' }}>Chưa tắt thông báo dự án nào.</div>
        ) : (
          <div role="list" style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {muted.map((m) => (
              <div key={m.projectId} role="listitem" data-project-id={m.projectId} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 10px', borderRadius: 8, background: '#F8FAFC' }}>
                <span style={{ flex: 1, minWidth: 0, fontSize: 13, color: '#0F172A', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {projectNames[m.projectId] || m.projectName || 'Dự án'}
                </span>
                <button
                  type="button"
                  onClick={() => unmuteProject(m.projectId)}
                  style={{ border: '1px solid #E2E8F0', background: '#FFFFFF', borderRadius: 7, padding: '4px 10px', fontSize: 12, fontWeight: 600, color: '#2563EB', cursor: 'pointer' }}
                >
                  Bật lại
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function Switch({ checked, onChange, label }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      style={{
        width: 36, height: 20, borderRadius: 999, border: 'none', padding: 2, cursor: 'pointer',
        background: checked ? '#2563EB' : '#CBD5E1', display: 'flex', alignItems: 'center',
        justifyContent: checked ? 'flex-end' : 'flex-start', transition: 'background 0.15s ease',
      }}
    >
      <span style={{ width: 16, height: 16, borderRadius: '50%', background: '#FFFFFF', boxShadow: '0 1px 2px rgba(15, 23, 42, 0.25)' }} />
    </button>
  );
}
