import React, { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { MailCheck, MailX, Loader2 } from 'lucide-react';
import { authApi } from '../api.js';
import AuthCard from '../components/common/AuthCard.jsx';

/** Trang đích của link "Xác thực email": /verify-email?token=... */
export default function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [state, setState] = useState({ status: 'loading', message: '' });
  const ran = useRef(false); // StrictMode gọi effect 2 lần; token chỉ dùng được 1 lần

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    if (!token) {
      setState({ status: 'error', message: 'Liên kết thiếu token xác thực.' });
      return;
    }
    authApi
      .verifyEmail(token)
      .then((res) => setState({ status: 'ok', message: res?.message || 'Xác thực email thành công.' }))
      .catch((err) => setState({ status: 'error', message: err?.message || 'Liên kết không hợp lệ hoặc đã hết hạn.' }));
  }, [token]);

  if (state.status === 'loading') {
    return (
      <AuthCard icon={<Loader2 size={28} className="spin" />} tone="info" title="Đang xác thực email..." message="Vui lòng đợi trong giây lát." />
    );
  }
  if (state.status === 'ok') {
    return (
      <AuthCard icon={<MailCheck size={28} />} tone="success" title="Email đã được xác thực" message={state.message}>
        <Link to="/login" className="auth-card-btn">Đăng nhập ngay</Link>
      </AuthCard>
    );
  }
  return (
    <AuthCard icon={<MailX size={28} />} tone="error" title="Không xác thực được" message={state.message}>
      <Link to="/login" className="auth-card-btn">Về trang đăng nhập</Link>
      <div style={{ fontSize: 12, color: '#64748B', marginTop: 10 }}>
        Tại trang đăng nhập, nhập tài khoản rồi chọn "Gửi lại email xác thực" nếu liên kết đã hết hạn.
      </div>
    </AuthCard>
  );
}
