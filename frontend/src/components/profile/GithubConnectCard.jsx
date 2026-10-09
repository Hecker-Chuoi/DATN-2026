import { useEffect, useState } from 'react';
import { Github, Link2, Unlink, AlertTriangle } from 'lucide-react';
import toast from 'react-hot-toast';
import { githubApi } from '../../api.js';

/**
 * Card "Tài khoản liên kết" trên trang cá nhân: kết nối / ngắt kết nối GitHub (OAuth App).
 * Kết nối là redirect toàn trang sang GitHub, quay về /github/callback.
 */
export default function GithubConnectCard() {
  const [status, setStatus] = useState(null); // {connected, login, avatarUrl, invalid}
  const [busy, setBusy] = useState(false);

  const load = () =>
    githubApi
      .me()
      .then((res) => setStatus(res?.data || { connected: false }))
      .catch(() => setStatus({ connected: false }));

  useEffect(() => {
    load();
  }, []);

  const connect = async () => {
    try {
      setBusy(true);
      const res = await githubApi.getAuthorizeUrl(`${window.location.origin}/github/callback`);
      if (res?.data?.url) window.location.href = res.data.url;
    } catch {
      setBusy(false);
    }
  };

  const disconnect = async () => {
    if (!window.confirm('Ngắt kết nối GitHub? Các repo đã liên kết với dự án vẫn giữ nguyên.')) return;
    try {
      setBusy(true);
      await githubApi.disconnect();
      toast.success('Đã ngắt kết nối GitHub');
      await load();
    } catch {
      // interceptor đã toast
    } finally {
      setBusy(false);
    }
  };

  const btn = (primary) => ({
    display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 14px', borderRadius: 8, fontSize: 13, fontWeight: 600,
    cursor: busy ? 'not-allowed' : 'pointer', border: primary ? 'none' : '1px solid #CBD5E1',
    background: primary ? '#0F172A' : '#FFF', color: primary ? '#FFF' : '#475569', opacity: busy ? 0.6 : 1,
  });

  return (
    <div style={{ backgroundColor: '#FFFFFF', borderRadius: 12, border: '1px solid #E2E8F0', padding: 24, boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <div style={{ width: 32, height: 32, borderRadius: 8, backgroundColor: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#0F172A' }}>
          <Link2 size={16} />
        </div>
        <div>
          <h3 style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', margin: 0 }}>Tài khoản liên kết</h3>
          <p style={{ fontSize: 12, color: '#64748B', margin: 0 }}>Dùng để liên kết repo GitHub với dự án phần mềm</p>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', border: '1px solid #E2E8F0', borderRadius: 10, background: '#F8FAFC' }}>
        {status?.connected && status.avatarUrl ? (
          <img src={status.avatarUrl} alt="" style={{ width: 36, height: 36, borderRadius: '50%' }} />
        ) : (
          <Github size={28} color="#0F172A" />
        )}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
            GitHub{status?.connected ? ` · @${status.login}` : ''}
          </div>
          <div style={{ fontSize: 12, color: status?.invalid ? '#B45309' : '#64748B', display: 'flex', alignItems: 'center', gap: 4 }}>
            {status == null && 'Đang kiểm tra...'}
            {status?.connected && !status.invalid && 'Đã kết nối'}
            {status?.connected && status.invalid && (<><AlertTriangle size={12} /> Token hết hiệu lực, hãy kết nối lại</>)}
            {status && !status.connected && 'Chưa kết nối'}
          </div>
        </div>
        {status?.connected && !status.invalid ? (
          <button type="button" onClick={disconnect} disabled={busy} style={btn(false)}>
            <Unlink size={14} /> Ngắt kết nối
          </button>
        ) : (
          <button type="button" onClick={connect} disabled={busy || status == null} style={btn(true)}>
            <Github size={14} /> {status?.invalid ? 'Kết nối lại' : 'Kết nối GitHub'}
          </button>
        )}
      </div>
    </div>
  );
}
