import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api';
import { User, Key, Mail, UserCheck, ShieldCheck, UserPlus, Award } from 'lucide-react';

export default function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    username: '',
    password: '',
    email: '',
    firstName: '',
    lastName: '',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  const handleChange = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.username || !form.password || !form.email) {
      setError('Vui lòng điền các trường bắt buộc (Username, Password, Email).');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await authApi.register(form);
      setSuccess(true);
      setTimeout(() => {
        navigate('/admin/login');
      }, 1500);
    } catch (err) {
      setError(err.message || 'Đăng ký thất bại. Vui lòng thử lại!');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-wrapper">
      <div className="auth-card">
        {/* Left Side - Register Form */}
        <div className="auth-left">
          <div className="auth-header">
            <div className="auth-brand">
              <ShieldCheck size={26} />
              <span>DATN SYSTEM</span>
            </div>
            <h2 className="auth-title">Tạo Tài Khoản Mới</h2>
            <p className="auth-subtitle">Đăng ký người dùng mới trong hệ thống Keycloak IAM</p>
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

          {success && (
            <div style={{
              background: '#f0fdf4',
              color: '#16a34a',
              padding: '12px 16px',
              borderRadius: 8,
              fontSize: 13,
              marginBottom: 20,
              border: '1px solid #bbf7d0'
            }}>
              🎉 Đăng ký thành công! Đang chuyển hướng sang Đăng nhập...
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div className="input-group">
                <label className="input-label">First Name</label>
                <input
                  type="text"
                  name="firstName"
                  className="input-control"
                  style={{ paddingLeft: 16 }}
                  value={form.firstName}
                  onChange={handleChange}
                  placeholder="Văn"
                />
              </div>

              <div className="input-group">
                <label className="input-label">Last Name</label>
                <input
                  type="text"
                  name="lastName"
                  className="input-control"
                  style={{ paddingLeft: 16 }}
                  value={form.lastName}
                  onChange={handleChange}
                  placeholder="Nguyễn"
                />
              </div>
            </div>

            <div className="input-group">
              <label className="input-label">Username *</label>
              <div className="input-field-wrapper">
                <span className="input-icon"><User size={18} /></span>
                <input
                  type="text"
                  name="username"
                  className="input-control"
                  value={form.username}
                  onChange={handleChange}
                  placeholder="nhap_username"
                  required
                />
              </div>
            </div>

            <div className="input-group">
              <label className="input-label">Email *</label>
              <div className="input-field-wrapper">
                <span className="input-icon"><Mail size={18} /></span>
                <input
                  type="email"
                  name="email"
                  className="input-control"
                  value={form.email}
                  onChange={handleChange}
                  placeholder="user@domain.com"
                  required
                />
              </div>
            </div>

            <div className="input-group">
              <label className="input-label">Password *</label>
              <div className="input-field-wrapper">
                <span className="input-icon"><Key size={18} /></span>
                <input
                  type="password"
                  name="password"
                  className="input-control"
                  value={form.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                  required
                />
              </div>
            </div>

            <button type="submit" className="btn-primary" disabled={loading || success}>
              {loading ? 'ĐANG TẠO...' : 'ĐĂNG KÝ NGAY'}
              {!loading && <UserPlus size={18} />}
            </button>
          </form>

          <div className="auth-footer">
            Already Have An Account ? <Link to="/admin/login">Log in here</Link>
          </div>
        </div>

        {/* Right Side - Visual Showcase */}
        <div className="auth-right">
          <div className="auth-right-bg"></div>

          <div className="mockup-wrapper">
            <img
              src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=120&auto=format&fit=crop&q=80"
              alt="User Badge"
              className="avatar-badge"
            />

            <div className="float-card-top">
              <span className="badge-tag">Security</span>
              <span className="float-val">IAM Realm</span>
            </div>

            <div className="mockup-dashboard">
              <div className="mockup-header">
                <div>
                  <div style={{ fontSize: 10, color: '#64748b', textTransform: 'uppercase' }}>Registration</div>
                  <div className="mockup-title">User Onboarding System</div>
                </div>
                <div className="badge-tag" style={{ background: '#f0fdf4', color: '#16a34a' }}>OIDC Standard</div>
              </div>

              <div className="mockup-chart-placeholder">
                <svg width="100%" height="100%" viewBox="0 0 300 100" preserveAspectRatio="none">
                  <path
                    d="M 0 50 Q 75 90, 150 30 T 300 60 L 300 100 L 0 100 Z"
                    fill="rgba(0, 112, 243, 0.15)"
                  />
                  <path
                    d="M 0 50 Q 75 90, 150 30 T 300 60"
                    fill="none"
                    stroke="#0070f3"
                    strokeWidth="3"
                  />
                </svg>
              </div>

              <div className="mockup-rows">
                <div className="mockup-row-item">
                  <span style={{ fontWeight: 600 }}>Default Role Assignment</span>
                  <span style={{ color: '#0070f3', fontWeight: 600 }}>USER</span>
                </div>
              </div>
            </div>

            <div className="float-card-bottom">
              <div style={{ background: '#eff6ff', padding: 10, borderRadius: 10, color: '#0070f3' }}>
                <Award size={24} />
              </div>
              <div>
                <div style={{ fontSize: 11, color: '#64748b', fontWeight: 600 }}>DATN 2026</div>
                <div className="float-val" style={{ fontSize: 18 }}>Keycloak Federated</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
