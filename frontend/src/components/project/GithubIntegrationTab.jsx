import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Github, ExternalLink, Unlink, RefreshCw, AlertCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import { githubApi } from '../../api.js';

const card = { backgroundColor: '#FFFFFF', border: '1px solid #E2E8F0', borderRadius: 10, padding: 20 };
const btn = (primary, disabled) => ({
  display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 14px', borderRadius: 8, fontSize: 13, fontWeight: 600,
  cursor: disabled ? 'not-allowed' : 'pointer', border: primary ? 'none' : '1px solid #CBD5E1',
  background: primary ? '#2563EB' : '#FFF', color: primary ? '#FFF' : '#475569', opacity: disabled ? 0.6 : 1,
});

/**
 * Sub-tab "Tích hợp GitHub" trong Cấu hình dự án (chỉ dự án phần mềm, admin).
 * Liên kết 1 repo / dự án; backend tự tạo webhook. Người dùng phải kết nối GitHub ở trang cá nhân trước.
 */
export default function GithubIntegrationTab({ projectId }) {
  const [me, setMe] = useState(null);
  const [link, setLink] = useState(null);
  const [loading, setLoading] = useState(true);
  const [repos, setRepos] = useState(null);
  const [selected, setSelected] = useState('');
  const [onOpen, setOnOpen] = useState(true);
  const [onMerge, setOnMerge] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = async () => {
    setLoading(true);
    const [m, l] = await Promise.allSettled([githubApi.me(), githubApi.getRepoLink(projectId)]);
    setMe(m.status === 'fulfilled' ? m.value?.data || { connected: false } : { connected: false });
    const lk = l.status === 'fulfilled' ? l.value?.data || null : null;
    setLink(lk);
    if (lk) {
      setOnOpen(lk.autoTransitionOnPrOpen);
      setOnMerge(lk.autoTransitionOnPrMerge);
    }
    setLoading(false);
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const fetchRepos = async () => {
    try {
      setBusy(true);
      const res = await githubApi.listRepos();
      const list = Array.isArray(res?.data) ? res.data : [];
      setRepos(list);
      if (list.length && !selected) setSelected(list[0].fullName);
    } catch {
      // interceptor đã toast
    } finally {
      setBusy(false);
    }
  };

  const save = async (owner, name) => {
    try {
      setBusy(true);
      const res = await githubApi.linkRepo(projectId, { owner, name, autoOnPrOpen: onOpen, autoOnPrMerge: onMerge });
      toast.success(res?.message || 'Đã lưu');
      setLink(res?.data || null);
      setRepos(null);
    } catch {
      // interceptor đã toast
    } finally {
      setBusy(false);
    }
  };

  const unlink = async () => {
    if (!window.confirm(`Gỡ liên kết repo ${link.repoOwner}/${link.repoName}? Webhook trên GitHub sẽ bị xóa.`)) return;
    try {
      setBusy(true);
      await githubApi.unlinkRepo(projectId);
      toast.success('Đã gỡ liên kết');
      setLink(null);
    } catch {
      // interceptor đã toast
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <div style={{ fontSize: 13, color: '#94A3B8' }}>Đang tải...</div>;

  const toggles = (disabled) => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 13, color: '#334155' }}>
      <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <input type="checkbox" checked={onOpen} onChange={(e) => setOnOpen(e.target.checked)} disabled={disabled} />
        Mở Pull Request nhắc key issue → tự chuyển issue sang <b>Đang làm</b>
      </label>
      <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <input type="checkbox" checked={onMerge} onChange={(e) => setOnMerge(e.target.checked)} disabled={disabled} />
        Merge Pull Request → tự chuyển issue sang <b>Hoàn thành</b>
      </label>
    </div>
  );

  return (
    <div style={card}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, fontSize: 15, fontWeight: 700, color: '#0F172A' }}>
        <Github size={18} /> Tích hợp GitHub
      </div>
      <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 18px' }}>
        Liên kết một repository với dự án. Commit, branch và Pull Request nhắc tới key issue (ví dụ <code>DATN-5</code>) sẽ hiện trên issue tương ứng.
      </p>

      {!me?.connected || me.invalid ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px', borderRadius: 8, background: '#FFFBEB', border: '1px solid #FCD34D', color: '#92400E', fontSize: 13 }}>
          <AlertCircle size={16} />
          <span>
            {me?.invalid ? 'Token GitHub của bạn hết hiệu lực. ' : 'Bạn chưa kết nối tài khoản GitHub. '}
            <Link to="/profile" style={{ color: '#2563EB', fontWeight: 600 }}>Kết nối ở Trang cá nhân</Link> rồi quay lại đây.
          </span>
        </div>
      ) : link ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', borderRadius: 8, background: '#F8FAFC', border: '1px solid #E2E8F0' }}>
            <Github size={22} color="#0F172A" />
            <div style={{ flex: 1 }}>
              <a href={link.htmlUrl} target="_blank" rel="noreferrer" style={{ fontSize: 14, fontWeight: 600, color: '#2563EB', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                {link.repoOwner}/{link.repoName} <ExternalLink size={13} />
              </a>
              <div style={{ fontSize: 12, color: '#64748B' }}>
                Nhánh mặc định <code>{link.defaultBranch}</code> · Webhook {link.webhookId ? `#${link.webhookId}` : 'chưa tạo'}
              </div>
            </div>
            <button type="button" onClick={unlink} disabled={busy} style={btn(false, busy)}>
              <Unlink size={14} /> Gỡ liên kết
            </button>
          </div>
          {toggles(busy)}
          <div>
            <button type="button" onClick={() => save(link.repoOwner, link.repoName)} disabled={busy} style={btn(true, busy)}>
              Lưu tùy chọn
            </button>
          </div>
        </div>
      ) : repos == null ? (
        <button type="button" onClick={fetchRepos} disabled={busy} style={btn(true, busy)}>
          <RefreshCw size={14} className={busy ? 'spin' : ''} /> Chọn repository
        </button>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {repos.length === 0 ? (
            <div style={{ fontSize: 13, color: '#64748B' }}>Tài khoản GitHub của bạn chưa có repository nào.</div>
          ) : (
            <select value={selected} onChange={(e) => setSelected(e.target.value)} style={{ padding: '8px 12px', borderRadius: 6, border: '1px solid #CBD5E1', fontSize: 13, maxWidth: 480 }}>
              {repos.map((r) => (
                <option key={r.id} value={r.fullName}>{r.fullName}{r.isPrivate ? ' (private)' : ''}</option>
              ))}
            </select>
          )}
          {toggles(busy)}
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              type="button"
              disabled={busy || !selected}
              onClick={() => { const [owner, name] = selected.split('/'); save(owner, name); }}
              style={btn(true, busy || !selected)}
            >
              <Github size={14} /> Liên kết
            </button>
            <button type="button" onClick={() => setRepos(null)} disabled={busy} style={btn(false, busy)}>Hủy</button>
          </div>
          <div style={{ fontSize: 12, color: '#94A3B8' }}>
            Hệ thống sẽ tạo webhook trên repo; bạn cần quyền admin repo. Khi dev local, webhook chỉ nhận được qua URL public (ngrok).
          </div>
        </div>
      )}
    </div>
  );
}
