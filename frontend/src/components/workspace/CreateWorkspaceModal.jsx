import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { X, Building2, Sparkles, AlertCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import { workspaceApi } from '../../api/workspaceServiceApi';
import { useWorkspace } from '../../context/WorkspaceContext';
import { wsPath } from '../../utils/workspacePath';
import { useModalBackdrop } from '../../hooks/useModalBackdrop';

const overlay = {
  position: 'fixed',
  inset: 0,
  backgroundColor: 'rgba(15, 23, 42, 0.6)',
  backdropFilter: 'blur(4px)',
  zIndex: 60,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: 16,
};

const inputStyle = {
  width: '100%',
  padding: '8px 12px',
  borderRadius: 6,
  border: '1px solid #CBD5E1',
  fontSize: 14,
  color: '#0F172A',
  outline: 'none',
};

const labelStyle = {
  display: 'block',
  fontSize: 13,
  fontWeight: 600,
  color: '#334155',
  marginBottom: 6,
};

/**
 * Modal tạo Không gian làm việc mới (Workspace).
 *
 * Hai điểm KHÁC có chủ đích so với bản gốc:
 * - Đọc lỗi bằng err.body?.message. Chuỗi err.response?.data?.message trong
 *   response interceptor của api.js
 *   throw một Error mới chỉ mang .status và .body.
 * - KHÔNG gọi toast.error. Interceptor đã toast tập trung rồi, page toast thêm
 *   là toast thứ hai. Ở đây chỉ set inline error.
 */
export default function CreateWorkspaceModal() {
  const { isCreateOpen, closeCreateWorkspace, refreshWorkspaces } = useWorkspace();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const close = () => {
    setName('');
    setDescription('');
    setError('');
    closeCreateWorkspace();
  };

  const backdropProps = useModalBackdrop(close);

  useEffect(() => {
    if (isCreateOpen) {
      setName('');
      setDescription('');
      setError('');
    }
  }, [isCreateOpen]);

  if (!isCreateOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Vui lòng nhập tên không gian làm việc');
      return;
    }

    try {
      setSubmitting(true);
      setError('');
      const res = await workspaceApi.create({
        name: name.trim(),
        description: description.trim() || null,
      });

      await refreshWorkspaces();
      toast.success('Đã tạo không gian làm việc mới');
      close();
      const newId = res?.data?.id || res?.id;
      if (newId) {
        navigate(wsPath(newId, '/projects'));
      }
    } catch (err) {
      setError(err.body?.message || err.message || 'Không thể tạo không gian làm việc. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  const disabled = submitting || !name.trim();

  return (
    <div style={overlay} {...backdropProps}>
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          width: '100%',
          maxWidth: 480,
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          style={{
            padding: '16px 20px',
            borderBottom: '1px solid #E2E8F0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            backgroundColor: '#FAFAFA',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 36,
                height: 36,
                borderRadius: 8,
                backgroundColor: '#EFF6FF',
                color: '#2563EB',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Building2 size={20} />
            </div>
            <div>
              <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                Tạo Không gian làm việc mới
              </h3>
              <p style={{ fontSize: 12, color: '#64748B', margin: 0 }}>
                Cấp cao nhất: một không gian làm việc chứa nhiều nhóm và dự án
              </p>
            </div>
          </div>
          <button
            onClick={close}
            style={{
              border: 'none',
              background: 'transparent',
              padding: 6,
              borderRadius: 6,
              cursor: 'pointer',
              color: '#64748B',
            }}
          >
            <X size={18} />
          </button>
        </div>

        <form
          onSubmit={handleSubmit}
          style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}
        >
          {error && (
            <div
              style={{
                padding: '10px 14px',
                borderRadius: 6,
                backgroundColor: '#FEF2F2',
                border: '1px solid #FEE2E2',
                color: '#DC2626',
                fontSize: 13,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label style={labelStyle}>
              Tên không gian làm việc <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                if (error) setError('');
              }}
              placeholder="Ví dụ: Công ty ABC, Đồ án tốt nghiệp..."
              autoFocus
              style={inputStyle}
            />
            <p style={{ fontSize: 11, color: '#94A3B8', margin: '6px 0 0' }}>
              Đường dẫn (slug) sẽ được sinh tự động từ tên.
            </p>
          </div>

          <div>
            <label style={labelStyle}>Mô tả (Tùy chọn)</label>
            <textarea
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Mục tiêu hoặc phạm vi của không gian làm việc..."
              style={{ ...inputStyle, fontSize: 13, resize: 'vertical', fontFamily: 'inherit' }}
            />
          </div>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              gap: 10,
              marginTop: 8,
              paddingTop: 16,
              borderTop: '1px solid #F1F5F9',
            }}
          >
            <button
              type="button"
              onClick={close}
              style={{
                backgroundColor: '#F1F5F9',
                color: '#475569',
                border: 'none',
                padding: '8px 16px',
                borderRadius: 6,
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={disabled}
              style={{
                backgroundColor: disabled ? '#94A3B8' : '#2563EB',
                color: '#FFFFFF',
                border: 'none',
                padding: '8px 18px',
                borderRadius: 6,
                fontSize: 13,
                fontWeight: 600,
                cursor: disabled ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
              }}
            >
              <Sparkles size={14} />
              <span>{submitting ? 'Đang tạo...' : 'Tạo không gian làm việc'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
