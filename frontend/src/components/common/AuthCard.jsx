import React from 'react';

const TONES = {
  info: { bg: '#DBEAFE', color: '#1D4ED8' },
  success: { bg: '#DCFCE7', color: '#15803D' },
  error: { bg: '#FEE2E2', color: '#DC2626' },
};

/**
 * Khung thẻ căn giữa dùng cho các trang public: xác thực email, quên / đặt lại mật khẩu.
 * children: nút hành động hoặc form. Lớp .auth-card-btn dùng cho nút/link chính.
 */
export default function AuthCard({ icon, tone = 'info', title, message, children }) {
  const t = TONES[tone] || TONES.info;
  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24, backgroundColor: '#F8FAFC', fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      <style>{`
        .auth-card-btn{display:inline-flex;align-items:center;justify-content:center;gap:6px;background:#2563EB;color:#fff;border-radius:8px;padding:10px 16px;font-size:14px;font-weight:600;text-decoration:none}
        .auth-card-btn:hover{background:#1D4ED8}
        .spin{animation:auth-spin 1s linear infinite}
        @keyframes auth-spin{to{transform:rotate(360deg)}}
      `}</style>
      <div style={{ width: '100%', maxWidth: 440, backgroundColor: '#FFFFFF', border: '1px solid #E2E8F0', borderRadius: 12, padding: '36px 32px', textAlign: 'center', boxShadow: '0 4px 20px rgba(15,23,42,0.06)' }}>
        <div style={{ width: 56, height: 56, borderRadius: 14, backgroundColor: t.bg, color: t.color, display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
          {icon}
        </div>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#0F172A', margin: '0 0 8px' }}>{title}</h2>
        {message && <p style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6, margin: '0 0 20px' }}>{message}</p>}
        {children}
      </div>
    </div>
  );
}
