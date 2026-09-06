import React, { useState, useEffect, useRef } from 'react';
import {
  X,
  Share2,
  Bookmark,
  CheckSquare,
  AlertCircle,
  CircleDot,
  GitCommitHorizontal,
  ChevronDown,
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
  User,
  Paperclip,
  UploadCloud,
  Download,
  Trash2,
  Edit2,
  CornerDownRight,
  Link2,
  ExternalLink,
  Image as ImageIcon,
  FileArchive,
  Code as CodeIcon,
  Search,
  Check,
  AlertTriangle,
  Lock
} from 'lucide-react';
import toast from 'react-hot-toast';
import { workItemApi } from '../../api.js';
import MemberSelect from '../common/MemberSelect.jsx';
import StoryPointsHelpTooltip from '../common/StoryPointsHelpTooltip.jsx';
import ResolveSubtasksModal from '../modals/ResolveSubtasksModal.jsx';
import { getPriorityConfig, PRIORITY_CONFIG } from '../../utils/priorityConfig.js';

const TYPE_ICONS = {
  EPIC: { icon: Bookmark, color: '#6554C0', label: 'Epic' },
  STORY: { icon: Bookmark, color: '#36B37E', label: 'Story' },
  TASK: { icon: CheckSquare, color: '#0052CC', label: 'Task' },
  BUG: { icon: CircleDot, color: '#E5493A', label: 'Bug' },
  SUBTASK: { icon: GitCommitHorizontal, color: '#4C9AFF', label: 'Sub-task' },
};

