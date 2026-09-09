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
  MoreHorizontal
} from 'lucide-react';
import toast from 'react-hot-toast';
import { workItemApi, workflowApi } from '../../api.js';
import { getPriorityConfig, PRIORITY_CONFIG } from '../../utils/priorityConfig.js';

const TYPE_ICONS = {
  EPIC: { icon: Bookmark, color: '#6554C0', label: 'Epic' },
  STORY: { icon: Bookmark, color: '#36B37E', label: 'Story' },
  TASK: { icon: CheckSquare, color: '#0052CC', label: 'Task' },
  BUG: { icon: CircleDot, color: '#E5493A', label: 'Bug' },
  SUBTASK: { icon: GitCommitHorizontal, color: '#4C9AFF', label: 'Sub-task' },
};

export default function ProjectBoardView({
  projectId,
  projectKey,
  canCreate = false,
  onOpenCreateModal,
  onSelectWorkItem,
  refreshKey = 0
}) {
  const [items, setItems] = useState([]);
  const [statuses, setStatuses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTypeFilter, setSelectedTypeFilter] = useState('ALL');

  const fetchBoardData = async () => {
    if (!projectId) return;
    try {
      setLoading(true);
      const [itemsRes, workflowRes] = await Promise.all([
        workItemApi.listAll({ projectId, size: 100 }),
        workflowApi.getProjectWorkflow(projectId).catch(() => null),
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
      item.typeCode === selectedTypeFilter ||
      item.typeName?.toUpperCase() === selectedTypeFilter;

    return matchesSearch && matchesType;
  });

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
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1, minWidth: 260 }}>
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
              maxWidth: 320,
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

          {/* Type Filter Pills */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            {['ALL', 'EPIC', 'STORY', 'TASK', 'BUG', 'SUBTASK'].map((t) => (
              <button
                key={t}
                onClick={() => setSelectedTypeFilter(t)}
                style={{
                  border: 'none',
                  padding: '4px 10px',
                  borderRadius: 6,
                  fontSize: 12,
                  fontWeight: selectedTypeFilter === t ? 700 : 500,
                  backgroundColor: selectedTypeFilter === t ? '#EFF6FF' : '#F8FAFC',
                  color: selectedTypeFilter === t ? '#2563EB' : '#64748B',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                {t === 'ALL' ? 'Tất cả' : t}
              </button>
            ))}
          </div>
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
          const columnItems = filteredItems.filter(
            (it) => it.statusId === st.id || it.statusCategory === st.category || it.statusName === st.name
          );

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
                    {st.name}
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

                {canCreate && (
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
                )}
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
                    const typeCode = (item.typeCode || item.typeName || 'TASK').toUpperCase();
                    const typeConfig = TYPE_ICONS[typeCode] || TYPE_ICONS.TASK;
                    const TypeIcon = typeConfig.icon;

                    const priorityConfig = getPriorityConfig(item.priority);
                    const PriorityIcon = priorityConfig.icon;

                    const todayStr = new Date().toISOString().substring(0, 10);
                    const isOverdue = item.dueDate && item.dueDate < todayStr && item.statusCategory !== 'DONE';

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
                        {/* Parent tag if belongs to Epic/Parent */}
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
                          {/* Left: Type Icon & Issue Code */}
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <div title={typeConfig.label}>
                              <TypeIcon size={14} color={typeConfig.color} />
                            </div>
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

                            {/* Subtask count */}
                            {item.subtaskCount > 0 && (
                              <span
                                style={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 2,
                                  color: '#64748B',
                                  fontSize: 11,
                                }}
                                title={`${item.subtaskDoneCount || item.completedSubtaskCount || 0}/${item.subtaskCount} Subtasks hoàn thành`}
                              >
                                <CheckCircle2 size={12} color="#10B981" />
                                <span>{item.subtaskDoneCount || item.completedSubtaskCount || 0}/{item.subtaskCount}</span>
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
                                title="Story Points"
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
