import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, SearchX, ArrowLeft } from 'lucide-react';

/**
 * Màn hình thay thế nội dung khi backend trả 403 (không có quyền) hoặc 404 (không tồn tại).
 */
export default function AccessDenied({
  status = 403,
  title,
  message,
  backTo = '/projects',
  backLabel = 'Quay lại danh sách dự án',
}) {
  const navigate = useNavigate();
  const isNotFound = status === 404;
  const Icon = isNotFound ? SearchX : ShieldAlert;

  const resolvedTitle = title || (isNotFound ? 'Không tìm thấy dự án' : 'Bạn không có quyền truy cập dự án này');
  const resolvedMessage =
    message ||
    (isNotFound
      ? 'Dự án không tồn tại hoặc đã bị xóa.'
      : 'Bạn không phải thành viên của dự án. Hãy liên hệ Trưởng dự án (Project Lead) hoặc Administrator để được thêm vào.');

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
            backgroundColor: isNotFound ? '#FEF3C7' : '#FEE2E2',
            color: isNotFound ? '#B45309' : '#DC2626',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px auto',
          }}
        >
          <Icon size={28} />
        </div>
        <div style={{ fontSize: 12, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.08em', marginBottom: 6 }}>
          {isNotFound ? '404 NOT FOUND' : '403 FORBIDDEN'}
        </div>
        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#0F172A', margin: '0 0 8px 0' }}>{resolvedTitle}</h2>
        <p style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6, margin: '0 0 20px 0' }}>{resolvedMessage}</p>
        <button
          onClick={() => navigate(backTo)}
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
          <span>{backLabel}</span>
        </button>
      </div>
    </div>
  );
}