export default function WorkItemDetailModal({ itemId: initialItemId, isOpen, onClose, onItemUpdated, members = [] }) {
  const [currentId, setCurrentId] = useState(initialItemId);
  const [item, setItem] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('comments'); // 'comments' | 'history' | 'attachments'

  // Inline edit state
  const [isEditingSummary, setIsEditingSummary] = useState(false);
  const [summaryDraft, setSummaryDraft] = useState('');
  const [isEditingDescription, setIsEditingDescription] = useState(false);
  const [descriptionDraft, setDescriptionDraft] = useState('');
  const [newSubtaskTitle, setNewSubtaskTitle] = useState('');
  const [isAddingSubtask, setIsAddingSubtask] = useState(false);
  const [showStatusDropdown, setShowStatusDropdown] = useState(false);
  const [showPriorityDropdown, setShowPriorityDropdown] = useState(false);
  const [showResolveSubtasksModal, setShowResolveSubtasksModal] = useState(false);
  const [pendingTransitionId, setPendingTransitionId] = useState(null);
  const [isResolvingSubtasks, setIsResolvingSubtasks] = useState(false);

  // Collaboration state
  const [comments, setComments] = useState([]);
  const [commentDraft, setCommentDraft] = useState('');
  const [replyParentId, setReplyParentId] = useState(null);
  const [replyDraft, setReplyDraft] = useState('');
  const [editingCommentId, setEditingCommentId] = useState(null);
  const [editingCommentDraft, setEditingCommentDraft] = useState('');

  const [attachments, setAttachments] = useState([]);
  const [isDraggingOver, setIsDraggingOver] = useState(false);
  const [isUploadingFile, setIsUploadingFile] = useState(false);
  const fileInputRef = useRef(null);

  const [links, setLinks] = useState([]);
  const [showLinkModal, setShowLinkModal] = useState(false);
  const [linkRelationType, setLinkRelationType] = useState('RELATES_TO');
  const [linkSearchQuery, setLinkSearchQuery] = useState('');
  const [linkSearchResults, setLinkSearchResults] = useState([]);
  const [selectedTargetItem, setSelectedTargetItem] = useState(null);
  const [isSearchingLinks, setIsSearchingLinks] = useState(false);
  const [isCreatingLink, setIsCreatingLink] = useState(false);

  const [changelogs, setChangelogs] = useState([]);

  useEffect(() => {
    setCurrentId(initialItemId);
  }, [initialItemId]);

  const fetchDetail = async (idToFetch = currentId) => {
    if (!idToFetch) return;
    try {
      setLoading(true);
      const res = await workItemApi.getById(idToFetch);
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

  const fetchComments = async (idToFetch = currentId) => {
    if (!idToFetch) return;
    try {
      const res = await workItemApi.getComments(idToFetch);
      if (res && res.data) {
        setComments(res.data);
      }
    } catch (err) {
      console.debug('Comments fetch error:', err);
    }
  };

  const fetchAttachments = async (idToFetch = currentId) => {
    if (!idToFetch) return;
    try {
      const res = await workItemApi.getAttachments(idToFetch);
      if (res && res.data) {
        setAttachments(res.data);
      }
    } catch (err) {
      console.debug('Attachments fetch error:', err);
    }
  };

  const fetchLinks = async (idToFetch = currentId) => {
    if (!idToFetch) return;
    try {
      const res = await workItemApi.getLinks(idToFetch);
      if (res && res.data) {
        setLinks(res.data);
      }
    } catch (err) {
      console.debug('Links fetch error:', err);
    }
  };

  const fetchChangelogs = async (idToFetch = currentId) => {
    if (!idToFetch) return;
    try {
      const res = await workItemApi.getChangelogs(idToFetch);
      if (res && res.data) {
        setChangelogs(res.data);
      }
    } catch (err) {
      console.debug('Changelogs fetch error:', err);
    }
  };

  useEffect(() => {
    if (isOpen && currentId) {
      fetchDetail(currentId);
      fetchComments(currentId);
      fetchAttachments(currentId);
      fetchLinks(currentId);
      fetchChangelogs(currentId);
    }
  }, [isOpen, currentId]);

  if (!isOpen) return null;

  // ─── WorkItem Basic Mutators ──────────────────────────────────────────────

  const handleSaveSummary = async () => {
    if (!summaryDraft.trim() || summaryDraft === item?.summary) {
      setIsEditingSummary(false);
      return;
    }
    try {
      const res = await workItemApi.update(currentId, { summary: summaryDraft.trim() });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật tiêu đề');
        fetchChangelogs(currentId);
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
      const res = await workItemApi.update(currentId, { description: descriptionDraft });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật mô tả');
        fetchChangelogs(currentId);
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      toast.error('Lỗi khi cập nhật mô tả');
    } finally {
      setIsEditingDescription(false);
    }
  };

  const handleExecuteTransition = async (transitionId, resolveAllSubtasks = false) => {
    if (!transitionId) return;
    try {
      if (resolveAllSubtasks) {
        setIsResolvingSubtasks(true);
      }
      setShowStatusDropdown(false);
      const res = await workItemApi.executeTransition(currentId, transitionId, { comment: '', resolveAllSubtasks });
      if (res && res.data) {
        toast.success(resolveAllSubtasks ? 'Đã hoàn thành tất cả nhiệm vụ con và công việc cha!' : 'Đã chuyển trạng thái thành công');
        setShowResolveSubtasksModal(false);
        setPendingTransitionId(null);
        fetchDetail(currentId);
        fetchChangelogs(currentId);
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      console.error('Failed to transition item:', err);
      toast.error(err.response?.data?.message || err.message || 'Không thể chuyển trạng thái');
    } finally {
      setIsResolvingSubtasks(false);
    }
  };

  const handleUpdatePriority = async (newPriority) => {
    try {
      setShowPriorityDropdown(false);
      const res = await workItemApi.update(currentId, { priority: newPriority });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật độ ưu tiên');
        fetchChangelogs(currentId);
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      toast.error('Lỗi cập nhật độ ưu tiên');
    }
  };

  const handleUpdateAssignee = async (newAssigneeId) => {
    try {
      const res = await workItemApi.update(currentId, { assigneeId: newAssigneeId || null });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật người thực hiện');
        fetchChangelogs(currentId);
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      console.error('Failed to update assignee:', err);
      toast.error('Lỗi khi cập nhật người thực hiện');
    }
  };

  const handleUpdateStoryPoints = async (val) => {
    const num = parseFloat(val);
    if (isNaN(num) && val !== '') return;
    try {
      const res = await workItemApi.update(currentId, { storyPoints: val === '' ? null : num });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật Story Points');
        fetchChangelogs(currentId);
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      toast.error('Lỗi cập nhật Story Points');
    }
  };

  const handleUpdateDueDate = async (newDate) => {
    try {
      const res = await workItemApi.update(currentId, { dueDate: newDate || null });
      if (res && res.data) {
        setItem(res.data);
        toast.success('Đã cập nhật hạn chót');
        fetchChangelogs(currentId);
        if (onItemUpdated) onItemUpdated(res.data);
      }
    } catch (err) {
      console.error('Failed to update due date:', err);
      toast.error('Lỗi khi cập nhật hạn chót');
    }
  };

  const handleAddSubtask = async (e) => {
    e.preventDefault();
    if (!newSubtaskTitle.trim()) return;
    try {
      await workItemApi.createSubtask(currentId, { summary: newSubtaskTitle.trim() });
      toast.success('Đã thêm Sub-task mới');
      setNewSubtaskTitle('');
      setIsAddingSubtask(false);
      fetchDetail(currentId);
      if (onItemUpdated) onItemUpdated();
    } catch (err) {
      toast.error('Lỗi khi tạo Sub-task');
    }
  };

  // ─── Step 5.4: Comments Logic ──────────────────────────────────────────────

  const handleAddComment = async (e) => {
    e.preventDefault();
    if (!commentDraft.trim()) return;
    try {
      await workItemApi.addComment(currentId, { body: commentDraft.trim() });
      toast.success('Đã gửi bình luận');
      setCommentDraft('');
      fetchComments(currentId);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Lỗi khi gửi bình luận');
    }
  };

  const handleAddReply = async (parentCommentId) => {
    if (!replyDraft.trim()) return;
    try {
      await workItemApi.addComment(currentId, { body: replyDraft.trim(), parentCommentId });
      toast.success('Đã trả lời bình luận');
      setReplyDraft('');
      setReplyParentId(null);
      fetchComments(currentId);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Lỗi khi gửi phản hồi');
    }
  };

  const handleSaveEditComment = async (commentId) => {
    if (!editingCommentDraft.trim()) return;
    try {
      await workItemApi.updateComment(currentId, commentId, { body: editingCommentDraft.trim() });
      toast.success('Đã cập nhật bình luận');
      setEditingCommentId(null);
      setEditingCommentDraft('');
      fetchComments(currentId);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Không thể cập nhật bình luận');
    }
  };

  const handleDeleteComment = async (commentId) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa bình luận này?')) return;
    try {
      await workItemApi.deleteComment(currentId, commentId);
      toast.success('Đã xóa bình luận');
      fetchComments(currentId);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Không thể xóa bình luận');
    }
  };

  // ─── Step 5.4: Attachments Logic ───────────────────────────────────────────

  const handleFileUpload = async (files) => {
    if (!files || files.length === 0) return;
    setIsUploadingFile(true);
    try {
      for (let i = 0; i < files.length; i++) {
        const formData = new FormData();
        formData.append('file', files[i]);
        await workItemApi.uploadAttachment(currentId, formData);
      }
      toast.success('Đã tải lên tệp đính kèm');
      fetchAttachments(currentId);
      fetchChangelogs(currentId);
    } catch (err) {
      console.error('File upload error:', err);
      toast.error(err.response?.data?.message || 'Không thể tải lên tệp tin');
    } finally {
      setIsUploadingFile(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleDeleteAttachment = async (attachmentId) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa tệp đính kèm này?')) return;
    try {
      await workItemApi.deleteAttachment(currentId, attachmentId);
      toast.success('Đã xóa tệp đính kèm');
      fetchAttachments(currentId);
      fetchChangelogs(currentId);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Không thể xóa tệp tin');
    }
  };

  const getFileIcon = (contentType = '', fileName = '') => {
    const ext = fileName.split('.').pop()?.toLowerCase();
    if (contentType.startsWith('image/') || ['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp'].includes(ext)) {
      return ImageIcon;
    }
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) {
      return FileArchive;
    }
    if (['js', 'jsx', 'ts', 'tsx', 'json', 'html', 'css', 'java', 'py', 'sql'].includes(ext)) {
      return CodeIcon;
    }
    return FileText;
  };

  // ─── Step 5.4: Linked Issues Logic ─────────────────────────────────────────

  const handleSearchLinkCandidates = async (query) => {
    setLinkSearchQuery(query);
    if (!query.trim() || !item?.projectId) {
      setLinkSearchResults([]);
      return;
    }
    setIsSearchingLinks(true);
    try {
      const res = await workItemApi.listAll({
        projectId: item.projectId,
        search: query.trim(),
        size: 10,
      });
      const list = res?.data?.content || res?.data || [];
      // Exclude: current item, parent item, and direct subtasks/children (Anti-Self-Parent rule)
      const filtered = list.filter((it) => {
        if (it.id === currentId) return false;
        if (item?.parentId && it.id === item.parentId) return false;
        if (item?.subtasks?.some((st) => st.id === it.id)) return false;
        if (item?.childStories?.some((cs) => cs.id === it.id)) return false;
        return true;
      });
      setLinkSearchResults(filtered);
    } catch (err) {
      console.debug('Failed to search link candidates:', err);
    } finally {
      setIsSearchingLinks(false);
    }
  };

  const handleCreateLink = async () => {
    if (!selectedTargetItem) {
      toast.error('Vui lòng chọn một công việc để liên kết');
      return;
    }

    // Scale Mismatch check for BLOCKS between Epic and Subtask
    const isCurrentEpic = item?.hierarchyLevel === 1 || item?.typeCode === 'EPIC';
    const isCurrentSubtask = item?.hierarchyLevel === -1 || item?.isSubtask || item?.typeCode === 'SUBTASK';
    const isTargetEpic = selectedTargetItem?.hierarchyLevel === 1 || selectedTargetItem?.typeCode === 'EPIC';
    const isTargetSubtask = selectedTargetItem?.hierarchyLevel === -1 || selectedTargetItem?.isSubtask || selectedTargetItem?.typeCode === 'SUBTASK';

    if (linkRelationType === 'BLOCKS' && ((isCurrentEpic && isTargetSubtask) || (isCurrentSubtask && isTargetEpic))) {
      toast.error('Không thể tạo liên kết BLOCKS trực tiếp giữa Epic và Sub-task (Scale Mismatch). Vui lòng liên kết ở cấp độ Story/Task.');
      return;
    }

    setIsCreatingLink(true);
    try {
      await workItemApi.createLink(currentId, {
        targetItemId: selectedTargetItem.id,
        linkType: linkRelationType,
      });
      toast.success('Đã liên kết công việc thành công');
      setShowLinkModal(false);
      setSelectedTargetItem(null);
      setLinkSearchQuery('');
      setLinkSearchResults([]);
      fetchLinks(currentId);
      fetchChangelogs(currentId);
    } catch (err) {
      console.error('Link creation error:', err);
      toast.error(err.response?.data?.message || err.message || 'Không thể tạo liên kết công việc');
    } finally {
      setIsCreatingLink(false);
    }
  };

  const handleDeleteLink = async (linkId) => {
    if (!window.confirm('Bạn có chắc muốn gỡ bỏ liên kết này?')) return;
    try {
      await workItemApi.deleteLink(currentId, linkId);
      toast.success('Đã xóa liên kết');
      fetchLinks(currentId);
      fetchChangelogs(currentId);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Không thể xóa liên kết');
    }
  };

  // ─── Helpers ───────────────────────────────────────────────────────────────

  const formatRelativeTime = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const diffSeconds = Math.floor((now - date) / 1000);

    if (diffSeconds < 60) return 'Vừa xong';
    const diffMinutes = Math.floor(diffSeconds / 60);
    if (diffMinutes < 60) return `${diffMinutes} phút trước`;
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours} giờ trước`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays} ngày trước`;
    return date.toLocaleDateString('vi-VN');
  };

  const priorityInfo = getPriorityConfig(item?.priority);
  const PriorityIcon = priorityInfo.icon;
  const todayStr = new Date().toISOString().substring(0, 10);
  const isOverdue = item?.dueDate && item.dueDate < todayStr && item?.statusCategory !== 'DONE';
  const typeCode = item?.typeCode?.toUpperCase() || 'TASK';
  const typeInfo = TYPE_ICONS[typeCode] || TYPE_ICONS.TASK;
  const TypeIcon = typeInfo.icon;

  const subtaskTotal = item?.subtasks?.length || 0;
  const subtaskDone = item?.subtasks?.filter((s) => s.statusCategory === 'DONE').length || 0;
  const progressPercent = subtaskTotal > 0 ? (subtaskDone / subtaskTotal) * 100 : 0;

  // Group links by relationship description
  const groupedLinks = links.reduce((acc, link) => {
    const groupKey = link.relationshipDescription || 'Liên kết';
    if (!acc[groupKey]) acc[groupKey] = [];
    acc[groupKey].push(link);
    return acc;
  }, {});

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.55)',
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
          maxWidth: 1160,
          height: '92vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          overflow: 'hidden',
          position: 'relative',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* ─── Top Header Bar ─── */}
        <div
          style={{
            height: 54,
            minHeight: 54,
            padding: '0 24px',
            borderBottom: '1px solid #E2E8F0',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            backgroundColor: '#FAFAFA',
          }}
        >
          {/* Breadcrumbs & Issue Type Badge */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 13, color: '#64748B', flexWrap: 'wrap' }}>
            <span style={{ fontWeight: 600, color: '#0F172A' }}>{item?.projectKey || 'PROJECT'}</span>
            <span>/</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontWeight: 700, color: '#2563EB', fontSize: 14 }}>{item?.code || '...'}</span>
            </div>

            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 5,
                padding: '3px 8px',
                borderRadius: 6,
                backgroundColor: typeInfo.color ? `${typeInfo.color}15` : '#EFF6FF',
                border: `1px solid ${typeInfo.color ? `${typeInfo.color}40` : '#BFDBFE'}`,
                color: typeInfo.color || '#2563EB',
                fontSize: 12,
                fontWeight: 700,
              }}
            >
              <TypeIcon size={14} color={typeInfo.color} />
              <span>{item?.typeName || typeInfo.label}</span>
            </div>

            {item?.parentCode && (
              <div
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  fontSize: 11,
                  color: '#6554C0',
                  backgroundColor: '#F3F0FF',
                  border: '1px solid #E9D5FF',
                  padding: '2px 8px',
                  borderRadius: 4,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
                onClick={() => {
                  if (item.parentId) setCurrentId(item.parentId);
                }}
                title={`Mở công việc cha: ${item.parentSummary || item.parentCode}`}
              >
                <span>Cha:</span>
                <span style={{ fontWeight: 700 }}>{item.parentCode}</span>
                {item.parentSummary && (
                  <span style={{ color: '#64748B', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    - {item.parentSummary}
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Action Buttons */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <button
              onClick={() => setShowLinkModal(true)}
              style={{
                border: '1px solid #CBD5E1',
                backgroundColor: '#FFFFFF',
                padding: '5px 10px',
                borderRadius: 6,
                cursor: 'pointer',
                color: '#334155',
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                fontSize: 12,
                fontWeight: 600,
              }}
              title="Thêm liên kết công việc"
            >
              <Link2 size={14} color="#2563EB" />
              <span>Link issue</span>
            </button>
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
            {/* ─── Left Column: Main Content (64% Width) ─── */}
            <div
              style={{
                flex: '0 0 64%',
                padding: '24px 28px',
                overflowY: 'auto',
                borderRight: '1px solid #E2E8F0',
                display: 'flex',
                flexDirection: 'column',
                gap: 20,
              }}
            >
              {/* ─── Warning Banners ─── */}
              {item?.isBlocked && (
                <div
                  style={{
                    padding: '12px 16px',
                    backgroundColor: '#FEF2F2',
                    border: '1px solid #FCA5A5',
                    borderRadius: 8,
                    color: '#991B1B',
                    fontSize: 13,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 6,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700 }}>
                    <Lock size={16} color="#DC2626" />
                    <span>CÔNG VIỆC BỊ CẢN TRỞ (BLOCKED)</span>
                  </div>
                  <div style={{ fontSize: 12, color: '#B91C1C', lineHeight: 1.5 }}>
                    Công việc này không thể chuyển sang <strong>In Progress</strong> hoặc <strong>Done</strong> vì đang bị chặn bởi các công việc chưa hoàn thành:
                  </div>
                  {item?.blockingWorkItems && item.blockingWorkItems.length > 0 && (
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 2 }}>
                      {item.blockingWorkItems.map((b) => (
                        <span
                          key={b.id}
                          onClick={() => setCurrentId(b.id)}
                          style={{
                            padding: '3px 8px',
                            borderRadius: 6,
                            backgroundColor: '#FFFFFF',
                            border: '1px solid #FECACA',
                            color: '#B91C1C',
                            fontWeight: 600,
                            fontSize: 12,
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                          }}
                          title={`Mở công việc cản trở: ${b.summary}`}
                        >
                          <Lock size={11} />
                          <span>[{b.code}] {b.summary}</span>
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {item?.hasTimelineConflict && (
                <div
                  style={{
                    padding: '12px 16px',
                    backgroundColor: '#FFFBEB',
                    border: '1px solid #FCD34D',
                    borderRadius: 8,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 12,
                    color: '#92400E',
                    fontSize: 13,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <AlertTriangle size={18} color="#D97706" style={{ flexShrink: 0 }} />
                    <div>
                      <strong>Cảnh báo lệch hạn: </strong>
                      <span>{item.timelineConflictMessage || 'Hạn chót của công việc con vượt quá hạn chót của công việc cha'}</span>
                    </div>
                  </div>
                  {item?.parentId && item?.parent?.dueDate && (
                    <button
                      type="button"
                      onClick={async () => {
                        try {
                          await workItemApi.update(currentId, { dueDate: item.parent.dueDate });
                          toast.success(`Đã đồng bộ hạn chót về Task cha (${item.parent.dueDate})`);
                          fetchDetail(currentId);
                          fetchChangelogs(currentId);
                        } catch (err) {
                          toast.error('Lỗi khi đồng bộ hạn chót');
                        }
                      }}
                      style={{
                        padding: '5px 12px',
                        borderRadius: 6,
                        border: '1px solid #F59E0B',
                        backgroundColor: '#FFFFFF',
                        color: '#B45309',
                        fontSize: 12,
                        fontWeight: 700,
                        cursor: 'pointer',
                        whiteSpace: 'nowrap',
                        boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
                      }}
                    >
                      Đồng bộ hạn về Task cha ({item.parent.dueDate})
                    </button>
                  )}
                </div>
              )}

              {item?.hasDependencyConflict && (
                <div
                  style={{
                    padding: '10px 16px',
                    backgroundColor: '#EFF6FF',
                    border: '1px solid #BFDBFE',
                    borderRadius: 8,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    color: '#1E40AF',
                    fontSize: 13,
                  }}
                >
                  <AlertCircle size={17} color="#2563EB" style={{ flexShrink: 0 }} />
                  <span>{item.dependencyConflictMessage || 'Xung đột thời gian: Blocker kết thúc sau ngày bắt đầu dự kiến của công việc này'}</span>
                </div>
              )}

              {/* Summary */}
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
                      rows={5}
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
                      minHeight: 56,
                      lineHeight: 1.6,
                      whiteSpace: 'pre-wrap',
                      border: '1px solid #E2E8F0',
                    }}
                  >
                    {item?.description || 'Chưa có mô tả chi tiết. Bấm vào đây để thêm...'}
                  </div>
                )}
              </div>

              {/* ─── Linked Issues Section (Step 5.4) ─── */}
              {links.length > 0 && (
                <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <Link2 size={16} color="#2563EB" />
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#334155' }}>Linked Issues</span>
                      <span style={{ fontSize: 11, backgroundColor: '#EFF6FF', color: '#2563EB', padding: '1px 6px', borderRadius: 10, fontWeight: 700 }}>
                        {links.length}
                      </span>
                    </div>
                    <button
                      onClick={() => setShowLinkModal(true)}
                      style={{
                        border: 'none',
                        background: 'transparent',
                        color: '#2563EB',
                        fontSize: 12,
                        fontWeight: 600,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 3,
                      }}
                    >
                      <Plus size={13} /> Thêm liên kết
                    </button>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    {Object.entries(groupedLinks).map(([groupTitle, groupItems]) => (
                      <div key={groupTitle} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>
                          {groupTitle} ({groupItems.length})
                        </span>
                        {groupItems.map((lnk) => {
                          const linkedTypeCode = lnk.linkedItemTypeCode?.toUpperCase() || 'TASK';
                          const LIcon = (TYPE_ICONS[linkedTypeCode] || TYPE_ICONS.TASK).icon;
                          const isDone = lnk.linkedItemStatusCategory === 'DONE';

                          return (
                            <div
                              key={lnk.id}
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
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1, minWidth: 0 }}>
                                <LIcon size={15} color={lnk.linkedItemTypeColor || '#0052CC'} />
                                <span
                                  onClick={() => setCurrentId(lnk.linkedItemId)}
                                  style={{
                                    fontWeight: 700,
                                    color: '#2563EB',
                                    cursor: 'pointer',
                                    fontSize: 12,
                                  }}
                                  title="Xem công việc này"
                                >
                                  {lnk.linkedItemCode}
                                </span>
                                <span
                                  style={{
                                    color: isDone ? '#94A3B8' : '#0F172A',
                                    textDecoration: isDone ? 'line-through' : 'none',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                  }}
                                >
                                  {lnk.linkedItemSummary}
                                </span>
                              </div>

                              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                <span
                                  style={{
                                    fontSize: 11,
                                    fontWeight: 600,
                                    padding: '2px 8px',
                                    borderRadius: 4,
                                    backgroundColor: lnk.linkedItemStatusColorHex ? `${lnk.linkedItemStatusColorHex}15` : '#EFF6FF',
                                    color: lnk.linkedItemStatusColorHex || '#2563EB',
                                  }}
                                >
                                  {lnk.linkedItemStatusName || 'TO DO'}
                                </span>
                                <button
                                  onClick={() => handleDeleteLink(lnk.id)}
                                  style={{
                                    border: 'none',
                                    background: 'transparent',
                                    cursor: 'pointer',
                                    color: '#94A3B8',
                                    padding: 2,
                                  }}
                                  title="Gỡ bỏ liên kết"
                                >
                                  <Trash2 size={14} />
                                </button>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Sub-tasks Section (Only for Level 0 Standard items, not Epics) */}
              {(item?.hierarchyLevel !== 1 && item?.typeCode !== 'EPIC') && (
                <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 16 }}>
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
                            <span
                              onClick={() => setCurrentId(sub.id)}
                              style={{ fontSize: 11, fontWeight: 700, color: '#2563EB', cursor: 'pointer' }}
                            >
                              {sub.code}
                            </span>
                            <span
                              style={{
                                color: isDone ? '#94A3B8' : '#0F172A',
                                textDecoration: isDone ? 'line-through' : 'none',
                              }}
                            >
                              {sub.summary}
                            </span>
                          </div>
                          <span
                            style={{
                              fontSize: 11,
                              fontWeight: 600,
                              padding: '2px 8px',
                              borderRadius: 12,
                              backgroundColor: sub.statusColorHex ? `${sub.statusColorHex}20` : '#E2E8F0',
                              color: sub.statusColorHex || '#475569',
                            }}
                          >
                            {sub.statusName}
                          </span>
                        </div>
                      );
                    })}
                  </div>

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
              )}

              {/* ─── Epic Child Stories & Progress (Step 5.5) ─── */}
              {(item?.hierarchyLevel === 1 || item?.typeCode === 'EPIC') && (
                <div style={{ border: '1px solid #E9D5FF', backgroundColor: '#FAF5FF', borderRadius: 8, padding: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <Bookmark size={16} color="#6554C0" />
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#581C87' }}>
                        Child Stories trong Epic ({item?.childStories?.length || 0})
                      </span>
                    </div>
                    <div style={{ fontSize: 12, fontWeight: 700, color: '#6554C0' }}>
                      {item?.doneStoryPoints || 0} / {item?.totalStoryPoints || 0} Story Points hoàn thành
                    </div>
                  </div>

                  {/* Epic Progress Bar */}
                  {item?.totalStoryPoints > 0 && (
                    <div style={{ width: '100%', height: 6, backgroundColor: '#E9D5FF', borderRadius: 3, overflow: 'hidden', marginBottom: 12 }}>
                      <div
                        style={{
                          width: `${Math.min(100, Math.round(((item.doneStoryPoints || 0) / item.totalStoryPoints) * 100))}%`,
                          height: '100%',
                          backgroundColor: '#6554C0',
                          transition: 'width 0.3s ease',
                        }}
                      />
                    </div>
                  )}

                  {/* Child Stories List */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    {item?.childStories && item.childStories.length > 0 ? (
                      item.childStories.map((cs) => {
                        const isDone = cs.statusCategory === 'DONE';
                        return (
                          <div
                            key={cs.id}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              padding: '8px 12px',
                              backgroundColor: '#FFFFFF',
                              borderRadius: 6,
                              border: '1px solid #E9D5FF',
                              fontSize: 13,
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              <span
                                onClick={() => setCurrentId(cs.id)}
                                style={{ fontSize: 12, fontWeight: 700, color: '#6554C0', cursor: 'pointer' }}
                              >
                                {cs.code}
                              </span>
                              <span style={{ color: isDone ? '#94A3B8' : '#0F172A', textDecoration: isDone ? 'line-through' : 'none' }}>
                                {cs.summary}
                              </span>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              {cs.storyPoints != null && (
                                <span style={{ fontSize: 11, fontWeight: 600, padding: '1px 6px', borderRadius: 4, backgroundColor: '#F3F0FF', color: '#6554C0' }}>
                                  {cs.storyPoints} SP
                                </span>
                              )}
                              <span
                                style={{
                                  fontSize: 11,
                                  fontWeight: 600,
                                  padding: '2px 8px',
                                  borderRadius: 4,
                                  backgroundColor: cs.statusColorHex ? `${cs.statusColorHex}15` : '#EFF6FF',
                                  color: cs.statusColorHex || '#2563EB',
                                }}
                              >
                                {cs.statusName || 'TO DO'}
                              </span>
                            </div>
                          </div>
                        );
                      })
                    ) : (
                      <div style={{ fontSize: 12, color: '#7E22CE', fontStyle: 'italic' }}>
                        Chưa có Story/Task con nào được gắn vào Epic này.
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* ─── Activity Tabs (Comments, Attachments, History) ─── */}
              <div style={{ marginTop: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 20, borderBottom: '1px solid #E2E8F0', paddingBottom: 8, marginBottom: 16 }}>
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
                    onClick={() => setActiveTab('attachments')}
                    style={{
                      border: 'none',
                      background: 'transparent',
                      fontSize: 13,
                      fontWeight: 600,
                      color: activeTab === 'attachments' ? '#2563EB' : '#64748B',
                      cursor: 'pointer',
                      borderBottom: activeTab === 'attachments' ? '2px solid #2563EB' : 'none',
                      paddingBottom: 4,
                    }}
                  >
                    Attachments ({attachments.length})
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

                {/* TAB 1: Threaded Comments (Step 5.4) */}
                {activeTab === 'comments' && (
                  <div>
                    <form onSubmit={handleAddComment} style={{ marginBottom: 20 }}>
                      <textarea
                        rows={3}
                        value={commentDraft}
                        onChange={(e) => setCommentDraft(e.target.value)}
                        placeholder="Thêm bình luận trao đổi (Hỗ trợ Markdown)..."
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

                    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                      {comments.length === 0 ? (
                        <div style={{ color: '#94A3B8', fontSize: 13, textAlign: 'center', padding: '16px 0' }}>
                          Chưa có bình luận nào. Hãy là người đầu tiên trao đổi!
                        </div>
                      ) : (
                        comments.map((c) => (
                          <div
                            key={c.id}
                            style={{
                              padding: 14,
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
                                    width: 26,
                                    height: 26,
                                    borderRadius: 13,
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
                                {c.isEdited && (
                                  <span style={{ fontSize: 10, color: '#94A3B8', fontStyle: 'italic' }}>(đã sửa)</span>
                                )}
                              </div>
                              <span style={{ fontSize: 11, color: '#94A3B8' }}>{formatRelativeTime(c.createdAt)}</span>
                            </div>

                            {editingCommentId === c.id ? (
                              <div style={{ marginTop: 8 }}>
                                <textarea
                                  rows={3}
                                  value={editingCommentDraft}
                                  onChange={(e) => setEditingCommentDraft(e.target.value)}
                                  style={{
                                    width: '100%',
                                    fontSize: 13,
                                    padding: 8,
                                    borderRadius: 6,
                                    border: '1px solid #2563EB',
                                    outline: 'none',
                                    fontFamily: 'inherit',
                                  }}
                                />
                                <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                                  <button
                                    onClick={() => handleSaveEditComment(c.id)}
                                    style={{
                                      backgroundColor: '#2563EB',
                                      color: '#FFF',
                                      border: 'none',
                                      padding: '4px 10px',
                                      borderRadius: 4,
                                      fontSize: 12,
                                      fontWeight: 600,
                                      cursor: 'pointer',
                                    }}
                                  >
                                    Lưu
                                  </button>
                                  <button
                                    onClick={() => setEditingCommentId(null)}
                                    style={{
                                      backgroundColor: '#F1F5F9',
                                      color: '#475569',
                                      border: 'none',
                                      padding: '4px 10px',
                                      borderRadius: 4,
                                      fontSize: 12,
                                      cursor: 'pointer',
                                    }}
                                  >
                                    Hủy
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <div style={{ color: '#334155', lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>{c.body}</div>
                            )}

                            {/* Comment Actions */}
                            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 8, fontSize: 11, color: '#64748B' }}>
                              <button
                                onClick={() => {
                                  setReplyParentId(replyParentId === c.id ? null : c.id);
                                  setReplyDraft('');
                                }}
                                style={{
                                  border: 'none',
                                  background: 'transparent',
                                  color: '#2563EB',
                                  fontWeight: 600,
                                  cursor: 'pointer',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 3,
                                  padding: 0,
                                }}
                              >
                                <CornerDownRight size={12} /> Trả lời
                              </button>
                              <button
                                onClick={() => {
                                  setEditingCommentId(c.id);
                                  setEditingCommentDraft(c.body);
                                }}
                                style={{ border: 'none', background: 'transparent', color: '#64748B', cursor: 'pointer', padding: 0 }}
                              >
                                Chỉnh sửa
                              </button>
                              <button
                                onClick={() => handleDeleteComment(c.id)}
                                style={{ border: 'none', background: 'transparent', color: '#DC2626', cursor: 'pointer', padding: 0 }}
                              >
                                Xóa
                              </button>
                            </div>

                            {/* Inline Reply Form */}
                            {replyParentId === c.id && (
                              <div style={{ marginTop: 10, paddingLeft: 12, borderLeft: '2px solid #BFDBFE' }}>
                                <textarea
                                  rows={2}
                                  value={replyDraft}
                                  onChange={(e) => setReplyDraft(e.target.value)}
                                  placeholder={`Trả lời ${c.authorName}...`}
                                  autoFocus
                                  style={{
                                    width: '100%',
                                    fontSize: 12,
                                    padding: 8,
                                    borderRadius: 6,
                                    border: '1px solid #CBD5E1',
                                    outline: 'none',
                                    fontFamily: 'inherit',
                                  }}
                                />
                                <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                                  <button
                                    onClick={() => handleAddReply(c.id)}
                                    disabled={!replyDraft.trim()}
                                    style={{
                                      backgroundColor: '#2563EB',
                                      color: '#FFF',
                                      border: 'none',
                                      padding: '4px 10px',
                                      borderRadius: 4,
                                      fontSize: 11,
                                      fontWeight: 600,
                                      cursor: 'pointer',
                                    }}
                                  >
                                    Phản hồi
                                  </button>
                                  <button
                                    onClick={() => setReplyParentId(null)}
                                    style={{
                                      backgroundColor: '#F1F5F9',
                                      color: '#475569',
                                      border: 'none',
                                      padding: '4px 10px',
                                      borderRadius: 4,
                                      fontSize: 11,
                                      cursor: 'pointer',
                                    }}
                                  >
                                    Hủy
                                  </button>
                                </div>
                              </div>
                            )}

                            {/* Threaded Nested Replies */}
                            {c.replies && c.replies.length > 0 && (
                              <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 10, paddingLeft: 16, borderLeft: '2px solid #E2E8F0' }}>
                                {c.replies.map((reply) => (
                                  <div
                                    key={reply.id}
                                    style={{
                                      padding: 10,
                                      backgroundColor: '#FFFFFF',
                                      borderRadius: 6,
                                      border: '1px solid #E2E8F0',
                                    }}
                                  >
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                        <div
                                          style={{
                                            width: 22,
                                            height: 22,
                                            borderRadius: 11,
                                            backgroundColor: '#3B82F6',
                                            color: '#FFF',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            fontSize: 10,
                                            fontWeight: 700,
                                          }}
                                        >
                                          {reply.authorName ? reply.authorName.charAt(0).toUpperCase() : 'U'}
                                        </div>
                                        <span style={{ fontWeight: 700, fontSize: 12, color: '#0F172A' }}>{reply.authorName}</span>
                                      </div>
                                      <span style={{ fontSize: 10, color: '#94A3B8' }}>{formatRelativeTime(reply.createdAt)}</span>
                                    </div>
                                    <div style={{ color: '#334155', fontSize: 12, lineHeight: 1.4 }}>{reply.body}</div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 6, fontSize: 10, color: '#64748B' }}>
                                      <button
                                        onClick={() => handleDeleteComment(reply.id)}
                                        style={{ border: 'none', background: 'transparent', color: '#DC2626', cursor: 'pointer', padding: 0 }}
                                      >
                                        Xóa
                                      </button>
                                    </div>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                )}

                {/* TAB 2: Attachments Dropzone (Step 5.4) */}
                {activeTab === 'attachments' && (
                  <div>
                    {/* Drag-and-drop Dropzone */}
                    <div
                      onDragOver={(e) => {
                        e.preventDefault();
                        setIsDraggingOver(true);
                      }}
                      onDragLeave={() => setIsDraggingOver(false)}
                      onDrop={(e) => {
                        e.preventDefault();
                        setIsDraggingOver(false);
                        handleFileUpload(e.dataTransfer.files);
                      }}
                      onClick={() => fileInputRef.current?.click()}
                      style={{
                        border: `2px dashed ${isDraggingOver ? '#2563EB' : '#CBD5E1'}`,
                        backgroundColor: isDraggingOver ? '#EFF6FF' : '#F8FAFC',
                        borderRadius: 8,
                        padding: '24px 16px',
                        textAlign: 'center',
                        cursor: 'pointer',
                        transition: 'all 0.15s ease',
                        marginBottom: 16,
                      }}
                    >
                      <input
                        type="file"
                        ref={fileInputRef}
                        multiple
                        onChange={(e) => handleFileUpload(e.target.files)}
                        style={{ display: 'none' }}
                      />
                      <UploadCloud size={32} color="#2563EB" style={{ margin: '0 auto 8px' }} />
                      <div style={{ fontSize: 13, fontWeight: 700, color: '#1E293B' }}>
                        {isUploadingFile ? 'Đang tải lên tệp...' : 'Kéo thả tệp vào đây hoặc Bấm để tải lên'}
                      </div>
                      <div style={{ fontSize: 11, color: '#64748B', marginTop: 4 }}>
                        Hỗ trợ hình ảnh (PNG, JPG, SVG), tài liệu PDF, DOCX, ZIP tối đa 50MB
                      </div>
                    </div>

                    {/* Attachment List / Grid */}
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 12 }}>
                      {attachments.map((att) => {
                        const FIcon = getFileIcon(att.contentType, att.fileName);
                        const isImg = att.contentType?.startsWith('image/');

                        return (
                          <div
                            key={att.id}
                            style={{
                              border: '1px solid #E2E8F0',
                              borderRadius: 8,
                              overflow: 'hidden',
                              backgroundColor: '#FFFFFF',
                              display: 'flex',
                              flexDirection: 'column',
                            }}
                          >
                            {isImg ? (
                              <div
                                style={{
                                  height: 100,
                                  backgroundColor: '#0F172A',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  overflow: 'hidden',
                                }}
                              >
                                <img
                                  src={att.fileUrl}
                                  alt={att.fileName}
                                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                  onError={(e) => {
                                    e.currentTarget.style.display = 'none';
                                  }}
                                />
                              </div>
                            ) : (
                              <div
                                style={{
                                  height: 60,
                                  backgroundColor: '#F1F5F9',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                }}
                              >
                                <FIcon size={28} color="#64748B" />
                              </div>
                            )}

                            <div style={{ padding: 10, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                              <div>
                                <div
                                  style={{
                                    fontSize: 12,
                                    fontWeight: 700,
                                    color: '#0F172A',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                  }}
                                  title={att.fileName}
                                >
                                  {att.fileName}
                                </div>
                                <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>
                                  {att.formattedFileSize} • {formatRelativeTime(att.uploadedAt)}
                                </div>
                              </div>

                              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 8, marginTop: 8, borderTop: '1px solid #F1F5F9', paddingTop: 6 }}>
                                <a
                                  href={att.downloadUrl || att.fileUrl}
                                  target="_blank"
                                  rel="noreferrer"
                                  style={{
                                    color: '#2563EB',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 3,
                                    fontSize: 11,
                                    textDecoration: 'none',
                                    fontWeight: 600,
                                  }}
                                >
                                  <Download size={12} /> Tải về
                                </a>
                                <button
                                  onClick={() => handleDeleteAttachment(att.id)}
                                  style={{
                                    border: 'none',
                                    background: 'transparent',
                                    color: '#DC2626',
                                    cursor: 'pointer',
                                    padding: 2,
                                  }}
                                  title="Xóa tệp"
                                >
                                  <Trash2 size={13} />
                                </button>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* TAB 3: Audit Trail / History Timeline (Step 5.3) */}
                {activeTab === 'history' && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    {changelogs.length === 0 ? (
                      <div style={{ color: '#94A3B8', fontSize: 13, textAlign: 'center', padding: '16px 0' }}>
                        Chưa có lịch sử thay đổi nào được ghi nhận.
                      </div>
                    ) : (
                      changelogs.map((log) => (
                        <div
                          key={log.id}
                          style={{
                            padding: '10px 14px',
                            borderLeft: '3px solid #2563EB',
                            backgroundColor: '#F8FAFC',
                            borderRadius: '0 6px 6px 0',
                            fontSize: 12,
                            color: '#334155',
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                            <span style={{ fontWeight: 700, color: '#0F172A' }}>{log.authorName || 'Người dùng'}</span>
                            <span style={{ fontSize: 11, color: '#94A3B8' }}>{formatRelativeTime(log.changedAt)}</span>
                          </div>

                          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                            <span>đã thay đổi</span>
                            <span style={{ fontWeight: 700, color: '#1E293B', backgroundColor: '#E2E8F0', padding: '1px 6px', borderRadius: 4 }}>
                              {log.fieldDisplayName || log.fieldName}
                            </span>
                            {log.oldValue && (
                              <>
                                <span>từ</span>
                                <span style={{ color: '#DC2626', textDecoration: 'line-through', backgroundColor: '#FEF2F2', padding: '1px 6px', borderRadius: 4 }}>
                                  {log.oldValue}
                                </span>
                              </>
                            )}
                            <span>thành</span>
                            <span style={{ color: '#059669', fontWeight: 600, backgroundColor: '#ECFDF5', padding: '1px 6px', borderRadius: 4 }}>
                              {log.newValue || '(trống)'}
                            </span>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* ─── Right Sidebar Column: Metadata & Attributes (36% Width) ─── */}
            <div
              style={{
                flex: '0 0 36%',
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
                        item.availableTransitions.map((t) => {
                          const tId = t.transitionId || t.id;
                          const isDoneTransition = t.toStatusCategory === 'DONE' || t.toStatusName?.toUpperCase() === 'DONE';
                          const incompleteDescendants = (item?.descendants && item.descendants.length > 0 ? item.descendants : (item?.subtasks || [])).filter(
                            (st) => st.statusCategory !== 'DONE'
                          );
                          const hasIncompleteSubtasks = incompleteDescendants.length > 0;

                          return (
                            <div
                              key={tId}
                              onClick={() => {
                                if (isDoneTransition && hasIncompleteSubtasks) {
                                  setShowStatusDropdown(false);
                                  setPendingTransitionId(tId);
                                  setShowResolveSubtasksModal(true);
                                } else {
                                  handleExecuteTransition(tId, false);
                                }
                              }}
                              style={{
                                padding: '8px 12px',
                                fontSize: 13,
                                fontWeight: 600,
                                color: t.toStatusColorHex || '#0F172A',
                                cursor: 'pointer',
                                borderBottom: '1px solid #F1F5F9',
                                transition: 'background-color 0.15s ease',
                              }}
                              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                            >
                              {t.name} (➔ {t.toStatusName})
                            </div>
                          );
                        })
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
                <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Độ ưu tiên (Priority)</span>
                <div style={{ position: 'relative', marginTop: 6 }}>
                  <button
                    onClick={() => setShowPriorityDropdown(!showPriorityDropdown)}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 8,
                      border: `1.5px solid ${priorityInfo.borderColor}`,
                      backgroundColor: priorityInfo.bgColor,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      cursor: 'pointer',
                      fontSize: 13,
                      fontWeight: 700,
                      color: priorityInfo.color,
                      transition: 'all 0.15s ease',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <PriorityIcon size={16} color={priorityInfo.color} />
                      <span>{priorityInfo.label}</span>
                      <span style={{ fontSize: 11, opacity: 0.75, fontWeight: 500 }}>({priorityInfo.subLabel})</span>
                    </div>
                    <ChevronDown size={14} color={priorityInfo.color} />
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
                        borderRadius: 8,
                        border: '1px solid #E2E8F0',
                        boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
                        zIndex: 20,
                        overflow: 'hidden',
                      }}
                    >
                      {Object.keys(PRIORITY_CONFIG).map((pKey) => {
                        const pConf = PRIORITY_CONFIG[pKey];
                        const PIcon = pConf.icon;
                        const isCurrent = item?.priority === pKey;
                        return (
                          <div
                            key={pKey}
                            onClick={() => handleUpdatePriority(pKey)}
                            style={{
                              padding: '9px 12px',
                              fontSize: 13,
                              fontWeight: isCurrent ? 700 : 500,
                              cursor: 'pointer',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              backgroundColor: isCurrent ? pConf.bgColor : 'transparent',
                              color: isCurrent ? pConf.color : '#0F172A',
                              borderBottom: '1px solid #F1F5F9',
                              transition: 'background-color 0.1s ease',
                            }}
                            onMouseEnter={(e) => {
                              if (!isCurrent) e.currentTarget.style.backgroundColor = '#F8FAFC';
                            }}
                            onMouseLeave={(e) => {
                              if (!isCurrent) e.currentTarget.style.backgroundColor = 'transparent';
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              <PIcon size={15} color={pConf.color} />
                              <span>{pConf.label}</span>
                              <span style={{ fontSize: 11, color: '#64748B' }}>- {pConf.subLabel}</span>
                            </div>
                            {isCurrent && <Check size={14} color={pConf.color} />}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>

              {/* Assignee Section */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Người thực hiện (Assignee)</span>
                <div style={{ marginTop: 6 }}>
                  <MemberSelect
                    members={members}
                    value={item?.assigneeId || ''}
                    onChange={handleUpdateAssignee}
                    placeholder="Chưa gán (Unassigned)"
                  />
                </div>
              </div>

              {/* Due Date Section */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Hạn chót (Due Date)</span>
                  {isOverdue && (
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
                        padding: '1px 6px',
                        borderRadius: 4,
                      }}
                    >
                      <AlertCircle size={10} /> Quá hạn
                    </span>
                  )}
                </div>
                <input
                  type="date"
                  value={item?.dueDate || ''}
                  onChange={(e) => handleUpdateDueDate(e.target.value)}
                  style={{
                    width: '100%',
                    marginTop: 6,
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: `1px solid ${isOverdue ? '#DC2626' : '#CBD5E1'}`,
                    backgroundColor: isOverdue ? '#FFF5F5' : '#FFFFFF',
                    fontSize: 13,
                    fontWeight: 600,
                    color: isOverdue ? '#DC2626' : '#0F172A',
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                />
              </div>

              {/* Story Points */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Story Points</span>
                  <StoryPointsHelpTooltip size={12} align="sidebar" />
                </div>
                {item?.hierarchyLevel === -1 || item?.isSubtask || item?.typeCode === 'SUBTASK' ? (
                  <div>
                    <input
                      type="text"
                      disabled
                      value="N/A"
                      title="Sub-task không tính Story Points trực tiếp"
                      style={{
                        width: '100%',
                        marginTop: 6,
                        padding: '8px 12px',
                        borderRadius: 6,
                        border: '1px solid #E2E8F0',
                        fontSize: 13,
                        fontWeight: 700,
                        color: '#94A3B8',
                        backgroundColor: '#F1F5F9',
                        cursor: 'not-allowed',
                        boxSizing: 'border-box',
                      }}
                    />
                    <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>
                      Sub-task không tính Story Points
                    </div>
                  </div>
                ) : item?.hierarchyLevel === 1 || item?.typeCode === 'EPIC' ? (
                  <div>
                    <input
                      type="text"
                      disabled
                      value={`${item?.doneStoryPoints || 0} / ${item?.totalStoryPoints || 0} pts`}
                      title="Epic tự động cuộn tổng Story Points từ các Story con"
                      style={{
                        width: '100%',
                        marginTop: 6,
                        padding: '8px 12px',
                        borderRadius: 6,
                        border: '1px solid #E2E8F0',
                        fontSize: 13,
                        fontWeight: 700,
                        color: '#6554C0',
                        backgroundColor: '#F3F0FF',
                        cursor: 'not-allowed',
                        boxSizing: 'border-box',
                      }}
                    />
                    <div style={{ fontSize: 11, color: '#6554C0', marginTop: 4 }}>
                      Roll-up tự động từ các Story con
                    </div>
                  </div>
                ) : (
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
                      boxSizing: 'border-box',
                    }}
                  />
                )}
              </div>

              {/* Time Tracking Section (Step 5.5 Roll-up) */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#64748B', textTransform: 'uppercase' }}>Time Tracking</span>
                <div style={{ marginTop: 6, padding: '10px 12px', backgroundColor: '#F8FAFC', borderRadius: 6, border: '1px solid #E2E8F0', fontSize: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                    <span style={{ color: '#64748B' }}>Đã làm (Logged):</span>
                    <strong style={{ color: '#0F172A' }}>
                      {Math.round(((item?.timeSpentSeconds || 0) / 3600) * 10) / 10}h
                    </strong>
                  </div>
                  {item?.subtasks && item.subtasks.length > 0 && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4, color: '#0284C7' }}>
                      <span>Tổng cuộn (Gồm Sub-tasks):</span>
                      <strong>
                        {Math.round(((item?.totalTimeSpentSeconds || 0) / 3600) * 10) / 10}h
                      </strong>
                    </div>
                  )}
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: '#64748B' }}>Còn lại (Remaining):</span>
                    <strong style={{ color: '#0F172A' }}>
                      {Math.round(((item?.remainingEstimateSeconds || 0) / 3600) * 10) / 10}h
                    </strong>
                  </div>
                </div>
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

        {/* ─── Link Issue Modal (Step 5.4) ─── */}
        {showLinkModal && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              backgroundColor: 'rgba(15, 23, 42, 0.4)',
              backdropFilter: 'blur(2px)',
              zIndex: 60,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
            onClick={() => setShowLinkModal(false)}
          >
            <div
              style={{
                backgroundColor: '#FFFFFF',
                borderRadius: 10,
                width: 480,
                padding: 20,
                boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.2)',
                display: 'flex',
                flexDirection: 'column',
                gap: 16,
              }}
              onClick={(e) => e.stopPropagation()}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>Liên kết công việc (Link issue)</h3>
                <button
                  onClick={() => setShowLinkModal(false)}
                  style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#64748B' }}
                >
                  <X size={16} />
                </button>
              </div>

              <div>
                <label style={{ fontSize: 12, fontWeight: 700, color: '#475569', display: 'block', marginBottom: 6 }}>
                  Loại quan hệ (Relationship)
                </label>
                <select
                  value={linkRelationType}
                  onChange={(e) => setLinkRelationType(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                    fontWeight: 600,
                  }}
                >
                  <option value="BLOCKS">Chặn (blocks)</option>
                  <option value="RELATES_TO">Liên quan đến (relates to)</option>
                  <option value="DUPLICATES">Trùng lặp với (duplicates)</option>
                  <option value="CAUSES">Gây ra (causes)</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: 12, fontWeight: 700, color: '#475569', display: 'block', marginBottom: 6 }}>
                  Tìm kiếm công việc cần liên kết
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    type="text"
                    value={linkSearchQuery}
                    onChange={(e) => handleSearchLinkCandidates(e.target.value)}
                    placeholder="Nhập mã (DATN-102) hoặc tiêu đề..."
                    style={{
                      width: '100%',
                      padding: '8px 12px 8px 32px',
                      borderRadius: 6,
                      border: '1px solid #CBD5E1',
                      fontSize: 13,
                      outline: 'none',
                      boxSizing: 'border-box',
                    }}
                  />
                  <Search size={14} color="#94A3B8" style={{ position: 'absolute', left: 10, top: 11 }} />
                </div>

                {/* Search Results Dropdown */}
                {linkSearchResults.length > 0 && (
                  <div
                    style={{
                      marginTop: 6,
                      maxHeight: 180,
                      overflowY: 'auto',
                      border: '1px solid #E2E8F0',
                      borderRadius: 6,
                      backgroundColor: '#FFFFFF',
                      boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
                    }}
                  >
                    {linkSearchResults.map((candidate) => {
                      const isSelected = selectedTargetItem?.id === candidate.id;
                      return (
                        <div
                          key={candidate.id}
                          onClick={() => {
                            setSelectedTargetItem(candidate);
                            setLinkSearchQuery(`${candidate.code} - ${candidate.summary}`);
                            setLinkSearchResults([]);
                          }}
                          style={{
                            padding: '8px 12px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            fontSize: 12,
                            cursor: 'pointer',
                            backgroundColor: isSelected ? '#EFF6FF' : 'transparent',
                            borderBottom: '1px solid #F1F5F9',
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <span style={{ fontWeight: 700, color: '#2563EB' }}>{candidate.code}</span>
                            <span style={{ color: '#0F172A' }}>{candidate.summary}</span>
                          </div>
                          <span style={{ fontSize: 10, color: '#64748B' }}>{candidate.statusName}</span>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {selectedTargetItem && (
                <div style={{ padding: 10, backgroundColor: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: 6, fontSize: 12, color: '#166534' }}>
                  Đã chọn: <b>{selectedTargetItem.code}</b> - {selectedTargetItem.summary}
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 8 }}>
                <button
                  type="button"
                  onClick={() => setShowLinkModal(false)}
                  style={{
                    backgroundColor: '#F1F5F9',
                    color: '#475569',
                    border: 'none',
                    padding: '8px 16px',
                    borderRadius: 6,
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  Hủy
                </button>
                <button
                  type="button"
                  onClick={handleCreateLink}
                  disabled={!selectedTargetItem || isCreatingLink}
                  style={{
                    backgroundColor: selectedTargetItem && !isCreatingLink ? '#2563EB' : '#94A3B8',
                    color: '#FFFFFF',
                    border: 'none',
                    padding: '8px 16px',
                    borderRadius: 6,
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: selectedTargetItem && !isCreatingLink ? 'pointer' : 'not-allowed',
                  }}
                >
                  {isCreatingLink ? 'Đang tạo...' : 'Liên kết'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ─── Resolve Subtasks Modal (Step 5.5) ─── */}
        <ResolveSubtasksModal
          isOpen={showResolveSubtasksModal}
          parentCode={item?.code}
          parentSummary={item?.summary}
          parentHierarchyLevel={item?.hierarchyLevel}
          subtasks={item?.descendants && item.descendants.length > 0 ? item.descendants : (item?.subtasks || [])}
          loading={isResolvingSubtasks}
          onConfirm={() => handleExecuteTransition(pendingTransitionId, true)}
          onCancel={() => {
            setShowResolveSubtasksModal(false);
            setPendingTransitionId(null);
          }}
        />
      </div>
    </div>
  );
}
