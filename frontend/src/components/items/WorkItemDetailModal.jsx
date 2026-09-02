import React, { useState, useEffect } from 'react';
import {
  X,
  Share2,
  MoreHorizontal,
  Bookmark,
  CheckSquare,
  AlertCircle,
  CircleDot,
  GitCommitHorizontal,
  ChevronDown,
  ChevronUp,
  Plus,
  Clock,
  Calendar,
  Layers,
  Sparkles,
  CheckCircle2,
  ArrowUp,
  ArrowDown,
  Equal,
  Send,
  MessageSquare,
  History,
  FileText,
  User
} from 'lucide-react';
import toast from 'react-hot-toast';
import { workItemApi, workflowApi } from '../../api/workServiceApi.js';

const PRIORITY_ICONS = {
  HIGHEST: { icon: ArrowUp, color: '#DE350B', label: 'Highest' },
  HIGH: { icon: ArrowUp, color: '#FF5630', label: 'High' },
  MEDIUM: { icon: Equal, color: '#FFAB00', label: 'Medium' },
  LOW: { icon: ArrowDown, color: '#36B37E', label: 'Low' },
  LOWEST: { icon: ArrowDown, color: '#6B778C', label: 'Lowest' },
};

const TYPE_ICONS = {
  EPIC: { icon: Bookmark, color: '#6554C0', label: 'Epic' },
  STORY: { icon: Bookmark, color: '#36B37E', label: 'Story' },
  TASK: { icon: CheckSquare, color: '#0052CC', label: 'Task' },
  BUG: { icon: CircleDot, color: '#E5493A', label: 'Bug' },
  SUBTASK: { icon: GitCommitHorizontal, color: '#4C9AFF', label: 'Sub-task' },
};

