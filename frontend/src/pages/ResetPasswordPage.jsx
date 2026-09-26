import React, { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { LockKeyhole, CheckCircle2 } from 'lucide-react';
import { authApi } from '../api.js';
import AuthCard from '../components/common/AuthCard.jsx';

/** Trang đích của link "Đặt lại mật khẩu": /reset-password?token=... */
export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(null);

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    if (password.length < 6) return setError('Mật khẩu phải có ít nhất 6 ký tự.');
    if (password !== confirm) return setError('Mật khẩu nhập lại không khớp.');
    try {
      setLoading(true);
      const res = await authApi.resetPassword(token, password);
      setDone(res?.message || 'Đặt lại mật khẩu thành công.');
    } catch (err) {
      setError(err?.message || 'Liên kết không hợp lệ hoặc đã hết hạn.');
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <AuthCard icon={<LockKeyhole size={28} />} tone="error" title="Liên kết không hợp lệ" message="Thiếu token đặt lại mật khẩu.">
        <Link to="/forgot-password" className="auth-card-btn">Yêu cầu liên kết mới</Link>
      </AuthCard>
    );
  }
  if (done) {
    return (
      <AuthCard icon={<CheckCircle2 size={28} />} tone="success" title="Mật khẩu đã được đổi" message={done}>
        <Link to="/login" className="auth-card-btn">Đăng nhập</Link>
      </AuthCard>
    );
  }

  const input = { padding: '10px 12px', borderRadius: 8, border: '1px solid #CBD5E1', fontSize: 14 };
  return (
    <AuthCard icon={<LockKeyhole size={28} />} tone="info" title="Đặt mật khẩu mới" message="Nhập mật khẩu mới cho tài khoản của bạn.">
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 10, textAlign: 'left' }}>
        <input type="password" required autoFocus placeholder="Mật khẩu mới (tối thiểu 6 ký tự)" value={password} onChange={(e) => setPassword(e.target.value)} style={input} />
        <input type="password" required placeholder="Nhập lại mật khẩu mới" value={confirm} onChange={(e) => setConfirm(e.target.value)} style={input} />
        {error && <div style={{ fontSize: 12, color: '#DC2626' }}>{error}</div>}
        <button type="submit" disabled={loading} className="auth-card-btn" style={{ border: 'none', cursor: loading ? 'not-allowed' : 'pointer' }}>
          {loading ? 'Đang lưu...' : 'Đổi mật khẩu'}
        </button>
      </form>
      <div style={{ fontSize: 12, color: '#64748B', marginTop: 12 }}>
        Liên kết hết hạn? <Link to="/forgot-password" style={{ color: '#2563EB' }}>Yêu cầu lại</Link>
      </div>
    </AuthCard>
  );
}
