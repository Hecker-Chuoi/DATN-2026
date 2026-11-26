import { useEffect, useState } from 'react';
import { Bell, BellOff } from 'lucide-react';
import { useNotifications } from './NotificationContext.jsx';

/**
 * Nút ở đầu trang dự án: tắt / bật thông báo của dự án này cho riêng mình.
 * Tắt rồi vẫn nhận thông báo gửi đích danh và việc cần mình xử lý.
 */
export default function ProjectMuteButton({ projectId, projectName }) {
  const { preferences, loadPreferences, muteProject, unmuteProject } = useNotifications();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    loadPreferences();
  }, [loadPreferences]);

  if (!projectId || !preferences) return null;
  const muted = (preferences.mutedProjects || []).some((m) => m.projectId === projectId);
  const label = muted ? 'Bật lại thông báo của dự án này' : 'Tắt thông báo của dự án này';

  const toggle = async () => {
    setBusy(true);
    try {
      if (muted) await unmuteProject(projectId);
      else await muteProject(projectId, projectName);
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      type="button"
      onClick={toggle}
      disabled={busy}
      title={label}
      aria-label={label}
      aria-pressed={muted}
      style={{
        background: muted ? '#FEF3C7' : 'transparent', border: 'none', cursor: busy ? 'wait' : 'pointer',
        color: muted ? '#B45309' : '#94A3B8', padding: 4, borderRadius: 4, display: 'flex', alignItems: 'center',
      }}
    >
      {muted ? <BellOff size={15} /> : <Bell size={15} />}
    </button>
  );
}
