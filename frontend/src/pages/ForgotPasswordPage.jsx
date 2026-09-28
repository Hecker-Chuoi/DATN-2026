import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { KeyRound, MailCheck } from 'lucide-react';
import { authApi } from '../api.js';
import AuthCard from '../components/common/AuthCard.jsx';

/** Nhập email để nhận link đặt lại mật khẩu. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(null);

  const submit = async (e) => {
    e.preventDefault();
    if (!email.trim()) return;
    try {
      setLoading(true);
      const res = await authApi.forgotPassword(email.trim());
      setSent(res?.message || 'Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu.');
    } catch {
      // interceptor đã hiện toast lỗi
    } finally {
      setLoading(false);
    }
  };

  if (sent) {
    return (
      <AuthCard icon={<MailCheck size={28} />} tone="success" title="Đã gửi hướng dẫn" message={sent}>
        <Link to="/login" className="auth-card-btn">Về trang đăng nhập</Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard icon={<KeyRound size={28} />} tone="info" title="Quên mật khẩu" message="Nhập email đã đăng ký, chúng tôi sẽ gửi liên kết đặt lại mật khẩu (hiệu lực 30 phút).">
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 10, textAlign: 'left' }}>
        <input
          type="email"
          required
          autoFocus
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          style={{ padding: '10px 12px', borderRadius: 8, border: '1px solid #CBD5E1', fontSize: 14 }}
        />
        <button type="submit" disabled={loading} className="auth-card-btn" style={{ border: 'none', cursor: loading ? 'not-allowed' : 'pointer' }}>
          {loading ? 'Đang gửi...' : 'Gửi liên kết đặt lại'}
        </button>
      </form>
      <div style={{ fontSize: 12, color: '#64748B', marginTop: 12 }}>
        <Link to="/login" style={{ color: '#2563EB' }}>Quay lại đăng nhập</Link>
      </div>
    </AuthCard>
  );
}
