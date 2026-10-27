import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  X,
  FolderKanban,
  Kanban,
  Zap,
  Sparkles,
  Calendar,
  Layers,
  Users,
  Info,
  Check,
  AlertCircle,
  LayoutTemplate,
  FilePlus2,
  Eye,
  RefreshCw
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi, templateApi } from '../../api.js';
import TemplateGallery from '../template/TemplateGallery.jsx';
import TemplatePreviewModal from '../template/TemplatePreviewModal.jsx';
import { addDays, categoryToken, PROJECT_TYPE_LABELS } from '../../constants/templateConstants.js';
import {
  generateProjectKey,
  sanitizeProjectKey,
  validateProjectForm,
  PROJECT_KEY_REGEX
} from '../../utils/projectValidation.js';
import { useWorkspace } from '../../context/WorkspaceContext.jsx';
import { wsPath } from '../../utils/workspacePath.js';
import { useModalBackdrop } from '../../hooks/useModalBackdrop.js';

export default function CreateProjectModal({ isOpen, onClose, onProjectCreated }) {
  const navigate = useNavigate();
  const { currentWorkspaceId, currentWorkspace } = useWorkspace();
  const backdropProps = useModalBackdrop(onClose);

  // workspaceId KHÔNG nằm trong formData: form không bao giờ cho sửa nó, nên nó
  // không phải state của form. Trước đây nó là hằng số hardcode
  // 'a0000000-0000-0000-0000-000000000001' ở cả hai chỗ (khởi tạo và re-init).
  const [formData, setFormData] = useState({
    name: '',
    key: '',
    description: '',
    projectType: 'SOFTWARE_SCRUM',
    startDate: new Date().toISOString().split('T')[0],
    endDate: '',
  });

  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [isKeyManuallyEdited, setIsKeyManuallyEdited] = useState(false);

  // ─── Tạo từ mẫu ───
  // mode: 'BLANK' = dự án trống (seed mặc định), 'TEMPLATE' = sao chép toàn bộ bản thiết kế của một dự án mẫu
  const [mode, setMode] = useState('BLANK');
  const [previewTemplateId, setPreviewTemplateId] = useState(null);
  const [selectedTemplate, setSelectedTemplate] = useState(null); // TemplatePreviewResponse
  const [templateCustomizations, setTemplateCustomizations] = useState(null);
  const [isEndDateManuallyEdited, setIsEndDateManuallyEdited] = useState(false);
  const [cloneOptions, setCloneOptions] = useState({ includeSprints: true, includeWorkItems: true, includeVersions: true });

  const prevIsOpenRef = useRef(false);

  useEffect(() => {
    // Chỉ khởi tạo/reset lại toàn bộ form khi modal chuyển từ trạng thái ĐÓNG sang MỞ.
    // Tránh việc các component cha re-render (do network/polling) làm mất dữ liệu người dùng đang nhập.
    if (isOpen && !prevIsOpenRef.current) {
      setMode('BLANK');
      setPreviewTemplateId(null);
      setSelectedTemplate(null);
      setTemplateCustomizations(null);
      setIsEndDateManuallyEdited(false);
      setCloneOptions({ includeSprints: true, includeWorkItems: true, includeVersions: true });
      setFormData({
        name: '',
        key: '',
        description: '',
        projectType: 'SOFTWARE_SCRUM',
        startDate: new Date().toISOString().split('T')[0],
        endDate: '',
      });
      setErrors({});
      setIsKeyManuallyEdited(false);
    }
    prevIsOpenRef.current = isOpen;
  }, [isOpen]);


  if (!isOpen) return null;

  const isTemplateMode = mode === 'TEMPLATE';
  const showGallery = isTemplateMode && !selectedTemplate;

  // Hạn chót gợi ý = ngày bắt đầu + độ dài timeline của mẫu (giữ nguyên khoảng cách các sprint)
  const suggestedEndDate = (startDate, template = selectedTemplate) => {
    const span = template?.timeline?.spanDays;
    return span != null && startDate ? addDays(startDate, span) : '';
  };

  const handleUseTemplate = (preview, customizations = null) => {
    setSelectedTemplate(preview);
    setTemplateCustomizations(customizations);
    setPreviewTemplateId(null);
    setIsEndDateManuallyEdited(false);
    setErrors({});
    setFormData((prev) => ({
      ...prev,
      name: prev.name || preview.name || '',
      key: prev.key || (preview.name ? generateProjectKey(preview.name) : ''),
      projectType: preview.projectType,
      description: prev.description || preview.description || '',
      endDate: suggestedEndDate(prev.startDate, preview) || prev.endDate,
    }));
  };

  const handleStartDateChange = (e) => {
    const startDate = e.target.value;
    setFormData((prev) => ({
      ...prev,
      startDate,
      endDate: isTemplateMode && !isEndDateManuallyEdited ? suggestedEndDate(startDate) || prev.endDate : prev.endDate,
    }));
  };

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

    // validateProjectForm chỉ kiểm name/key/endDate nên sẽ để lọt trường hợp
    // workspace chưa tải xong; không guard ở đây thì POST đi với
    // workspaceId: undefined và backend trả 400 khó hiểu.
    if (!currentWorkspaceId) {
      setErrors({ name: 'Chưa xác định được không gian làm việc. Vui lòng tải lại trang.' });
      return;
    }

    if (isTemplateMode && !selectedTemplate) {
      toast.error('Hãy chọn một dự án mẫu');
      return;
    }

    if (isTemplateMode) {
      try {
        setSubmitting(true);
        const res = await templateApi.createProject(selectedTemplate.id, {
          workspaceId: currentWorkspaceId,
          name: formData.name.trim(),
          key: formData.key.trim().toUpperCase(),
          description: formData.description?.trim() || undefined,
          startDate: formData.startDate || undefined,
          endDate: formData.endDate || undefined,
          ...cloneOptions,
          customizations: templateCustomizations || undefined,
        });
        const result = res.data;
        const createdProject = result?.project;
        const stats = result?.stats || {};
        toast.success(
          `Đã tạo ${createdProject?.name || formData.name} từ mẫu: ${stats.statuses || 0} trạng thái, ` +
            `${stats.sprints || 0} chu kỳ làm việc, ${stats.workItems || 0} công việc`
        );
        if (onProjectCreated) onProjectCreated(createdProject);
        onClose();
        if (createdProject?.id) {
          navigate(wsPath(currentWorkspaceId, `/projects/${createdProject.id}`));
        }
      } catch (err) {
        console.error('Failed to create project from template:', err);
      } finally {
        setSubmitting(false);
      }
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        workspaceId: currentWorkspaceId,
        name: formData.name.trim(),
        key: formData.key.trim().toUpperCase(),
        description: formData.description?.trim() || undefined,
        projectType: formData.projectType,
        isTemplate: false,
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
    <>
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
      {...backdropProps}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          width: '100%',
          maxWidth: isTemplateMode ? 820 : 580,
          transition: 'max-width 0.2s ease',
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
                  ? `Trong không gian làm việc ${currentWorkspace.name}`
                  : 'Khởi tạo không gian quản lý tiến độ cho nhóm của bạn'}
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

        {/* ─── Tabs: Dự án trống / Chọn mẫu dự án ─── */}
        <div style={{ display: 'flex', gap: 4, padding: '0 24px', borderBottom: '1px solid #E2E8F0', backgroundColor: '#FAFAFC' }}>
          {[
            { id: 'BLANK', label: 'Dự án trống', icon: <FilePlus2 size={14} /> },
            { id: 'TEMPLATE', label: 'Chọn mẫu dự án', icon: <LayoutTemplate size={14} /> },
          ].map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => {
                setMode(t.id);
                setErrors({});
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '10px 14px',
                border: 'none',
                borderBottom: `2px solid ${mode === t.id ? '#2563EB' : 'transparent'}`,
                background: 'none',
                color: mode === t.id ? '#2563EB' : '#64748B',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
                fontFamily: 'inherit',
              }}
            >
              {t.icon}
              {t.label}
            </button>
          ))}
        </div>

        {showGallery ? (
          <div style={{ overflowY: 'auto', padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ fontSize: 13, color: '#475569' }}>
              Chọn một mẫu để xem trước. Dự án mới sẽ kế thừa trạng thái, workflow, board, phân quyền và (tùy chọn)
              sprint, công việc của mẫu - mọi mốc thời gian được dời theo ngày bắt đầu bạn chọn.
            </div>
            <TemplateGallery
              workspaceId={currentWorkspaceId}
              selectedId={selectedTemplate?.id}
              onPreview={(t) => setPreviewTemplateId(t.id)}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: 12, borderTop: '1px solid #E2E8F0' }}>
              <button
                type="button"
                onClick={onClose}
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
            </div>
          </div>
        ) : (
        /* ─── Form Body ─── */
        <form onSubmit={handleSubmit} style={{ overflowY: 'auto', padding: '24px', display: 'flex', flexDirection: 'column', gap: 18 }}>
          {isTemplateMode && selectedTemplate && (
            <SelectedTemplateBanner
              template={selectedTemplate}
              customizations={templateCustomizations}
              onPreview={() => setPreviewTemplateId(selectedTemplate.id)}
              onChange={() => {
                setSelectedTemplate(null);
                setTemplateCustomizations(null);
              }}
            />
          )}

          {/* Project Name */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Tên dự án <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <input
              type="text"
              placeholder="VD: Chiến dịch Marketing 2026, Xây dựng Văn phòng..."
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

          {/* Project Key */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Mã dự án (Tiền tố viết tắt) <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <div style={{ position: 'relative' }}>
              <input
                type="text"
                placeholder="VD: MKT, XD, KH"
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

          {/* Project Type Card Selector (dự án từ mẫu kế thừa mô hình của mẫu) */}
          {!isTemplateMode && (
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 8 }}>
              Mô hình quản lý công việc
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
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
                  <span>Theo chu kỳ / Theo giai đoạn</span>
                </div>
                <p style={{ fontSize: 12, color: '#475569', margin: 0, lineHeight: 1.4 }}>
                  Quản lý theo từng chu kỳ hoặc giai đoạn định kỳ, lập kế hoạch và theo dõi tiến độ.
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
                  <span>Bảng công việc liên tục</span>
                </div>
                <p style={{ fontSize: 12, color: '#475569', margin: 0, lineHeight: 1.4 }}>
                  Luồng công việc liên tục, trực quan hóa trạng thái với giới hạn việc đang làm.
                </p>
              </div>
            </div>
          </div>
          )}

          {/* Timeline Dates */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                Ngày bắt đầu
              </label>
              <input
                type="date"
                value={formData.startDate}
                onChange={handleStartDateChange}
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
                onChange={(e) => {
                  setIsEndDateManuallyEdited(true);
                  setFormData({ ...formData, endDate: e.target.value });
                }}
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

          {/* Tùy chọn sao chép từ mẫu */}
          {isTemplateMode && selectedTemplate && (
            <div style={{ padding: '12px 14px', backgroundColor: '#F8FAFC', borderRadius: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Sao chép từ mẫu</div>
              <div style={{ fontSize: 12, color: '#64748B' }}>
                Trạng thái, quy trình làm việc, bảng làm việc, loại công việc, phân quyền, nhãn, hợp phần luôn được sao chép.
              </div>
              {[
                ['includeSprints', `Chu kỳ làm việc (${selectedTemplate.stats?.sprintCount || 0})`, 'Bỏ chọn: công việc nằm hết trong Kho việc chờ phân bổ'],
                ['includeWorkItems', `Công việc (${selectedTemplate.stats?.workItemCount || 0})`, 'Hạng mục lớn, yêu cầu, nhiệm vụ, nhiệm vụ con kèm liên kết & nhãn'],
                ['includeVersions', `Phiên bản (${selectedTemplate.versions?.length || 0})`, 'Ngày phát hành được dời theo lịch mới'],
              ].map(([key, label, hint]) => (
                <label key={key} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#334155', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={cloneOptions[key]}
                    onChange={(e) => setCloneOptions((prev) => ({ ...prev, [key]: e.target.checked }))}
                    style={{ width: 15, height: 15, accentColor: '#2563EB' }}
                  />
                  <span style={{ fontWeight: 600 }}>{label}</span>
                  <span style={{ fontSize: 12, color: '#94A3B8' }}>{hint}</span>
                </label>
              ))}
            </div>
          )}

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
            {isTemplateMode && selectedTemplate ? (
              <button
                type="button"
                onClick={() => setPreviewTemplateId(selectedTemplate.id)}
                disabled={submitting}
                style={{
                  padding: '8px 16px',
                  borderRadius: 8,
                  border: '1px solid #CBD5E1',
                  backgroundColor: '#FFFFFF',
                  color: '#334155',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                ← Quay lại xem mẫu
              </button>
            ) : (
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
            )}
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
                <span>{isTemplateMode ? 'Đang sao chép từ mẫu...' : 'Đang khởi tạo hệ sinh thái...'}</span>
              ) : (
                <>
                  <Sparkles size={15} />
                  <span>{isTemplateMode ? 'Tạo dự án từ mẫu' : 'Tạo dự án'}</span>
                </>
              )}
            </button>
          </div>
        </form>
        )}
      </div>
    </div>

    {/* Anh em với overlay (không lồng bên trong) để click trong modal preview không nổi bọt làm đóng form */}
    <TemplatePreviewModal
      isOpen={!!previewTemplateId}
      templateId={previewTemplateId}
      onClose={() => setPreviewTemplateId(null)}
      onUse={handleUseTemplate}
      initialCustomizations={templateCustomizations}
    />
    </>
  );
}

