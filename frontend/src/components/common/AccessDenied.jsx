import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, SearchX, LockKeyhole, ArrowLeft } from 'lucide-react';

const PRESETS = {
  401: {
    Icon: LockKeyhole,
    label: '401 UNAUTHORIZED',
    bg: '#E0E7FF',
    color: '#4338CA',
    title: 'Phiên đăng nhập đã hết hạn',
    message: 'Vui lòng đăng nhập lại để tiếp tục sử dụng hệ thống.',
    backTo: '/login',
    backLabel: 'Đăng nhập lại',
  },
  403: {
    Icon: ShieldAlert,
    label: '403 FORBIDDEN',
    bg: '#FEE2E2',
    color: '#DC2626',
    title: 'Bạn không có quyền truy cập dự án này',
    message: 'Bạn không phải thành viên của dự án. Hãy liên hệ Trưởng dự án hoặc Quản trị viên để được thêm vào.',
    backTo: '/projects',
    backLabel: 'Quay lại danh sách dự án',
  },
  404: {
    Icon: SearchX,
    label: '404 NOT FOUND',
    bg: '#FEF3C7',
    color: '#B45309',
    title: 'Không tìm thấy trang',
    message: 'Đường dẫn không tồn tại hoặc nội dung đã bị xóa.',
    backTo: '/projects',
    backLabel: 'Về trang chủ',
  },
};

/**
 * Trang lỗi dùng chung: 401 (hết phiên), 403 (không có quyền), 404 (không tồn tại).
 * Dùng làm route `/401`, `/403`, `/404`, catch-all `*`, hoặc thay nội dung trang khi API trả lỗi.
 */
export default function AccessDenied({ status = 403, title, message, backTo, backLabel }) {
  const navigate = useNavigate();

  React.useEffect(() => {
    if (status === 401) {
      try {
        sessionStorage.setItem('session_expired_notice', 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      } catch {
        // ignore
      }
      navigate('/login', { replace: true });
    }
  }, [status, navigate]);

  if (status === 401) {
    return null;
  }

  const preset = PRESETS[status] || PRESETS[403];
  const { Icon } = preset;
  const resolvedTitle = title || preset.title;
  const resolvedMessage = message || preset.message;
  const resolvedBackTo = backTo || preset.backTo;
  const resolvedBackLabel = backLabel || preset.backLabel;

  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 40,
        backgroundColor: '#FFFFFF',
      }}
    >
      <div
        style={{
          maxWidth: 460,
          width: '100%',
          textAlign: 'center',
          padding: '40px 32px',
          borderRadius: 12,
          border: '1px solid #E2E8F0',
          backgroundColor: '#F8FAFC',
        }}
      >
        <div
          style={{
            width: 56,
            height: 56,
            borderRadius: 14,
            backgroundColor: preset.bg,
            color: preset.color,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px auto',
          }}
        >
          <Icon size={28} />
        </div>
        <div style={{ fontSize: 12, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.08em', marginBottom: 6 }}>
          {preset.label}
        </div>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#0F172A', margin: '0 0 8px 0' }}>{resolvedTitle}</h2>
        <p style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6, margin: '0 0 20px 0' }}>{resolvedMessage}</p>
        <button
          onClick={() => navigate(resolvedBackTo)}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            backgroundColor: '#2563EB',
            color: '#FFFFFF',
            border: 'none',
            borderRadius: 8,
            padding: '9px 16px',
            fontSize: 13,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          <ArrowLeft size={14} />
          <span>{resolvedBackLabel}</span>
        </button>
      </div>
    </div>
  );
}
