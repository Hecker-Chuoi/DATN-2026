import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi, tokenStore } from '../api';
import { User, Key, Building2, LogIn, ArrowRight, ShieldCheck, Activity, Award } from 'lucide-react';

export default function LoginPage({ onLoginSuccess }) {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    orgId: 'DATN-2026',
    username: '',
    password: '',
  });
  const [loading, setLoading] = useState(false);
  const [googleLoading, setGoogleLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.username || !form.password) {
      setError('Vui lòng nhập Username và Mật khẩu.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const res = await authApi.login(form.username, form.password);
      const accessToken = res?.data?.access_token;
      if (accessToken) {
        tokenStore.set(accessToken);
      }
      if (onLoginSuccess) onLoginSuccess();
      navigate('/admin');
    } catch (err) {
      setError(err.message || 'Đăng nhập thất bại. Kiểm tra lại thông tin!');
    } finally {
      setLoading(false);
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
    <div className="auth-wrapper">
      <div className="auth-card">
        {/* Left Side - Auth Form (Matching image.png structure) */}
        <div className="auth-left">
          <div className="auth-header">
            <div className="auth-brand">
              <ShieldCheck size={26} />
              <span>DATN SYSTEM</span>
            </div>
            <h2 className="auth-title">Đăng Nhập Quản Trị</h2>
            <p className="auth-subtitle">Nhập thông tin tài khoản của bạn để truy cập Dashboard</p>
          </div>

          {error && (
            <div style={{
              background: '#fef2f2',
              color: '#dc2626',
              padding: '12px 16px',
              borderRadius: 8,
              fontSize: 13,
              marginBottom: 20,
              border: '1px solid #fecaca'
            }}>
              {error}
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="input-group">
              <label className="input-label">Organisation ID</label>
              <div className="input-field-wrapper">
                <span className="input-icon"><Building2 size={18} /></span>
                <input
                  type="text"
                  name="orgId"
                  className="input-control"
                  value={form.orgId}
                  onChange={handleChange}
                  placeholder="541115DSFA"
                />
              </div>
            </div>

            <div className="input-group">
              <label className="input-label">Username</label>
              <div className="input-field-wrapper">
                <span className="input-icon"><User size={18} /></span>
                <input
                  type="text"
                  name="username"
                  className="input-control"
                  value={form.username}
                  onChange={handleChange}
                  placeholder="Nhập tên đăng nhập"
                  autoFocus
                />
              </div>
            </div>

            <div className="input-group">
              <label className="input-label">Password</label>
              <div className="input-field-wrapper">
                <span className="input-icon"><Key size={18} /></span>
                <input
                  type="password"
                  name="password"
                  className="input-control"
                  value={form.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                />
              </div>
            </div>

            <div className="form-options">
              <span style={{ color: '#94a3b8' }}>Ghi nhớ tài khoản</span>
              <a href="#forgot" onClick={(e) => { e.preventDefault(); alert('Vui lòng liên hệ Admin để khôi phục mật khẩu!'); }} className="forgot-link">
                Forgot Your Password
              </a>
            </div>

            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'ĐANG XỬ LÝ...' : 'LOG IN'}
              {!loading && <LogIn size={18} />}
            </button>
          </form>

          <div className="divider">HOẶC</div>

          <button type="button" className="btn-google" onClick={handleGoogleLogin} disabled={googleLoading}>
            <svg width="18" height="18" viewBox="0 0 24 24">
              <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
              <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
              <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
              <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
            </svg>
            <span>{googleLoading ? 'Chuyển hướng Google...' : 'Sign in with Google'}</span>
          </button>

          <div className="auth-footer">
            Don't Have An Account ? <Link to="/admin/register">Register as an owner</Link>
          </div>
        </div>

        {/* Right Side - Visual Graphic Showcase (Matching image.png visual style) */}
        <div className="auth-right">
          <div className="auth-right-bg"></div>

          <div className="mockup-wrapper">
            {/* Avatar badge top-left */}
            <img
              src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&auto=format&fit=crop&q=80"
              alt="User Badge"
              className="avatar-badge"
            />

            {/* Floating Top Widget */}
            <div className="float-card-top">
              <span className="badge-tag">Total Users</span>
              <span className="float-val">1,588 <span style={{ fontSize: 12, color: '#10b981', fontWeight: 600 }}>+12%</span></span>
            </div>

            {/* Main Dashboard Preview Mockup Card */}
            <div className="mockup-dashboard">
              <div className="mockup-header">
                <div>
                  <div style={{ fontSize: 10, color: '#64748b', textTransform: 'uppercase' }}>Dashboard Overview</div>
                  <div className="mockup-title">Identity Service Metrics</div>
                </div>
                <div className="badge-tag">Active</div>
              </div>

              {/* Chart Mockup */}
              <div className="mockup-chart-placeholder">
                <svg width="100%" height="100%" viewBox="0 0 300 100" preserveAspectRatio="none">
                  <path
                    d="M 0 70 Q 50 20, 100 60 T 200 30 T 300 50 L 300 100 L 0 100 Z"
                    fill="rgba(0, 112, 243, 0.15)"
                  />
                  <path
                    d="M 0 70 Q 50 20, 100 60 T 200 30 T 300 50"
                    fill="none"
                    stroke="#0070f3"
                    strokeWidth="3"
                  />
                  <circle cx="200" cy="30" r="5" fill="#0070f3" />
                </svg>
              </div>

              <div className="mockup-rows">
                <div className="mockup-row-item">
                  <span style={{ fontWeight: 600 }}>Keycloak IAM Server</span>
                  <span style={{ color: '#10b981', fontWeight: 600 }}>Connected</span>
                </div>
                <div className="mockup-row-item">
                  <span style={{ fontWeight: 600 }}>Spring API Gateway</span>
                  <span style={{ color: '#0070f3', fontWeight: 600 }}>Port 8081</span>
                </div>
              </div>
            </div>

            {/* Floating Bottom Widget */}
            <div className="float-card-bottom">
              <div style={{ background: '#eff6ff', padding: 10, borderRadius: 10, color: '#0070f3' }}>
                <Award size={24} />
              </div>
              <div>
                <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600 }}>MICROSERVICES</div>
                <div className="float-val" style={{ fontSize: 18 }}>12 Completed</div>
              </div>
            </div>

            {/* Avatar badge bottom-right */}
            <img
              src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=120&auto=format&fit=crop&q=80"
              alt="User Badge Right"
              className="avatar-badge-right"
            />
          </div>
        </div>
      </div>
    </div>
  );
}
