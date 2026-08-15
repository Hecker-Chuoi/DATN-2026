import React, { useState } from 'react';
import { X, CheckSquare, Bug, Bookmark, Zap, Calendar, Clock, User, Flag } from 'lucide-react';
import { taskApi } from '../../api.js';

export default function CreateTaskModal({ projectId, milestones, parentTask, onClose, onSuccess }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState('TASK');
  const [priority, setPriority] = useState('MEDIUM');
  const [milestoneId, setMilestoneId] = useState(parentTask?.milestoneId || '');
  const [estimate, setEstimate] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!title.trim()) return;

    try {
      setLoading(true);
      setErrorMsg('');

      await taskApi.create({
        projectId,
        parentTaskId: parentTask ? parentTask.id : null,
        milestoneId: milestoneId || null,
        title: title.trim(),
        description: description.trim() || null,
        type,
        priority,
        estimate: estimate ? parseInt(estimate, 10) : null,
        status: 'TODO',
      });

      onSuccess();
    } catch (err) {
      setErrorMsg(err.message || 'Lỗi khi tạo tác vụ. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.45)',
        backdropFilter: 'blur(3px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: 20,
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 540,
          backgroundColor: '#FFFFFF',
          borderRadius: 14,
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          border: '1px solid #E2E8F0',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '16px 20px',
            borderBottom: '1px solid #F1F5F9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: 6,
                backgroundColor: parentTask ? '#F5F3FF' : '#EFF6FF',
                color: parentTask ? '#7C3AED' : '#2563EB',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <CheckSquare size={15} />
            </div>
            <div>
              <h2 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                {parentTask ? `Tạo Tác Vụ Con cho [${parentTask.code}]` : 'Tạo Tác Vụ Mới'}
              </h2>
              {parentTask && (
                <div style={{ fontSize: 11, color: '#64748B' }}>Task cha: {parentTask.title}</div>
              )}
            </div>
          </div>

          <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer' }}>
            <X size={18} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} style={{ padding: '20px' }}>
          {errorMsg && (
            <div style={{ padding: '8px 12px', borderRadius: 6, backgroundColor: '#FEF2F2', color: '#DC2626', fontSize: 12, marginBottom: 14 }}>
              {errorMsg}
            </div>
          )}

          {/* Title */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
              Tiêu đề tác vụ *
            </label>
            <input
              type="text"
              placeholder="VD: Viết Unit test cho cổng thanh toán VNPay"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: 6,
                border: '1px solid #CBD5E1',
                fontSize: 13,
                outline: 'none',
              }}
            />
          </div>

          {/* Type & Priority Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 14 }}>
            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                Loại tác vụ (Type)
              </label>
              <select
                value={type}
                onChange={(e) => setType(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  backgroundColor: '#FFFFFF',
                  outline: 'none',
                }}
              >
                <option value="TASK">Task (Công việc)</option>
                <option value="STORY">User Story (Tính năng)</option>
                <option value="BUG">Bug (Lỗi phần mềm)</option>
                <option value="EPIC">Epic (Dự án lớn)</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                Mức độ ưu tiên
              </label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  backgroundColor: '#FFFFFF',
                  outline: 'none',
                }}
              >
                <option value="LOW">Thấp (Low)</option>
                <option value="MEDIUM">Bình thường (Medium)</option>
                <option value="HIGH">Cao (High)</option>
                <option value="URGENT">Khẩn cấp (Urgent)</option>
              </select>
            </div>
          </div>

          {/* Milestone & Estimate Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 120px', gap: 12, marginBottom: 14 }}>
            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                Gán vào Cột mốc (Milestone)
              </label>
              <select
                value={milestoneId}
                onChange={(e) => setMilestoneId(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  backgroundColor: '#FFFFFF',
                  outline: 'none',
                }}
              >
                <option value="">-- Chưa gán cột mốc (Backlog) --</option>
                {milestones && milestones.map((m, idx) => (
                  <option key={m.id || `milestone-opt-${idx}`} value={m.id}>
                    {m.name} ({m.progressPercentage || 0}%)
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                Ước lượng (Giờ)
              </label>
              <input
                type="number"
                min="1"
                placeholder="VD: 8"
                value={estimate}
                onChange={(e) => setEstimate(e.target.value)}
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  outline: 'none',
                }}
              />
            </div>
          </div>

          {/* Description */}
          <div style={{ marginBottom: 20 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
              Mô tả chi tiết & Tiêu chí nghiệm thu (Acceptance Criteria)
            </label>
            <textarea
              rows={3}
              placeholder="Mô tả các bước thực hiện, dữ liệu đầu vào hoặc tài liệu tham khảo..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: 6,
                border: '1px solid #CBD5E1',
                fontSize: 13,
                outline: 'none',
                resize: 'vertical',
              }}
            />
          </div>

          {/* Actions */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '8px 16px',
                borderRadius: 6,
                border: '1px solid #CBD5E1',
                backgroundColor: '#FFFFFF',
                color: '#475569',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{
                padding: '8px 18px',
                borderRadius: 6,
                border: 'none',
                backgroundColor: '#2563EB',
                color: '#FFFFFF',
                fontSize: 13,
                fontWeight: 600,
                cursor: loading ? 'not-allowed' : 'pointer',
              }}
            >
              {loading ? 'Đang tạo...' : 'Tạo tác vụ'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
