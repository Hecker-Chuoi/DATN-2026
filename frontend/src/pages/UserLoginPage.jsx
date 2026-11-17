import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { authApi, userTokenStore } from '../api.js';
import { LogIn, ArrowRight, ShieldCheck, Mail, Lock, User } from 'lucide-react';

export default function UserLoginPage({ onLoginSuccess }) {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    username: '',
    password: '',
  });
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [error, setError] = useState(null);
  const [needsVerification, setNeedsVerification] = useState(false);
  const [resending, setResending] = useState(false);

  useEffect(() => {
    const expiredMsg = sessionStorage.getItem('session_expired_notice');
    if (expiredMsg) {
      toast.error(expiredMsg);
      sessionStorage.removeItem('session_expired_notice');
    }
  }, []);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.username || !form.password) {
      setError('Vui lòng nhập Tên đăng nhập và Mật khẩu.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const res = await authApi.login(form.username, form.password);
      const accessToken = res?.data?.access_token;
      const refreshToken = res?.data?.refresh_token;
      if (accessToken) {
        userTokenStore.set(accessToken, refreshToken);
      }
      toast.success('Đăng nhập thành công!');
      if (onLoginSuccess) onLoginSuccess();
      const redirectPath = sessionStorage.getItem('redirect_after_login') || '/projects';
      sessionStorage.removeItem('redirect_after_login');
      navigate(redirectPath);
    } catch (err) {
      setError(err.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin!');
      setNeedsVerification(err?.status === 403); // 403 = tài khoản chưa xác thực email
    } finally {
      setLoading(false);
    }
  };

  const handleResendVerification = async () => {
    try {
      setResending(true);
      const res = await authApi.resendVerification(form.username.trim());
      toast.success(res?.message || 'Đã gửi lại email xác thực.');
    } catch {
      // interceptor đã hiện toast lỗi
    } finally {
      setResending(false);
    }
  };

  const handleGoogleLogin = async () => {
    setGoogleLoading(true);
    setError(null);
    try {
      const redirectUri = `${window.location.origin}/auth/callback`;
      const res = await authApi.googleInit(redirectUri);
      const authUrl = res?.data?.authorizationUrl;

      if (authUrl) {
        window.location.href = authUrl;
      } else {
        throw new Error('Không nhận được Authorization URL từ máy chủ');
      }
    } catch (err) {
      setError(err.message || 'Khởi tạo đăng nhập Google thất bại');
      setGoogleLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        backgroundColor: '#F8FAFC',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px 16px',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 440,
          backgroundColor: '#FFFFFF',
          borderRadius: 16,
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05), 0 20px 25px -5px rgba(0, 0, 0, 0.05)',
          border: '1px solid #E2E8F0',
          padding: '36px 32px',
        }}
      >
        {/* Brand Header */}
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 10,
              backgroundColor: '#2563EB',
              color: '#FFFFFF',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 20,
              fontWeight: 800,
              margin: '0 auto 12px auto',
              boxShadow: '0 4px 10px rgba(37,99,235,0.25)',
            }}
          >
            D
          </div>
          <h1 style={{ fontSize: 22, fontWeight: 700, color: '#0F172A', margin: '0 0 6px 0' }}>
            Không gian Làm việc Trực tuyến
          </h1>
          <p style={{ fontSize: 13, color: '#64748B', margin: 0 }}>
            Đăng nhập để vào không gian quản lý dự án & tác vụ
          </p>
        </div>

        {/* Error Alert */}
        {error && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 8,
              backgroundColor: '#FEF2F2',
              border: '1px solid #FEE2E2',
              color: '#DC2626',
              fontSize: 13,
              marginBottom: 18,
            }}
          >
            {error}
            {needsVerification && (
              <div style={{ marginTop: 8 }}>
                <button
                  type="button"
                  onClick={handleResendVerification}
                  disabled={resending || !form.username}
                  style={{ background: 'none', border: 'none', color: '#2563EB', fontWeight: 600, cursor: 'pointer', padding: 0, fontSize: 13 }}
                >
                  {resending ? 'Đang gửi...' : 'Gửi lại email xác thực'}
                </button>
              </div>
            )}
          </div>
        )}

        {/* Google SSO Button */}
        <button
          type="button"
          onClick={handleGoogleLogin}
          disabled={googleLoading}
          style={{
            width: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 10,
            backgroundColor: '#FFFFFF',
            border: '1px solid #CBD5E1',
            borderRadius: 8,
            padding: '10px 16px',
            fontSize: 14,
            fontWeight: 600,
            color: '#1E293B',
            cursor: googleLoading ? 'not-allowed' : 'pointer',
            transition: 'all 0.15s ease',
            boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
            marginBottom: 20,
          }}
          onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
          onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
        >
          <svg width="18" height="18" viewBox="0 0 24 24">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
          </svg>
          <span>{googleLoading ? 'Đang chuyển hướng...' : 'Đăng nhập bằng Google'}</span>
        </button>

        {/* Divider */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            margin: '0 0 20px 0',
            color: '#94A3B8',
            fontSize: 12,
          }}
        >
          <div style={{ flex: 1, height: 1, backgroundColor: '#E2E8F0' }} />
          <span style={{ padding: '0 12px', fontWeight: 500 }}>HOẶC ĐĂNG NHẬP BẰNG TÀI KHOẢN</span>
          <div style={{ flex: 1, height: 1, backgroundColor: '#E2E8F0' }} />
        </div>

        {/* Regular Login Form */}
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 5 }}>
              Tên đăng nhập
            </label>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <User size={16} color="#94A3B8" style={{ position: 'absolute', left: 12 }} />
              <input
                type="text"
                name="username"
                value={form.username}
                onChange={handleChange}
                placeholder="VD: nguyen.van.a"
                required
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 36px',
                  borderRadius: 8,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  outline: 'none',
                  color: '#0F172A',
                }}
              />
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 5 }}>
              Mật khẩu
            </label>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <Lock size={16} color="#94A3B8" style={{ position: 'absolute', left: 12 }} />
              <input
                type="password"
                name="password"
                value={form.password}
                onChange={handleChange}
                placeholder="••••••••"
                required
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 36px',
                  borderRadius: 8,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  outline: 'none',
                  color: '#0F172A',
                }}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              backgroundColor: '#2563EB',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: 8,
              padding: '10px 16px',
              fontSize: 14,
              fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              marginTop: 6,
              boxShadow: '0 2px 4px rgba(37,99,235,0.25)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 8,
              transition: 'background-color 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
          >
            <span>{loading ? 'Đang đăng nhập...' : 'Đăng nhập vào hệ thống'}</span>
            {!loading && <ArrowRight size={16} />}
          </button>
        </form>

        {/* Footer Links */}
        <div style={{ marginTop: 22, textAlign: 'center', fontSize: 13, color: '#64748B' }}>
          Chưa có tài khoản?{' '}
          <Link to="/register" style={{ color: '#2563EB', fontWeight: 600, textDecoration: 'none' }}>
            Đăng ký ngay
          </Link>
          {' · '}
          <Link to="/forgot-password" style={{ color: '#64748B', fontWeight: 600, textDecoration: 'none' }}>
            Quên mật khẩu?
          </Link>
        </div>

        <div style={{ marginTop: 14, paddingTop: 14, borderTop: '1px solid #F1F5F9', textAlign: 'center', fontSize: 12, color: '#94A3B8' }}>
          Bạn là Quản trị viên hệ thống?{' '}
          <Link to="/admin/login" style={{ color: '#64748B', fontWeight: 600, textDecoration: 'underline' }}>
            Cổng Quản trị hệ thống
          </Link>
        </div>
      </div>
    </div>
  );
}
