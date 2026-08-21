import React, { useState, useRef, useEffect } from 'react';
import {
  Diamond,
  Plus,
  X,
  Check,
  Calendar,
  MoreHorizontal,
  ChevronDown,
  GripVertical,
  Edit2,
  Trash2,
  Filter,
  CheckCircle2
} from 'lucide-react';
import { milestoneApi } from '../../api.js';

export default function MilestoneLinearList({
  projectId,
  milestones = [],
  activeMilestoneId,
  onSelectMilestone,
  onMilestonesChanged,
}) {
  const [isCreating, setIsCreating] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDueDate, setNewDueDate] = useState('');
  const [isDatePickerOpen, setIsDatePickerOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [openMenuId, setOpenMenuId] = useState(null);

  // Edit inline state
  const [editingMilestoneId, setEditingMilestoneId] = useState(null);
  const [editName, setEditName] = useState('');
  const [editDueDate, setEditDueDate] = useState('');

  const inputRef = useRef(null);
  const menuRef = useRef(null);

  useEffect(() => {
    if (isCreating && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isCreating]);

  // Close menus on outside click
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setOpenMenuId(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleCreateSubmit = async (e) => {
    if (e) e.preventDefault();
    if (!newName.trim()) return;

    try {
      setLoading(true);
      await milestoneApi.create({
        projectId,
        name: newName.trim(),
        dueDate: newDueDate ? `${newDueDate}T23:59:59` : null,
      });
      setNewName('');
      setNewDueDate('');
      setIsCreating(false);
      setIsDatePickerOpen(false);
      if (onMilestonesChanged) onMilestonesChanged();
    } catch (err) {
      alert(err.message || 'Lỗi khi tạo Milestone');
    } finally {
      setLoading(false);
    }
  };

  const handleEditSubmit = async (milestoneId) => {
    if (!editName.trim()) return;
    try {
      setLoading(true);
      await milestoneApi.update(milestoneId, {
        name: editName.trim(),
        dueDate: editDueDate ? `${editDueDate}T23:59:59` : null,
      });
      setEditingMilestoneId(null);
      if (onMilestonesChanged) onMilestonesChanged();
    } catch (err) {
      alert(err.message || 'Lỗi khi cập nhật Milestone');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (milestoneId) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa cột mốc này?')) return;
    try {
      await milestoneApi.delete(milestoneId);
      if (activeMilestoneId === milestoneId && onSelectMilestone) {
        onSelectMilestone(null);
      }
      if (onMilestonesChanged) onMilestonesChanged();
    } catch (err) {
      alert(err.message || 'Lỗi khi xóa Milestone');
    }
  };

  const startEdit = (m) => {
    setEditingMilestoneId(m.id);
    setEditName(m.name);
    setEditDueDate(m.dueDate ? m.dueDate.substring(0, 10) : '');
    setOpenMenuId(null);
  };

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        borderRadius: 12,
        border: '1px solid #E2E8F0',
        padding: '16px',
        maxWidth: 380,
        width: '100%',
        boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
      }}
    >
      {/* ─── Header ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: 12,
          padding: '0 4px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Milestones</span>
          <ChevronDown size={14} color="#64748B" />
        </div>

        <button
          onClick={() => {
            setIsCreating(true);
            setEditingMilestoneId(null);
          }}
          title="Tạo cột mốc mới"
          style={{
            background: 'none',
            border: 'none',
            color: '#64748B',
            cursor: 'pointer',
            padding: 4,
            borderRadius: 4,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
          onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
        >
          <Plus size={16} />
        </button>
      </div>

      {/* ─── Inline Creation Box (create_mile_stone1.png & create_mile_stone2.png) ─── */}
      {isCreating && (
        <form
          onSubmit={handleCreateSubmit}
          style={{
            backgroundColor: '#F8FAFC',
            borderRadius: 8,
            border: '1px solid #E2E8F0',
            padding: '8px 10px',
            marginBottom: 12,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            position: 'relative',
          }}
        >
          <Diamond size={15} color="#94A3B8" />

          <input
            ref={inputRef}
            type="text"
            placeholder="Milestone name"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            disabled={loading}
            style={{
              flex: 1,
              background: 'none',
              border: 'none',
              outline: 'none',
              fontSize: 13,
              color: '#0F172A',
            }}
          />

          {/* Target Date Picker Trigger */}
          <div style={{ position: 'relative' }}>
            <button
              type="button"
              onClick={() => setIsDatePickerOpen(!isDatePickerOpen)}
              title="Chọn ngày mục tiêu (Target Date)"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                padding: '3px 8px',
                borderRadius: 6,
                border: '1px solid #CBD5E1',
                backgroundColor: newDueDate ? '#EFF6FF' : '#FFFFFF',
                color: newDueDate ? '#2563EB' : '#64748B',
                fontSize: 11,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              <Calendar size={13} />
              <span>{newDueDate ? newDueDate : 'Target date'}</span>
            </button>

            {isDatePickerOpen && (
              <div
                style={{
                  position: 'absolute',
                  top: '110%',
                  right: 0,
                  zIndex: 50,
                  backgroundColor: '#FFFFFF',
                  borderRadius: 8,
                  boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                  border: '1px solid #E2E8F0',
                  padding: 10,
                }}
              >
                <input
                  type="date"
                  value={newDueDate}
                  onChange={(e) => {
                    setNewDueDate(e.target.value);
                    setIsDatePickerOpen(false);
                  }}
                  style={{
                    padding: '6px 8px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 12,
                    outline: 'none',
                  }}
                />
              </div>
            )}
          </div>

          {/* Cancel */}
          <button
            type="button"
            onClick={() => {
              setIsCreating(false);
              setNewName('');
              setNewDueDate('');
            }}
            style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', padding: 2 }}
          >
            <X size={15} />
          </button>

          {/* Submit */}
          <button
            type="submit"
            disabled={!newName.trim() || loading}
            style={{
              background: 'none',
              border: 'none',
              color: newName.trim() ? '#2563EB' : '#CBD5E1',
              cursor: newName.trim() ? 'pointer' : 'default',
              padding: 2,
            }}
          >
            <Check size={16} />
          </button>
        </form>
      )}

      {/* ─── Milestone Items List (list_milestone.png) ─── */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {milestones.length === 0 && !isCreating ? (
          <div style={{ padding: '20px 0', textAlign: 'center', color: '#94A3B8', fontSize: 13 }}>
            Chưa có cột mốc nào. Nhấn + để tạo.
          </div>
        ) : (
          milestones.map((m) => {
            const isSelected = activeMilestoneId === m.id;
            const isEditing = editingMilestoneId === m.id;
            const completed = m.completedTasks || 0;
            const total = m.totalTasks || 0;
            const percent = m.progressPercentage || 0;

            const isDone = percent === 100 && total > 0;

            if (isEditing) {
              return (
                <div
                  key={m.id}
                  style={{
                    backgroundColor: '#F8FAFC',
                    borderRadius: 8,
                    border: '1px solid #93C5FD',
                    padding: '8px 10px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                  }}
                >
                  <Diamond size={15} color="#2563EB" />
                  <input
                    type="text"
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    style={{
                      flex: 1,
                      border: '1px solid #CBD5E1',
                      borderRadius: 4,
                      padding: '2px 6px',
                      fontSize: 13,
                    }}
                  />
                  <input
                    type="date"
                    value={editDueDate}
                    onChange={(e) => setEditDueDate(e.target.value)}
                    style={{ fontSize: 11, padding: '2px 4px', border: '1px solid #CBD5E1', borderRadius: 4 }}
                  />
                  <button
                    onClick={() => handleEditSubmit(m.id)}
                    style={{ background: 'none', border: 'none', color: '#2563EB', cursor: 'pointer' }}
                  >
                    <Check size={16} />
                  </button>
                  <button
                    onClick={() => setEditingMilestoneId(null)}
                    style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer' }}
                  >
                    <X size={15} />
                  </button>
                </div>
              );
            }

            return (
              <div
                key={m.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '8px 10px',
                  borderRadius: 8,
                  backgroundColor: isSelected ? '#F1F5F9' : 'transparent',
                  border: isSelected ? '1px solid #E2E8F0' : '1px solid transparent',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                  position: 'relative',
                }}
                onClick={() => onSelectMilestone && onSelectMilestone(isSelected ? null : m.id)}
                onMouseEnter={(e) => {
                  if (!isSelected) e.currentTarget.style.backgroundColor = '#F8FAFC';
                }}
                onMouseLeave={(e) => {
                  if (!isSelected) e.currentTarget.style.backgroundColor = 'transparent';
                }}
              >
                {/* Left: Icon, Name, Stats */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden', flex: 1 }}>
                  <GripVertical size={13} color="#CBD5E1" style={{ flexShrink: 0 }} />
                  <Diamond
                    size={14}
                    fill={isDone ? '#8B5CF6' : isSelected ? '#EAB308' : '#818CF8'}
                    color={isDone ? '#7C3AED' : isSelected ? '#CA8A04' : '#6366F1'}
                    style={{ flexShrink: 0 }}
                  />
                  <span
                    style={{
                      fontSize: 13,
                      fontWeight: isSelected ? 600 : 500,
                      color: '#1E293B',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {m.name}
                  </span>
                  <span
                    style={{
                      fontSize: 11,
                      color: '#64748B',
                      flexShrink: 0,
                    }}
                  >
                    {percent}% of {total}
                  </span>
                </div>

                {/* Right: Actions */}
                <div
                  style={{ display: 'flex', alignItems: 'center', gap: 6 }}
                  onClick={(e) => e.stopPropagation()}
                >
                  {isSelected ? (
                    <button
                      onClick={() => onSelectMilestone && onSelectMilestone(null)}
                      style={{
                        background: 'none',
                        border: 'none',
                        fontSize: 11,
                        color: '#64748B',
                        fontWeight: 600,
                        cursor: 'pointer',
                        padding: '2px 4px',
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.color = '#0F172A')}
                      onMouseLeave={(e) => (e.currentTarget.style.color = '#64748B')}
                    >
                      Clear filter
                    </button>
                  ) : (
                    <span
                      onClick={() => onSelectMilestone && onSelectMilestone(m.id)}
                      style={{
                        fontSize: 11,
                        color: '#94A3B8',
                        cursor: 'pointer',
                      }}
                    >
                      See tasks
                    </span>
                  )}

                  <div style={{ position: 'relative' }}>
                    <button
                      onClick={() => setOpenMenuId(openMenuId === m.id ? null : m.id)}
                      style={{
                        background: 'none',
                        border: 'none',
                        color: '#94A3B8',
                        cursor: 'pointer',
                        padding: 3,
                        borderRadius: 4,
                        display: 'flex',
                        alignItems: 'center',
                      }}
                    >
                      <MoreHorizontal size={14} />
                    </button>

                    {openMenuId === m.id && (
                      <div
                        ref={menuRef}
                        style={{
                          position: 'absolute',
                          right: 0,
                          top: '100%',
                          zIndex: 60,
                          backgroundColor: '#FFFFFF',
                          borderRadius: 8,
                          boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
                          border: '1px solid #E2E8F0',
                          padding: 4,
                          minWidth: 120,
                        }}
                      >
                        <button
                          onClick={() => startEdit(m)}
                          style={{
                            width: '100%',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            padding: '6px 8px',
                            background: 'none',
                            border: 'none',
                            fontSize: 12,
                            color: '#334155',
                            cursor: 'pointer',
                            borderRadius: 4,
                            textAlign: 'left',
                          }}
                          onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F1F5F9')}
                          onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                        >
                          <Edit2 size={13} />
                          <span>Chỉnh sửa</span>
                        </button>

                        <button
                          onClick={() => handleDelete(m.id)}
                          style={{
                            width: '100%',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                            padding: '6px 8px',
                            background: 'none',
                            border: 'none',
                            fontSize: 12,
                            color: '#DC2626',
                            cursor: 'pointer',
                            borderRadius: 4,
                            textAlign: 'left',
                          }}
                          onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#FEF2F2')}
                          onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                        >
                          <Trash2 size={13} />
                          <span>Xóa cột mốc</span>
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}

        {/* No milestone filter */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '8px 10px',
            borderRadius: 8,
            backgroundColor: activeMilestoneId === 'NONE' ? '#F1F5F9' : 'transparent',
            cursor: 'pointer',
            fontSize: 13,
            color: '#64748B',
            marginTop: 4,
            borderTop: '1px solid #F1F5F9',
          }}
          onClick={() => onSelectMilestone && onSelectMilestone(activeMilestoneId === 'NONE' ? null : 'NONE')}
        >
          <Diamond size={13} color="#CBD5E1" />
          <span>No milestone</span>
        </div>
      </div>
    </div>
  );
}
