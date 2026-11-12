import { useEffect, useState } from 'react';
import {
  X,
  LayoutTemplate,
  AlertTriangle,
  XCircle,
  CheckCircle2,
  ChevronRight,
  ChevronDown,
  ArrowRight,
  Users2,
  CalendarRange,
  Check,
  Pencil,
  Trash2,
  RotateCcw,
  Sparkles,
} from 'lucide-react';
import { templateApi } from '../../api.js';
import { STATUS_TOKENS, PRIORITY_TOKENS } from '../../constants/jiraTokens.js';
import { categoryToken, PROJECT_TYPE_LABELS, formatOffset, formatIntegrityMessage } from '../../constants/templateConstants.js';
import { useModalBackdrop } from '../../hooks/useModalBackdrop.js';
import { getTypeDisplayName, toDisplayHierarchyLevel } from '../../constants/workConstants.js';

const TABS = [
  { id: 'OVERVIEW', label: 'Tổng quan' },
  { id: 'WORKFLOW', label: 'Trạng thái & Quy trình' },
  { id: 'PLAN', label: 'Chu kỳ & Công việc' },
  { id: 'ROLES', label: 'Phân quyền' },
];

const EMPTY_CUSTOMIZATIONS = {
  renamedStatuses: {},
  renamedTypes: {},
  excludedSprintIds: [],
  renamedSprints: {},
  excludedWorkItemIds: [],
  renamedWorkItems: {},
};

/**
 * Modal xem trước toàn bộ bản thiết kế của một dự án mẫu và cho phép Tùy chỉnh nhanh (Quick Customize)
 * các thành phần trước khi tạo dự án thật:
 * - Đổi tên Trạng thái, Loại công việc, Sprint, Công việc
 * - Xóa/Bỏ qua Sprint hoặc Công việc không cần thiết (kèm tính năng khôi phục Undo)
 */
