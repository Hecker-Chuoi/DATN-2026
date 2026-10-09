import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  X,
  FolderKanban,
  Kanban,
  Briefcase,
  Zap,
  Sparkles,
  Calendar,
  Layers,
  Users,
  Info,
  Check,
  AlertCircle
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi } from '../../api.js';
import {
  generateProjectKey,
  sanitizeProjectKey,
  validateProjectForm,
  PROJECT_KEY_REGEX
} from '../../utils/projectValidation.js';
import { useWorkspace } from '../../context/WorkspaceContext.jsx';
import { wsPath } from '../../utils/workspacePath.js';

// teams: chỉ những nhóm mà user là trưởng nhóm (hoặc mọi nhóm với Global ADMIN); allowPersonal: cho phép dự án không gắn nhóm (chỉ ADMIN)
export default function CreateProjectModal({ isOpen, onClose, teams = [], defaultTeamId = null, allowPersonal = false, onProjectCreated }) {
  const navigate = useNavigate();
  const { currentWorkspaceId, currentWorkspace } = useWorkspace();

  // workspaceId KHÔNG nằm trong formData: form không bao giờ cho sửa nó, nên nó
  // không phải state của form. Trước đây nó là hằng số hardcode
  // 'a0000000-0000-0000-0000-000000000001' ở cả hai chỗ (khởi tạo và re-init).
  const [formData, setFormData] = useState({
    teamId: defaultTeamId || (teams[0]?.id || ''),
    name: '',
    key: '',
    description: '',
    projectType: 'SOFTWARE_SCRUM',
    isTemplate: false,
    startDate: new Date().toISOString().split('T')[0],
    endDate: '',
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [isKeyManuallyEdited, setIsKeyManuallyEdited] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setFormData({
        teamId: defaultTeamId || (teams[0]?.id || ''),
        name: '',
        key: '',
        description: '',
        projectType: 'SOFTWARE_SCRUM',
        isTemplate: false,
        startDate: new Date().toISOString().split('T')[0],
        endDate: '',
      });
      setErrors({});
      setIsKeyManuallyEdited(false);
    }
  }, [isOpen, defaultTeamId, teams]);

  if (!isOpen) return null;

  const handleNameChange = (e) => {
    const newName = e.target.value;
    setFormData((prev) => {
      const updated = { ...prev, name: newName };
      if (!isKeyManuallyEdited) {
        updated.key = generateProjectKey(newName);
      }
      return updated;
    });

    if (errors.name) {
      setErrors((prev) => ({ ...prev, name: undefined }));
    }
  };

  const handleKeyChange = (e) => {
    const rawKey = e.target.value;
    const sanitized = sanitizeProjectKey(rawKey);
    setIsKeyManuallyEdited(true);
    setFormData((prev) => ({ ...prev, key: sanitized }));

    if (errors.key) {
      setErrors((prev) => ({ ...prev, key: undefined }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    const validation = validateProjectForm(formData);
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    if (!allowPersonal && !formData.teamId) {
      setErrors({ teamId: 'Chỉ trưởng nhóm mới được tạo dự án. Hãy chọn nhóm mà bạn là trưởng nhóm.' });
      return;
    }

    // validateProjectForm chỉ kiểm name/key/endDate nên sẽ để lọt trường hợp
    // workspace chưa tải xong; không guard ở đây thì POST đi với
    // workspaceId: undefined và backend trả 400 khó hiểu.
    if (!currentWorkspaceId) {
      setErrors({ name: 'Chưa xác định được workspace. Vui lòng tải lại trang.' });
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        workspaceId: currentWorkspaceId,
        teamId: formData.teamId || undefined,
        name: formData.name.trim(),
        key: formData.key.trim().toUpperCase(),
        description: formData.description?.trim() || undefined,
        projectType: formData.projectType,
        isTemplate: formData.isTemplate,
        startDate: formData.startDate || undefined,
        endDate: formData.endDate || undefined,
      };

      const res = await projectApi.create(payload);
      const createdProject = res.data;

      toast.success(`Dự án ${createdProject?.name || formData.name} đã được tạo thành công!`);

      if (onProjectCreated) {
        onProjectCreated(createdProject);
      }
      onClose();

      // Navigate to the newly created project board
      if (createdProject?.id) {
        navigate(wsPath(currentWorkspaceId, `/projects/${createdProject.id}`));
      }
    } catch (err) {
      console.error('Failed to create project:', err);
      // Toast already shown by axios interceptor if backend returned message
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.65)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: 16,
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          width: '100%',
          maxWidth: 580,
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          animation: 'modalSlideIn 0.2s cubic-bezier(0.16, 1, 0.3, 1)',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* ─── Header ─── */}
        <div
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid #E2E8F0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            backgroundColor: '#FAFAFC',
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
              <FolderKanban size={20} />
            </div>
            <div>
              <h2 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>Tạo Dự án Mới</h2>
              <p style={{ fontSize: 12, color: '#64748B', margin: '2px 0 0 0' }}>
                {currentWorkspace?.name
                  ? `Trong workspace ${currentWorkspace.name}`
                  : 'Khởi tạo không gian làm việc Scrum hoặc Kanban cho nhóm của bạn'}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              color: '#94A3B8',
              cursor: 'pointer',
              padding: 6,
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* ─── Form Body ─── */}
        <form onSubmit={handleSubmit} style={{ overflowY: 'auto', padding: '24px', display: 'flex', flexDirection: 'column', gap: 18 }}>
          {/* Project Name */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Tên dự án <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <input
              type="text"
              placeholder="VD: Core Banking Platform, E-Commerce App..."
              value={formData.name}
              onChange={handleNameChange}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: 8,
                border: `1px solid ${errors.name ? '#EF4444' : '#CBD5E1'}`,
                fontSize: 14,
                color: '#0F172A',
                outline: 'none',
                boxSizing: 'border-box',
                transition: 'border-color 0.15s ease',
              }}
              autoFocus
            />
            {errors.name && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#EF4444', fontSize: 12, marginTop: 4 }}>
                <AlertCircle size={13} />
                <span>{errors.name}</span>
              </div>
            )}
          </div>

          {/* Project Key & Team Selection */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            {/* Project Key */}
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                Mã dự án (Tiền tố viết tắt) <span style={{ color: '#EF4444' }}>*</span>
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  type="text"
                  placeholder="VD: CBE, PRJ"
                  value={formData.key}
                  onChange={handleKeyChange}
                  maxLength={10}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    borderRadius: 8,
                    border: `1px solid ${errors.key ? '#EF4444' : '#CBD5E1'}`,
                    fontSize: 14,
                    fontWeight: 700,
                    color: '#2563EB',
                    letterSpacing: '0.05em',
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                />
              </div>
              <div style={{ fontSize: 11, color: '#64748B', marginTop: 4 }}>
                Tiền tố cho mã thẻ công việc (VD: <strong>{formData.key || 'PRJ'}-101</strong>)
              </div>
              {errors.key && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#EF4444', fontSize: 12, marginTop: 4 }}>
                  <AlertCircle size={13} />
                  <span>{errors.key}</span>
                </div>
              )}
            </div>

            {/* Team Selector */}
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                Nhóm trực thuộc
              </label>
              <select
                value={formData.teamId}
                onChange={(e) => setFormData({ ...formData, teamId: e.target.value })}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  borderRadius: 8,
                  border: '1px solid #CBD5E1',
                  fontSize: 14,
                  color: '#0F172A',
                  backgroundColor: '#FFFFFF',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              >
                {allowPersonal ? (
                  <option value="">-- Cá nhân (Không thuộc nhóm) --</option>
                ) : (
                  teams.length === 0 && <option value="">-- Bạn chưa là trưởng nhóm nào --</option>
                )}
                {teams.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
              {errors.teamId && (
                <div style={{ fontSize: 12, color: '#EF4444', marginTop: 4 }}>{errors.teamId}</div>
              )}
            </div>
          </div>

          {/* Project Type Card Selector */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 8 }}>
              Mô hình quản lý công việc
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
              {/* Scrum Card */}
              <div
                onClick={() => setFormData({ ...formData, projectType: 'SOFTWARE_SCRUM' })}
                style={{
                  padding: '14px 16px',
                  borderRadius: 8,
                  border: `2px solid ${formData.projectType === 'SOFTWARE_SCRUM' ? '#2563EB' : '#E2E8F0'}`,
                  backgroundColor: formData.projectType === 'SOFTWARE_SCRUM' ? '#EFF6FF' : '#FFFFFF',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  position: 'relative',
                }}
              >
                {formData.projectType === 'SOFTWARE_SCRUM' && (
                  <div
                    style={{
                      position: 'absolute',
                      top: 10,
                      right: 10,
                      width: 18,
                      height: 18,
                      borderRadius: '50%',
                      backgroundColor: '#2563EB',
                      color: '#FFFFFF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Check size={12} strokeWidth={3} />
                  </div>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#1D4ED8', fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                  <Zap size={16} />
                  <span>Quy trình Scrum</span>
                </div>
                <p style={{ fontSize: 12, color: '#475569', margin: 0, lineHeight: 1.4 }}>
                  Phát triển lặp theo chu kỳ, quản lý danh sách công việc tồn đọng và biểu đồ tiến độ.
                </p>
              </div>

              {/* Kanban Card */}
              <div
                onClick={() => setFormData({ ...formData, projectType: 'SOFTWARE_KANBAN' })}
                style={{
                  padding: '14px 16px',
                  borderRadius: 8,
                  border: `2px solid ${formData.projectType === 'SOFTWARE_KANBAN' ? '#2563EB' : '#E2E8F0'}`,
                  backgroundColor: formData.projectType === 'SOFTWARE_KANBAN' ? '#EFF6FF' : '#FFFFFF',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  position: 'relative',
                }}
              >
                {formData.projectType === 'SOFTWARE_KANBAN' && (
                  <div
                    style={{
                      position: 'absolute',
                      top: 10,
                      right: 10,
                      width: 18,
                      height: 18,
                      borderRadius: '50%',
                      backgroundColor: '#2563EB',
                      color: '#FFFFFF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Check size={12} strokeWidth={3} />
                  </div>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#1D4ED8', fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                  <Kanban size={16} />
                  <span>Bảng Kanban</span>
                </div>
                <p style={{ fontSize: 12, color: '#475569', margin: 0, lineHeight: 1.4 }}>
                  Luồng công việc liên tục, trực quan hóa trạng thái và giới hạn số lượng công việc đang làm.
                </p>
              </div>

              {/* Business Card: dự án không phải phần mềm, không có tích hợp GitHub */}
              <div
                onClick={() => setFormData({ ...formData, projectType: 'BUSINESS' })}
                style={{
                  padding: '14px 16px',
                  borderRadius: 8,
                  border: `2px solid ${formData.projectType === 'BUSINESS' ? '#2563EB' : '#E2E8F0'}`,
                  backgroundColor: formData.projectType === 'BUSINESS' ? '#EFF6FF' : '#FFFFFF',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  position: 'relative',
                }}
              >
                {formData.projectType === 'BUSINESS' && (
                  <div
                    style={{
                      position: 'absolute',
                      top: 10,
                      right: 10,
                      width: 18,
                      height: 18,
                      borderRadius: '50%',
                      backgroundColor: '#2563EB',
                      color: '#FFFFFF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Check size={12} strokeWidth={3} />
                  </div>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#1D4ED8', fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
                  <Briefcase size={16} />
                  <span>Dự án Kinh doanh</span>
                </div>
                <p style={{ fontSize: 12, color: '#475569', margin: 0, lineHeight: 1.4 }}>
                  Quản lý công việc tổng quát (marketing, sự kiện, vận hành...), không gắn kho mã nguồn.
                </p>
              </div>
            </div>
          </div>

          {/* Timeline Dates */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                Ngày bắt đầu
              </label>
              <input
                type="date"
                value={formData.startDate}
                onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  borderRadius: 8,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  color: '#0F172A',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                Hạn chót dự kiến
              </label>
              <input
                type="date"
                value={formData.endDate}
                onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                style={{
                  width: '100%',
                  padding: '8px 12px',
                  borderRadius: 8,
                  border: `1px solid ${errors.endDate ? '#EF4444' : '#CBD5E1'}`,
                  fontSize: 13,
                  color: '#0F172A',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              />
              {errors.endDate && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#EF4444', fontSize: 12, marginTop: 4 }}>
                  <AlertCircle size={13} />
                  <span>{errors.endDate}</span>
                </div>
              )}
            </div>
          </div>

          {/* Description */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Mô tả dự án
            </label>
            <textarea
              rows={3}
              placeholder="Tóm tắt mục tiêu, phạm vi và công nghệ của dự án..."
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: 8,
                border: '1px solid #CBD5E1',
                fontSize: 13,
                color: '#0F172A',
                outline: 'none',
                resize: 'vertical',
                boxSizing: 'border-box',
                fontFamily: 'inherit',
              }}
            />
          </div>

          {/* Template Checkbox */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px', backgroundColor: '#F8FAFC', borderRadius: 8 }}>
            <input
              type="checkbox"
              id="isTemplate"
              checked={formData.isTemplate}
              onChange={(e) => setFormData({ ...formData, isTemplate: e.target.checked })}
              style={{ width: 16, height: 16, cursor: 'pointer', accentColor: '#2563EB' }}
            />
            <label htmlFor="isTemplate" style={{ fontSize: 13, color: '#334155', cursor: 'pointer', userSelect: 'none' }}>
              Đặt làm <strong>Dự án mẫu</strong> để tái sử dụng cấu hình cho các dự án sau
            </label>
          </div>

          {/* ─── Footer Buttons ─── */}
          <div
            style={{
              paddingTop: 12,
              borderTop: '1px solid #E2E8F0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              gap: 10,
            }}
          >
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              style={{
                padding: '8px 16px',
                borderRadius: 8,
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
              disabled={submitting}
              style={{
                padding: '8px 20px',
                borderRadius: 8,
                border: 'none',
                backgroundColor: submitting ? '#93C5FD' : '#2563EB',
                color: '#FFFFFF',
                fontSize: 13,
                fontWeight: 600,
                cursor: submitting ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                boxShadow: '0 2px 4px rgba(37,99,235,0.2)',
              }}
            >
              {submitting ? (
                <span>Đang khởi tạo hệ sinh thái...</span>
              ) : (
                <>
                  <Sparkles size={15} />
                  <span>Tạo dự án</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
