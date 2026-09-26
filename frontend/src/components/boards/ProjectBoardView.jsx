import React, { useState, useEffect } from 'react';
import {
  Plus,
  Bookmark,
  CheckSquare,
  CircleDot,
  GitCommitHorizontal,
  ArrowUp,
  ArrowDown,
  Equal,
  Sparkles,
  Layers,
  Search,
  Filter,
  CheckCircle2,
  Clock,
  User,
  MoreHorizontal,
  Lock,
  AlertTriangle,
  X,
  Zap,
  GitCommit
} from 'lucide-react';
import toast from 'react-hot-toast';
import { workItemApi, workflowApi, workItemTypeApi } from '../../api.js';
import { getPriorityConfig, PRIORITY_CONFIG } from '../../utils/priorityConfig.js';
import { getStatusDisplayName, getTypeDisplayName } from '../../constants/workConstants.js';

const TYPE_CONFIG = {
  EPIC: { label: 'Hạng mục lớn', color: '#6554C0', bgColor: '#F3F0FF', borderColor: '#DDD6FE' },
  STORY: { label: 'Câu chuyện', color: '#059669', bgColor: '#ECFDF5', borderColor: '#A7F3D0' },
  TASK: { label: 'Nhiệm vụ', color: '#0052CC', bgColor: '#EFF6FF', borderColor: '#BFDBFE' },
  BUG: { label: 'Lỗi phần mềm', color: '#E5493A', bgColor: '#FEF2F2', borderColor: '#FECACA' },
  SUBTASK: { label: 'Nhiệm vụ con', color: '#0284C7', bgColor: '#F0F9FF', borderColor: '#BAE6FD' },
  SUB_TASK: { label: 'Nhiệm vụ con', color: '#0284C7', bgColor: '#F0F9FF', borderColor: '#BAE6FD' },
};
const TYPE_ICONS = TYPE_CONFIG;

/**
 * Helper tính toán tiến độ công việc con (Sub-tasks của Story/Task, Child Stories của Epic)
 * và phân loại màu sắc trực quan theo mốc tiến độ:
 * - 100%: Xanh lá cây rõ ràng, nổi bật (#10B981 / #059669)
 * - 70% - 99%: Xanh dương (#2563EB)
 * - 40% - 69%: Vàng hổ phách (#F59E0B)
 * - 1% - 39%: Cam (#EA580C)
 * - 0%: Xám trung tính (#94A3B8)
 */
const getSubtaskProgressConfig = (subtaskCount, subtaskDoneCount) => {
  const total = Number(subtaskCount) || 0;
  if (total <= 0) return null;
  const done = Math.max(0, Number(subtaskDoneCount) || 0);
  const percent = Math.min(100, Math.max(0, Math.round((done / total) * 100)));

  if (percent === 100) {
    return {
      total,
      done,
      percent,
      barColor: '#10B981', // Xanh lá cây tươi sáng, rõ ràng
      textColor: '#059669', // Xanh đậm dễ đọc
      trackBg: '#DCFCE7',
      label: 'Hoàn thành 100%',
    };
  }

  if (percent >= 70) {
    return {
      total,
      done,
      percent,
      barColor: '#2563EB', // Xanh dương
      textColor: '#1D4ED8',
      trackBg: '#DBEAFE',
      label: 'Sắp hoàn thành',
    };
  }

  if (percent >= 40) {
    return {
      total,
      done,
      percent,
      barColor: '#F59E0B', // Vàng cam hổ phách
      textColor: '#B45309',
      trackBg: '#FEF3C7',
      label: 'Đang tiến hành',
    };
  }

  if (percent > 0) {
    return {
      total,
      done,
      percent,
      barColor: '#EA580C', // Cam
      textColor: '#C2410C',
      trackBg: '#FFEDD5',
      label: 'Mới bắt đầu',
    };
  }

  return {
    total,
    done,
    percent: 0,
    barColor: '#94A3B8', // Xám
    textColor: '#64748B',
    trackBg: '#E2E8F0',
    label: 'Chưa bắt đầu',
  };
};

