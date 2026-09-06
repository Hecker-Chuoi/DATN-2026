import React, { useState, useEffect } from 'react';
import {
  X,
  Bookmark,
  CheckSquare,
  AlertCircle,
  CircleDot,
  GitCommitHorizontal,
  ArrowUp,
  ArrowDown,
  Equal,
  Sparkles,
  Layers,
  User,
  Hash,
  FileText
} from 'lucide-react';
import toast from 'react-hot-toast';
import { workItemApi, projectApi } from '../../api.js';
import MemberSelect from '../common/MemberSelect.jsx';
import StoryPointsHelpTooltip from '../common/StoryPointsHelpTooltip.jsx';
import { PRIORITY_CONFIG } from '../../utils/priorityConfig.js';

const TYPE_CONFIGS = {
  EPIC: { label: 'Epic', icon: Bookmark, color: '#6554C0', bgColor: '#F3F0FF', level: 1 },
  STORY: { label: 'Story', icon: Bookmark, color: '#36B37E', bgColor: '#E3FCEF', level: 0 },
  TASK: { label: 'Task', icon: CheckSquare, color: '#0052CC', bgColor: '#DEEBFF', level: 0 },
  BUG: { label: 'Bug', icon: CircleDot, color: '#E5493A', bgColor: '#FFEBE6', level: 0 },
  SUBTASK: { label: 'Sub-task', icon: GitCommitHorizontal, color: '#4C9AFF', bgColor: '#E6FCFF', level: -1 },
};

const PRIORITIES = [
  { value: 'HIGHEST', label: 'Highest', icon: ArrowUp, color: '#DE350B' },
  { value: 'HIGH', label: 'High', icon: ArrowUp, color: '#FF5630' },
  { value: 'MEDIUM', label: 'Medium', icon: Equal, color: '#FFAB00' },
  { value: 'LOW', label: 'Low', icon: ArrowDown, color: '#36B37E' },
  { value: 'LOWEST', label: 'Lowest', icon: ArrowDown, color: '#6B778C' },
];

