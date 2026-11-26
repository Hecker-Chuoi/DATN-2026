import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Loader2, SearchX, ShieldAlert } from 'lucide-react';
import { resolveItemPath } from '../notifications/browseLink.js';

/**
 * /browse/:code — đích của link trong email và thông báo.
 * Tra mã công việc rồi chuyển sang trang dự án với chi tiết công việc mở sẵn.
 */
export default function BrowseItemRedirect() {
  const { code } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    const commentId = new URLSearchParams(location.search).get('comment') || undefined;
    resolveItemPath(code, { commentId })
      .then((path) => { if (!cancelled) navigate(path, { replace: true }); })
      .catch((err) => { if (!cancelled) setError(err); });
    return () => { cancelled = true; };
  }, [code, location.search, navigate]);

  if (!error) {
    return (
      <div style={pageStyle}>
        <Loader2 size={20} color="#2563EB" style={{ animation: 'spin 1s linear infinite' }} />
        <span style={{ fontSize: 13, color: '#64748B' }}>Đang mở công việc {code}…</span>
      </div>
    );
  }

  const forbidden = error.status === 403;
  const Icon = forbidden ? ShieldAlert : SearchX;
  return (
    <div style={pageStyle}>
      <div style={{ width: 52, height: 52, borderRadius: 12, background: forbidden ? '#FEE2E2' : '#FEF3C7', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Icon size={24} color={forbidden ? '#DC2626' : '#B45309'} />
      </div>
      <h2 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: 0 }}>
        {forbidden ? 'Bạn không có quyền xem công việc này' : 'Không mở được công việc'}
      </h2>
      <p style={{ fontSize: 13, color: '#64748B', margin: 0, maxWidth: 440, textAlign: 'center' }}>{error.friendlyMessage}</p>
      <button
        type="button"
        onClick={() => navigate('/', { replace: true })}
        style={{ marginTop: 4, background: '#2563EB', color: '#FFFFFF', border: 'none', padding: '9px 18px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
      >
        Về trang chủ
      </button>
    </div>
  );
}

const pageStyle = {
  minHeight: '100vh',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  justifyContent: 'center',
  gap: 12,
  background: '#F8FAFC',
  fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  padding: 16,
};
