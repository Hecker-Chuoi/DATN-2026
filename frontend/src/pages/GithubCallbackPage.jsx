import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { RefreshCw, AlertCircle, Github } from 'lucide-react';
import { githubApi } from '../api.js';

/**
 * Đích redirect của GitHub OAuth (Authorization callback URL = {origin}/github/callback).
 * Khác GoogleCallback: người dùng ĐÃ đăng nhập hệ thống, chỉ đổi code lấy token GitHub và lưu theo user.
 */
export default function GithubCallbackPage() {
  const navigate = useNavigate();
  const [errorMsg, setErrorMsg] = useState('');
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    if (!code || !state) {
      setErrorMsg(params.get('error_description') || 'GitHub không trả về mã xác thực. Hãy thử kết nối lại.');
      return;
    }
    githubApi
      .oauthCallback({ code, state, redirectUri: `${window.location.origin}/github/callback` })
      .then((res) => {
        toast.success(res?.message || 'Đã kết nối GitHub');
        navigate('/profile', { replace: true });
      })
      .catch((err) => setErrorMsg(err.body?.message || err.message || 'Kết nối GitHub thất bại.'));
  }, [navigate]);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F8FAFC' }}>
      <div style={{ maxWidth: 440, width: '100%', background: '#FFF', border: '1px solid #E2E8F0', borderRadius: 12, padding: 32, textAlign: 'center' }}>
        {errorMsg ? (
          <>
            <AlertCircle size={40} color="#DC2626" style={{ marginBottom: 12 }} />
            <h2 style={{ fontSize: 18, fontWeight: 700, color: '#DC2626', margin: '0 0 8px' }}>Kết nối GitHub thất bại</h2>
            <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 20px' }}>{errorMsg}</p>
            <button type="button" onClick={() => navigate('/profile')} style={{ background: '#2563EB', color: '#FFF', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
              Về trang cá nhân
            </button>
          </>
        ) : (
          <>
            <Github size={40} color="#0F172A" style={{ marginBottom: 12 }} />
            <h2 style={{ fontSize: 18, fontWeight: 700, color: '#0F172A', margin: '0 0 8px' }}>Đang kết nối GitHub</h2>
            <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 16px' }}>Vui lòng chờ trong giây lát...</p>
            <RefreshCw size={24} className="spin" color="#2563EB" />
          </>
        )}
      </div>
    </div>
  );
}