export default function TemplatePreviewModal({
  templateId,
  isOpen,
  onClose,
  onUse,
  initialCustomizations = null,
}) {
  const backdropProps = useModalBackdrop(onClose);
  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState('OVERVIEW');

  const [customizations, setCustomizations] = useState(EMPTY_CUSTOMIZATIONS);

  useEffect(() => {
    if (!isOpen || !templateId) return;
    let cancelled = false;
    setLoading(true);
    setPreview(null);
    setTab('OVERVIEW');

    // Nạp lại cấu hình tùy chỉnh ban đầu nếu có (khi người dùng quay lại từ Form)
    if (initialCustomizations) {
      setCustomizations({
        renamedStatuses: { ...(initialCustomizations.renamedStatuses || {}) },
        renamedTypes: { ...(initialCustomizations.renamedTypes || {}) },
        excludedSprintIds: [...(initialCustomizations.excludedSprintIds || [])],
        renamedSprints: { ...(initialCustomizations.renamedSprints || {}) },
        excludedWorkItemIds: [...(initialCustomizations.excludedWorkItemIds || [])],
        renamedWorkItems: { ...(initialCustomizations.renamedWorkItems || {}) },
      });
    } else {
      setCustomizations(EMPTY_CUSTOMIZATIONS);
    }

    templateApi
      .preview(templateId)
      .then((res) => {
        if (!cancelled) setPreview(res?.data || null);
      })
      .catch(() => {
        if (!cancelled) onClose();
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, templateId]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!isOpen) return null;

  const errors = preview?.integrity?.errors || [];
  const warnings = preview?.integrity?.warnings || [];
  const cat = categoryToken(preview?.templateCategory);

  // Đếm tổng số lượng sửa đổi
  const countChanges =
    Object.keys(customizations.renamedStatuses).length +
    Object.keys(customizations.renamedTypes).length +
    customizations.excludedSprintIds.length +
    Object.keys(customizations.renamedSprints).length +
    customizations.excludedWorkItemIds.length +
    Object.keys(customizations.renamedWorkItems).length;

  const handleResetCustomizations = () => {
    setCustomizations(EMPTY_CUSTOMIZATIONS);
  };

  const handleRenameStatus = (statusId, originalName, newName) => {
    setCustomizations((prev) => {
      const next = { ...prev.renamedStatuses };
      if (!newName || newName.trim() === originalName) {
        delete next[statusId];
      } else {
        next[statusId] = newName.trim();
      }
      return { ...prev, renamedStatuses: next };
    });
  };

  const handleRenameType = (typeId, originalName, newName) => {
    setCustomizations((prev) => {
      const next = { ...prev.renamedTypes };
      if (!newName || newName.trim() === originalName) {
        delete next[typeId];
      } else {
        next[typeId] = newName.trim();
      }
      return { ...prev, renamedTypes: next };
    });
  };

  const handleRenameSprint = (sprintId, originalName, newName) => {
    setCustomizations((prev) => {
      const next = { ...prev.renamedSprints };
      if (!newName || newName.trim() === originalName) {
        delete next[sprintId];
      } else {
        next[sprintId] = newName.trim();
      }
      return { ...prev, renamedSprints: next };
    });
  };

  const handleToggleSprint = (sprintId) => {
    setCustomizations((prev) => {
      const exists = prev.excludedSprintIds.includes(sprintId);
      const next = exists
        ? prev.excludedSprintIds.filter((id) => id !== sprintId)
        : [...prev.excludedSprintIds, sprintId];
      return { ...prev, excludedSprintIds: next };
    });
  };

  const handleRenameItem = (itemId, originalSummary, newSummary) => {
    setCustomizations((prev) => {
      const next = { ...prev.renamedWorkItems };
      if (!newSummary || newSummary.trim() === originalSummary) {
        delete next[itemId];
      } else {
        next[itemId] = newSummary.trim();
      }
      return { ...prev, renamedWorkItems: next };
    });
  };

  const handleToggleItem = (itemId) => {
    setCustomizations((prev) => {
      const exists = prev.excludedWorkItemIds.includes(itemId);
      const next = exists
        ? prev.excludedWorkItemIds.filter((id) => id !== itemId)
        : [...prev.excludedWorkItemIds, itemId];
      return { ...prev, excludedWorkItemIds: next };
    });
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.55)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10000,
        padding: 16,
      }}
      {...backdropProps}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          boxShadow: '0 25px 50px -12px rgba(0,0,0,0.3)',
          width: '100%',
          maxWidth: 960,
          height: '88vh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{ padding: '18px 24px 0', borderBottom: '1px solid #E2E8F0' }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                backgroundColor: cat.bg,
                color: cat.color,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <LayoutTemplate size={20} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <h2 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                  {preview?.name || (loading ? 'Đang tải mẫu...' : 'Dự án mẫu')}
                </h2>
                {preview && (
                  <>
                    <span style={badge(cat.color, cat.bg)}>{cat.label}</span>
                    <span style={badge('#475569', '#F1F5F9')}>
                      {PROJECT_TYPE_LABELS[preview.projectType] || preview.projectType}
                    </span>
                    <span style={{ fontSize: 12, color: '#64748B', display: 'flex', alignItems: 'center', gap: 4 }}>
                      <Users2 size={13} /> {preview.usageCount} dự án đã dùng
                    </span>
                  </>
                )}
              </div>
              <p style={{ fontSize: 12.5, color: '#64748B', margin: '4px 0 0' }}>
                {preview?.description || 'Xem trước và tinh chỉnh cấu hình sẽ được sao chép vào dự án mới.'}
              </p>
            </div>
            <button type="button" onClick={onClose} style={iconBtn} title="Đóng">
              <X size={18} />
            </button>
          </div>

          <div style={{ display: 'flex', gap: 2, marginTop: 14, overflowX: 'auto' }}>
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                style={{
                  padding: '9px 14px',
                  border: 'none',
                  borderBottom: `2px solid ${tab === t.id ? '#2563EB' : 'transparent'}`,
                  background: 'none',
                  color: tab === t.id ? '#2563EB' : '#475569',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                  fontFamily: 'inherit',
                }}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: 24, backgroundColor: '#FAFBFC' }}>
          {loading || !preview ? (
            <div style={{ color: '#64748B', fontSize: 13 }}>Đang tải bản thiết kế của mẫu...</div>
          ) : (
            <>
              {/* Banner hiển thị số lượng thay đổi tùy chỉnh */}
              {countChanges > 0 && (
                <div
                  style={{
                    marginBottom: 16,
                    padding: '10px 14px',
                    borderRadius: 8,
                    backgroundColor: '#EFF6FF',
                    border: '1px solid #BFDBFE',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    fontSize: 12.5,
                    color: '#1E40AF',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <Sparkles size={16} color="#2563EB" />
                    <span>
                      Đã tùy chỉnh: <strong>{countChanges} thay đổi</strong> (Đổi tên / Bỏ bớt). Các lựa chọn này sẽ được áp dụng tự động khi bạn tạo dự án.
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={handleResetCustomizations}
                    style={{
                      border: '1px solid #93C5FD',
                      backgroundColor: '#FFFFFF',
                      color: '#1D4ED8',
                      borderRadius: 6,
                      padding: '4px 8px',
                      fontSize: 11.5,
                      fontWeight: 600,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                    }}
                  >
                    <RotateCcw size={12} /> Đặt lại mặc định
                  </button>
                </div>
              )}

              <IntegrityBanner errors={errors} warnings={warnings} />
              {tab === 'OVERVIEW' && <OverviewTab p={preview} />}
              {tab === 'WORKFLOW' && (
                <WorkflowTab
                  p={preview}
                  customizations={customizations}
                  onRenameStatus={handleRenameStatus}
                  onRenameType={handleRenameType}
                />
              )}
              {tab === 'PLAN' && (
                <PlanTab
                  p={preview}
                  customizations={customizations}
                  onRenameSprint={handleRenameSprint}
                  onToggleSprint={handleToggleSprint}
                  onRenameItem={handleRenameItem}
                  onToggleItem={handleToggleItem}
                />
              )}
              {tab === 'ROLES' && <RolesTab p={preview} />}
            </>
          )}
        </div>

        {/* Footer */}
        <div
          style={{
            padding: '14px 24px',
            borderTop: '1px solid #E2E8F0',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
          }}
        >
          <span style={{ fontSize: 12, color: '#64748B', flex: 1 }}>
            Thành viên, bình luận, tệp đính kèm và lịch sử của mẫu không được sao chép. Các chu kỳ được đặt lại thành
            “Dự kiến”, công việc về trạng thái khởi tạo.
          </span>
          <button type="button" onClick={onClose} style={secondaryBtn}>
            Đóng
          </button>
          <button
            type="button"
            disabled={!preview || errors.length > 0}
            onClick={() => onUse(preview, customizations)}
            style={{
              ...primaryBtn,
              backgroundColor: !preview || errors.length > 0 ? '#93C5FD' : '#2563EB',
              cursor: !preview || errors.length > 0 ? 'not-allowed' : 'pointer',
            }}
            title={errors.length > 0 ? 'Mẫu đang có lỗi toàn vẹn, không thể sử dụng' : undefined}
          >
            <Check size={15} /> Dùng mẫu này {countChanges > 0 ? `(${countChanges} tùy chỉnh)` : ''}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Inline Editable Text Component ──────────────────────────────────────────

function EditableText({
  value,
  originalValue,
  onSave,
  onReset,
  placeholder = 'Nhập tên...',
  style = {},
  textStyle = {},
}) {
  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState(value);

  useEffect(() => {
    setDraft(value);
  }, [value]);

  const handleSave = () => {
    const trimmed = draft.trim();
    if (trimmed && trimmed !== value) {
      onSave(trimmed);
    }
    setIsEditing(false);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSave();
    } else if (e.key === 'Escape') {
      setDraft(value);
      setIsEditing(false);
    }
  };

  const isCustomized = originalValue && value !== originalValue;

  if (isEditing) {
    return (
      <span
        style={{ display: 'inline-flex', alignItems: 'center', gap: 4, ...style }}
        onClick={(e) => e.stopPropagation()}
      >
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={handleKeyDown}
          autoFocus
          placeholder={placeholder}
          style={{
            fontSize: 12,
            padding: '2px 6px',
            borderRadius: 4,
            border: '1px solid #2563EB',
            outline: 'none',
            minWidth: 100,
            maxWidth: 260,
            fontFamily: 'inherit',
          }}
        />
        <button
          type="button"
          onClick={handleSave}
          title="Lưu thay đổi"
          style={{
            border: 'none',
            background: '#2563EB',
            color: '#fff',
            borderRadius: 4,
            padding: '3px 5px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <Check size={12} />
        </button>
        <button
          type="button"
          onClick={() => {
            setDraft(value);
            setIsEditing(false);
          }}
          title="Hủy"
          style={{
            border: '1px solid #CBD5E1',
            background: '#F8FAFC',
            color: '#64748B',
            borderRadius: 4,
            padding: '3px 5px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <X size={12} />
        </button>
      </span>
    );
  }

  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, ...style }}>
      <span
        style={{
          ...textStyle,
          color: isCustomized ? '#1D4ED8' : textStyle.color || '#0F172A',
          fontWeight: isCustomized ? 700 : textStyle.fontWeight,
        }}
      >
        {value}
      </span>
      {isCustomized && (
        <span
          title={`Tên gốc trong mẫu: ${originalValue}`}
          style={{
            fontSize: 10,
            color: '#1D4ED8',
            backgroundColor: '#EFF6FF',
            padding: '1px 5px',
            borderRadius: 3,
            fontWeight: 500,
            border: '1px solid #BFDBFE',
          }}
        >
          (đã sửa)
        </span>
      )}
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setIsEditing(true);
        }}
        title="Nhấp để sửa tên"
        style={{
          border: 'none',
          background: 'none',
          color: isCustomized ? '#2563EB' : '#94A3B8',
          cursor: 'pointer',
          padding: 2,
          borderRadius: 4,
          display: 'inline-flex',
          alignItems: 'center',
        }}
      >
        <Pencil size={11} />
      </button>
      {isCustomized && onReset && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onReset();
          }}
          title={`Khôi phục về tên gốc: ${originalValue}`}
          style={{
            border: 'none',
            background: 'none',
            color: '#64748B',
            cursor: 'pointer',
            padding: 2,
            display: 'inline-flex',
            alignItems: 'center',
          }}
        >
          <RotateCcw size={11} />
        </button>
      )}
    </span>
  );
}

