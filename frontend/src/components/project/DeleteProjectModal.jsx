import React, { useState, useEffect } from 'react';
import { AlertTriangle, Trash2, X, AlertCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi } from '../../api.js';

export default function DeleteProjectModal({ isOpen, onClose, project, onDeleted }) {
  const [confirmInput, setConfirmInput] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setConfirmInput('');
      setIsDeleting(false);
    }
  }, [isOpen]);

  if (!isOpen || !project) return null;

  const expectedKey = (project.key || '').trim().toUpperCase();
  const expectedName = (project.name || '').trim().toLowerCase();
  const inputTrimmed = confirmInput.trim();

  // Cho phép xác nhận bằng cách nhập đúng Key (VD: PRJ) hoặc tên dự án
  const isMatch =
    (expectedKey && inputTrimmed.toUpperCase() === expectedKey) ||
    (expectedName && inputTrimmed.toLowerCase() === expectedName);

  const handleDelete = async (e) => {
    e.preventDefault();
    if (!isMatch || isDeleting) return;

    try {
      setIsDeleting(true);
      await projectApi.delete(project.id);
      toast.success(`Dự án "${project.name}" đã được chuyển vào thùng rác!`);
      if (onDeleted) {
        onDeleted(project);
      }
      onClose();
    } catch (err) {
      console.error('Error deleting project:', err);
      const msg = err.response?.data?.message || err.message || 'Xóa dự án thất bại';
      toast.error(msg);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1100,
        backdropFilter: 'blur(3px)',
        padding: '16px',
        boxSizing: 'border-box',
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget && !isDeleting) onClose();
      }}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          width: '100%',
          maxWidth: 480,
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.15), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
          overflow: 'hidden',
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: '18px 24px',
            borderBottom: '1px solid #F1F5F9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            backgroundColor: '#FEF2F2',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 34,
                height: 34,
                borderRadius: 8,
                backgroundColor: '#FEE2E2',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#EF4444',
              }}
            >
              <Trash2 size={18} />
            </div>
            <div>
              <h3 style={{ fontSize: 16, fontWeight: 700, color: '#991B1B', margin: 0 }}>
                Xóa dự án
              </h3>
              <span style={{ fontSize: 12, color: '#B91C1C' }}>
                Hành động chuyển vào thùng rác (Soft Delete)
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isDeleting}
            style={{
              border: 'none',
              background: 'transparent',
              cursor: isDeleting ? 'not-allowed' : 'pointer',
              color: '#991B1B',
              padding: 4,
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleDelete} style={{ padding: '20px 24px' }}>
          {/* Warning banner */}
          <div
            style={{
              backgroundColor: '#FFFBEB',
              border: '1px solid #FDE68A',
              borderRadius: 8,
              padding: '12px 14px',
              display: 'flex',
              gap: 10,
              marginBottom: 16,
            }}
          >
            <AlertTriangle size={18} color="#D97706" style={{ flexShrink: 0, marginTop: 2 }} />
            <div style={{ fontSize: 13, color: '#92400E', lineHeight: 1.5 }}>
              Bạn đang chuẩn bị xóa dự án <strong>{project.name}</strong> (Mã: <code>{project.key}</code>).
              Tất cả các công việc (issues), bảng Kanban/Scrum, chu kỳ Sprints và lịch sử liên quan sẽ bị ẩn khỏi không gian làm việc.
            </div>
          </div>

          <div style={{ marginBottom: 18 }}>
            <label
              style={{
                display: 'block',
                fontSize: 13,
                fontWeight: 600,
                color: '#334155',
                marginBottom: 6,
              }}
            >
              Để xác nhận, vui lòng nhập mã dự án <span style={{ color: '#EF4444', fontWeight: 700 }}>{project.key}</span>:
            </label>
            <input
              type="text"
              autoFocus
              placeholder={`Nhập "${project.key}" để tiếp tục`}
              value={confirmInput}
              onChange={(e) => setConfirmInput(e.target.value)}
              disabled={isDeleting}
              style={{
                width: '100%',
                padding: '10px 12px',
                borderRadius: 6,
                border: isMatch ? '1px solid #10B981' : '1px solid #CBD5E1',
                fontSize: 13,
                outline: 'none',
                boxSizing: 'border-box',
                backgroundColor: isMatch ? '#F0FDF4' : '#FFFFFF',
                transition: 'all 0.15s ease',
              }}
            />
            {confirmInput && !isMatch && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 4, fontSize: 11, color: '#EF4444' }}>
                <AlertCircle size={12} />
                <span>Mã xác nhận chưa chính xác</span>
              </div>
            )}
          </div>

          {/* Action buttons */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, paddingTop: 6 }}>
            <button
              type="button"
              onClick={onClose}
              disabled={isDeleting}
              style={{
                padding: '9px 16px',
                borderRadius: 6,
                border: '1px solid #E2E8F0',
                backgroundColor: '#FFFFFF',
                color: '#475569',
                fontSize: 13,
                fontWeight: 600,
                cursor: isDeleting ? 'not-allowed' : 'pointer',
              }}
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={!isMatch || isDeleting}
              style={{
                padding: '9px 18px',
                borderRadius: 6,
                border: 'none',
                backgroundColor: isMatch ? '#EF4444' : '#FCA5A5',
                color: '#FFFFFF',
                fontSize: 13,
                fontWeight: 600,
                cursor: !isMatch || isDeleting ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                boxShadow: isMatch ? '0 1px 2px rgba(239, 68, 68, 0.25)' : 'none',
                transition: 'all 0.15s ease',
              }}
            >
              <Trash2 size={14} />
              <span>{isDeleting ? 'Đang xóa dự án...' : 'Xác nhận xóa dự án'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
