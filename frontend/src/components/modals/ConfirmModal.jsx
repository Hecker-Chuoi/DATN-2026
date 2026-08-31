import React from 'react';
import { AlertTriangle, Info, AlertCircle, X } from 'lucide-react';
 
export default function ConfirmModal({
  isOpen,
  title = 'Xác nhận',
  message = 'Bạn có chắc chắn muốn thực hiện hành động này?',
  confirmText = 'Xác nhận',
  cancelText = 'Hủy bỏ',
  type = 'danger', // 'danger' | 'warning' | 'info'
  onConfirm,
  onCancel,
  loading = false
}) {
  if (!isOpen) return null;
 
  // Determine color theme based on modal type
  const themeColors = {
    danger: {
      bg: '#FEF2F2',
      text: '#DC2626',
      btnBg: '#EF4444',
      btnHover: '#DC2626',
      icon: <AlertTriangle size={20} />
    },
    warning: {
      bg: '#FFF7ED',
      text: '#EA580C',
      btnBg: '#F97316',
      btnHover: '#EA580C',
      icon: <AlertCircle size={20} />
    },
    info: {
      bg: '#EFF6FF',
      text: '#2563EB',
      btnBg: '#3B82F6',
      btnHover: '#2563EB',
      icon: <Info size={20} />
    }
  };
 
  const currentTheme = themeColors[type] || themeColors.info;
 
  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.6)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10000,
        padding: 20,
        animation: 'fadeIn 0.15s ease-out'
      }}
      onClick={onCancel}
    >
      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes scaleUp {
          from { transform: scale(0.95); opacity: 0; }
          to { transform: scale(1); opacity: 1; }
        }
      `}</style>
      <div
        style={{
          width: '100%',
          maxWidth: 420,
          backgroundColor: '#FFFFFF',
          borderRadius: 16,
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.15), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
          border: '1px solid #E2E8F0',
          overflow: 'hidden',
          animation: 'scaleUp 0.15s ease-out'
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '16px 20px 8px 20px',
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 38,
                height: 38,
                borderRadius: 10,
                backgroundColor: currentTheme.bg,
                color: currentTheme.text,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {currentTheme.icon}
            </div>
            <h2 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: 0 }}>
              {title}
            </h2>
          </div>
 
          <button
            onClick={onCancel}
            style={{
              background: 'none',
              border: 'none',
              color: '#94A3B8',
              cursor: 'pointer',
              padding: 4,
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'background-color 0.15s'
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#F1F5F9'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            <X size={18} />
          </button>
        </div>
 
        {/* Message body */}
        <div style={{ padding: '0 20px 20px 20px', marginTop: 8 }}>
          <p style={{ fontSize: 14, color: '#475569', lineHeight: 1.5, margin: 0 }}>
            {message}
          </p>
        </div>
 
        {/* Footer actions */}
        <div
          style={{
            padding: '14px 20px',
            backgroundColor: '#F8FAFC',
            borderTop: '1px solid #F1F5F9',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 10,
          }}
        >
          <button
            type="button"
            onClick={onCancel}
            disabled={loading}
            style={{
              padding: '8px 16px',
              borderRadius: 8,
              border: '1px solid #CBD5E1',
              backgroundColor: '#FFFFFF',
              color: '#475569',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.15s'
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.borderColor = '#94A3B8';
              e.currentTarget.style.backgroundColor = '#F8FAFC';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = '#CBD5E1';
              e.currentTarget.style.backgroundColor = '#FFFFFF';
            }}
          >
            {cancelText}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={loading}
            style={{
              padding: '8px 18px',
              borderRadius: 8,
              border: 'none',
              backgroundColor: currentTheme.btnBg,
              color: '#FFFFFF',
              fontSize: 13,
              fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              transition: 'all 0.15s'
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = currentTheme.btnHover}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = currentTheme.btnBg}
          >
            {loading ? 'Đang xử lý...' : confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