export default function WorkItemDetailModal({ itemId, isOpen, onClose, onItemUpdated }) {
  const [item, setItem] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('comments'); // 'comments' | 'history' | 'worklog'
  const [isEditingSummary, setIsEditingSummary] = useState(false);
  const [summaryDraft, setSummaryDraft] = useState('');
  const [isEditingDescription, setIsEditingDescription] = useState(false);
  const [descriptionDraft, setDescriptionDraft] = useState('');
  const [newSubtaskTitle, setNewSubtaskTitle] = useState('');
  const [isAddingSubtask, setIsAddingSubtask] = useState(false);
  const [showStatusDropdown, setShowStatusDropdown] = useState(false);
  const [showPriorityDropdown, setShowPriorityDropdown] = useState(false);
  const [commentDraft, setCommentDraft] = useState('');
  const [comments, setComments] = useState([]);
  const [changelogs, setChangelogs] = useState([]);

  const fetchDetail = async () => {
    if (!itemId) return;
    try {
      setLoading(true);
      const res = await workItemApi.getById(itemId);
      if (res && res.data) {
        setItem(res.data);
        setSummaryDraft(res.data.summary || '');
        setDescriptionDraft(res.data.description || '');
      }
    } catch (err) {
      console.error('Failed to load item detail:', err);
      toast.error('Không thể tải chi tiết công việc');
    } finally {
      setLoading(false);
    }
  };

  const fetchComments = async () => {
    if (!itemId) return;
    try {
      const res = await workItemApi.getComments(itemId);
      if (res && res.data) {
        setComments(res.data);
      }
    } catch (err) {
      // Graceful ignore
    }
  };

  const fetchChangelogs = async () => {
    if (!itemId) return;
    try {
      const res = await workItemApi.getChangelogs(itemId);
      if (res && res.data) {
        setChangelogs(res.data);
      }
    } catch (err) {
      // Graceful ignore
    }
  };

  useEffect(() => {
    if (isOpen && itemId) {
      fetchDetail();
      fetchComments();
      fetchChangelogs();
    }
  }, [isOpen, itemId]);

  if (!isOpen) return null;

  const handleSaveSummary = async () => {
    if (!summaryDraft.trim() || summaryDraft === item?.summary) {
      setIsEditingSummary(false);
      return;
    }
    try {
      const res = await workItemApi.update(itemId, { summary: summaryDraft.trim() });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật tiêu đề');
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      toast.error('Lỗi khi cập nhật tiêu đề');
    } finally {
      setIsEditingSummary(false);
    }
  };

  const handleSaveDescription = async () => {
    try {
      const res = await workItemApi.update(itemId, { description: descriptionDraft });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật mô tả');
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      toast.error('Lỗi khi cập nhật mô tả');
    } finally {
      setIsEditingDescription(false);
    }
  };

  const handleExecuteTransition = async (transitionId) => {
    try {
      setShowStatusDropdown(false);
      const res = await workItemApi.executeTransition(itemId, { transitionId });
      if (res && res.data) {
        toast.success('Đã chuyển trạng thái');
        fetchDetail();
        fetchChangelogs();
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Không thể chuyển trạng thái');
    }
  };

  const handleUpdatePriority = async (newPriority) => {
    try {
      setShowPriorityDropdown(false);
      const res = await workItemApi.update(itemId, { priority: newPriority });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật độ ưu tiên');
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      toast.error('Lỗi cập nhật độ ưu tiên');
    }
  };

  const handleUpdateStoryPoints = async (val) => {
    const num = parseFloat(val);
    if (isNaN(num) && val !== '') return;
    try {
      const res = await workItemApi.update(itemId, { storyPoints: val === '' ? null : num });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật Story Points');
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      toast.error('Lỗi cập nhật Story Points');
    }
  };

  const handleAddSubtask = async (e) => {
    e.preventDefault();
    if (!newSubtaskTitle.trim()) return;
    try {
      await workItemApi.createSubtask(itemId, { summary: newSubtaskTitle.trim() });
      toast.success('Đã thêm Sub-task mới');
      setNewSubtaskTitle('');
      setIsAddingSubtask(false);
      fetchDetail();
      if (onItemUpdated) onItemUpdated();
    } catch (err) {
      toast.error('Lỗi khi tạo Sub-task');
    }
  };

  const handleAddComment = async (e) => {
    e.preventDefault();
    if (!commentDraft.trim()) return;
    try {
      await workItemApi.addComment(itemId, { body: commentDraft.trim() });
      toast.success('Đã gửi bình luận');
      setCommentDraft('');
      fetchComments();
    } catch (err) {
      toast.error('Lỗi khi gửi bình luận');
    }
  };

  const priorityInfo = PRIORITY_ICONS[item?.priority] || PRIORITY_ICONS.MEDIUM;
  const PriorityIcon = priorityInfo.icon;

  const typeCode = item?.typeCode?.toUpperCase() || 'TASK';
  const typeInfo = TYPE_ICONS[typeCode] || TYPE_ICONS.TASK;
  const TypeIcon = typeInfo.icon;

  const subtaskTotal = item?.subtasks?.length || 0;
  const subtaskDone = item?.subtasks?.filter(s => s.statusCategory === 'DONE').length || 0;
  const progressPercent = subtaskTotal > 0 ? (subtaskDone / subtaskTotal) * 100 : 0;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.5)',
        backdropFilter: 'blur(4px)',
        zIndex: 50,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          width: '100%',
          maxWidth: 1080,
          height: '90vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* ─── Top Header Bar ─── */}
        <div
          style={{
            height: 52,
            minHeight: 52,
            padding: '0 20px',
            borderBottom: '1px solid #E2E8F0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            backgroundColor: '#FAFAFA',
          }}
        >
          {/* Breadcrumbs */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#64748B' }}>
            <span style={{ fontWeight: 600, color: '#0F172A' }}>{item?.projectKey || 'PROJECT'}</span>
            <span>/</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <TypeIcon size={14} color={typeInfo.color} />
              <span style={{ fontWeight: 700, color: '#2563EB' }}>{item?.code || '...'}</span>
            </div>
          </div>

          {/* Action Buttons */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <button
              onClick={() => {
                navigator.clipboard.writeText(window.location.href);
                toast.success('Đã sao chép liên kết');
              }}
              style={{
                border: 'none',
                background: 'transparent',
                padding: '6px 10px',
                borderRadius: 6,
                cursor: 'pointer',
                color: '#64748B',
                display: 'flex',
                alignItems: 'center',
                gap: 4,
                fontSize: 12,
              }}
              title="Chia sẻ liên kết"
            >
              <Share2 size={14} />
            </button>
            <button
              onClick={onClose}
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
        </div>

        {/* ─── Modal Body (2 Columns Layout) ─── */}
        {loading ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#64748B' }}>
            Đang tải dữ liệu chi tiết Issue...
          </div>
        ) : (
          <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
            {/* ─── Left Column: Main Content (65% Width) ─── */}
            <div
              style={{
                flex: '0 0 65%',
                padding: '24px 28px',
                overflowY: 'auto',
                borderRight: '1px solid #E2E8F0',
                display: 'flex',
                flexDirection: 'column',
                gap: 24,
              }}
            >
              {/* Summary (Inline Editable) */}
              <div>
                {isEditingSummary ? (
                  <input
                    type="text"
                    value={summaryDraft}
                    onChange={(e) => setSummaryDraft(e.target.value)}
                    onBlur={handleSaveSummary}
                    onKeyDown={(e) => e.key === 'Enter' && handleSaveSummary()}
                    autoFocus
                    style={{
                      width: '100%',
                      fontSize: 20,
                      fontWeight: 700,
                      color: '#0F172A',
                      padding: '4px 8px',
                      borderRadius: 6,
                      border: '2px solid #2563EB',
                      outline: 'none',
                    }}
                  />
                ) : (
                  <h2
                    onClick={() => setIsEditingSummary(true)}
                    style={{
                      fontSize: 20,
                      fontWeight: 700,
                      color: '#0F172A',
                      cursor: 'pointer',
                      padding: '4px 0',
                      lineHeight: 1.3,
                    }}
                    title="Click để chỉnh sửa tiêu đề"
                  >
                    {item?.summary || 'Untitled Issue'}
                  </h2>
                )}
              </div>

              {/* Description */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span style={{ fontSize: 13, fontWeight: 700, color: '#334155' }}>Description (Mô tả chi tiết)</span>
                  {!isEditingDescription && (
                    <button
                      onClick={() => setIsEditingDescription(true)}
                      style={{
                        border: 'none',
                        background: 'transparent',
                        color: '#2563EB',
                        fontSize: 12,
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      Chỉnh sửa
                    </button>
                  )}
                </div>

                {isEditingDescription ? (
                  <div>
                    <textarea
                      rows={6}
                      value={descriptionDraft}
                      onChange={(e) => setDescriptionDraft(e.target.value)}
                      placeholder="Thêm mô tả, tiêu chí nghiệm thu, link tài liệu (Hỗ trợ Markdown)..."
                      style={{
                        width: '100%',
                        fontSize: 13,
                        padding: 12,
                        borderRadius: 6,
                        border: '1px solid #CBD5E1',
                        outline: 'none',
                        fontFamily: 'inherit',
                        resize: 'vertical',
                      }}
                    />
                    <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                      <button
                        onClick={handleSaveDescription}
                        style={{
                          backgroundColor: '#2563EB',
                          color: '#FFFFFF',
                          border: 'none',
                          padding: '6px 14px',
                          borderRadius: 6,
                          fontSize: 13,
                          fontWeight: 600,
                          cursor: 'pointer',
                        }}
                      >
                        Lưu
                      </button>
                      <button
                        onClick={() => {
                          setDescriptionDraft(item?.description || '');
                          setIsEditingDescription(false);
                        }}
                        style={{
                          backgroundColor: '#F1F5F9',
                          color: '#475569',
                          border: 'none',
                          padding: '6px 14px',
                          borderRadius: 6,
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
                  <div
                    onClick={() => setIsEditingDescription(true)}
                    style={{
                      padding: 12,
                      backgroundColor: '#F8FAFC',
                      borderRadius: 6,
                      fontSize: 13,
                      color: item?.description ? '#334155' : '#94A3B8',
                      cursor: 'pointer',
                      minHeight: 64,
                      lineHeight: 1.6,
                      whiteSpace: 'pre-wrap',
                      border: '1px solid #E2E8F0',
                    }}
                  >
                    {item?.description || 'Chưa có mô tả chi tiết. Bấm vào đây để thêm...'}
                  </div>
                )}
              </div>

              {/* Sub-tasks Section */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: '#334155' }}>Sub-tasks</span>
                    {subtaskTotal > 0 && (
                      <span style={{ fontSize: 12, color: '#64748B' }}>
                        {subtaskDone} of {subtaskTotal} completed ({Math.round(progressPercent)}%)
                      </span>
                    )}
                  </div>
                  {!isAddingSubtask && (
                    <button
                      onClick={() => setIsAddingSubtask(true)}
                      style={{
                        border: 'none',
                        background: 'transparent',
                        color: '#2563EB',
                        fontSize: 12,
                        fontWeight: 600,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                      }}
                    >
                      <Plus size={14} />
                      <span>Thêm Subtask</span>
                    </button>
                  )}
                </div>

                {/* Progress Bar */}
                {subtaskTotal > 0 && (
                  <div
                    style={{
                      height: 6,
                      backgroundColor: '#E2E8F0',
                      borderRadius: 3,
                      overflow: 'hidden',
                      marginBottom: 12,
                    }}
                  >
                    <div
                      style={{
                        width: `${progressPercent}%`,
                        height: '100%',
                        backgroundColor: '#10B981',
                        transition: 'width 0.3s ease',
                      }}
                    />
                  </div>
                )}

                {/* Subtask List */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {item?.subtasks?.map((sub) => {
                    const isDone = sub.statusCategory === 'DONE';
                    return (
                      <div
                        key={sub.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '8px 12px',
                          backgroundColor: '#F8FAFC',
                          borderRadius: 6,
                          border: '1px solid #E2E8F0',
                          fontSize: 13,
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <CheckCircle2
                            size={16}
                            color={isDone ? '#10B981' : '#94A3B8'}
                            style={{ cursor: 'pointer' }}
                          />
                          <span style={{ fontSize: 11, fontWeight: 700, color: '#2563EB' }}>{sub.code}</span>
                          <span
                            style={{
                              color: isDone ? '#94A3B8' : '#0F172A',
                              textDecoration: isDone ? 'line-through' : 'none',
                            }}
                          >
                            {sub.summary}
                          </span>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span
                            style={{
                              fontSize: 11,
                              fontWeight: 600,
                              padding: '2px 8px',
                              borderRadius: 4,
                              backgroundColor: sub.statusColorHex ? `${sub.statusColorHex}15` : '#EFF6FF',
                              color: sub.statusColorHex || '#2563EB',
                            }}
                          >
                            {sub.statusName || 'TO DO'}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* Inline Add Subtask Form */}
                {isAddingSubtask && (
                  <form onSubmit={handleAddSubtask} style={{ marginTop: 8, display: 'flex', gap: 8 }}>
                    <input
                      type="text"
                      value={newSubtaskTitle}
                      onChange={(e) => setNewSubtaskTitle(e.target.value)}
                      placeholder="Nhập tiêu đề Sub-task..."
                      autoFocus
                      style={{
                        flex: 1,
                        fontSize: 13,
                        padding: '6px 10px',
                        borderRadius: 6,
                        border: '1px solid #2563EB',
                        outline: 'none',
                      }}
                    />
                    <button
                      type="submit"
                      style={{
                        backgroundColor: '#2563EB',
                        color: '#FFFFFF',
                        border: 'none',
                        padding: '6px 14px',
                        borderRadius: 6,
                        fontSize: 12,
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      Tạo
                    </button>
                    <button
                      type="button"
                      onClick={() => setIsAddingSubtask(false)}
                      style={{
                        backgroundColor: '#F1F5F9',
                        color: '#475569',
                        border: 'none',
                        padding: '6px 14px',
                        borderRadius: 6,
                        fontSize: 12,
                        fontWeight: 600,
                        cursor: 'pointer',
                      }}
                    >
                      Hủy
                    </button>
                  </form>
                )}
              </div>

              {/* Activity Section */}
              <div style={{ marginTop: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 16, borderBottom: '1px solid #E2E8F0', paddingBottom: 8, marginBottom: 16 }}>
                  <span style={{ fontSize: 13, fontWeight: 700, color: '#334155' }}>Activity:</span>
                  <button
                    onClick={() => setActiveTab('comments')}
                    style={{
                      border: 'none',
                      background: 'transparent',
                      fontSize: 13,
                      fontWeight: 600,
                      color: activeTab === 'comments' ? '#2563EB' : '#64748B',
                      cursor: 'pointer',
                      borderBottom: activeTab === 'comments' ? '2px solid #2563EB' : 'none',
                      paddingBottom: 4,
                    }}
                  >
                    Comments ({comments.length})
                  </button>
                  <button
                    onClick={() => setActiveTab('history')}
                    style={{
                      border: 'none',
                      background: 'transparent',
                      fontSize: 13,
                      fontWeight: 600,
                      color: activeTab === 'history' ? '#2563EB' : '#64748B',
                      cursor: 'pointer',
                      borderBottom: activeTab === 'history' ? '2px solid #2563EB' : 'none',
                      paddingBottom: 4,
                    }}
                  >
                    History ({changelogs.length})
                  </button>
                </div>

                {activeTab === 'comments' && (
                  <div>
                    {/* Add Comment Input */}
                    <form onSubmit={handleAddComment} style={{ marginBottom: 16 }}>
                      <textarea
                        rows={3}
                        value={commentDraft}
                        onChange={(e) => setCommentDraft(e.target.value)}
                        placeholder="Viết bình luận hoặc gắn thẻ @đồng nghiệp..."
                        style={{
                          width: '100%',
                          fontSize: 13,
                          padding: 10,
                          borderRadius: 6,
                          border: '1px solid #CBD5E1',
                          outline: 'none',
                          fontFamily: 'inherit',
                          resize: 'vertical',
                          marginBottom: 8,
                        }}
                      />
                      <button
                        type="submit"
                        disabled={!commentDraft.trim()}
                        style={{
                          backgroundColor: commentDraft.trim() ? '#2563EB' : '#94A3B8',
                          color: '#FFFFFF',
                          border: 'none',
                          padding: '6px 14px',
                          borderRadius: 6,
                          fontSize: 12,
                          fontWeight: 600,
                          cursor: commentDraft.trim() ? 'pointer' : 'not-allowed',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                        }}
                      >
                        <Send size={12} />
                        <span>Gửi bình luận</span>
                      </button>
                    </form>

                    {/* Comments List */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                      {comments.map((c) => (
                        <div
                          key={c.id}
                          style={{
                            padding: 12,
                            backgroundColor: '#F8FAFC',
                            borderRadius: 8,
                            border: '1px solid #E2E8F0',
                            fontSize: 13,
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              <div
                                style={{
                                  width: 24,
                                  height: 24,
                                  borderRadius: 12,
                                  backgroundColor: '#2563EB',
                                  color: '#FFF',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: 11,
                                  fontWeight: 700,
                                }}
                              >
                                {c.authorName ? c.authorName.charAt(0).toUpperCase() : 'U'}
                              </div>
                              <span style={{ fontWeight: 700, color: '#0F172A' }}>{c.authorName || 'Người dùng'}</span>
                            </div>
                            <span style={{ fontSize: 11, color: '#94A3B8' }}>{c.createdAt?.substring(0, 10)}</span>
                          </div>
                          <div style={{ color: '#334155', lineHeight: 1.5 }}>{c.body}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {activeTab === 'history' && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {changelogs.map((log) => (
                      <div
                        key={log.id}
                        style={{
                          padding: '8px 12px',
                          borderLeft: '3px solid #2563EB',
                          backgroundColor: '#F8FAFC',
                          fontSize: 12,
                          color: '#475569',
                        }}
                      >
                        <span style={{ fontWeight: 700, color: '#0F172A' }}>{log.authorName || 'Người dùng'}</span>{' '}
                        đã thay đổi <b>{log.fieldName}</b> từ <span style={{ color: '#DE350B' }}>"{log.oldValue || 'trống'}"</span> sang{' '}
                        <span style={{ color: '#00875A' }}>"{log.newValue || 'trống'}"</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* ─── Right Sidebar Column: Metadata & Attributes (35% Width) ─── */}
            <div
              style={{
                flex: '0 0 35%',
                padding: '24px 20px',
                overflowY: 'auto',
                backgroundColor: '#FAFAFA',
                display: 'flex',
                flexDirection: 'column',
                gap: 20,
              }}
            >
              {/* Status Section */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Status</span>
                <div style={{ position: 'relative', marginTop: 6 }}>
                  <button
                    onClick={() => setShowStatusDropdown(!showStatusDropdown)}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid #CBD5E1',
                      backgroundColor: '#FFFFFF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      cursor: 'pointer',
                      fontSize: 13,
                      fontWeight: 700,
                      color: item?.statusColorHex || '#2563EB',
                    }}
                  >
                    <span>{item?.statusName || 'TO DO'}</span>
                    <ChevronDown size={14} color="#64748B" />
                  </button>

                  {/* Dropdown transitions */}
                  {showStatusDropdown && (
                    <div
                      style={{
                        position: 'absolute',
                        top: '100%',
                        left: 0,
                        right: 0,
                        marginTop: 4,
                        backgroundColor: '#FFFFFF',
                        borderRadius: 6,
                        border: '1px solid #E2E8F0',
                        boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
                        zIndex: 20,
                        overflow: 'hidden',
                      }}
                    >
                      {item?.availableTransitions?.length > 0 ? (
                        item.availableTransitions.map((t) => (
                          <div
                            key={t.id}
                            onClick={() => handleExecuteTransition(t.id)}
                            style={{
                              padding: '8px 12px',
                              fontSize: 13,
                              fontWeight: 600,
                              color: t.toStatusColorHex || '#0F172A',
                              cursor: 'pointer',
                              borderBottom: '1px solid #F1F5F9',
                            }}
                          >
                            {t.name} (➔ {t.toStatusName})
                          </div>
                        ))
                      ) : (
                        <div style={{ padding: 12, fontSize: 12, color: '#94A3B8', textAlign: 'center' }}>
                          Không có bước chuyển hợp lệ
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>

              {/* Priority Section */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Priority</span>
                <div style={{ position: 'relative', marginTop: 6 }}>
                  <button
                    onClick={() => setShowPriorityDropdown(!showPriorityDropdown)}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid #CBD5E1',
                      backgroundColor: '#FFFFFF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      cursor: 'pointer',
                      fontSize: 13,
                      fontWeight: 600,
                      color: '#0F172A',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <PriorityIcon size={16} color={priorityInfo.color} />
                      <span>{priorityInfo.label}</span>
                    </div>
                    <ChevronDown size={14} color="#64748B" />
                  </button>

                  {showPriorityDropdown && (
                    <div
                      style={{
                        position: 'absolute',
                        top: '100%',
                        left: 0,
                        right: 0,
                        marginTop: 4,
                        backgroundColor: '#FFFFFF',
                        borderRadius: 6,
                        border: '1px solid #E2E8F0',
                        boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
                        zIndex: 20,
                      }}
                    >
                      {Object.keys(PRIORITY_ICONS).map((pKey) => {
                        const pConf = PRIORITY_ICONS[pKey];
                        const PIcon = pConf.icon;
                        return (
                          <div
                            key={pKey}
                            onClick={() => handleUpdatePriority(pKey)}
                            style={{
                              padding: '8px 12px',
                              fontSize: 13,
                              fontWeight: 600,
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              gap: 8,
                            }}
                          >
                            <PIcon size={14} color={pConf.color} />
                            <span>{pConf.label}</span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>

              {/* Story Points */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Story Points</span>
                <input
                  type="number"
                  step="0.5"
                  defaultValue={item?.storyPoints || ''}
                  onBlur={(e) => handleUpdateStoryPoints(e.target.value)}
                  placeholder="0"
                  style={{
                    width: '100%',
                    marginTop: 6,
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    fontWeight: 700,
                    color: '#0F172A',
                    outline: 'none',
                    backgroundColor: '#FFFFFF',
                  }}
                />
              </div>

              {/* Sprint info */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Sprint</span>
                <div
                  style={{
                    marginTop: 6,
                    padding: '8px 12px',
                    backgroundColor: '#EFF6FF',
                    borderRadius: 6,
                    border: '1px solid #BFDBFE',
                    fontSize: 13,
                    fontWeight: 600,
                    color: '#2563EB',
                  }}
                >
                  {item?.sprintName || 'Backlog'}
                </div>
              </div>

              {/* Component & Version */}
              {item?.componentName && (
                <div>
                  <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Component</span>
                  <div style={{ marginTop: 4, fontSize: 13, fontWeight: 600, color: '#334155' }}>
                    {item.componentName}
                  </div>
                </div>
              )}

              {/* Dates */}
              <div style={{ borderTop: '1px solid #E2E8F0', paddingTop: 16, display: 'flex', flexDirection: 'column', gap: 6, fontSize: 11, color: '#64748B' }}>
                <div>Created: {item?.createdAt?.substring(0, 10)}</div>
                <div>Updated: {item?.updatedAt?.substring(0, 10)}</div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
