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
  FileText,
  Zap,
  GitCommit,
  FileCheck,
  Sliders,
  AlertTriangle
} from 'lucide-react';
import toast from 'react-hot-toast';
import { workItemApi, projectApi } from '../../api.js';
import MemberSelect from '../common/MemberSelect.jsx';
import CustomSelect from '../common/CustomSelect.jsx';
import StoryPointsPicker from '../common/StoryPointsPicker.jsx';
import StoryPointsHelpTooltip from '../common/StoryPointsHelpTooltip.jsx';
import { PRIORITY_CONFIG } from '../../utils/priorityConfig.js';
import { getTypeDisplayName } from '../../constants/workConstants.js';
import { useModalBackdrop } from '../../hooks/useModalBackdrop.js';

const TYPE_CONFIGS = {
  EPIC: { label: 'Hạng mục lớn', icon: Zap, color: '#9333EA', bgColor: '#F5F3FF', level: 1 },
  STORY: { label: 'Yêu cầu', icon: Bookmark, color: '#10B981', bgColor: '#ECFDF5', level: 0 },
  TASK: { label: 'Công việc', icon: CheckSquare, color: '#3B82F6', bgColor: '#EFF6FF', level: 0 },
  BUG: { label: 'Sự cố', icon: AlertCircle, color: '#EF4444', bgColor: '#FEF2F2', level: 0 },
  SUBTASK: { label: 'Công việc con', icon: GitCommit, color: '#06B6D4', bgColor: '#ECFEFF', level: -1 },
};

const renderTypeIcon = (iconName, color = '#2563EB', size = 14) => {
  switch (iconName) {
    case 'Zap': return <Zap size={size} color={color} />;
    case 'Bookmark': return <Bookmark size={size} color={color} />;
    case 'CheckSquare': return <CheckSquare size={size} color={color} />;
    case 'Bug': return <AlertCircle size={size} color={color} />;
    case 'GitCommit': return <GitCommit size={size} color={color} />;
    case 'Layers': return <Layers size={size} color={color} />;
    case 'Sparkles': return <Sparkles size={size} color={color} />;
    case 'FileCheck': return <FileCheck size={size} color={color} />;
    case 'Sliders': return <Sliders size={size} color={color} />;
    case 'AlertTriangle': return <AlertTriangle size={size} color={color} />;
    default: return <CheckSquare size={size} color={color} />;
  }
};