export default function CreateWorkItemModal({
  isOpen,
  onClose,
  projectId,
  projectKey,
  projectTypes = [],
  members = [],
  defaultParentId = null,
  defaultTypeId = null,
  onItemCreated
}) {
  const [types, setTypes] = useState([]);
  const [selectedType, setSelectedType] = useState(null);
  const [summary, setSummary] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState('MEDIUM');
  const [storyPoints, setStoryPoints] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [parentId, setParentId] = useState(defaultParentId || '');
  const [assigneeId, setAssigneeId] = useState('');
  const [existingItems, setExistingItems] = useState([]);
  const [loadingItems, setLoadingItems] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [errors, setErrors] = useState({});

  // Khởi tạo danh sách Loại công việc
  useEffect(() => {
    if (projectTypes && projectTypes.length > 0) {
      setTypes(projectTypes);
      const defaultType = defaultTypeId
        ? projectTypes.find((t) => t.id === defaultTypeId)
        : projectTypes.find((t) => t.code === 'STORY' || t.hierarchyLevel === 0) || projectTypes[0];
      setSelectedType(defaultType || projectTypes[0]);
    }
  }, [projectTypes, defaultTypeId]);

  // Load danh sách items hiện có trong project để chọn Parent
  useEffect(() => {
    if (isOpen && projectId) {
      setSummary('');
      setDescription('');
      setPriority('MEDIUM');
      setStoryPoints('');
      setDueDate('');
      setParentId(defaultParentId || '');
      setAssigneeId('');
      setErrors({});

      const fetchExistingItems = async () => {
        try {
          setLoadingItems(true);
          const res = await workItemApi.listAll({ projectId, size: 100 });
          if (res && res.data) {
            const list = res.data.content || res.data || [];
            setExistingItems(list);
          }
        } catch (err) {
          console.warn('Could not load existing items for parent selection:', err);
        } finally {
          setLoadingItems(false);
        }
      };
      fetchExistingItems();
    }
  }, [isOpen, projectId, defaultParentId]);

  if (!isOpen) return null;

  const currentLevel = selectedType?.hierarchyLevel ?? 0;
  const isEpic = currentLevel === 1 || selectedType?.code === 'EPIC';
  const isSubtask = currentLevel === -1 || selectedType?.code === 'SUBTASK' || selectedType?.isSubtask;

  // Lọc danh sách cha hợp lệ theo 3-level hierarchy
  const availableParents = existingItems.filter((it) => {
    if (isSubtask) {
      // Subtask cần cha là Story/Task/Bug (Level 0)
      return it.hierarchyLevel === 0 || (!it.isSubtask && it.typeCode !== 'EPIC');
    }
    if (!isEpic) {
      // Story/Task/Bug có thể gắn vào Epic (Level 1)
      return it.hierarchyLevel === 1 || it.typeCode === 'EPIC';
    }
    return false;
  });

  const handleSubmit = async (e) => {
    e.preventDefault();

    const errs = {};
    if (!summary.trim()) {
      errs.summary = 'Tiêu đề công việc không được để trống';
    } else if (summary.trim().length > 500) {
      errs.summary = 'Tiêu đề công việc tối đa 500 ký tự';
    }
    if (!selectedType?.id) {
      errs.typeId = 'Vui lòng chọn loại công việc';
    }
    if (isSubtask && !parentId) {
      errs.parentId = 'Sub-task bắt buộc phải có công việc cha (Story/Task/Bug)';
    }
    if (isEpic && parentId) {
      errs.parentId = 'Epic không thể có công việc cha';
    }
    if (storyPoints && !isSubtask && !isEpic) {
      const spNum = parseFloat(storyPoints);
      if (isNaN(spNum) || spNum < 0) {
        errs.storyPoints = 'Điểm Story Points không thể âm';
      } else if (spNum > 999.99) {
        errs.storyPoints = 'Điểm Story Points tối đa 999.99';
      }
    }

    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        projectId,
        typeId: selectedType.id,
        summary: summary.trim(),
        description: description.trim() || undefined,
        priority,
        parentId: isEpic ? null : (parentId || null),
        assigneeId: assigneeId || null,
        storyPoints: isSubtask || isEpic ? null : (storyPoints ? parseFloat(storyPoints) : null),
        dueDate: dueDate || null,
      };

      const res = await workItemApi.create(projectId, payload);
      const createdItem = res.data;

      toast.success(`Công việc ${createdItem?.code || ''} đã được tạo thành công!`);

      if (onItemCreated) {
        onItemCreated(createdItem);
      }
      onClose();
    } catch (err) {
      console.error('Failed to create work item:', err);
      // Error toast already triggered by axios interceptor if backend returned error
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
          maxWidth: 620,
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
            padding: '16px 24px',
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
                width: 32,
                height: 32,
                borderRadius: 8,
                backgroundColor: '#EFF6FF',
                color: '#2563EB',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Sparkles size={18} />
            </div>
            <div>
              <h2 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                Tạo Công việc Mới
              </h2>
              <p style={{ fontSize: 12, color: '#64748B', margin: '2px 0 0 0' }}>
                Dự án: <strong>{projectKey || 'SALES'}</strong>
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
        <form
          onSubmit={handleSubmit}
          style={{
            overflowY: 'auto',
            padding: '20px 24px',
            display: 'flex',
            flexDirection: 'column',
            gap: 16,
          }}
        >
          {/* Issue Type Selector */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Loại công việc (Issue Type) <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {types.map((t) => {
                const isSelected = selectedType?.id === t.id;
                const conf = TYPE_CONFIGS[t.code] || {
                  label: t.name,
                  icon: Bookmark,
                  color: t.colorHex || '#2563EB',
                  bgColor: '#EFF6FF',
                };
                const IconComponent = conf.icon;

                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => {
                      setSelectedType(t);
                      if (t.hierarchyLevel === 1 || t.code === 'EPIC') {
                        setParentId('');
                      }
                      if (errors.typeId) setErrors((prev) => ({ ...prev, typeId: undefined }));
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      padding: '6px 12px',
                      borderRadius: 8,
                      border: `1.5px solid ${isSelected ? conf.color : '#E2E8F0'}`,
                      backgroundColor: isSelected ? conf.bgColor : '#FFFFFF',
                      color: isSelected ? conf.color : '#475569',
                      fontSize: 13,
                      fontWeight: isSelected ? 700 : 500,
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                  >
                    <IconComponent size={14} color={conf.color} />
                    <span>{t.name || conf.label}</span>
                  </button>
                );
              })}
            </div>
            {errors.typeId && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#EF4444', fontSize: 12, marginTop: 4 }}>
                <AlertCircle size={13} />
                <span>{errors.typeId}</span>
              </div>
            )}
          </div>

          {/* Summary / Title */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Tiêu đề công việc <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <input
              type="text"
              placeholder="VD: Khách hàng quét mã QR VNPay để thanh toán..."
              value={summary}
              onChange={(e) => {
                setSummary(e.target.value);
                if (errors.summary) setErrors((prev) => ({ ...prev, summary: undefined }));
              }}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: 8,
                border: `1px solid ${errors.summary ? '#EF4444' : '#CBD5E1'}`,
                fontSize: 14,
                color: '#0F172A',
                outline: 'none',
                boxSizing: 'border-box',
              }}
              autoFocus
            />
            {errors.summary && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#EF4444', fontSize: 12, marginTop: 4 }}>
                <AlertCircle size={13} />
                <span>{errors.summary}</span>
              </div>
            )}
          </div>

          {/* Parent Issue Selector (Controlled by 3-Level Hierarchy) */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Công việc cha (Parent Issue) {isSubtask && <span style={{ color: '#EF4444' }}>*</span>}
            </label>
            {isEpic ? (
              <div
                style={{
                  padding: '8px 12px',
                  borderRadius: 8,
                  backgroundColor: '#F1F5F9',
                  color: '#64748B',
                  fontSize: 12,
                  fontStyle: 'italic',
                  border: '1px dashed #CBD5E1',
                }}
              >
                ℹ️ Epic là cấp cao nhất (Level 1) và không thể trực thuộc công việc khác.
              </div>
            ) : (
              <select
                value={parentId}
                onChange={(e) => {
                  setParentId(e.target.value);
                  if (errors.parentId) setErrors((prev) => ({ ...prev, parentId: undefined }));
                }}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  borderRadius: 8,
                  border: `1px solid ${errors.parentId ? '#EF4444' : '#CBD5E1'}`,
                  fontSize: 13,
                  color: '#0F172A',
                  backgroundColor: '#FFFFFF',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              >
                <option value="">
                  {isSubtask ? '-- Bắt buộc chọn Story/Task/Bug cha --' : '-- Không có (Độc lập / Thuộc Backlog) --'}
                </option>
                {availableParents.map((p) => (
                  <option key={p.id} value={p.id}>
                    [{p.code}] {p.summary} ({p.typeName || p.typeCode})
                  </option>
                ))}
              </select>
            )}
            {errors.parentId && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#EF4444', fontSize: 12, marginTop: 4 }}>
                <AlertCircle size={13} />
                <span>{errors.parentId}</span>
              </div>
            )}
          </div>

          {/* Priority Selection Pills */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 8 }}>
              Mức độ ưu tiên (Priority)
            </label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {Object.keys(PRIORITY_CONFIG).map((pKey) => {
                const pConf = PRIORITY_CONFIG[pKey];
                const PIcon = pConf.icon;
                const isSelected = priority === pKey;

                return (
                  <button
                    key={pKey}
                    type="button"
                    onClick={() => setPriority(pKey)}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      padding: '6px 12px',
                      borderRadius: 8,
                      border: `1.5px solid ${isSelected ? pConf.color : '#E2E8F0'}`,
                      backgroundColor: isSelected ? pConf.bgColor : '#FFFFFF',
                      color: isSelected ? pConf.color : '#64748B',
                      fontSize: 12,
                      fontWeight: isSelected ? 700 : 500,
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                  >
                    <PIcon size={13} color={isSelected ? pConf.color : '#94A3B8'} />
                    <span>{pConf.label}</span>
                    <span style={{ fontSize: 10, opacity: 0.75 }}>({pConf.subLabel})</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Due Date, Story Points & Assignee Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1.5fr', gap: 12 }}>
            {/* Due Date */}
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                Hạn chót (Due Date)
              </label>
              <input
                type="date"
                value={dueDate}
                onChange={(e) => setDueDate(e.target.value)}
                style={{
                  width: '100%',
                  padding: '7px 10px',
                  borderRadius: 8,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  color: '#0F172A',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              />
            </div>

            {/* Story Points */}
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', margin: 0 }}>
                  Story Points
                </label>
                <StoryPointsHelpTooltip align="center" />
              </div>
              {isSubtask || isEpic ? (
                <div>
                  <input
                    type="text"
                    disabled
                    value="N/A"
                    style={{
                      width: '100%',
                      padding: '7px 10px',
                      borderRadius: 8,
                      border: '1px solid #E2E8F0',
                      backgroundColor: '#F1F5F9',
                      fontSize: 13,
                      color: '#94A3B8',
                      cursor: 'not-allowed',
                      boxSizing: 'border-box',
                    }}
                    title={isEpic ? "Epic tính Story Points cuộn từ các Story con (Roll-up)" : "Sub-task không tính Story Points trực tiếp (ước lượng bằng Thời gian)"}
                  />
                  <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>
                    {isEpic ? 'Epic tự động tính điểm từ các Story con' : 'Sub-task không tính Story Points trực tiếp'}
                  </div>
                </div>
              ) : (
                <div>
                  <input
                    type="number"
                    step="0.5"
                    min="0"
                    max="100"
                    placeholder="VD: 5.0"
                    value={storyPoints}
                    onChange={(e) => {
                      setStoryPoints(e.target.value);
                      if (errors.storyPoints) setErrors((prev) => ({ ...prev, storyPoints: undefined }));
                    }}
                    style={{
                      width: '100%',
                      padding: '7px 10px',
                      borderRadius: 8,
                      border: `1px solid ${errors.storyPoints ? '#EF4444' : '#CBD5E1'}`,
                      fontSize: 13,
                      color: '#0F172A',
                      outline: 'none',
                      boxSizing: 'border-box',
                    }}
                  />
                  {errors.storyPoints && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#EF4444', fontSize: 11, marginTop: 4 }}>
                      <AlertCircle size={12} />
                      <span>{errors.storyPoints}</span>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Assignee */}
            <div>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                Người thực hiện
              </label>
              <MemberSelect
                members={members}
                value={assigneeId}
                onChange={(newId) => setAssigneeId(newId)}
                placeholder="Chưa gán (Unassigned)"
              />
            </div>
          </div>

          {/* Description */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Mô tả chi tiết (Markdown)
            </label>
            <textarea
              rows={3}
              placeholder="Mô tả tiêu chí nghiệm thu (Acceptance Criteria), các bước tái hiện..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
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
              <Sparkles size={15} />
              <span>{submitting ? 'Đang tạo...' : 'Tạo mới'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
