import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { authApi, userTokenStore, adminTokenStore } from '../api.js';
import { RefreshCw, AlertCircle, ShieldCheck } from 'lucide-react';

export default function GoogleCallback({ onLoginSuccess }) {
  const navigate = useNavigate();
  const [status, setStatus] = useState('loading');
  const [errorMsg, setErrorMsg] = useState('');
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const isAdminCallback = window.location.pathname.includes('/admin');
    const redirectUri = `${window.location.origin}${window.location.pathname}`;

    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');

    if (!code) {
      setStatus('error');
      setErrorMsg('Không tìm thấy Authorization code từ Google. Vui lòng đăng nhập lại.');
      return;
    }

    authApi
      .googleCallback(code, redirectUri)
      .then((res) => {
        const accessToken = res?.data?.access_token;
        const refreshToken = res?.data?.refresh_token;
        if (accessToken) {
          if (isAdminCallback) {
            adminTokenStore.set(accessToken, refreshToken);
          } else {
            userTokenStore.set(accessToken, refreshToken);
          }
        }
        toast.success('Đăng nhập Google thành công!');
        if (onLoginSuccess) {
          onLoginSuccess();
        }
        const savedRedirect = sessionStorage.getItem('redirect_after_login');
        sessionStorage.removeItem('redirect_after_login');
        navigate(savedRedirect || (isAdminCallback ? '/admin' : '/projects'));
      })
      .catch((err) => {
        console.error('Google callback error:', err);
        setStatus('error');
        setErrorMsg(err.message || 'Đăng nhập bằng Google thất bại. Vui lòng thử lại.');
      });
  }, [navigate, onLoginSuccess]);

  if (status === 'loading') {
    return (
      <div className="auth-wrapper">
        <div className="auth-card" style={{ maxWidth: 500, minHeight: 300, padding: 40, alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
          <ShieldCheck size={48} color="#0070f3" style={{ marginBottom: 16 }} />
          <h2 style={{ fontSize: 22, fontWeight: 700, fontFamily: 'var(--font-heading)', marginBottom: 8 }}>
            Đang Xử Lý Đăng Nhập Google
          </h2>
          <p style={{ color: '#64748b', fontSize: 14, marginBottom: 24 }}>
            Vui lòng chờ trong giây lát để hệ thống xác thực tài khoản...
          </p>
          <RefreshCw size={32} className="spin" color="#0070f3" />
        </div>
      </div>
    );
  }

  return (
    <div className="auth-wrapper">
      <div className="auth-card" style={{ maxWidth: 500, minHeight: 300, padding: 40, alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
        <AlertCircle size={48} color="#dc2626" style={{ marginBottom: 16 }} />
        <h2 style={{ fontSize: 22, fontWeight: 700, fontFamily: 'var(--font-heading)', color: '#dc2626', marginBottom: 8 }}>
          Đăng Nhập Thất Bại
        </h2>
        <div style={{ background: '#fef2f2', color: '#dc2626', padding: '12px 16px', borderRadius: 8, fontSize: 13, margin: '16px 0 24px 0', width: '100%' }}>
          {errorMsg}
        </div>
        <button
          className="btn-primary"
          onClick={() => navigate('/admin/login')}
          style={{ width: '100%' }}
        >
          Quay lại Đăng Nhập
        </button>
      </div>
    </div>
  );
}