const PRIORITIES = [
  { value: 'HIGHEST', label: 'Khẩn cấp', icon: ArrowUp, color: '#DE350B' },
  { value: 'HIGH', label: 'Cao', icon: ArrowUp, color: '#FF5630' },
  { value: 'MEDIUM', label: 'Trung bình', icon: Equal, color: '#FFAB00' },
  { value: 'LOW', label: 'Thấp', icon: ArrowDown, color: '#36B37E' },
  { value: 'LOWEST', label: 'Rất thấp', icon: ArrowDown, color: '#6B778C' },
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
  defaultSprintId = null,
  sprints = [],
  onItemCreated
}) {
  const backdropProps = useModalBackdrop(onClose);
  const [types, setTypes] = useState([]);
  const [sprintId, setSprintId] = useState(defaultSprintId);
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
      setSprintId(defaultSprintId || '');
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
  const isEpic = currentLevel === 1;
  const isSubtask = currentLevel === -1 || Boolean(selectedType?.isSubtask);

  // Phân loại types theo 3 cấp bậc
  const epicTypes = types.filter((t) => t.hierarchyLevel === 1 || t.code === 'EPIC');
  const subtaskTypes = types.filter((t) => t.hierarchyLevel === -1 || t.code === 'SUBTASK' || Boolean(t.isSubtask));
  const standardTypes = types.filter(
    (t) => !epicTypes.some((ep) => ep.id === t.id) && !subtaskTypes.some((sub) => sub.id === t.id)
  );

  const renderTypeButton = (t) => {
    const isSelected = selectedType?.id === t.id;
    const conf = TYPE_CONFIGS[t.code];
    const color = t.colorHex || conf?.color || '#2563EB';
    const bgColor = isSelected ? (conf?.bgColor || `${color}1A`) : '#FFFFFF';
    const iconName = t.iconName || (t.hierarchyLevel === 1 || t.code === 'EPIC' ? 'Zap' : t.hierarchyLevel === -1 || t.code === 'SUBTASK' || t.isSubtask ? 'GitCommit' : t.code === 'BUG' ? 'Bug' : 'CheckSquare');

    const isEp = t.hierarchyLevel === 1 || t.code === 'EPIC';
    const isSub = t.hierarchyLevel === -1 || t.code === 'SUBTASK' || Boolean(t.isSubtask);
    const levelBadgeText = isEp ? 'Cấp 1' : isSub ? 'Cấp -1' : 'Cấp 0';

    return (
      <button
        key={t.id}
        type="button"
        onClick={() => {
          setSelectedType(t);
          if (isEp) {
            setParentId('');
          } else if (isSub) {
            const currParent = existingItems.find((it) => it.id === parentId);
            if (currParent && currParent.hierarchyLevel === 1) {
              setParentId('');
            }
          }
          if (errors.typeId) setErrors((prev) => ({ ...prev, typeId: undefined }));
        }}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          padding: '6px 12px',
          borderRadius: 8,
          border: `1.5px solid ${isSelected ? color : '#E2E8F0'}`,
          backgroundColor: bgColor,
          color: isSelected ? color : '#334155',
          fontSize: 13,
          fontWeight: isSelected ? 700 : 500,
          cursor: 'pointer',
          transition: 'all 0.15s ease',
          boxShadow: isSelected ? `0 1px 3px ${color}33` : 'none',
        }}
      >
        {renderTypeIcon(iconName, isSelected ? color : '#64748B', 14)}
        <span>{getTypeDisplayName(t.name || conf?.label || t.code)}</span>
        <span
          style={{
            fontSize: 10,
            fontWeight: 700,
            padding: '1px 5px',
            borderRadius: 4,
            backgroundColor: isSelected ? `${color}20` : '#F1F5F9',
            color: isSelected ? color : '#64748B',
            border: `1px solid ${isSelected ? `${color}40` : '#E2E8F0'}`,
            letterSpacing: '0.02em',
          }}
        >
          {levelBadgeText}
        </span>
      </button>
    );
  };

  // Lọc danh sách cha hợp lệ theo 3-level hierarchy
  const availableParents = existingItems.filter((it) => {
    if (isSubtask) {
      // Subtask cần cha là Standard Issue (Level 0)
      return it.hierarchyLevel === 0;
    }
    if (!isEpic) {
      // Standard Issue (Level 0) có thể gắn vào Epic (Level 1)
      return it.hierarchyLevel === 1;
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
      errs.parentId = 'Công việc con bắt buộc phải có công việc cha';
    }
    if (isEpic && parentId) {
      errs.parentId = 'Hạng mục lớn không thể có công việc cha';
    }
    if (storyPoints && !isEpic) {
      const spNum = parseFloat(storyPoints);
      if (isNaN(spNum) || spNum < 0) {
        errs.storyPoints = 'Điểm ước lượng không thể âm';
      } else if (spNum > 999.99) {
        errs.storyPoints = 'Điểm ước lượng tối đa 999.99';
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
        storyPoints: isEpic ? null : (storyPoints ? parseFloat(storyPoints) : null),
        dueDate: dueDate || null,
        // Sub-task di theo Sprint cua cong viec cha, khong gan rieng.
        sprintId: isSubtask ? null : (sprintId || null),
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
      {...backdropProps}
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
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
              <label style={{ fontSize: 13, fontWeight: 600, color: '#334155', margin: 0 }}>
                Loại công việc <span style={{ color: '#EF4444' }}>*</span>
              </label>
              <span style={{ fontSize: 11, color: '#64748B' }}>
                Phân cấp 3 tầng (Cấp 1, Cấp 0, Cấp -1)
              </span>
            </div>

            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 12,
                backgroundColor: '#F8FAFC',
                padding: '12px 14px',
                borderRadius: 10,
                border: '1px solid #E2E8F0',
              }}
            >
              {/* Group Cấp 1: Epic */}
              {epicTypes.length > 0 && (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                    <span
                      style={{
                        fontSize: 10,
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        letterSpacing: '0.05em',
                        color: '#7C3AED',
                        backgroundColor: '#F5F3FF',
                        padding: '1px 7px',
                        borderRadius: 4,
                        border: '1px solid #DDD6FE',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                      }}
                    >
                      <Zap size={10} /> Cấp 1 • Hạng mục lớn
                    </span>
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>Cấp cao nhất, không có cha</span>
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {epicTypes.map(renderTypeButton)}
                  </div>
                </div>
              )}

              {/* Group Cấp 0: Standard */}
              {standardTypes.length > 0 && (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                    <span
                      style={{
                        fontSize: 10,
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        letterSpacing: '0.05em',
                        color: '#2563EB',
                        backgroundColor: '#EFF6FF',
                        padding: '1px 7px',
                        borderRadius: 4,
                        border: '1px solid #BFDBFE',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                      }}
                    >
                      <CheckSquare size={10} /> Cấp 0 • Tiêu chuẩn
                    </span>
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>Công việc chính, có thể gắn vào Hạng mục lớn</span>
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {standardTypes.map(renderTypeButton)}
                  </div>
                </div>
              )}

              {/* Group Cấp -1: Subtask */}
              {subtaskTypes.length > 0 && (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                    <span
                      style={{
                        fontSize: 10,
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        letterSpacing: '0.05em',
                        color: '#0891B2',
                        backgroundColor: '#ECFEFF',
                        padding: '1px 7px',
                        borderRadius: 4,
                        border: '1px solid #A5F3FC',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                      }}
                    >
                      <GitCommit size={10} /> Cấp -1 • Công việc con
                    </span>
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>Phụ thuộc, bắt buộc có cha Cấp 0</span>
                  </div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {subtaskTypes.map(renderTypeButton)}
                  </div>
                </div>
              )}
            </div>

            {/* Selected Type Context Notice */}
            {selectedType && (
              <div
                style={{
                  marginTop: 8,
                  padding: '7px 12px',
                  borderRadius: 6,
                  backgroundColor: isEpic ? '#FAF5FF' : isSubtask ? '#F0FDFA' : '#F0F9FF',
                  border: `1px solid ${isEpic ? '#E9D5FF' : isSubtask ? '#CCFBF1' : '#BAE6FD'}`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  fontSize: 12,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ color: '#64748B' }}>Đang chọn:</span>
                  <strong style={{ color: isEpic ? '#7C3AED' : isSubtask ? '#0D9488' : '#0284C7' }}>
                    {getTypeDisplayName(selectedType.name || selectedType.code)}
                  </strong>
                  <span
                    style={{
                      fontSize: 10,
                      fontWeight: 700,
                      padding: '1px 6px',
                      borderRadius: 4,
                      backgroundColor: isEpic ? '#7C3AED' : isSubtask ? '#0D9488' : '#0284C7',
                      color: '#FFFFFF',
                    }}
                  >
                    {isEpic ? 'Cấp 1 (Hạng mục lớn)' : isSubtask ? 'Cấp -1 (Công việc con)' : 'Cấp 0 (Tiêu chuẩn)'}
                  </span>
                </div>
                <span style={{ color: '#64748B', fontSize: 11, fontStyle: 'italic' }}>
                  {isEpic
                    ? 'Không có công việc cha'
                    : isSubtask
                    ? 'Bắt buộc chọn công việc cha (Cấp 0)'
                    : 'Tùy chọn gắn vào Hạng mục lớn cha'}
                </span>
              </div>
            )}

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
              placeholder="VD: Triển khai kế hoạch khảo sát, chuẩn bị báo cáo tiến độ..."
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
              Công việc cha {isSubtask && <span style={{ color: '#EF4444' }}>*</span>}
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
                ℹ️ Hạng mục lớn là cấp cao nhất (Cấp 1) và không thể trực thuộc công việc khác.
              </div>
            ) : (
              <CustomSelect
                value={parentId}
                onChange={(val) => {
                  setParentId(val);
                  if (errors.parentId) setErrors((prev) => ({ ...prev, parentId: undefined }));
                }}
                options={[
                  {
                    value: '',
                    label: isSubtask ? '-- Bắt buộc chọn công việc cha --' : '-- Không có (Độc lập / Kho việc) --',
                  },
                  ...availableParents.map((p) => ({
                    value: p.id,
                    label: `[${p.code}] ${p.summary} (${getTypeDisplayName(p.typeName || p.typeCode)})`,
                  })),
                ]}
                placeholder={isSubtask ? 'Chọn công việc cha...' : 'Không có (Độc lập)'}
              />
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
              Mức độ ưu tiên
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
                Hạn chót
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
                  Điểm ước lượng
                </label>
                <StoryPointsHelpTooltip align="center" />
              </div>
              {isEpic ? (
                <div>
                  <input
                    type="text"
                    disabled
                    value="Không áp dụng"
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
                    title="Hạng mục lớn tự động tính điểm từ các công việc con"
                  />
                  <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>
                    Hạng mục lớn tự động tính điểm từ các công việc con
                  </div>
                </div>
              ) : (
                <div style={{ marginTop: 2 }}>
                  <StoryPointsPicker
                    value={storyPoints}
                    onChange={(val) => {
                      setStoryPoints(val);
                      if (errors.storyPoints) setErrors((prev) => ({ ...prev, storyPoints: undefined }));
                    }}
                    align="left"
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
                placeholder="Chưa phân công"
              />
            </div>

            {/* Sprint - Sub-task luon di theo Sprint cua cong viec cha */}
            {sprints.length > 0 && (
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Chu kỳ
                </label>
                {isSubtask ? (
                  <div
                    title="Công việc con đi theo chu kỳ của công việc cha"
                    style={{
                      padding: '7px 10px', borderRadius: 8, border: '1px solid #CBD5E1',
                      fontSize: 13, color: '#94A3B8', backgroundColor: '#F8FAFC',
                    }}
                  >
                    Theo công việc cha
                  </div>
                ) : (
                  <CustomSelect
                    value={sprintId || ''}
                    onChange={(val) => setSprintId(val)}
                    options={[
                      { value: '', label: 'Kho việc chờ phân bổ' },
                      ...sprints
                        .filter((sp) => sp.state !== 'CLOSED')
                        .map((sp) => ({
                          value: sp.id,
                          label: sp.name,
                          badge: (
                            <span
                              style={{
                                fontSize: 10,
                                fontWeight: 600,
                                padding: '1px 6px',
                                borderRadius: 999,
                                backgroundColor: sp.state === 'ACTIVE' ? '#DCFCE7' : '#FEF3C7',
                                color: sp.state === 'ACTIVE' ? '#15803D' : '#B45309',
                              }}
                            >
                              {sp.state === 'ACTIVE' ? 'Đang chạy' : 'Kế hoạch'}
                            </span>
                          ),
                        })),
                    ]}
                    placeholder="Kho việc chờ phân bổ"
                  />
                )}
              </div>
            )}
          </div>

          {/* Description */}
          <div>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
              Mô tả chi tiết
            </label>
            <textarea
              rows={3}
              placeholder="Mô tả tiêu chí nghiệm thu, các bước thực hiện..."
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