export default function ProjectBoardView({
  projectId,
  projectKey,
  projectTypes: initialProjectTypes = [],
  canCreate = true,
  onOpenCreateModal,
  onSelectWorkItem,
  refreshKey = 0
}) {
  const [items, setItems] = useState([]);
  const [statuses, setStatuses] = useState([]);
  const [types, setTypes] = useState(initialProjectTypes);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTypeFilter, setSelectedTypeFilter] = useState('ALL');
  const [selectedStatusFilter, setSelectedStatusFilter] = useState('ALL');

  useEffect(() => {
    if (initialProjectTypes && initialProjectTypes.length > 0) {
      setTypes(initialProjectTypes);
    }
  }, [initialProjectTypes]);

  const fetchBoardData = async () => {
    if (!projectId) return;
    try {
      setLoading(true);
      const [itemsRes, workflowRes, typesRes] = await Promise.all([
        workItemApi.listAll({ projectId, size: 100 }),
        workflowApi.getProjectWorkflow(projectId).catch(() => null),
        workItemTypeApi.listByProject(projectId).catch(() => ({ data: [] })),
      ]);

      if (itemsRes && itemsRes.data) {
        const list = itemsRes.data.content || itemsRes.data || [];
        setItems(list);
      }

      if (workflowRes && workflowRes.data && workflowRes.data.statuses) {
        setStatuses(workflowRes.data.statuses);
      } else {
        // Fallback standard statuses
        setStatuses([
          { id: 'todo', name: 'To Do', category: 'TODO' },
          { id: 'inprogress', name: 'In Progress', category: 'IN_PROGRESS' },
          { id: 'done', name: 'Done', category: 'DONE' },
        ]);
      }

      if (typesRes && typesRes.data && typesRes.data.length > 0) {
        setTypes(typesRes.data);
      }
    } catch (err) {
      console.error('Failed to load board data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBoardData();
  }, [projectId, refreshKey]);

  const filteredItems = items.filter((item) => {
    const matchesSearch =
      !searchQuery.trim() ||
      item.summary?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.code?.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesType =
      selectedTypeFilter === 'ALL' ||
      item.typeId === selectedTypeFilter ||
      item.typeCode === selectedTypeFilter ||
      (item.typeName && item.typeName.toLowerCase() === selectedTypeFilter.toLowerCase());

    const matchesStatus =
      selectedStatusFilter === 'ALL' ||
      item.statusId === selectedStatusFilter ||
      (item.statusName && item.statusName.toLowerCase() === selectedStatusFilter.toLowerCase());

    return matchesSearch && matchesType && matchesStatus;
  });

  const typeFilterOptions =
    types.length > 0
      ? [
          { key: 'ALL', label: 'Tất cả' },
          ...types.map((t) => ({
            key: t.id || t.code,
            id: t.id,
            code: t.code,
            label: getTypeDisplayName(t.name || t.code),
            color: t.colorHex || TYPE_CONFIG[t.code]?.color || '#2563EB',
            level: t.hierarchyLevel,
          })),
        ]
      : [
          { key: 'ALL', label: 'Tất cả' },
          { key: 'EPIC', label: 'Hạng mục lớn', color: '#6554C0' },
          { key: 'TASK', label: 'Nhiệm vụ', color: '#0052CC' },
          { key: 'SUBTASK', label: 'Nhiệm vụ con', color: '#0284C7' },
        ];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        overflow: 'hidden',
        padding: '0 24px 24px 24px',
        backgroundColor: '#FFFFFF',
      }}
    >
      {/* ─── Board Toolbar & Filters ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 0 16px 0',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flex: 1, flexWrap: 'wrap' }}>
          {/* Search Box */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              backgroundColor: '#F8FAFC',
              border: '1px solid #E2E8F0',
              borderRadius: 8,
              padding: '6px 12px',
              maxWidth: 240,
              width: '100%',
            }}
          >
            <Search size={14} color="#94A3B8" />
            <input
              type="text"
              placeholder="Tìm theo mã hoặc từ khóa..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{
                border: 'none',
                background: 'transparent',
                outline: 'none',
                fontSize: 13,
                color: '#0F172A',
                width: '100%',
              }}
            />
          </div>

          {/* Type Filter Pills (Dynamic from Project) */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', marginRight: 2 }}>
              Loại:
            </span>
            {typeFilterOptions.map((opt) => {
              const isSelected =
                selectedTypeFilter === opt.key ||
                (opt.id && selectedTypeFilter === opt.id) ||
                (opt.code && selectedTypeFilter === opt.code);
              return (
                <button
                  key={opt.key}
                  type="button"
                  onClick={() => setSelectedTypeFilter(isSelected && opt.key !== 'ALL' ? 'ALL' : opt.key)}
                  style={{
                    border: isSelected && opt.key !== 'ALL' ? `1px solid ${opt.color || '#2563EB'}50` : '1px solid transparent',
                    padding: '4px 9px',
                    borderRadius: 6,
                    fontSize: 12,
                    fontWeight: isSelected ? 700 : 500,
                    backgroundColor: isSelected ? (opt.key === 'ALL' ? '#EFF6FF' : `${opt.color || '#2563EB'}15`) : '#F8FAFC',
                    color: isSelected ? (opt.color || '#2563EB') : '#64748B',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 5,
                  }}
                >
                  {opt.color && opt.key !== 'ALL' && (
                    <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: opt.color }} />
                  )}
                  <span>{opt.label}</span>
                </button>
              );
            })}
          </div>

          {/* Status Filter (Dynamic from Project) */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase' }}>
              Trạng thái:
            </span>
            <select
              value={selectedStatusFilter}
              onChange={(e) => setSelectedStatusFilter(e.target.value)}
              style={{
                padding: '4px 10px',
                borderRadius: 6,
                border: selectedStatusFilter !== 'ALL' ? '1px solid #3B82F6' : '1px solid #CBD5E1',
                backgroundColor: selectedStatusFilter !== 'ALL' ? '#EFF6FF' : '#F8FAFC',
                color: selectedStatusFilter !== 'ALL' ? '#1D4ED8' : '#334155',
                fontSize: 12,
                fontWeight: selectedStatusFilter !== 'ALL' ? 700 : 500,
                outline: 'none',
                cursor: 'pointer',
              }}
            >
              <option value="ALL">Tất cả trạng thái ({items.length})</option>
              {statuses.map((st) => {
                const count = items.filter(
                  (it) =>
                    it.statusId === st.id ||
                    (it.statusName && st.name && it.statusName.trim().toLowerCase() === st.name.trim().toLowerCase())
                ).length;
                return (
                  <option key={st.id || st.name} value={st.id || st.name}>
                    {getStatusDisplayName(st.name)} ({count})
                  </option>
                );
              })}
            </select>
          </div>

          {/* Reset Filters Button */}
          {(selectedTypeFilter !== 'ALL' || selectedStatusFilter !== 'ALL' || searchQuery) && (
            <button
              type="button"
              onClick={() => {
                setSelectedTypeFilter('ALL');
                setSelectedStatusFilter('ALL');
                setSearchQuery('');
              }}
              style={{
                border: 'none',
                background: 'transparent',
                color: '#EF4444',
                fontSize: 12,
                fontWeight: 600,
                cursor: 'pointer',
                padding: '4px 6px',
                borderRadius: 4,
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
              }}
              title="Đặt lại tất cả bộ lọc"
            >
              <X size={13} />
              <span>Xóa lọc</span>
            </button>
          )}
        </div>

        {/* Nút Tạo Công Việc Nhanh */}
        {canCreate && (
          <button
            onClick={() => onOpenCreateModal && onOpenCreateModal()}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              backgroundColor: '#2563EB',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: 8,
              padding: '7px 16px',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
              transition: 'background-color 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
          >
            <Plus size={16} />
            <span>Tạo công việc</span>
          </button>
        )}
      </div>

      {/* ─── Board Columns Grid ─── */}
      <div
        style={{
          flex: 1,
          display: 'grid',
          gridTemplateColumns: `repeat(${Math.max(statuses.length, 3)}, minmax(280px, 1fr))`,
          gap: 16,
          overflowX: 'auto',
          overflowY: 'hidden',
          paddingBottom: 8,
        }}
      >
        {statuses.map((st) => {
          const columnItems = filteredItems.filter((it) => {
            if (it.statusId && st.id && it.statusId === st.id) {
              return true;
            }
            if (it.statusName && st.name && it.statusName.trim().toLowerCase() === st.name.trim().toLowerCase()) {
              return true;
            }
            return false;
          });

          const getCategoryColor = (cat) => {
            if (cat === 'DONE') return '#10B981';
            if (cat === 'IN_PROGRESS') return '#3B82F6';
            return '#64748B';
          };

          return (
            <div
              key={st.id || st.name}
              style={{
                backgroundColor: '#F8FAFC',
                borderRadius: 10,
                border: '1px solid #E2E8F0',
                display: 'flex',
                flexDirection: 'column',
                maxHeight: '100%',
                minWidth: 280,
              }}
            >
              {/* Column Header */}
              <div
                style={{
                  padding: '12px 14px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  borderBottom: '1px solid #E2E8F0',
                  backgroundColor: '#F1F5F9',
                  borderTopLeftRadius: 10,
                  borderTopRightRadius: 10,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div
                    style={{
                      width: 8,
                      height: 8,
                      borderRadius: '50%',
                      backgroundColor: st.colorHex || getCategoryColor(st.category),
                    }}
                  />
                  <span style={{ fontSize: 13, fontWeight: 700, color: '#334155', textTransform: 'uppercase' }}>
                    {getStatusDisplayName(st.name)}
                  </span>
                  <span
                    style={{
                      fontSize: 11,
                      fontWeight: 700,
                      backgroundColor: '#E2E8F0',
                      color: '#475569',
                      padding: '2px 7px',
                      borderRadius: 10,
                    }}
                  >
                    {columnItems.length}
                  </span>
                </div>

                <button
                  onClick={() => onOpenCreateModal && onOpenCreateModal()}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#64748B',
                    cursor: 'pointer',
                    padding: 4,
                    borderRadius: 4,
                    display: 'flex',
                    alignItems: 'center',
                  }}
                  title="Thêm công việc vào cột này"
                >
                  <Plus size={15} />
                </button>
              </div>

              {/* Card List in Column */}
              <div
                style={{
                  flex: 1,
                  overflowY: 'auto',
                  padding: 10,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 10,
                }}
              >
                {columnItems.length === 0 ? (
                  <div
                    style={{
                      padding: '24px 12px',
                      textAlign: 'center',
                      color: '#94A3B8',
                      fontSize: 12,
                      fontStyle: 'italic',
                      border: '1px dashed #CBD5E1',
                      borderRadius: 8,
                    }}
                  >
                    Chưa có công việc trong trạng thái này
                  </div>
                ) : (
                  columnItems.map((item) => {
                    const rawTypeCode = (item.typeCode || item.typeName || 'TASK').toUpperCase().replace('-', '_');
                    const matchedType = types.find(
                      (t) =>
                        t.id === item.typeId ||
                        (item.typeCode && t.code?.toUpperCase() === item.typeCode.toUpperCase()) ||
                        (item.typeName && t.name?.toLowerCase() === item.typeName.toLowerCase())
                    );
                    const typeConfig = TYPE_CONFIG[rawTypeCode] || TYPE_CONFIG[rawTypeCode.replace('_', '')] || TYPE_CONFIG.TASK;
                    const displayTypeName = getTypeDisplayName(item.typeName || matchedType?.name || typeConfig.label || rawTypeCode);
                    const typeColor = item.typeColor || matchedType?.colorHex || typeConfig.color || '#2563EB';

                    const priorityConfig = getPriorityConfig(item.priority);
                    const PriorityIcon = priorityConfig.icon;

                    const todayStr = new Date().toISOString().substring(0, 10);
                    const isOverdue = item.dueDate && item.dueDate < todayStr && item.statusCategory !== 'DONE';

                    const subtaskProgress = getSubtaskProgressConfig(
                      item.subtaskCount,
                      item.subtaskDoneCount ?? item.completedSubtaskCount ?? 0
                    );

                    return (
                      <div
                        key={item.id}
                        onClick={() => onSelectWorkItem && onSelectWorkItem(item.id)}
                        style={{
                          backgroundColor: '#FFFFFF',
                          borderRadius: 8,
                          padding: '12px 14px',
                          border: `1px solid ${priorityConfig.key === 'HIGHEST' ? '#FECACA' : '#E2E8F0'}`,
                          borderLeft: priorityConfig.key === 'HIGHEST' ? '3px solid #DC2626' : (priorityConfig.key === 'HIGH' ? '3px solid #EA580C' : '1px solid #E2E8F0'),
                          boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                          cursor: 'pointer',
                          transition: 'all 0.15s ease',
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 8,
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.borderColor = '#2563EB';
                          e.currentTarget.style.boxShadow = '0 4px 6px -1px rgba(37,99,235,0.1)';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.borderColor = priorityConfig.key === 'HIGHEST' ? '#FECACA' : '#E2E8F0';
                          e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.05)';
                        }}
                      >
                        {/* Parent tag & Conflict/Blocked badges */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                          {item.parentCode && (
                            <div
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 4,
                                fontSize: 10,
                                fontWeight: 700,
                                color: '#6554C0',
                                backgroundColor: '#F3F0FF',
                                padding: '2px 6px',
                                borderRadius: 4,
                                alignSelf: 'flex-start',
                              }}
                            >
                              <Bookmark size={10} />
                              <span>{item.parentCode}</span>
                            </div>
                          )}

                          {item.isBlocked && (
                            <span
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 3,
                                fontSize: 10,
                                fontWeight: 700,
                                color: '#DC2626',
                                backgroundColor: '#FEF2F2',
                                border: '1px solid #FECACA',
                                padding: '1px 5px',
                                borderRadius: 4,
                              }}
                              title="Công việc bị cản trở bởi blocker chưa xong"
                            >
                              <Lock size={10} /> Bị chặn
                            </span>
                          )}

                          {item.hasTimelineConflict && (
                            <span
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 3,
                                fontSize: 10,
                                fontWeight: 700,
                                color: '#D97706',
                                backgroundColor: '#FFFBEB',
                                border: '1px solid #FCD34D',
                                padding: '1px 5px',
                                borderRadius: 4,
                              }}
                              title={item.timelineConflictMessage || 'Cảnh báo lệch hạn thời gian liên tầng'}
                            >
                              <AlertTriangle size={10} /> Lệch hạn
                            </span>
                          )}
                        </div>

                        {/* Title / Summary */}
                        <div
                          style={{
                            fontSize: 13,
                            fontWeight: 600,
                            color: '#0F172A',
                            lineHeight: 1.4,
                            wordBreak: 'break-word',
                          }}
                        >
                          {item.summary}
                        </div>

                        {/* Metadata Footer */}
                        <div
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            paddingTop: 6,
                            borderTop: '1px solid #F1F5F9',
                            fontSize: 11,
                            flexWrap: 'wrap',
                            gap: 4,
                          }}
                        >
                          {/* Left: Type Badge (text only, no icon) & Issue Code */}
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <span
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                fontSize: 10,
                                fontWeight: 700,
                                color: typeColor,
                                backgroundColor: `${typeColor}15`,
                                border: `1px solid ${typeColor}40`,
                                padding: '1px 6px',
                                borderRadius: 4,
                                lineHeight: '16px',
                                whiteSpace: 'nowrap',
                              }}
                            >
                              {displayTypeName}
                            </span>
                            <span style={{ fontWeight: 700, color: '#2563EB' }}>
                              {item.code}
                            </span>
                          </div>

                          {/* Right: Due Date, Points, Subtasks & Priority Badge */}
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            {/* Due Date Chip if exists */}
                            {item.dueDate && (
                              <span
                                style={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: 3,
                                  fontSize: 10,
                                  fontWeight: isOverdue ? 700 : 500,
                                  color: isOverdue ? '#DC2626' : '#64748B',
                                  backgroundColor: isOverdue ? '#FEF2F2' : '#F8FAFC',
                                  border: `1px solid ${isOverdue ? '#FECACA' : '#E2E8F0'}`,
                                  padding: '1px 5px',
                                  borderRadius: 4,
                                }}
                                title={isOverdue ? `Quá hạn: ${item.dueDate}` : `Hạn chót: ${item.dueDate}`}
                              >
                                <Clock size={10} color={isOverdue ? '#DC2626' : '#64748B'} />
                                <span>{item.dueDate.substring(5)}</span>
                              </span>
                            )}

                            {/* Subtask count with dynamic milestone color */}
                            {subtaskProgress && (
                              <span
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 2,
                                  color: subtaskProgress.textColor,
                                  fontSize: 11,
                                  fontWeight: subtaskProgress.percent === 100 ? 700 : 500,
                                }}
                                title={`${subtaskProgress.done}/${subtaskProgress.total} công việc con hoàn thành (${subtaskProgress.percent}%)`}
                              >
                                <CheckCircle2 size={12} color={subtaskProgress.barColor} />
                                <span>{subtaskProgress.done}/{subtaskProgress.total}</span>
                              </span>
                            )}

                            {/* Story Points */}
                            {item.storyPoints != null && (
                              <span
                                style={{
                                  backgroundColor: '#F1F5F9',
                                  color: '#334155',
                                  fontWeight: 700,
                                  fontSize: 10,
                                  padding: '1px 5px',
                                  borderRadius: 10,
                                }}
                                title="Điểm ước lượng"
                              >
                                {item.storyPoints}
                              </span>
                            )}

                            {/* Rich Priority Badge */}
                            <div
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 3,
                                padding: '2px 6px',
                                borderRadius: 4,
                                backgroundColor: priorityConfig.bgColor,
                                border: `1px solid ${priorityConfig.borderColor}`,
                                color: priorityConfig.color,
                                fontSize: 10,
                                fontWeight: 700,
                              }}
                              title={`Độ ưu tiên: ${priorityConfig.label} (${priorityConfig.subLabel})`}
                            >
                              <PriorityIcon size={11} color={priorityConfig.color} />
                              <span>{priorityConfig.label}</span>
                            </div>
                          </div>
                        </div>

                        {/* Subtask Progress Bar at the bottom of the card */}
                        {subtaskProgress && (
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 8,
                              paddingTop: 2,
                            }}
                            title={`Tiến độ công việc con: ${subtaskProgress.done}/${subtaskProgress.total} hoàn thành (${subtaskProgress.percent}%)`}
                          >
                            {/* Track & Filled Bar */}
                            <div
                              style={{
                                flex: 1,
                                height: 5,
                                backgroundColor: subtaskProgress.trackBg,
                                borderRadius: 99,
                                overflow: 'hidden',
                              }}
                            >
                              <div
                                style={{
                                  width: `${subtaskProgress.percent}%`,
                                  height: '100%',
                                  backgroundColor: subtaskProgress.barColor,
                                  borderRadius: 99,
                                  transition: 'width 0.3s ease',
                                }}
                              />
                            </div>

                            {/* Percentage Text */}
                            <span
                              style={{
                                fontSize: 10,
                                fontWeight: 700,
                                color: subtaskProgress.textColor,
                                fontVariantNumeric: 'tabular-nums',
                                minWidth: 28,
                                textAlign: 'right',
                                lineHeight: 1,
                              }}
                            >
                              {subtaskProgress.percent}%
                            </span>
                          </div>
                        )}
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