/** Mẫu đang được chọn, hiển thị phía trên form khi tạo dự án từ mẫu. */
function SelectedTemplateBanner({ template, customizations, onPreview, onChange }) {
  const cat = categoryToken(template.templateCategory);
  const s = template.stats || {};

  const customCount = customizations
    ? Object.keys(customizations.renamedStatuses || {}).length +
      Object.keys(customizations.renamedTypes || {}).length +
      (customizations.excludedSprintIds || []).length +
      Object.keys(customizations.renamedSprints || {}).length +
      (customizations.excludedWorkItemIds || []).length +
      Object.keys(customizations.renamedWorkItems || {}).length
    : 0;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '12px 14px',
        borderRadius: 10,
        border: '1px solid #BFDBFE',
        backgroundColor: '#F8FBFF',
      }}
    >
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: 8,
          backgroundColor: cat.bg,
          color: cat.color,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <LayoutTemplate size={18} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 11, color: '#64748B', fontWeight: 600 }}>
            MẪU · {cat.label} · {PROJECT_TYPE_LABELS[template.projectType] || template.projectType}
          </span>
          {customCount > 0 && (
            <span
              style={{
                fontSize: 10.5,
                fontWeight: 700,
                color: '#1D4ED8',
                backgroundColor: '#EFF6FF',
                padding: '1px 6px',
                borderRadius: 4,
                border: '1px solid #BFDBFE',
              }}
            >
              ✨ Đã tùy chỉnh ({customCount})
            </span>
          )}
        </div>
        <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', marginTop: 1 }}>{template.name}</div>
        <div style={{ fontSize: 12, color: '#475569', marginTop: 1 }}>
          {s.statusCount || 0} trạng thái · {s.workflowCount || 0} workflow · {s.sprintCount || 0} sprint ·{' '}
          {s.workItemCount || 0} công việc
          {template.timeline?.spanDays != null && <> · {template.timeline.spanDays} ngày</>}
        </div>
      </div>
      <button type="button" onClick={onPreview} style={bannerBtn} title="Xem trước và tùy chỉnh chi tiết">
        <Eye size={13} /> {customCount > 0 ? 'Tùy chỉnh' : 'Xem trước'}
      </button>
      <button type="button" onClick={onChange} style={bannerBtn} title="Chọn mẫu khác">
        <RefreshCw size={13} /> Đổi mẫu
      </button>
    </div>
  );
}

const bannerBtn = {
  display: 'flex',
  alignItems: 'center',
  gap: 4,
  padding: '6px 10px',
  borderRadius: 6,
  border: '1px solid #CBD5E1',
  backgroundColor: '#FFFFFF',
  color: '#334155',
  fontSize: 12,
  fontWeight: 600,
  cursor: 'pointer',
  whiteSpace: 'nowrap',
};