// ─── Kiểm tra toàn vẹn ──────────────────────────────────────────────────────

function IntegrityBanner({ errors, warnings }) {
  const [open, setOpen] = useState(false);
  if (errors.length === 0 && warnings.length === 0) {
    return (
      <div style={{ ...banner('#ECFDF5', '#A7F3D0', '#047857'), marginBottom: 16 }}>
        <CheckCircle2 size={15} /> Mẫu hợp lệ: trạng thái, quy trình, bảng làm việc, chu kỳ và thời gian đều nhất quán.
      </div>
    );
  }
  return (
    <div style={{ marginBottom: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
      {errors.length > 0 && (
        <div style={{ ...banner('#FEF2F2', '#FECACA', '#B91C1C'), alignItems: 'flex-start' }}>
          <XCircle size={15} style={{ marginTop: 1, flexShrink: 0 }} />
          <div>
            <strong>Mẫu chưa thể sử dụng ({errors.length} lỗi):</strong>
            <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
              {errors.map((e, i) => (
                <li key={i}>{formatIntegrityMessage(e)}</li>
              ))}
            </ul>
          </div>
        </div>
      )}
      {warnings.length > 0 && (
        <div style={{ ...banner('#FFFBEB', '#FDE68A', '#92400E'), flexDirection: 'column', alignItems: 'stretch' }}>
          <button
            type="button"
            onClick={() => setOpen(!open)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              background: 'none',
              border: 'none',
              color: 'inherit',
              padding: 0,
              cursor: 'pointer',
              fontSize: 12.5,
              fontFamily: 'inherit',
              fontWeight: 600,
            }}
          >
            <AlertTriangle size={15} /> {warnings.length} lưu ý khi dùng mẫu
            {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>
          {open && (
            <ul style={{ margin: '6px 0 0', paddingLeft: 26 }}>
              {warnings.map((w, i) => (
                <li key={i}>{formatIntegrityMessage(w)}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

// ─── Tab: Tổng quan ─────────────────────────────────────────────────────────

function OverviewTab({ p }) {
  const s = p.stats || {};
  const cards = [
    ['Trạng thái', s.statusCount],
    ['Loại công việc', s.workItemTypeCount],
    ['Quy trình', s.workflowCount],
    ['Chuyển bước', s.transitionCount],
    ['Chu kỳ', s.sprintCount],
    ['Công việc', s.workItemCount],
    ['Hạng mục lớn', s.epicCount],
    ['Điểm ước lượng', s.totalStoryPoints ?? 0],
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 10 }}>
        {cards.map(([label, value]) => (
          <div key={label} style={card}>
            <div style={{ fontSize: 11, color: '#64748B', fontWeight: 600, textTransform: 'uppercase' }}>{label}</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: '#0F172A', marginTop: 2 }}>{value ?? 0}</div>
          </div>
        ))}
      </div>

      <Section title="Dòng thời gian">
        <TimelineChart p={p} />
      </Section>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
        <Section title={`Nhãn (${p.labels?.length || 0})`}>
          <ChipList items={(p.labels || []).map((l) => ({ text: l.name, color: l.colorHex }))} empty="Không có nhãn" />
        </Section>
        <Section title={`Component (${p.components?.length || 0})`}>
          <ChipList items={(p.components || []).map((c) => ({ text: c.name }))} empty="Không có component" />
        </Section>
        <Section title={`Version (${p.versions?.length || 0})`}>
          <ChipList
            items={(p.versions || []).map((v) => ({
              text: v.releaseOffsetDays != null ? `${v.name} · ${formatOffset(v.releaseOffsetDays)}` : v.name,
            }))}
            empty="Không có version"
          />
        </Section>
        <Section title={`Trường tùy chỉnh (${p.customFields?.length || 0})`}>
          <ChipList
            items={(p.customFields || []).map((f) => ({ text: `${f.name} (${f.fieldType})` }))}
            empty="Không có trường tùy chỉnh"
          />
        </Section>
      </div>
    </div>
  );
}

function TimelineChart({ p }) {
  const span = p.timeline?.spanDays;
  const sprints = (p.sprints || []).filter((s) => s.startOffsetDays != null && s.durationDays != null);
  if (!span) {
    return <div style={{ fontSize: 12.5, color: '#64748B' }}>Mẫu không có mốc thời gian - dự án mới sẽ dùng ngày bạn nhập.</div>;
  }
  return (
    <div>
      <div style={{ fontSize: 12.5, color: '#475569', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
        <CalendarRange size={14} /> Tổng thời lượng <strong>{span} ngày</strong>. Khi tạo dự án, mọi mốc được dời theo
        ngày bắt đầu bạn chọn và giữ nguyên khoảng cách tương đối.
      </div>
      {sprints.length === 0 ? (
        <div style={{ fontSize: 12.5, color: '#94A3B8' }}>Mẫu không có chu kỳ nào được gắn mốc ngày.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {sprints.map((s) => (
            <div key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{ width: 110, fontSize: 12, color: '#334155', fontWeight: 600, flexShrink: 0 }}>{s.name}</div>
              <div style={{ position: 'relative', flex: 1, height: 18, backgroundColor: '#F1F5F9', borderRadius: 4 }}>
                <div
                  style={{
                    position: 'absolute',
                    left: `${(s.startOffsetDays / span) * 100}%`,
                    width: `${Math.max((s.durationDays / span) * 100, 2)}%`,
                    top: 0,
                    bottom: 0,
                    backgroundColor: '#60A5FA',
                    borderRadius: 4,
                  }}
                  title={`${formatOffset(s.startOffsetDays)} → ${formatOffset(s.startOffsetDays + s.durationDays)}`}
                />
              </div>
              <div style={{ width: 90, fontSize: 11, color: '#64748B', flexShrink: 0 }}>
                {formatOffset(s.startOffsetDays)} · {s.durationDays}d
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Tab: Trạng thái & Quy trình ────────────────────────────────────────────

function WorkflowTab({ p, customizations, onRenameStatus, onRenameType }) {
  const renamedStatuses = customizations?.renamedStatuses || {};
  const renamedTypes = customizations?.renamedTypes || {};

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Section
        title={`Trạng thái (${p.statuses?.length || 0})`}
        subtitle="Nhấp vào biểu tượng bút chì để sửa đổi tên trạng thái hiển thị theo ý bạn (quy trình luân chuyển vẫn giữ nguyên)."
      >
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          {(p.statuses || []).map((s) => {
            const currentName = renamedStatuses[s.id] || s.name;
            return (
              <div
                key={s.id}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  backgroundColor: '#FFFFFF',
                  padding: '4px 8px',
                  borderRadius: 6,
                  border: '1px solid #E2E8F0',
                }}
              >
                <StatusPill name={currentName} category={s.category} />
                <EditableText
                  value={currentName}
                  originalValue={s.name}
                  onSave={(val) => onRenameStatus(s.id, s.name, val)}
                  onReset={() => onRenameStatus(s.id, s.name, s.name)}
                  textStyle={{ display: 'none' }} // Đã có StatusPill hiển thị chữ
                />
              </div>
            );
          })}
        </div>
      </Section>

      <Section
        title={`Loại công việc (${p.workItemTypes?.length || 0})`}
        subtitle="Nhấp vào biểu tượng bút chì để đổi tên phân cấp công việc (VD: Hạng mục lớn → Dự án lớn, Công việc → Tác vụ). Để thêm loại mới, bạn có thể tạo dự án xong rồi vào Cài đặt dự án."
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {(p.workItemTypes || []).map((t) => {
            const currentName = renamedTypes[t.id] || getTypeDisplayName(t.name || t.code);
            return (
              <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                <span style={{ width: 10, height: 10, borderRadius: 3, backgroundColor: t.colorHex || '#94A3B8' }} />
                <div style={{ paddingLeft: t.hierarchyLevel === 0 ? 14 : t.hierarchyLevel < 0 ? 28 : 0 }}>
                  <EditableText
                    value={currentName}
                    originalValue={getTypeDisplayName(t.name || t.code)}
                    onSave={(val) => onRenameType(t.id, t.name, val)}
                    onReset={() => onRenameType(t.id, t.name, getTypeDisplayName(t.name || t.code))}
                    textStyle={{ fontWeight: 600, color: '#0F172A' }}
                  />
                </div>
                <span style={{ fontSize: 11, color: '#94A3B8' }}>
                  Cấp {toDisplayHierarchyLevel(t.hierarchyLevel)}
                </span>
              </div>
            );
          })}
        </div>
      </Section>

      {(p.workflows || []).map((w) => (
        <Section
          key={w.id}
          title={`Quy trình: ${w.name}${w.isDefault ? ' (mặc định)' : ''}`}
          subtitle={w.appliesToTypes?.length ? `Áp dụng cho: ${w.appliesToTypes.map(getTypeDisplayName).join(', ')}` : 'Chưa gắn loại công việc nào'}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {(w.transitions || []).map((t, i) => {
              const from = (p.statuses || []).find((s) => s.name === t.fromStatus);
              const to = (p.statuses || []).find((s) => s.name === t.toStatus);
              const fromName = from && renamedStatuses[from.id] ? renamedStatuses[from.id] : t.fromStatus;
              const toName = to && renamedStatuses[to.id] ? renamedStatuses[to.id] : t.toStatus;

              return (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5 }}>
                  {fromName ? (
                    <StatusPill name={fromName} category={from?.category} />
                  ) : (
                    <span style={{ fontSize: 11, color: '#64748B', fontStyle: 'italic' }}>(Tạo mới)</span>
                  )}
                  <ArrowRight size={13} color="#94A3B8" />
                  <StatusPill name={toName} category={to?.category} />
                  <span style={{ color: '#64748B' }}>“{t.name}”</span>
                </div>
              );
            })}
          </div>
        </Section>
      ))}
    </div>
  );
}
// ─── Helper tính tập ID công việc bị loại trừ theo cây cha-con ───────────────

function getExcludedItemIdsSet(excludedList, workItemsTree) {
  const set = new Set(excludedList || []);
  if (!workItemsTree || !workItemsTree.length) return set;

  function traverse(node, parentExcluded) {
    const isExcluded = parentExcluded || set.has(node.id);
    if (isExcluded) {
      set.add(node.id);
    }
    if (node.children?.length) {
      node.children.forEach((c) => traverse(c, isExcluded));
    }
  }

  workItemsTree.forEach((root) => traverse(root, set.has(root.id)));
  return set;
}

// ─── Tab: Sprint & Công việc ────────────────────────────────────────────────

function PlanTab({
  p,
  customizations,
  onRenameSprint,
  onToggleSprint,
  onRenameItem,
  onToggleItem,
}) {
  const s = p.stats || {};
  const excludedSprintIds = customizations?.excludedSprintIds || [];
  const renamedSprints = customizations?.renamedSprints || {};
  const excludedItemIds = customizations?.excludedWorkItemIds || [];
  const renamedWorkItems = customizations?.renamedWorkItems || {};

  // Tính tập hợp tất cả các work items bị loại trừ (gồm chính nó và các con cháu)
  const fullExcludedItemSet = getExcludedItemIdsSet(excludedItemIds, p.workItems);

  // Đếm số công việc còn lại (chưa bị loại trừ)
  const activeItemCount = (p.workItems || []).reduce((acc, root) => {
    function countActive(node) {
      let cnt = fullExcludedItemSet.has(node.id) ? 0 : 1;
      if (node.children) {
        node.children.forEach((c) => {
          cnt += countActive(c);
        });
      }
      return cnt;
    }
    return acc + countActive(root);
  }, 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Section
        title={`Chu kỳ làm việc (${p.sprints?.length || 0})`}
        subtitle="Có thể đổi tên hoặc bấm biểu tượng Thùng rác để bỏ qua chu kỳ không cần (công việc thuộc chu kỳ đó sẽ tự động chuyển về Kho việc)."
      >
        {p.sprints?.length ? (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5 }}>
            <thead>
              <tr style={{ color: '#64748B', textAlign: 'left' }}>
                <th style={th}>Tên chu kỳ</th>
                <th style={th}>Mục tiêu</th>
                <th style={th}>Bắt đầu</th>
                <th style={th}>Độ dài</th>
                <th style={th}>Công việc</th>
                <th style={th}>Khối lượng (Điểm)</th>
                <th style={{ ...th, textAlign: 'right' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {p.sprints.map((sp) => {
                const isExcluded = excludedSprintIds.includes(sp.id);
                const currentName = renamedSprints[sp.id] || sp.name;

                return (
                  <tr
                    key={sp.id}
                    style={{
                      borderTop: '1px solid #E2E8F0',
                      opacity: isExcluded ? 0.5 : 1,
                      backgroundColor: isExcluded ? '#F8FAFC' : 'transparent',
                    }}
                  >
                    <td style={{ ...td, fontWeight: 600 }}>
                      <EditableText
                        value={currentName}
                        originalValue={sp.name}
                        onSave={(val) => onRenameSprint(sp.id, sp.name, val)}
                        onReset={() => onRenameSprint(sp.id, sp.name, sp.name)}
                        textStyle={{
                          textDecoration: isExcluded ? 'line-through' : 'none',
                          color: isExcluded ? '#94A3B8' : '#0F172A',
                        }}
                      />
                      {isExcluded && (
                        <span style={{ marginLeft: 6, fontSize: 10, color: '#DC2626', fontWeight: 600 }}>
                          (Bỏ qua)
                        </span>
                      )}
                    </td>
                    <td style={{ ...td, color: '#64748B' }}>{sp.goal || '—'}</td>
                    <td style={td}>{formatOffset(sp.startOffsetDays)}</td>
                    <td style={td}>{sp.durationDays != null ? `${sp.durationDays} ngày` : '—'}</td>
                    <td style={td}>{sp.itemCount}</td>
                    <td style={td}>{sp.storyPoints ?? 0}</td>
                    <td style={{ ...td, textAlign: 'right' }}>
                      <button
                        type="button"
                        onClick={() => onToggleSprint(sp.id)}
                        title={isExcluded ? 'Khôi phục chu kỳ này' : 'Bỏ qua chu kỳ này'}
                        style={{
                          border: 'none',
                          background: isExcluded ? '#EFF6FF' : '#FEF2F2',
                          color: isExcluded ? '#2563EB' : '#DC2626',
                          cursor: 'pointer',
                          padding: '4px 8px',
                          borderRadius: 6,
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 4,
                          fontSize: 11.5,
                          fontWeight: 600,
                        }}
                      >
                        {isExcluded ? (
                          <>
                            <RotateCcw size={12} /> Khôi phục
                          </>
                        ) : (
                          <>
                            <Trash2 size={12} /> Bỏ qua
                          </>
                        )}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        ) : (
          <div style={{ fontSize: 12.5, color: '#94A3B8' }}>Mẫu không có chu kỳ làm việc.</div>
        )}
      </Section>

      <Section
        title={`Cây công việc (${activeItemCount}/${s.workItemCount || 0})`}
        subtitle="Có thể sửa tên tiêu đề tóm tắt hoặc bấm biểu tượng Thùng rác để bỏ qua công việc không dùng. Nếu xóa việc cha, các việc con sẽ tự động được bỏ qua."
      >
        {p.workItems?.length ? (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {p.workItems.map((n) => (
              <TreeRow
                key={n.id}
                node={n}
                depth={0}
                customizations={customizations}
                fullExcludedItemSet={fullExcludedItemSet}
                onRenameItem={onRenameItem}
                onToggleItem={onToggleItem}
              />
            ))}
          </div>
        ) : (
          <div style={{ fontSize: 12.5, color: '#94A3B8' }}>Mẫu không có công việc.</div>
        )}
      </Section>
    </div>
  );
}

function TreeRow({
  node,
  depth,
  customizations,
  fullExcludedItemSet,
  onRenameItem,
  onToggleItem,
  parentExcluded = false,
}) {
  const [open, setOpen] = useState(depth === 0);
  const hasChildren = node.children?.length > 0;
  const pr = PRIORITY_TOKENS[node.priority];

  const excludedItemIds = customizations?.excludedWorkItemIds || [];
  const renamedWorkItems = customizations?.renamedWorkItems || {};

  const isExplicitlyExcluded = excludedItemIds.includes(node.id);
  const isExcluded = fullExcludedItemSet.has(node.id);
  const isExcludedByParent = isExcluded && !isExplicitlyExcluded;

  const currentSummary = renamedWorkItems[node.id] || node.summary;

  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '6px 4px',
          paddingLeft: 4 + depth * 22,
          borderBottom: '1px solid #F1F5F9',
          fontSize: 12.5,
          opacity: isExcluded ? 0.45 : 1,
          backgroundColor: isExcluded ? '#F8FAFC' : 'transparent',
          transition: 'opacity 0.15s',
        }}
      >
        <button
          type="button"
          onClick={() => setOpen(!open)}
          style={{ ...iconBtn, padding: 0, visibility: hasChildren ? 'visible' : 'hidden' }}
        >
          {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        </button>
        <span style={{ width: 9, height: 9, borderRadius: 2, backgroundColor: node.typeColor || '#94A3B8', flexShrink: 0 }} />
        <span style={{ fontSize: 11, color: '#64748B', width: 85, flexShrink: 0 }}>{getTypeDisplayName(node.typeName || node.typeCode)}</span>

        <div style={{ flex: 1, minWidth: 0 }}>
          <EditableText
            value={currentSummary}
            originalValue={node.summary}
            onSave={(val) => onRenameItem(node.id, node.summary, val)}
            onReset={() => onRenameItem(node.id, node.summary, node.summary)}
            textStyle={{
              textDecoration: isExcluded ? 'line-through' : 'none',
              color: isExcluded ? '#94A3B8' : '#0F172A',
            }}
          />
        </div>

        {isExcluded && (
          <span style={{ fontSize: 10, color: '#DC2626', fontWeight: 600, flexShrink: 0 }}>
            {isExcludedByParent ? '(Bỏ qua theo cha)' : '(Bỏ qua)'}
          </span>
        )}

        {node.sprintName && <span style={badge('#1D4ED8', '#EFF6FF')}>{node.sprintName}</span>}
        {pr && <span style={{ fontSize: 11, color: pr.color, fontWeight: 600 }}>{pr.label}</span>}
        {node.storyPoints != null && (
          <span style={{ fontSize: 11, backgroundColor: '#F1F5F9', borderRadius: 999, padding: '0 7px' }}>
            {node.storyPoints}
          </span>
        )}

        {/* Nút xóa/khôi phục */}
        {!isExcludedByParent ? (
          <button
            type="button"
            onClick={() => onToggleItem(node.id)}
            title={isExplicitlyExcluded ? 'Khôi phục công việc này' : 'Bỏ qua công việc này'}
            style={{
              border: 'none',
              background: 'none',
              color: isExplicitlyExcluded ? '#2563EB' : '#94A3B8',
              cursor: 'pointer',
              padding: 4,
              borderRadius: 4,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            {isExplicitlyExcluded ? <RotateCcw size={13} /> : <Trash2 size={13} />}
          </button>
        ) : (
          <span style={{ width: 21 }} title="Bị bỏ qua do công việc cấp cha bị xóa" />
        )}
      </div>

      {open &&
        hasChildren &&
        node.children.map((c) => (
          <TreeRow
            key={c.id}
            node={c}
            depth={depth + 1}
            customizations={customizations}
            fullExcludedItemSet={fullExcludedItemSet}
            onRenameItem={onRenameItem}
            onToggleItem={onToggleItem}
            parentExcluded={isExcluded}
          />
        ))}
    </>
  );
}

// ─── Tab: Phân quyền ────────────────────────────────────────────────────────

function RolesTab({ p }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ fontSize: 12.5, color: '#64748B' }}>
        Ma trận quyền được sao chép nguyên vẹn. Người tạo dự án và Project Lead trở thành ADMINISTRATOR; thành viên
        nhóm được đồng bộ theo vai trò trong nhóm.
      </div>
      {(p.roles || []).map((r) => (
        <Section key={r.name} title={r.name} subtitle={r.description}>
          <ChipList items={(r.permissions || []).map((k) => ({ text: k }))} empty="Không có quyền" />
        </Section>
      ))}
    </div>
  );
}

// ─── Primitive Components & Styles ──────────────────────────────────────────

function Section({ title, subtitle, children }) {
  return (
    <div style={card}>
      <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>{title}</div>
      {subtitle && <div style={{ fontSize: 12, color: '#64748B', marginTop: 2 }}>{subtitle}</div>}
      <div style={{ marginTop: 10 }}>{children}</div>
    </div>
  );
}

function StatusPill({ name, category }) {
  const tk = STATUS_TOKENS[category] || STATUS_TOKENS.TODO;
  return (
    <span
      style={{
        fontSize: 11,
        fontWeight: 700,
        padding: '2px 8px',
        borderRadius: 4,
        color: tk.color,
        backgroundColor: tk.bg,
        textTransform: 'uppercase',
        whiteSpace: 'nowrap',
      }}
    >
      {name}
    </span>
  );
}

function ChipList({ items, empty }) {
  if (!items.length) return <div style={{ fontSize: 12.5, color: '#94A3B8' }}>{empty}</div>;
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
      {items.map((it, i) => (
        <span
          key={i}
          style={{
            fontSize: 11.5,
            padding: '2px 8px',
            borderRadius: 4,
            backgroundColor: '#F1F5F9',
            color: '#334155',
            borderLeft: it.color ? `3px solid ${it.color}` : undefined,
          }}
        >
          {it.text}
        </span>
      ))}
    </div>
  );
}

const card = {
  backgroundColor: '#FFFFFF',
  border: '1px solid #E2E8F0',
  borderRadius: 10,
  padding: 14,
};

const th = { padding: '6px 8px', fontWeight: 600, fontSize: 11.5 };
const td = { padding: '7px 8px', color: '#0F172A' };

const iconBtn = {
  background: 'none',
  border: 'none',
  color: '#94A3B8',
  cursor: 'pointer',
  padding: 6,
  borderRadius: 6,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

const secondaryBtn = {
  padding: '8px 16px',
  borderRadius: 8,
  border: '1px solid #CBD5E1',
  backgroundColor: '#FFFFFF',
  color: '#475569',
  fontSize: 13,
  fontWeight: 600,
  cursor: 'pointer',
  fontFamily: 'inherit',
};

const primaryBtn = {
  padding: '8px 18px',
  borderRadius: 8,
  border: 'none',
  color: '#FFFFFF',
  fontSize: 13,
  fontWeight: 600,
  display: 'flex',
  alignItems: 'center',
  gap: 6,
  fontFamily: 'inherit',
};

function badge(color, bg) {
  return {
    fontSize: 10.5,
    fontWeight: 700,
    padding: '2px 7px',
    borderRadius: 4,
    color,
    backgroundColor: bg,
    whiteSpace: 'nowrap',
  };
}

function banner(bg, border, color) {
  return {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '10px 12px',
    borderRadius: 8,
    backgroundColor: bg,
    border: `1px solid ${border}`,
    color,
    fontSize: 12.5,
  };
}
