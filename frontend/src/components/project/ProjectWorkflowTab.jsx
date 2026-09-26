import React, { useState, useEffect } from 'react';
import {
  GitBranch,
  ArrowRight,
  Plus,
  Trash2,
  Shield,
  Layers,
  CheckCircle2,
  AlertCircle,
  Clock,
  Sparkles,
  Info,
  Sliders,
  Check,
  X,
  FileCheck,
  Edit2,
  Zap,
  CheckSquare,
  GitCommit,
  Bookmark,
  AlertTriangle,
  RotateCcw,
  Palette,
  HelpCircle,
  BookOpen,
  Calendar,
  TrendingUp,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { workflowApi, workItemTypeApi } from '../../api.js';
import {
  STATUS_CATEGORY_CONFIG,
  WORK_ITEM_TYPE_CONFIG,
  TASK_TYPE_PRESETS,
  resolveDynamicTypeConfig,
  HIERARCHY_LEVELS,
  getTypeDisplayName,
  getStatusDisplayName,
  getStatusCategoryDisplayName,
  getHierarchyDisplayName,
} from '../../constants/workConstants.js';

// Helper render icon loại công việc
const renderTypeIcon = (iconName, color = '#2563EB', size = 16) => {
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

const COLOR_PRESETS = [
  '#9333EA', '#2563EB', '#10B981', '#EF4444', '#06B6D4',
  '#D97706', '#EA580C', '#64748B', '#7C3AED', '#EC4899',
];

const ICON_PRESETS = [
  { name: 'Zap', label: 'Tia chớp (Hạng mục lớn)' },
  { name: 'CheckSquare', label: 'Ô kiểm (Nhiệm vụ)' },
  { name: 'Bookmark', label: 'Thẻ đánh dấu (Câu chuyện)' },
  { name: 'Bug', label: 'Con bọ (Lỗi phần mềm)' },
  { name: 'GitCommit', label: 'Điểm nhánh (Nhiệm vụ con)' },
  { name: 'Layers', label: 'Phân tầng (Giai đoạn)' },
  { name: 'FileCheck', label: 'Kiểm định (Nghiệm thu)' },
  { name: 'Sparkles', label: 'Ngôi sao (Chiến dịch)' },
  { name: 'Sliders', label: 'Kênh / Tùy biến' },
  { name: 'AlertTriangle', label: 'Cảnh báo sự cố' },
];

export default function ProjectWorkflowTab({ projectId, project, isAdmin, onTypesUpdated }) {
  const SUBTAB_STORAGE_KEY = `project_${projectId}_workflow_subtab`;
  const [activeSubTab, setActiveSubTab] = useState(() => {
    try {
      const saved = sessionStorage.getItem(SUBTAB_STORAGE_KEY);
      if (saved === 'statuses') return 'diagram';
      return saved || 'diagram';
    } catch {
      return 'diagram';
    }
  });

  const handleSelectSubTab = (tabKey) => {
    setActiveSubTab(tabKey);
    try {
      sessionStorage.setItem(SUBTAB_STORAGE_KEY, tabKey);
    } catch (e) {}
  };

  const [workflowDetail, setWorkflowDetail] = useState(null);
  const [workflowScheme, setWorkflowScheme] = useState(null);
  const [statuses, setStatuses] = useState([]);
  const [projectTypes, setProjectTypes] = useState([]);
  const [loading, setLoading] = useState(true);

  // Add / Edit Transition Modal State
  const [isAddTransitionOpen, setIsAddTransitionOpen] = useState(false);
  const [editingTransition, setEditingTransition] = useState(null);
  const [fromStatusId, setFromStatusId] = useState('');
  const [toStatusId, setToStatusId] = useState('');
  const [transitionName, setTransitionName] = useState('');
  const [requireAssigneeOnly, setRequireAssigneeOnly] = useState(false);
  const [requireSubtasksDone, setRequireSubtasksDone] = useState(false);
  const [requireResolution, setRequireResolution] = useState(false);
  const [submittingTransition, setSubmittingTransition] = useState(false);

  // Add / Edit Status Modal State
  const [isAddStatusOpen, setIsAddStatusOpen] = useState(false);
  const [editingStatus, setEditingStatus] = useState(null);
  const [newStatusName, setNewStatusName] = useState('');
  const [newStatusCategory, setNewStatusCategory] = useState('IN_PROGRESS');
  const [newStatusColor, setNewStatusColor] = useState('#3B82F6');
  const [newStatusDesc, setNewStatusDesc] = useState('');
  const [submittingStatus, setSubmittingStatus] = useState(false);

  // Create / Edit Work Item Type Modal State
  const [isTypeModalOpen, setIsTypeModalOpen] = useState(false);
  const [editingType, setEditingType] = useState(null);
  const [typeFormData, setTypeFormData] = useState({
    name: '',
    code: '',
    description: '',
    hierarchyLevel: 0,
    colorHex: '#3B82F6',
    iconName: 'CheckSquare',
  });
  const [submittingType, setSubmittingType] = useState(false);

  // Preset Template Modal State
  const [isPresetModalOpen, setIsPresetModalOpen] = useState(false);
  const [selectedPreset, setSelectedPreset] = useState(TASK_TYPE_PRESETS[0]);
  const [applyingPreset, setApplyingPreset] = useState(false);

  // Explanation Modal State
  const [isExplanationModalOpen, setIsExplanationModalOpen] = useState(false);
  const [explanationTab, setExplanationTab] = useState('hierarchy'); // 'hierarchy' | 'workflow' | 'time'

  const fetchWorkflowData = async (isInitial = false) => {
    try {
      if (isInitial) setLoading(true);
      const [wfRes, schemeRes, statusesRes, typesRes] = await Promise.all([
        workflowApi.getProjectWorkflow(projectId),
        workflowApi.getProjectWorkflowScheme(projectId),
        workflowApi.getProjectStatuses(projectId),
        workItemTypeApi.listByProject(projectId).catch(() => ({ data: [] })),
      ]);

      if (wfRes && wfRes.data) setWorkflowDetail(wfRes.data);
      if (schemeRes && schemeRes.data) setWorkflowScheme(schemeRes.data);
      if (statusesRes && statusesRes.data) setStatuses(statusesRes.data);
      if (typesRes && typesRes.data) setProjectTypes(typesRes.data);
    } catch (err) {
      console.error('Failed to fetch workflow details:', err);
    } finally {
      if (isInitial) setLoading(false);
    }
  };

  useEffect(() => {
    if (projectId) {
      fetchWorkflowData(true);
    }
  }, [projectId]);

  const handleOpenCreateTransition = () => {
    setEditingTransition(null);
    setFromStatusId('');
    setToStatusId('');
    setTransitionName('');
    setRequireAssigneeOnly(false);
    setRequireSubtasksDone(false);
    setRequireResolution(false);
    setIsAddTransitionOpen(true);
  };

  const handleOpenEditTransition = (t) => {
    setEditingTransition(t);
    setFromStatusId(t.fromStatusId || '');
    setToStatusId(t.toStatusId || '');
    setTransitionName(t.name || '');

    const conditions = Array.isArray(t.conditionsConfig) ? t.conditionsConfig : [];
    const validators = Array.isArray(t.validatorsConfig) ? t.validatorsConfig : [];
    setRequireAssigneeOnly(conditions.some((c) => c.type === 'ASSIGNEE_ONLY'));
    setRequireSubtasksDone(validators.some((v) => v.type === 'ALL_SUBTASKS_DONE'));
    setRequireResolution(validators.some((v) => v.type === 'FIELD_REQUIRED' && v.params?.fields?.includes('resolution')));

    setIsAddTransitionOpen(true);
  };

  const handleSaveTransition = async (e) => {
    e.preventDefault();

    if (!workflowDetail?.id) {
      toast.error('Không tìm thấy thông tin quy trình của dự án. Vui lòng tải lại trang.');
      return;
    }

    if (!toStatusId) {
      toast.error('Vui lòng chọn trạng thái đích');
      return;
    }

    const trimmedName = transitionName.trim();
    if (!trimmedName) {
      toast.error('Vui lòng nhập tên bước chuyển (Action Name)');
      return;
    }

    if (trimmedName.length > 150) {
      toast.error('Tên bước chuyển tối đa 150 ký tự');
      return;
    }

    const targetFromId = fromStatusId || null;
    if (targetFromId && targetFromId === toStatusId) {
      toast.error('Trạng thái đích không được trùng với trạng thái bắt đầu');
      return;
    }

    // Kiểm tra trùng lặp bước chuyển giữa 2 trạng thái trong quy trình
    const currentTransitions = workflowDetail?.transitions || [];
    const isDuplicatePath = currentTransitions.some((t) => {
      if (editingTransition && t.id === editingTransition.id) return false;
      const tFrom = t.fromStatusId || null;
      return tFrom === targetFromId && t.toStatusId === toStatusId;
    });
    if (isDuplicatePath) {
      toast.error('Đã tồn tại bước chuyển giữa hai trạng thái này trong quy trình');
      return;
    }

    // Kiểm tra trùng lặp tên bước chuyển (không phân biệt hoa thường)
    const isDuplicateName = currentTransitions.some((t) => {
      if (editingTransition && t.id === editingTransition.id) return false;
      return (t.name || '').trim().toLowerCase() === trimmedName.toLowerCase();
    });
    if (isDuplicateName) {
      toast.error(`Tên bước chuyển "${trimmedName}" đã tồn tại trong quy trình. Vui lòng đặt tên khác.`);
      return;
    }

    try {
      setSubmittingTransition(true);
      const conditionsConfig = [];
      if (requireAssigneeOnly) {
        conditionsConfig.push({ type: 'ASSIGNEE_ONLY', params: {} });
      }

      const validatorsConfig = [];
      if (requireSubtasksDone) {
        validatorsConfig.push({ type: 'ALL_SUBTASKS_DONE', params: {} });
      }
      if (requireResolution) {
        validatorsConfig.push({ type: 'FIELD_REQUIRED', params: { fields: ['resolution'] } });
      }

      const postFunctionsConfig = [
        { type: 'EMIT_AUDIT_LOG', params: {} }
      ];

      const payload = {
        workflowId: workflowDetail.id,
        fromStatusId: targetFromId,
        toStatusId,
        name: trimmedName,
        conditionsConfig,
        validatorsConfig,
        postFunctionsConfig,
      };

      if (editingTransition) {
        await workflowApi.updateTransition(workflowDetail.id, editingTransition.id, payload);
        toast.success('Cập nhật bước chuyển quy trình thành công!');
      } else {
        await workflowApi.createTransition(workflowDetail.id, payload);
        toast.success('Tạo bước chuyển quy trình thành công!');
      }

      setIsAddTransitionOpen(false);
      setEditingTransition(null);
      setFromStatusId('');
      setToStatusId('');
      setTransitionName('');
      setRequireAssigneeOnly(false);
      setRequireSubtasksDone(false);
      setRequireResolution(false);
      fetchWorkflowData(false);
    } catch (err) {
      console.error('Failed to save transition:', err);
      const msg =
        err.message ||
        err.body?.message ||
        err.response?.data?.message ||
        (editingTransition ? 'Cập nhật bước chuyển thất bại' : 'Tạo bước chuyển thất bại');
      toast.error(msg);
    } finally {
      setSubmittingTransition(false);
    }
  };

  const handleDeleteTransition = async (transitionId) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa bước chuyển này khỏi quy trình?')) {
      return;
    }

    try {
      await workflowApi.deleteTransition(workflowDetail.id, transitionId);
      toast.success('Đã xóa bước chuyển quy trình');
      fetchWorkflowData(false);
    } catch (err) {
      console.error('Failed to delete transition:', err);
      const msg = err.message || err.body?.message || err.response?.data?.message || 'Xóa bước chuyển thất bại';
      toast.error(msg);
    }
  };

  const handleOpenCreateStatus = (defaultCategory = 'IN_PROGRESS') => {
    setEditingStatus(null);
    setNewStatusName('');
    setNewStatusCategory(defaultCategory);
    setNewStatusColor(defaultCategory === 'TODO' ? '#64748B' : defaultCategory === 'DONE' ? '#10B981' : '#3B82F6');
    setNewStatusDesc('');
    setIsAddStatusOpen(true);
  };

  const handleOpenEditStatus = (st) => {
    setEditingStatus(st);
    setNewStatusName(st.name || '');
    setNewStatusCategory(st.category || 'IN_PROGRESS');
    setNewStatusColor(st.colorHex || '#3B82F6');
    setNewStatusDesc(st.description || '');
    setIsAddStatusOpen(true);
  };

  const handleSaveStatus = async (e) => {
    e.preventDefault();
    if (!newStatusName.trim()) {
      toast.error('Vui lòng nhập tên trạng thái');
      return;
    }

    try {
      setSubmittingStatus(true);
      const payload = {
        projectId,
        name: newStatusName.trim(),
        category: newStatusCategory,
        colorHex: newStatusColor,
        description: newStatusDesc.trim(),
      };

      if (editingStatus) {
        await workflowApi.updateStatus(editingStatus.id, payload);
        toast.success('Cập nhật trạng thái thành công!');
      } else {
        await workflowApi.createStatus(payload, projectId);
        toast.success('Tạo trạng thái mới cho dự án thành công!');
      }

      setIsAddStatusOpen(false);
      setEditingStatus(null);
      setNewStatusName('');
      setNewStatusDesc('');
      fetchWorkflowData(false);
    } catch (err) {
      const msg = err.response?.data?.message || (editingStatus ? 'Cập nhật trạng thái thất bại' : 'Tạo trạng thái thất bại');
      toast.error(msg);
    } finally {
      setSubmittingStatus(false);
    }
  };

  const handleDeleteStatus = async (st) => {
    const currentStatuses = statuses.length > 0 ? statuses : (workflowDetail?.statuses || []);
    const sameCategoryCount = currentStatuses.filter((x) => x.category === st.category).length;
    if (sameCategoryCount <= 1) {
      toast.error(`Không thể xóa trạng thái "${st.name}". Dự án bắt buộc phải duy trì tối thiểu 1 trạng thái cho danh mục ${st.category}!`);
      return;
    }

    if (!window.confirm(`Bạn có chắc chắn muốn xóa trạng thái "${st.name}"?`)) {
      return;
    }

    try {
      await workflowApi.deleteStatus(st.id);
      toast.success(`Đã xóa trạng thái "${st.name}" thành công!`);
      fetchWorkflowData(false);
    } catch (err) {
      const msg = err.response?.data?.message || 'Xóa trạng thái thất bại';
      toast.error(msg);
    }
  };

  // ─── Task Type CRUD & Preset Handlers ─────────────────────────────────────────
  const handleOpenCreateTypeModal = () => {
    setEditingType(null);
    setTypeFormData({
      name: '',
      code: '',
      description: '',
      hierarchyLevel: 0,
      colorHex: '#3B82F6',
      iconName: 'CheckSquare',
    });
    setIsTypeModalOpen(true);
  };

  const handleOpenEditTypeModal = (type) => {
    setEditingType(type);
    setTypeFormData({
      name: type.name || '',
      code: type.code || '',
      description: type.description || '',
      hierarchyLevel: type.hierarchyLevel ?? 0,
      colorHex: type.colorHex || '#3B82F6',
      iconName: type.iconName || 'CheckSquare',
    });
    setIsTypeModalOpen(true);
  };

  const handleSaveType = async (e) => {
    e.preventDefault();
    if (!typeFormData.name.trim()) {
      toast.error('Vui lòng nhập tên loại công việc');
      return;
    }
    if (!typeFormData.code.trim()) {
      toast.error('Vui lòng nhập mã loại công việc');
      return;
    }

    try {
      setSubmittingType(true);
      const payload = {
        name: typeFormData.name.trim(),
        code: typeFormData.code.trim().toUpperCase(),
        description: typeFormData.description.trim(),
        hierarchyLevel: Number(typeFormData.hierarchyLevel),
        colorHex: typeFormData.colorHex,
        iconName: typeFormData.iconName,
      };

      if (editingType) {
        await workItemTypeApi.update(projectId, editingType.id, payload);
        toast.success('Đã cập nhật loại công việc thành công');
      } else {
        await workItemTypeApi.create(projectId, payload);
        toast.success('Đã tạo loại công việc mới thành công');
      }

      setIsTypeModalOpen(false);
      await fetchWorkflowData(false);
      if (onTypesUpdated) onTypesUpdated();
    } catch (err) {
      const msg = err.response?.data?.message || (editingType ? 'Cập nhật thất bại' : 'Tạo mới thất bại');
      toast.error(msg);
    } finally {
      setSubmittingType(false);
    }
  };

  const handleDeleteType = async (type) => {
    // 1. Kiểm tra ràng buộc tối thiểu 1 loại cho mỗi level (Level 1, Level 0, Level -1)
    const sameLevelTypes = projectTypes.filter((t) => t.hierarchyLevel === type.hierarchyLevel);
    if (sameLevelTypes.length <= 1) {
      const lvlStr = type.hierarchyLevel === 1 ? 'Cấp 1 (Hạng mục lớn)' : type.hierarchyLevel === 0 ? 'Cấp 0 (Tiêu chuẩn)' : 'Cấp -1 (Nhiệm vụ con)';
      toast.error(`Không thể xóa! Dự án luôn phải duy trì tối thiểu 1 loại công việc cho ${lvlStr}.`);
      return;
    }

    const typeDisplayName = getTypeDisplayName(type.name || type.code);
    if (!window.confirm(`Bạn có chắc chắn muốn xóa loại công việc "${typeDisplayName}" (${type.code})?`)) {
      return;
    }

    try {
      await workItemTypeApi.delete(projectId, type.id);
      toast.success(`Đã xóa loại công việc "${typeDisplayName}" thành công`);
      await fetchWorkflowData(false);
      if (onTypesUpdated) onTypesUpdated();
    } catch (err) {
      const msg = err.response?.data?.message || 'Xóa loại công việc thất bại';
      toast.error(msg);
    }
  };

  const handleApplyPreset = async () => {
    if (!selectedPreset) return;
    if (!window.confirm(`Bạn có chắc chắn muốn áp dụng mẫu "${selectedPreset.name}"? Hệ thống sẽ thêm các loại công việc mới và cố gắng xóa các loại cũ chưa có công việc sử dụng.`)) {
      return;
    }

    try {
      setApplyingPreset(true);
      const existingCodes = new Set(projectTypes.map((t) => (t.code || '').toUpperCase()));
      const presetCodes = new Set(selectedPreset.types.map((t) => t.code.toUpperCase()));

      // 1. Thêm các types mới trong preset
      for (const t of selectedPreset.types) {
        if (!existingCodes.has(t.code.toUpperCase())) {
          try {
            await workItemTypeApi.create(projectId, t);
          } catch (e) {
            console.warn('Could not create preset type:', t.code, e);
          }
        }
      }

      // 2. Lấy lại danh sách types mới sau khi thêm
      const freshRes = await workItemTypeApi.listByProject(projectId);
      const updatedTypes = freshRes?.data || [];

      // 3. Xóa các types cũ không thuộc preset mới (nếu thỏa mãn điều kiện an toàn)
      const keptDueToUse = [];
      for (const oldType of updatedTypes) {
        if (!presetCodes.has(oldType.code.toUpperCase())) {
          try {
            await workItemTypeApi.delete(projectId, oldType.id);
          } catch (delErr) {
            // Có thể lỗi do đang có items sử dụng hoặc vi phạm min level
            keptDueToUse.push(oldType.name);
          }
        }
      }

      setIsPresetModalOpen(false);
      await fetchWorkflowData(false);
      if (onTypesUpdated) onTypesUpdated();

      if (keptDueToUse.length > 0) {
        toast.success(`Đã áp dụng cấu hình "${selectedPreset.name}". Giữ lại các loại: ${keptDueToUse.join(', ')} do đang có dữ liệu sử dụng hoặc để đảm bảo cấp bậc.`, { duration: 6000 });
      } else {
        toast.success(`Đã áp dụng toàn bộ cấu hình "${selectedPreset.name}" thành công!`);
      }
    } catch (err) {
      console.error('Error applying preset:', err);
      toast.error('Có lỗi xảy ra khi áp dụng mẫu cấu hình');
    } finally {
      setApplyingPreset(false);
    }
  };

  if (loading && !workflowDetail) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', color: '#64748B' }}>
        <div style={{ display: 'inline-block', width: 24, height: 24, border: '3px solid #E2E8F0', borderTopColor: '#2563EB', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
        <div style={{ marginTop: 12, fontSize: 14, fontWeight: 500 }}>Đang tải quy trình và trạng thái dự án...</div>
      </div>
    );
  }

  const allStatuses = statuses.length > 0 ? statuses : (workflowDetail?.statuses || []);
  const todoStatuses = allStatuses.filter((s) => s.category === 'TODO');
  const inProgressStatuses = allStatuses.filter((s) => s.category === 'IN_PROGRESS');
  const doneStatuses = allStatuses.filter((s) => s.category === 'DONE');
  const transitions = workflowDetail?.transitions || [];

  return (
    <div style={{ padding: '24px', maxWidth: 1100, margin: '0 auto', width: '100%', boxSizing: 'border-box' }}>
      {/* ─── Navigation Sub-Tabs ─── */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, borderBottom: '1px solid #E2E8F0', paddingBottom: 8 }}>
        <button
          type="button"
          onClick={() => handleSelectSubTab('diagram')}
          style={{
            padding: '8px 16px',
            borderRadius: 6,
            border: 'none',
            backgroundColor: activeSubTab === 'diagram' ? '#EFF6FF' : 'transparent',
            color: activeSubTab === 'diagram' ? '#2563EB' : '#64748B',
            fontWeight: activeSubTab === 'diagram' ? 700 : 500,
            fontSize: 13,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <GitBranch size={15} />
          <span>Sơ đồ Quy trình & Trạng thái ({allStatuses.length})</span>
        </button>

        <button
          type="button"
          onClick={() => handleSelectSubTab('scheme')}
          style={{
            padding: '8px 16px',
            borderRadius: 6,
            border: 'none',
            backgroundColor: activeSubTab === 'scheme' ? '#EFF6FF' : 'transparent',
            color: activeSubTab === 'scheme' ? '#2563EB' : '#64748B',
            fontWeight: activeSubTab === 'scheme' ? 700 : 500,
            fontSize: 13,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <Layers size={15} />
          <span>Phân bổ Loại Công việc ({projectTypes.length})</span>
        </button>
      </div>

      {/* ─── TAB 1: Visual FSM Diagram & Transitions Table ─── */}
      {activeSubTab === 'diagram' && (
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 10,
            border: '1px solid #E2E8F0',
            padding: '20px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          }}
        >
          {/* Header with Title & Action Buttons (matching Tab 2 layout) */}
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              justifyContent: 'space-between',
              marginBottom: 20,
              gap: 16,
              flexWrap: 'wrap',
            }}
          >
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <GitBranch size={18} color="#2563EB" />
                <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                  {workflowDetail?.name || 'Sơ đồ Quy trình & Quản lý Trạng thái (Visual Workflow & Transitions)'}
                </h3>
                {workflowDetail?.isDefault && (
                  <span
                    style={{
                      fontSize: 11,
                      fontWeight: 600,
                      padding: '2px 8px',
                      borderRadius: 12,
                      backgroundColor: '#F3E8FF',
                      color: '#7E22CE',
                      border: '1px solid #E9D5FF',
                    }}
                  >
                    Mặc định Dự án
                  </span>
                )}
              </div>
              <p style={{ fontSize: 13, color: '#64748B', marginTop: 6, marginBottom: 0, maxWidth: 720, lineHeight: 1.5 }}>
                {workflowDetail?.description || 'Máy trạng thái FSM kiểm soát vòng đời và các bước chuyển trạng thái (FSM) cho công việc trong dự án.'}
              </p>
            </div>

            {isAdmin && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <button
                  type="button"
                  onClick={() => handleOpenCreateStatus()}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    padding: '8px 14px',
                    borderRadius: 8,
                    backgroundColor: '#EFF6FF',
                    color: '#2563EB',
                    border: '1px solid #BFDBFE',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#DBEAFE')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#EFF6FF')}
                >
                  <Plus size={15} color="#2563EB" />
                  <span>Thêm Trạng Thái</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleOpenCreateTransition()}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    padding: '8px 16px',
                    borderRadius: 8,
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    border: 'none',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: 'pointer',
                    boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
                >
                  <Plus size={15} />
                  <span>Thêm Bước Chuyển</span>
                </button>
              </div>
            )}
          </div>

          {/* Visual FSM Lanes */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr 1fr',
              gap: 16,
              marginBottom: 24,
            }}
          >
            {/* TODO Lane */}
            <div
              style={{
                backgroundColor: '#F8FAFC',
                borderRadius: 10,
                border: '1px solid #E2E8F0',
                padding: '14px',
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: '#475569', marginBottom: 12 }}>
                <Clock size={15} color="#64748B" />
                <span>1. CẦN LÀM (TODO)</span>
                <span style={{ fontSize: 11, backgroundColor: '#E2E8F0', padding: '1px 6px', borderRadius: 10, marginLeft: 'auto' }}>
                  {todoStatuses.length}
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
                {todoStatuses.map((st) => {
                  const canDelete = todoStatuses.length > 1;
                  return (
                    <div
                      key={st.id}
                      style={{
                        padding: '10px 12px',
                        backgroundColor: '#FFFFFF',
                        borderRadius: 8,
                        border: '1px solid #CBD5E1',
                        borderLeft: `4px solid ${st.colorHex || '#94A3B8'}`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 8,
                      }}
                    >
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {st.name}
                        </div>
                        <div style={{ fontSize: 11, color: '#64748B', marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {st.description || 'Trạng thái khởi tạo'}
                        </div>
                      </div>
                      {isAdmin && (
                        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                          <button
                            type="button"
                            onClick={() => handleOpenEditStatus(st)}
                            title="Chỉnh sửa trạng thái"
                            style={{
                              border: 'none',
                              background: 'transparent',
                              cursor: 'pointer',
                              padding: 4,
                              borderRadius: 4,
                              color: '#64748B',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#F1F5F9'; e.currentTarget.style.color = '#2563EB'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; e.currentTarget.style.color = '#64748B'; }}
                          >
                            <Edit2 size={13} />
                          </button>
                          <button
                            type="button"
                            disabled={!canDelete}
                            onClick={() => canDelete && handleDeleteStatus(st)}
                            title={canDelete ? "Xóa trạng thái" : "Không thể xóa (Cần giữ tối thiểu 1 trạng thái cho danh mục này)"}
                            style={{
                              border: 'none',
                              background: 'transparent',
                              cursor: canDelete ? 'pointer' : 'not-allowed',
                              padding: 4,
                              borderRadius: 4,
                              color: canDelete ? '#64748B' : '#CBD5E1',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                            }}
                            onMouseEnter={(e) => {
                              if (canDelete) {
                                e.currentTarget.style.backgroundColor = '#FEF2F2';
                                e.currentTarget.style.color = '#EF4444';
                              }
                            }}
                            onMouseLeave={(e) => {
                              if (canDelete) {
                                e.currentTarget.style.backgroundColor = 'transparent';
                                e.currentTarget.style.color = '#64748B';
                              }
                            }}
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              {isAdmin && (
                <button
                  type="button"
                  onClick={() => handleOpenCreateStatus('TODO')}
                  style={{
                    marginTop: 10,
                    padding: '8px',
                    borderRadius: 8,
                    border: '1px dashed #CBD5E1',
                    backgroundColor: 'transparent',
                    color: '#64748B',
                    fontSize: 12,
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 6,
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = '#F1F5F9';
                    e.currentTarget.style.borderColor = '#94A3B8';
                    e.currentTarget.style.color = '#1E293B';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = 'transparent';
                    e.currentTarget.style.borderColor = '#CBD5E1';
                    e.currentTarget.style.color = '#64748B';
                  }}
                >
                  <Plus size={14} />
                  <span>Thêm trạng thái</span>
                </button>
              )}
            </div>

            {/* IN PROGRESS Lane */}
            <div
              style={{
                backgroundColor: '#EFF6FF',
                borderRadius: 10,
                border: '1px solid #BFDBFE',
                padding: '14px',
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: '#1D4ED8', marginBottom: 12 }}>
                <Sparkles size={15} color="#2563EB" />
                <span>2. ĐANG XỬ LÝ (IN PROGRESS)</span>
                <span style={{ fontSize: 11, backgroundColor: '#DBEAFE', padding: '1px 6px', borderRadius: 10, marginLeft: 'auto' }}>
                  {inProgressStatuses.length}
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
                {inProgressStatuses.map((st) => {
                  const canDelete = inProgressStatuses.length > 1;
                  return (
                    <div
                      key={st.id}
                      style={{
                        padding: '10px 12px',
                        backgroundColor: '#FFFFFF',
                        borderRadius: 8,
                        border: '1px solid #93C5FD',
                        borderLeft: `4px solid ${st.colorHex || '#3B82F6'}`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 8,
                      }}
                    >
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {st.name}
                        </div>
                        <div style={{ fontSize: 11, color: '#64748B', marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {st.description || 'Đang thực hiện'}
                        </div>
                      </div>
                      {isAdmin && (
                        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                          <button
                            type="button"
                            onClick={() => handleOpenEditStatus(st)}
                            title="Chỉnh sửa trạng thái"
                            style={{
                              border: 'none',
                              background: 'transparent',
                              cursor: 'pointer',
                              padding: 4,
                              borderRadius: 4,
                              color: '#64748B',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#EFF6FF'; e.currentTarget.style.color = '#2563EB'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; e.currentTarget.style.color = '#64748B'; }}
                          >
                            <Edit2 size={13} />
                          </button>
                          <button
                            type="button"
                            disabled={!canDelete}
                            onClick={() => canDelete && handleDeleteStatus(st)}
                            title={canDelete ? "Xóa trạng thái" : "Không thể xóa (Cần giữ tối thiểu 1 trạng thái cho danh mục này)"}
                            style={{
                              border: 'none',
                              background: 'transparent',
                              cursor: canDelete ? 'pointer' : 'not-allowed',
                              padding: 4,
                              borderRadius: 4,
                              color: canDelete ? '#64748B' : '#CBD5E1',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                            }}
                            onMouseEnter={(e) => {
                              if (canDelete) {
                                e.currentTarget.style.backgroundColor = '#FEF2F2';
                                e.currentTarget.style.color = '#EF4444';
                              }
                            }}
                            onMouseLeave={(e) => {
                              if (canDelete) {
                                e.currentTarget.style.backgroundColor = 'transparent';
                                e.currentTarget.style.color = '#64748B';
                              }
                            }}
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              {isAdmin && (
                <button
                  type="button"
                  onClick={() => handleOpenCreateStatus('IN_PROGRESS')}
                  style={{
                    marginTop: 10,
                    padding: '8px',
                    borderRadius: 8,
                    border: '1px dashed #93C5FD',
                    backgroundColor: 'transparent',
                    color: '#2563EB',
                    fontSize: 12,
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 6,
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = '#DBEAFE';
                    e.currentTarget.style.borderColor = '#3B82F6';
                    e.currentTarget.style.color = '#1D4ED8';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = 'transparent';
                    e.currentTarget.style.borderColor = '#93C5FD';
                    e.currentTarget.style.color = '#2563EB';
                  }}
                >
                  <Plus size={14} />
                  <span>Thêm trạng thái</span>
                </button>
              )}
            </div>

            {/* DONE Lane */}
            <div
              style={{
                backgroundColor: '#F0FDF4',
                borderRadius: 10,
                border: '1px solid #BBF7D0',
                padding: '14px',
                display: 'flex',
                flexDirection: 'column',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: '#15803D', marginBottom: 12 }}>
                <CheckCircle2 size={15} color="#16A34A" />
                <span>3. HOÀN THÀNH (DONE)</span>
                <span style={{ fontSize: 11, backgroundColor: '#DCFCE7', padding: '1px 6px', borderRadius: 10, marginLeft: 'auto' }}>
                  {doneStatuses.length}
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
                {doneStatuses.map((st) => {
                  const canDelete = doneStatuses.length > 1;
                  return (
                    <div
                      key={st.id}
                      style={{
                        padding: '10px 12px',
                        backgroundColor: '#FFFFFF',
                        borderRadius: 8,
                        border: '1px solid #86EFAC',
                        borderLeft: `4px solid ${st.colorHex || '#10B981'}`,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 8,
                      }}
                    >
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {st.name}
                        </div>
                        <div style={{ fontSize: 11, color: '#64748B', marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                          {st.description || 'Đã nghiệm thu'}
                        </div>
                      </div>
                      {isAdmin && (
                        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                          <button
                            type="button"
                            onClick={() => handleOpenEditStatus(st)}
                            title="Chỉnh sửa trạng thái"
                            style={{
                              border: 'none',
                              background: 'transparent',
                              cursor: 'pointer',
                              padding: 4,
                              borderRadius: 4,
                              color: '#64748B',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#F0FDF4'; e.currentTarget.style.color = '#16A34A'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; e.currentTarget.style.color = '#64748B'; }}
                          >
                            <Edit2 size={13} />
                          </button>
                          <button
                            type="button"
                            disabled={!canDelete}
                            onClick={() => canDelete && handleDeleteStatus(st)}
                            title={canDelete ? "Xóa trạng thái" : "Không thể xóa (Cần giữ tối thiểu 1 trạng thái cho danh mục này)"}
                            style={{
                              border: 'none',
                              background: 'transparent',
                              cursor: canDelete ? 'pointer' : 'not-allowed',
                              padding: 4,
                              borderRadius: 4,
                              color: canDelete ? '#64748B' : '#CBD5E1',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                            }}
                            onMouseEnter={(e) => {
                              if (canDelete) {
                                e.currentTarget.style.backgroundColor = '#FEF2F2';
                                e.currentTarget.style.color = '#EF4444';
                              }
                            }}
                            onMouseLeave={(e) => {
                              if (canDelete) {
                                e.currentTarget.style.backgroundColor = 'transparent';
                                e.currentTarget.style.color = '#64748B';
                              }
                            }}
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              {isAdmin && (
                <button
                  type="button"
                  onClick={() => handleOpenCreateStatus('DONE')}
                  style={{
                    marginTop: 10,
                    padding: '8px',
                    borderRadius: 8,
                    border: '1px dashed #86EFAC',
                    backgroundColor: 'transparent',
                    color: '#16A34A',
                    fontSize: 12,
                    fontWeight: 600,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 6,
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = '#DCFCE7';
                    e.currentTarget.style.borderColor = '#16A34A';
                    e.currentTarget.style.color = '#15803D';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = 'transparent';
                    e.currentTarget.style.borderColor = '#86EFAC';
                    e.currentTarget.style.color = '#16A34A';
                  }}
                >
                  <Plus size={14} />
                  <span>Thêm trạng thái</span>
                </button>
              )}
            </div>
          </div>

          {/* Transitions Table */}
          <div
            style={{
              borderRadius: 8,
              border: '1px solid #E2E8F0',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                padding: '12px 16px',
                borderBottom: '1px solid #E2E8F0',
                backgroundColor: '#F8FAFC',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ fontWeight: 700, fontSize: 13, color: '#0F172A' }}>
                Bảng Cấu hình Các Bước Chuyển Trạng thái ({transitions.length} bước chuyển)
              </div>
              <span style={{ fontSize: 12, color: '#64748B' }}>
                Kiểm soát luồng di chuyển của công việc từ trạng thái xuất phát tới trạng thái đích
              </span>
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: isAdmin ? '2fr 2fr 2fr 2.5fr 140px' : '2fr 2fr 2fr 3fr',
                padding: '12px 16px',
                backgroundColor: '#F8FAFC',
                borderBottom: '1px solid #E2E8F0',
                fontSize: 12,
                fontWeight: 700,
                color: '#475569',
              }}
            >
              <span>Từ trạng thái xuất phát</span>
              <span>Tên nút thao tác</span>
              <span>Đến trạng thái đích</span>
              <span>Điều kiện & Ràng buộc</span>
              {isAdmin && <span style={{ textAlign: 'right' }}>Thao tác</span>}
            </div>

            {transitions.length === 0 ? (
              <div style={{ padding: '32px', textAlign: 'center', color: '#64748B', fontSize: 13 }}>
                Chưa có bước chuyển nào được thiết lập.
              </div>
            ) : (
              transitions.map((t) => {
                const hasConditions = t.conditionsConfig && Array.isArray(t.conditionsConfig) && t.conditionsConfig.length > 0;
                const hasValidators = t.validatorsConfig && Array.isArray(t.validatorsConfig) && t.validatorsConfig.length > 0;

                return (
                  <div
                    key={t.id}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: isAdmin ? '2fr 2fr 2fr 2.5fr 140px' : '2fr 2fr 2fr 3fr',
                      padding: '14px 16px',
                      borderBottom: '1px solid #F1F5F9',
                      alignItems: 'center',
                      fontSize: 13,
                    }}
                  >
                    {/* From Status */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span
                        style={{
                          padding: '3px 8px',
                          borderRadius: 4,
                          fontSize: 12,
                          fontWeight: 600,
                          backgroundColor: '#F1F5F9',
                          color: '#334155',
                          border: '1px solid #CBD5E1',
                        }}
                      >
                        {getStatusDisplayName(t.fromStatusName)}
                      </span>
                    </div>

                    {/* Transition Name */}
                    <div style={{ fontWeight: 600, color: '#2563EB', display: 'flex', alignItems: 'center', gap: 6 }}>
                      <ArrowRight size={14} color="#64748B" />
                      <span>{t.name}</span>
                    </div>

                    {/* To Status */}
                    <div>
                      <span
                        style={{
                          padding: '3px 8px',
                          borderRadius: 4,
                          fontSize: 12,
                          fontWeight: 600,
                          backgroundColor: t.toStatusCategory === 'DONE' ? '#DCFCE7' : t.toStatusCategory === 'IN_PROGRESS' ? '#DBEAFE' : '#F1F5F9',
                          color: t.toStatusCategory === 'DONE' ? '#15803D' : t.toStatusCategory === 'IN_PROGRESS' ? '#1D4ED8' : '#475569',
                          border: `1px solid ${t.toStatusCategory === 'DONE' ? '#86EFAC' : t.toStatusCategory === 'IN_PROGRESS' ? '#93C5FD' : '#CBD5E1'}`,
                        }}
                      >
                        {getStatusDisplayName(t.toStatusName)}
                      </span>
                    </div>

                    {/* Rules */}
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                      {hasConditions && (
                        <span
                          style={{
                            fontSize: 11,
                            padding: '2px 6px',
                            borderRadius: 4,
                            backgroundColor: '#FEF3C7',
                            color: '#92400E',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 3,
                          }}
                        >
                          <Shield size={11} /> Điều kiện
                        </span>
                      )}
                      {hasValidators && (
                        <span
                          style={{
                            fontSize: 11,
                            padding: '2px 6px',
                            borderRadius: 4,
                            backgroundColor: '#FEE2E2',
                            color: '#991B1B',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 3,
                          }}
                        >
                          <FileCheck size={11} /> Kiểm tra hợp lệ
                        </span>
                      )}
                      {!hasConditions && !hasValidators && (
                        <span style={{ fontSize: 12, color: '#94A3B8' }}>Không ràng buộc</span>
                      )}
                    </div>

                    {/* Actions */}
                    {isAdmin && (
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 6 }}>
                        <button
                          type="button"
                          onClick={() => handleOpenEditTransition(t)}
                          style={{
                            border: '1px solid #E2E8F0',
                            background: '#FFFFFF',
                            color: '#64748B',
                            cursor: 'pointer',
                            padding: '5px 8px',
                            borderRadius: 6,
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                            fontSize: 12,
                            fontWeight: 500,
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.color = '#2563EB';
                            e.currentTarget.style.borderColor = '#BFDBFE';
                            e.currentTarget.style.backgroundColor = '#EFF6FF';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.color = '#64748B';
                            e.currentTarget.style.borderColor = '#E2E8F0';
                            e.currentTarget.style.backgroundColor = '#FFFFFF';
                          }}
                          title="Chỉnh sửa bước chuyển"
                        >
                          <Edit2 size={13} />
                          <span>Sửa</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDeleteTransition(t.id)}
                          style={{
                            border: '1px solid #E2E8F0',
                            background: '#FFFFFF',
                            color: '#64748B',
                            cursor: 'pointer',
                            padding: '5px 8px',
                            borderRadius: 6,
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                            fontSize: 12,
                            fontWeight: 500,
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.color = '#EF4444';
                            e.currentTarget.style.borderColor = '#FECACA';
                            e.currentTarget.style.backgroundColor = '#FEF2F2';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.color = '#64748B';
                            e.currentTarget.style.borderColor = '#E2E8F0';
                            e.currentTarget.style.backgroundColor = '#FFFFFF';
                          }}
                          title="Xóa bước chuyển"
                        >
                          <Trash2 size={13} />
                          <span>Xóa</span>
                        </button>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* ─── TAB 2: Workflow Scheme & Work Item Types ─── */}
      {activeSubTab === 'scheme' && (
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 10,
            border: '1px solid #E2E8F0',
            padding: '20px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          }}
        >
          {/* Header with Title & Action Buttons */}
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              justifyContent: 'space-between',
              marginBottom: 16,
              gap: 16,
              flexWrap: 'wrap',
            }}
          >
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Layers size={18} color="#2563EB" />
                <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                  Sơ đồ Phân bổ & Quản lý Loại Công việc
                </h3>
              </div>
              <p style={{ fontSize: 13, color: '#64748B', marginTop: 6, marginBottom: 0, maxWidth: 720, lineHeight: 1.5 }}>
                Tùy biến các loại công việc riêng của dự án này. <strong>Quy tắc hệ thống:</strong> Luôn duy trì tối thiểu 1 loại cho <strong>Cấp 1</strong> (Hạng mục lớn), 1 loại cho <strong>Cấp 0</strong> (Tiêu chuẩn) và 1 loại cho <strong>Cấp -1</strong> (Nhiệm vụ con).
              </p>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              {isAdmin && (
                <>
                  <button
                    type="button"
                    onClick={() => setIsPresetModalOpen(true)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      padding: '8px 14px',
                      borderRadius: 8,
                      backgroundColor: '#EFF6FF',
                      color: '#2563EB',
                      border: '1px solid #BFDBFE',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#DBEAFE')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#EFF6FF')}
                  >
                    <Sparkles size={15} color="#2563EB" />
                    <span>Mẫu Cấu Hình Gợi Ý</span>
                  </button>

                  <button
                    type="button"
                    onClick={handleOpenCreateTypeModal}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      padding: '8px 16px',
                      borderRadius: 8,
                      backgroundColor: '#2563EB',
                      color: '#FFFFFF',
                      border: 'none',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: 'pointer',
                      boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
                  >
                    <Plus size={16} />
                    <span>Thêm Loại Công Việc</span>
                  </button>
                </>
              )}

              <button
                type="button"
                onClick={() => setIsExplanationModalOpen(true)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 14px',
                  borderRadius: 8,
                  backgroundColor: '#F8FAFC',
                  color: '#334155',
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.backgroundColor = '#F1F5F9';
                  e.currentTarget.style.borderColor = '#94A3B8';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = '#F8FAFC';
                  e.currentTarget.style.borderColor = '#CBD5E1';
                }}
              >
                <HelpCircle size={15} color="#2563EB" />
                <span>Hướng Dẫn Phân Cấp</span>
              </button>
            </div>
          </div>

          {/* Types Table */}
          <div style={{ borderRadius: 8, border: '1px solid #E2E8F0', overflow: 'hidden' }}>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: isAdmin ? '3fr 1.2fr 2fr 100px' : '3.5fr 1.5fr 2.5fr',
                padding: '12px 16px',
                backgroundColor: '#F8FAFC',
                borderBottom: '1px solid #E2E8F0',
                fontSize: 12,
                fontWeight: 700,
                color: '#475569',
              }}
            >
              <span>Loại công việc & Mô tả</span>
              <span>Cấp bậc</span>
              <span>Quy trình làm việc áp dụng</span>
              {isAdmin && <span style={{ textAlign: 'right' }}>Thao tác</span>}
            </div>

            {projectTypes.length === 0 ? (
              <div style={{ padding: '32px', textAlign: 'center', color: '#64748B', fontSize: 13 }}>
                Chưa có loại công việc nào được định cấu hình.
              </div>
            ) : (
              [...projectTypes].sort((a, b) => b.hierarchyLevel - a.hierarchyLevel).map((t) => {
                const hex = t.colorHex || '#3B82F6';
                const level = t.hierarchyLevel;
                const sameLevelCount = projectTypes.filter((x) => x.hierarchyLevel === level).length;
                const canDelete = sameLevelCount > 1;

                return (
                  <div
                    key={t.id}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: isAdmin ? '3fr 1.2fr 2fr 100px' : '3.5fr 1.5fr 2.5fr',
                      padding: '14px 16px',
                      borderBottom: '1px solid #F1F5F9',
                      alignItems: 'center',
                      fontSize: 13,
                    }}
                  >
                    {/* Work Item Type & Describe */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span
                          style={{
                            padding: '3px 8px',
                            borderRadius: 6,
                            fontSize: 12,
                            fontWeight: 700,
                            backgroundColor: `${hex}15`,
                            color: hex,
                            border: `1px solid ${hex}40`,
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 5,
                          }}
                        >
                          {renderTypeIcon(t.iconName, hex, 14)}
                          <span>{getTypeDisplayName(t.name || t.code)}</span>
                        </span>
                        <span style={{ fontSize: 11, color: '#64748B', fontWeight: 600 }}>({t.code})</span>
                      </div>
                      <div style={{ fontSize: 12, color: t.description ? '#475569' : '#94A3B8', fontStyle: t.description ? 'normal' : 'italic', marginTop: 2 }}>
                        {t.description ? t.description : 'Chưa thiết lập mô tả'}
                      </div>
                    </div>

                    {/* Hierarchy Level */}
                    <div>
                      {level === 1 && (
                        <span
                          style={{
                            padding: '3px 10px',
                            borderRadius: 12,
                            fontSize: 11,
                            fontWeight: 700,
                            backgroundColor: '#F3E8FF',
                            color: '#9333EA',
                            border: '1px solid #D8B4FE',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                          }}
                        >
                          <Zap size={12} /> Cấp 1 • Hạng mục lớn
                        </span>
                      )}
                      {level === 0 && (
                        <span
                          style={{
                            padding: '3px 10px',
                            borderRadius: 12,
                            fontSize: 11,
                            fontWeight: 700,
                            backgroundColor: '#EFF6FF',
                            color: '#2563EB',
                            border: '1px solid #BFDBFE',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                          }}
                        >
                          <CheckSquare size={12} /> Cấp 0 • Tiêu chuẩn
                        </span>
                      )}
                      {level === -1 && (
                        <span
                          style={{
                            padding: '3px 10px',
                            borderRadius: 12,
                            fontSize: 11,
                            fontWeight: 700,
                            backgroundColor: '#E0F2FE',
                            color: '#0284C7',
                            border: '1px solid #7DD3FC',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                          }}
                        >
                          <GitCommit size={12} /> Cấp -1 • Nhiệm vụ con
                        </span>
                      )}
                    </div>

                    {/* Workflow */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#0F172A', fontWeight: 600 }}>
                      <GitBranch size={14} color="#2563EB" />
                      <span>{workflowDetail?.name || 'Quy trình làm việc mặc định'}</span>
                    </div>

                    {/* Actions */}
                    {isAdmin && (
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 6 }}>
                        <button
                          type="button"
                          onClick={() => handleOpenEditTypeModal(t)}
                          style={{
                            border: '1px solid #E2E8F0',
                            background: '#FFFFFF',
                            color: '#475569',
                            cursor: 'pointer',
                            padding: '5px 8px',
                            borderRadius: 6,
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                            fontSize: 12,
                            fontWeight: 500,
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.color = '#2563EB';
                            e.currentTarget.style.borderColor = '#BFDBFE';
                            e.currentTarget.style.backgroundColor = '#EFF6FF';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.color = '#475569';
                            e.currentTarget.style.borderColor = '#E2E8F0';
                            e.currentTarget.style.backgroundColor = '#FFFFFF';
                          }}
                          title="Chỉnh sửa loại công việc"
                        >
                          <Edit2 size={13} />
                          <span>Sửa</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => handleDeleteType(t)}
                          style={{
                            border: '1px solid #E2E8F0',
                            background: '#FFFFFF',
                            color: canDelete ? '#64748B' : '#CBD5E1',
                            cursor: canDelete ? 'pointer' : 'not-allowed',
                            padding: '5px 8px',
                            borderRadius: 6,
                            display: 'flex',
                            alignItems: 'center',
                            gap: 4,
                            fontSize: 12,
                            fontWeight: 500,
                          }}
                          onMouseEnter={(e) => {
                            if (canDelete) {
                              e.currentTarget.style.color = '#EF4444';
                              e.currentTarget.style.borderColor = '#FECACA';
                              e.currentTarget.style.backgroundColor = '#FEF2F2';
                            }
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.color = canDelete ? '#64748B' : '#CBD5E1';
                            e.currentTarget.style.borderColor = '#E2E8F0';
                            e.currentTarget.style.backgroundColor = '#FFFFFF';
                          }}
                          title={canDelete ? 'Xóa loại công việc' : 'Không thể xóa (Cần giữ tối thiểu 1 loại cho cấp bậc này)'}
                        >
                          <Trash2 size={13} />
                          <span>Xóa</span>
                        </button>
                      </div>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* ─── MODAL: Add Transition ─── */}
      {isAddTransitionOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            backdropFilter: 'blur(2px)',
          }}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              width: '100%',
              maxWidth: 480,
              padding: '24px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>
                <GitBranch size={18} color="#2563EB" />
                <span>{editingTransition ? 'Chỉnh Sửa Bước Chuyển Trạng Thái' : 'Thêm Bước Chuyển Trạng Thái'}</span>
              </div>
              <button
                type="button"
                onClick={() => {
                  setIsAddTransitionOpen(false);
                  setEditingTransition(null);
                }}
                style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#64748B' }}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveTransition}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Trạng thái xuất phát
                </label>
                <select
                  value={fromStatusId}
                  onChange={(e) => setFromStatusId(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                  }}
                >
                  <option value="">-- Bất kỳ trạng thái nào (Khởi tạo / Tự do) --</option>
                  {allStatuses.map((st) => (
                    <option key={st.id} value={st.id} disabled={toStatusId && st.id === toStatusId}>
                      {getStatusDisplayName(st.name)} ({getStatusCategoryDisplayName(st.category)}){toStatusId && st.id === toStatusId ? ' (Trùng trạng thái đích)' : ''}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Trạng thái đích *
                </label>
                <select
                  value={toStatusId}
                  onChange={(e) => setToStatusId(e.target.value)}
                  required
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                  }}
                >
                  <option value="">-- Chọn trạng thái đích --</option>
                  {allStatuses.map((st) => (
                    <option key={st.id} value={st.id} disabled={fromStatusId && st.id === fromStatusId}>
                      {getStatusDisplayName(st.name)} ({getStatusCategoryDisplayName(st.category)}){fromStatusId && st.id === fromStatusId ? ' (Trùng trạng thái bắt đầu)' : ''}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Tên nút bấm chuyển *
                </label>
                <input
                  type="text"
                  placeholder="VD: Bắt đầu làm, Gửi xét duyệt, Phê duyệt & Hoàn tất"
                  value={transitionName}
                  onChange={(e) => setTransitionName(e.target.value)}
                  maxLength={150}
                  required
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                />
              </div>

              <div style={{ marginBottom: 16, backgroundColor: '#F8FAFC', padding: 12, borderRadius: 8, border: '1px solid #E2E8F0' }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: '#334155', marginBottom: 8 }}>
                  Ràng buộc & Điều kiện chuyển đổi
                </div>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: '#475569', marginBottom: 6, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={requireAssigneeOnly}
                    onChange={(e) => setRequireAssigneeOnly(e.target.checked)}
                  />
                  <span>Chỉ người được giao việc mới được chuyển</span>
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: '#475569', marginBottom: 6, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={requireSubtasksDone}
                    onChange={(e) => setRequireSubtasksDone(e.target.checked)}
                  />
                  <span>Bắt buộc tất cả nhiệm vụ con phải hoàn thành trước</span>
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: '#475569', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={requireResolution}
                    onChange={(e) => setRequireResolution(e.target.checked)}
                  />
                  <span>Bắt buộc chọn lý do giải quyết</span>
                </label>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button
                  type="button"
                  onClick={() => {
                    setIsAddTransitionOpen(false);
                    setEditingTransition(null);
                  }}
                  style={{
                    padding: '8px 14px',
                    borderRadius: 6,
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
                  disabled={submittingTransition}
                  style={{
                    padding: '8px 18px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: submittingTransition ? 'not-allowed' : 'pointer',
                  }}
                >
                  {submittingTransition ? 'Đang lưu...' : editingTransition ? 'Lưu Thay Đổi' : 'Thêm Bước Chuyển'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── MODAL: Add Status ─── */}
      {isAddStatusOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            backdropFilter: 'blur(2px)',
          }}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              width: '100%',
              maxWidth: 440,
              padding: '24px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>
                <Sliders size={18} color="#2563EB" />
                <span>{editingStatus ? 'Chỉnh Sửa Trạng Thái' : 'Tạo Trạng Thái Công Việc Mới'}</span>
              </div>
              <button
                type="button"
                onClick={() => {
                  setIsAddStatusOpen(false);
                  setEditingStatus(null);
                }}
                style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#64748B' }}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveStatus}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Tên Trạng Thái *
                </label>
                <input
                  type="text"
                  placeholder="VD: Đang duyệt mã, Đang kiểm thử, Sẵn sàng nghiệm thu"
                  value={newStatusName}
                  onChange={(e) => setNewStatusName(e.target.value)}
                  required
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                />
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Nhóm trạng thái chuẩn *
                </label>
                <select
                  value={newStatusCategory}
                  onChange={(e) => setNewStatusCategory(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                  }}
                >
                  <option value="TODO">Cần làm</option>
                  <option value="IN_PROGRESS">Đang thực hiện</option>
                  <option value="DONE">Hoàn thành</option>
                </select>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Mã màu hiển thị
                </label>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <input
                    type="color"
                    value={newStatusColor}
                    onChange={(e) => setNewStatusColor(e.target.value)}
                    style={{ width: 36, height: 36, border: 'none', borderRadius: 4, cursor: 'pointer' }}
                  />
                  <input
                    type="text"
                    value={newStatusColor}
                    onChange={(e) => setNewStatusColor(e.target.value)}
                    style={{
                      flex: 1,
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid #CBD5E1',
                      fontSize: 13,
                    }}
                  />
                </div>
              </div>

              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Mô Tả
                </label>
                <textarea
                  rows={2}
                  placeholder="Mô tả ý nghĩa của trạng thái..."
                  value={newStatusDesc}
                  onChange={(e) => setNewStatusDesc(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                    boxSizing: 'border-box',
                    resize: 'none',
                  }}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button
                  type="button"
                  onClick={() => {
                    setIsAddStatusOpen(false);
                    setEditingStatus(null);
                  }}
                  style={{
                    padding: '8px 14px',
                    borderRadius: 6,
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
                  disabled={submittingStatus}
                  style={{
                    padding: '8px 18px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: submittingStatus ? 'not-allowed' : 'pointer',
                  }}
                >
                  {submittingStatus
                    ? (editingStatus ? 'Đang cập nhật...' : 'Đang tạo...')
                    : (editingStatus ? 'Cập Nhật Trạng Thái' : 'Tạo Trạng Thái')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* ─── MODAL 3: Create / Edit Work Item Type ─── */}
      {isTypeModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            backdropFilter: 'blur(2px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 16,
          }}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              width: '100%',
              maxWidth: 540,
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
              overflow: 'hidden',
            }}
          >
            {/* Modal Header */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '16px 20px',
                borderBottom: '1px solid #E2E8F0',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: 8,
                    backgroundColor: `${typeFormData.colorHex}20`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {renderTypeIcon(typeFormData.iconName, typeFormData.colorHex, 18)}
                </div>
                <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                  {editingType ? 'Chỉnh Sửa Loại Công Việc' : 'Thêm Loại Công Việc Mới'}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setIsTypeModalOpen(false)}
                style={{ border: 'none', background: 'transparent', color: '#94A3B8', cursor: 'pointer', padding: 4 }}
              >
                <X size={18} />
              </button>
            </div>

            {/* Modal Form */}
            <form onSubmit={handleSaveType} style={{ padding: 20, maxHeight: '80vh', overflowY: 'auto' }}>
              {/* Name & Code */}
              <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 12, marginBottom: 14 }}>
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                    Tên Loại Công Việc *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="VD: Nghiệm thu, Yêu cầu,..."
                    value={typeFormData.name}
                    onChange={(e) => setTypeFormData({ ...typeFormData, name: e.target.value })}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid #CBD5E1',
                      fontSize: 13,
                      outline: 'none',
                      boxSizing: 'border-box',
                    }}
                  />
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                    Mã loại (viết hoa) *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="VD: KIEM_THU, TAI_LIEU"
                    value={typeFormData.code}
                    onChange={(e) => setTypeFormData({ ...typeFormData, code: e.target.value.toUpperCase() })}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: '1px solid #CBD5E1',
                      fontSize: 13,
                      outline: 'none',
                      boxSizing: 'border-box',
                      fontWeight: 700,
                      textTransform: 'uppercase',
                    }}
                  />
                </div>
              </div>

              {/* Hierarchy Level Selection */}
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Cấp bậc phân cấp *
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8 }}>
                  {/* Level 1: Epic */}
                  <div
                    onClick={() => setTypeFormData({ ...typeFormData, hierarchyLevel: 1 })}
                    style={{
                      padding: '10px',
                      borderRadius: 8,
                      border: `1.5px solid ${typeFormData.hierarchyLevel === 1 ? '#9333EA' : '#E2E8F0'}`,
                      backgroundColor: typeFormData.hierarchyLevel === 1 ? '#F3E8FF' : '#F8FAFC',
                      cursor: 'pointer',
                      textAlign: 'center',
                      transition: 'all 0.15s ease',
                    }}
                  >
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#9333EA', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
                      <Zap size={14} /> Cấp 1
                    </div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: '#6B21A8', marginTop: 2 }}>Hạng mục lớn</div>
                    <div style={{ fontSize: 10, color: '#7E22CE', marginTop: 4 }}>Công việc cấp cao nhất</div>
                  </div>

                  {/* Level 0: Standard */}
                  <div
                    onClick={() => setTypeFormData({ ...typeFormData, hierarchyLevel: 0 })}
                    style={{
                      padding: '10px',
                      borderRadius: 8,
                      border: `1.5px solid ${typeFormData.hierarchyLevel === 0 ? '#2563EB' : '#E2E8F0'}`,
                      backgroundColor: typeFormData.hierarchyLevel === 0 ? '#EFF6FF' : '#F8FAFC',
                      cursor: 'pointer',
                      textAlign: 'center',
                      transition: 'all 0.15s ease',
                    }}
                  >
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#2563EB', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
                      <CheckSquare size={14} /> Cấp 0
                    </div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: '#1D4ED8', marginTop: 2 }}>Tiêu chuẩn</div>
                    <div style={{ fontSize: 10, color: '#1E40AF', marginTop: 4 }}>Trực thuộc hạng mục lớn hoặc độc lập</div>
                  </div>

                  {/* Level -1: Subtask */}
                  <div
                    onClick={() => setTypeFormData({ ...typeFormData, hierarchyLevel: -1 })}
                    style={{
                      padding: '10px',
                      borderRadius: 8,
                      border: `1.5px solid ${typeFormData.hierarchyLevel === -1 ? '#0284C7' : '#E2E8F0'}`,
                      backgroundColor: typeFormData.hierarchyLevel === -1 ? '#E0F2FE' : '#F8FAFC',
                      cursor: 'pointer',
                      textAlign: 'center',
                      transition: 'all 0.15s ease',
                    }}
                  >
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#0284C7', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
                      <GitCommit size={14} /> Cấp -1
                    </div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: '#0369A1', marginTop: 2 }}>Nhiệm vụ con</div>
                    <div style={{ fontSize: 10, color: '#075985', marginTop: 4 }}>Bắt buộc trực thuộc công việc Cấp 0</div>
                  </div>
                </div>
              </div>

              {/* Description (Describe) */}
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Mô tả mục đích sử dụng
                </label>
                <textarea
                  rows={3}
                  placeholder="Mô tả ý nghĩa, tiêu chuẩn và hướng dẫn sử dụng loại công việc này..."
                  value={typeFormData.description}
                  onChange={(e) => setTypeFormData({ ...typeFormData, description: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                    boxSizing: 'border-box',
                    resize: 'vertical',
                    fontFamily: 'inherit',
                  }}
                />
              </div>

              {/* Color Hex & Presets */}
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Màu Sắc Nhận Diện
                </label>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8, flexWrap: 'wrap' }}>
                  {COLOR_PRESETS.map((col) => (
                    <button
                      key={col}
                      type="button"
                      onClick={() => setTypeFormData({ ...typeFormData, colorHex: col })}
                      style={{
                        width: 26,
                        height: 26,
                        borderRadius: '50%',
                        backgroundColor: col,
                        border: typeFormData.colorHex?.toUpperCase() === col ? '2.5px solid #0F172A' : '2px solid #FFFFFF',
                        cursor: 'pointer',
                        boxShadow: '0 1px 3px rgba(0,0,0,0.15)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      {typeFormData.colorHex?.toUpperCase() === col && <Check size={13} color="#FFFFFF" />}
                    </button>
                  ))}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginLeft: 'auto' }}>
                    <input
                      type="color"
                      value={typeFormData.colorHex}
                      onChange={(e) => setTypeFormData({ ...typeFormData, colorHex: e.target.value })}
                      style={{ width: 32, height: 32, border: 'none', borderRadius: 4, cursor: 'pointer' }}
                    />
                    <input
                      type="text"
                      value={typeFormData.colorHex}
                      onChange={(e) => setTypeFormData({ ...typeFormData, colorHex: e.target.value })}
                      style={{
                        width: 80,
                        padding: '6px 8px',
                        borderRadius: 6,
                        border: '1px solid #CBD5E1',
                        fontSize: 12,
                        textAlign: 'center',
                        fontWeight: 600,
                      }}
                    />
                  </div>
                </div>
              </div>

              {/* Icon Selector */}
              <div style={{ marginBottom: 18 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Biểu tượng hiển thị
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6 }}>
                  {ICON_PRESETS.map((ic) => {
                    const isSelected = typeFormData.iconName === ic.name;
                    return (
                      <button
                        key={ic.name}
                        type="button"
                        onClick={() => setTypeFormData({ ...typeFormData, iconName: ic.name })}
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          gap: 4,
                          padding: '8px 4px',
                          borderRadius: 6,
                          border: `1px solid ${isSelected ? typeFormData.colorHex : '#E2E8F0'}`,
                          backgroundColor: isSelected ? `${typeFormData.colorHex}15` : '#F8FAFC',
                          cursor: 'pointer',
                          transition: 'all 0.1s ease',
                        }}
                      >
                        {renderTypeIcon(ic.name, isSelected ? typeFormData.colorHex : '#64748B', 18)}
                        <span style={{ fontSize: 10, color: isSelected ? typeFormData.colorHex : '#64748B', fontWeight: isSelected ? 700 : 500 }}>
                          {ic.name}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Form Actions */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, paddingTop: 12, borderTop: '1px solid #E2E8F0' }}>
                <button
                  type="button"
                  onClick={() => setIsTypeModalOpen(false)}
                  style={{
                    padding: '8px 14px',
                    borderRadius: 6,
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
                  disabled={submittingType}
                  style={{
                    padding: '8px 18px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: submittingType ? 'not-allowed' : 'pointer',
                  }}
                >
                  {submittingType ? 'Đang lưu...' : editingType ? 'Cập Nhật' : 'Tạo Mới'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── MODAL 4: Presets / Mẫu Cấu Hình Gợi Ý ─── */}
      {isPresetModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            backdropFilter: 'blur(2px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 16,
          }}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              width: '100%',
              maxWidth: 720,
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
              overflow: 'hidden',
            }}
          >
            {/* Modal Header */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '16px 20px',
                borderBottom: '1px solid #E2E8F0',
                backgroundColor: '#F8FAFC',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Sparkles size={20} color="#2563EB" />
                <div>
                  <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                    Mẫu Cấu Hình Loại Công Việc Gợi Ý
                  </h3>
                  <div style={{ fontSize: 12, color: '#64748B', marginTop: 2 }}>
                    Chọn bộ cấu hình gợi ý chuyên nghiệp theo từng loại hình dự án
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsPresetModalOpen(false)}
                style={{ border: 'none', background: 'transparent', color: '#94A3B8', cursor: 'pointer', padding: 4 }}
              >
                <X size={18} />
              </button>
            </div>

            {/* Modal Body */}
            <div style={{ padding: 20, maxHeight: '72vh', overflowY: 'auto' }}>
              {/* Presets List */}
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 20 }}>
                {TASK_TYPE_PRESETS.map((preset) => {
                  const isSelected = selectedPreset?.id === preset.id;
                  return (
                    <div
                      key={preset.id}
                      onClick={() => setSelectedPreset(preset)}
                      style={{
                        padding: '14px',
                        borderRadius: 10,
                        border: `1.5px solid ${isSelected ? '#2563EB' : '#E2E8F0'}`,
                        backgroundColor: isSelected ? '#EFF6FF' : '#FFFFFF',
                        cursor: 'pointer',
                        transition: 'all 0.15s ease',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                        <span style={{ fontSize: 14, fontWeight: 700, color: isSelected ? '#1D4ED8' : '#0F172A' }}>
                          {preset.name}
                        </span>
                        {isSelected && (
                          <div style={{ width: 20, height: 20, borderRadius: '50%', backgroundColor: '#2563EB', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <Check size={12} color="#FFFFFF" />
                          </div>
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: '#64748B', lineHeight: 1.4 }}>
                        {preset.description}
                      </div>
                      <div style={{ marginTop: 8, fontSize: 11, fontWeight: 600, color: isSelected ? '#2563EB' : '#94A3B8' }}>
                        Bao gồm {preset.types.length} loại công việc
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Selected Preset Details */}
              {selectedPreset && (
                <div style={{ backgroundColor: '#F8FAFC', borderRadius: 8, border: '1px solid #E2E8F0', padding: 16, marginBottom: 16 }}>
                  <div style={{ fontSize: 13, fontWeight: 700, color: '#334155', marginBottom: 10 }}>
                    Chi tiết các loại công việc trong mẫu "{selectedPreset.name}":
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {selectedPreset.types.map((t) => (
                      <div
                        key={t.code}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '8px 12px',
                          backgroundColor: '#FFFFFF',
                          borderRadius: 6,
                          border: '1px solid #E2E8F0',
                          fontSize: 12,
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span
                            style={{
                              padding: '2px 8px',
                              borderRadius: 4,
                              fontSize: 11,
                              fontWeight: 700,
                              backgroundColor: `${t.colorHex}15`,
                              color: t.colorHex,
                              border: `1px solid ${t.colorHex}40`,
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 4,
                            }}
                          >
                            {renderTypeIcon(t.iconName, t.colorHex, 13)}
                            <span>{getTypeDisplayName(t.name || t.code)}</span>
                          </span>
                          <span style={{ color: '#64748B', fontWeight: 600 }}>({t.code})</span>
                          <span style={{ color: '#64748B' }}>- {t.description}</span>
                        </div>
                        <span
                          style={{
                            fontSize: 11,
                            fontWeight: 700,
                            padding: '2px 6px',
                            borderRadius: 4,
                            backgroundColor: t.hierarchyLevel === 1 ? '#F3E8FF' : t.hierarchyLevel === 0 ? '#EFF6FF' : '#E0F2FE',
                            color: t.hierarchyLevel === 1 ? '#9333EA' : t.hierarchyLevel === 0 ? '#2563EB' : '#0284C7',
                          }}
                        >
                          Cấp {t.hierarchyLevel}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Warning note */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 10,
                  padding: 12,
                  borderRadius: 8,
                  backgroundColor: '#FEF3C7',
                  border: '1px solid #FDE68A',
                  fontSize: 12,
                  color: '#92400E',
                }}
              >
                <AlertTriangle size={16} color="#D97706" style={{ marginTop: 2, flexShrink: 0 }} />
                <div>
                  <strong>Cơ chế bảo toàn an toàn:</strong> Khi áp dụng mẫu, hệ thống sẽ thêm các loại công việc mới và tự động dọn dẹp các loại cũ chưa có công việc nào sử dụng. Các loại cũ đang có công việc sử dụng sẽ được giữ lại an toàn.
                </div>
              </div>
            </div>

            {/* Modal Actions */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                gap: 8,
                padding: '14px 20px',
                borderTop: '1px solid #E2E8F0',
                backgroundColor: '#F8FAFC',
              }}
            >
              <button
                type="button"
                onClick={() => setIsPresetModalOpen(false)}
                style={{
                  padding: '8px 16px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  backgroundColor: '#FFFFFF',
                  color: '#475569',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Đóng
              </button>
              <button
                type="button"
                onClick={handleApplyPreset}
                disabled={applyingPreset}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 20px',
                  borderRadius: 6,
                  border: 'none',
                  backgroundColor: '#2563EB',
                  color: '#FFFFFF',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: applyingPreset ? 'not-allowed' : 'pointer',
                  boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
                }}
              >
                <Sparkles size={15} />
                <span>{applyingPreset ? 'Đang áp dụng...' : 'Áp Dụng Cấu Hình Này'}</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ─── MODAL 5: Architectural Explanation Modal (Cấp bậc, Quy trình & Thời gian) ─── */}
      {isExplanationModalOpen && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.65)',
            backdropFilter: 'blur(3px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '16px',
          }}
          onClick={() => setIsExplanationModalOpen(false)}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 14,
              width: '100%',
              maxWidth: 900,
              maxHeight: '90vh',
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
              border: '1px solid #CBD5E1',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '18px 24px',
                borderBottom: '1px solid #E2E8F0',
                backgroundColor: '#F8FAFC',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: 10,
                    backgroundColor: '#EFF6FF',
                    border: '1px solid #BFDBFE',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#2563EB',
                  }}
                >
                  <BookOpen size={20} />
                </div>
                <div>
                  <h3 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                    Kiến Trúc Quản Lý: Cấp Bậc, Quy Trình & Thời Gian
                  </h3>
                  <p style={{ fontSize: 13, color: '#64748B', margin: '2px 0 0 0' }}>
                    Hiểu rõ cách phân cấp cây công việc, mối quan hệ trạng thái quy trình và cơ chế cộng dồn thời gian tự động.
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setIsExplanationModalOpen(false)}
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
                onMouseEnter={(e) => (e.currentTarget.style.color = '#0F172A')}
                onMouseLeave={(e) => (e.currentTarget.style.color = '#94A3B8')}
              >
                <X size={20} />
              </button>
            </div>

            {/* Sub-tab Navigation */}
            <div
              style={{
                display: 'flex',
                borderBottom: '1px solid #E2E8F0',
                backgroundColor: '#FFFFFF',
                padding: '0 24px',
                gap: 8,
              }}
            >
              <button
                type="button"
                onClick={() => setExplanationTab('hierarchy')}
                style={{
                  padding: '12px 16px',
                  border: 'none',
                  borderBottom: explanationTab === 'hierarchy' ? '2px solid #2563EB' : '2px solid transparent',
                  backgroundColor: 'transparent',
                  color: explanationTab === 'hierarchy' ? '#2563EB' : '#64748B',
                  fontWeight: explanationTab === 'hierarchy' ? 700 : 500,
                  fontSize: 13,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  transition: 'all 0.15s ease',
                }}
              >
                <Layers size={16} />
                <span>1. Phân Cấp Cây Công Việc</span>
              </button>

              <button
                type="button"
                onClick={() => setExplanationTab('workflow')}
                style={{
                  padding: '12px 16px',
                  border: 'none',
                  borderBottom: explanationTab === 'workflow' ? '2px solid #2563EB' : '2px solid transparent',
                  backgroundColor: 'transparent',
                  color: explanationTab === 'workflow' ? '#2563EB' : '#64748B',
                  fontWeight: explanationTab === 'workflow' ? 700 : 500,
                  fontSize: 13,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  transition: 'all 0.15s ease',
                }}
              >
                <GitBranch size={16} />
                <span>2. Mối Quan Hệ Loại Công Việc & Trạng Thái</span>
              </button>

              <button
                type="button"
                onClick={() => setExplanationTab('time')}
                style={{
                  padding: '12px 16px',
                  border: 'none',
                  borderBottom: explanationTab === 'time' ? '2px solid #2563EB' : '2px solid transparent',
                  backgroundColor: 'transparent',
                  color: explanationTab === 'time' ? '#2563EB' : '#64748B',
                  fontWeight: explanationTab === 'time' ? 700 : 500,
                  fontSize: 13,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  transition: 'all 0.15s ease',
                }}
              >
                <Clock size={16} />
                <span>3. Mối Quan Hệ Loại Công Việc & Thời Gian</span>
              </button>
            </div>

            {/* Modal Scrollable Body */}
            <div
              style={{
                padding: '24px',
                overflowY: 'auto',
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                gap: 20,
              }}
            >
              {/* ─── TAB 1: HIERARCHY LEVELS ─── */}
              {explanationTab === 'hierarchy' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {/* Rule Banner */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 12,
                      padding: 14,
                      borderRadius: 8,
                      backgroundColor: '#EFF6FF',
                      border: '1px solid #BFDBFE',
                      fontSize: 13,
                      color: '#1E40AF',
                      lineHeight: 1.5,
                    }}
                  >
                    <Shield size={18} color="#2563EB" style={{ marginTop: 2, flexShrink: 0 }} />
                    <div>
                      <strong>Ràng buộc bất biến của hệ thống:</strong> Mỗi dự án luôn bắt buộc phải duy trì tối thiểu <strong>1 loại cho Cấp 1</strong>, <strong>1 loại cho Cấp 0</strong>, và <strong>1 loại cho Cấp -1</strong>. Hệ thống ngăn chặn việc xóa hoặc chuyển đổi làm mất đi loại công việc duy nhất của bất kỳ cấp độ nào.
                    </div>
                  </div>

                  {/* Visual Tree Flow */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      padding: '14px',
                      backgroundColor: '#F8FAFC',
                      borderRadius: 10,
                      border: '1px solid #E2E8F0',
                      gap: 12,
                      flexWrap: 'wrap',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 14px', borderRadius: 8, backgroundColor: '#F3E8FF', border: '1px solid #E9D5FF' }}>
                      <Zap size={16} color="#9333EA" />
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#6B21A8' }}>Cấp 1: Hạng mục lớn</span>
                    </div>

                    <ArrowRight size={18} color="#94A3B8" />

                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 14px', borderRadius: 8, backgroundColor: '#EFF6FF', border: '1px solid #BFDBFE' }}>
                      <CheckSquare size={16} color="#2563EB" />
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#1D4ED8' }}>Cấp 0: Tiêu chuẩn (Nhiệm vụ / Câu chuyện / Lỗi)</span>
                    </div>

                    <ArrowRight size={18} color="#94A3B8" />

                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 14px', borderRadius: 8, backgroundColor: '#E0F2FE', border: '1px solid #BAE6FD' }}>
                      <GitCommit size={16} color="#0284C7" />
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#0369A1' }}>Cấp -1: Nhiệm vụ con</span>
                    </div>
                  </div>

                  {/* Level Cards */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                    {/* Level 1 Card */}
                    <div
                      style={{
                        padding: 16,
                        borderRadius: 10,
                        border: '1px solid #E9D5FF',
                        backgroundColor: '#FAF5FF',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                        <span style={{ padding: '3px 10px', borderRadius: 6, fontSize: 12, fontWeight: 700, backgroundColor: '#9333EA', color: '#FFFFFF' }}>
                          Cấp 1
                        </span>
                        <h4 style={{ fontSize: 15, fontWeight: 700, color: '#581C87', margin: 0 }}>
                          Hạng Mục Lớn — Tầng Bao Quát & Chiến Lược
                        </h4>
                      </div>
                      <p style={{ fontSize: 13, color: '#6B21A8', margin: '0 0 10px 0', lineHeight: 1.5 }}>
                        Đại diện cho mục tiêu dài hạn, cụm tính năng lớn, gói thầu trọng điểm hoặc sáng kiến chiến lược kéo dài qua nhiều tháng hoặc nhiều chu kỳ làm việc.
                      </p>
                      <ul style={{ margin: 0, paddingLeft: 20, fontSize: 13, color: '#4A044E', lineHeight: 1.6 }}>
                        <li><strong>Vị trí cây phân cấp:</strong> Là cấp cao nhất trong dự án (Tuyệt đối <strong>không được phép có công việc cha</strong>).</li>
                        <li><strong>Quan hệ con:</strong> Chỉ chứa trực tiếp các công việc con thuộc <strong>Cấp 0</strong>. Không được chứa trực tiếp Nhiệm vụ con Cấp -1.</li>
                        <li><strong>Ví dụ:</strong> <em>Hệ thống Cổng Thanh Toán Trực Tuyến</em>, <em>Thi công hạng mục Cọc & Móng Tòa nhà</em>, <em>Chiến dịch Marketing Tết 2026</em>.</li>
                      </ul>
                    </div>

                    {/* Level 0 Card */}
                    <div
                      style={{
                        padding: 16,
                        borderRadius: 10,
                        border: '1px solid #BFDBFE',
                        backgroundColor: '#F0F9FF',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                        <span style={{ padding: '3px 10px', borderRadius: 6, fontSize: 12, fontWeight: 700, backgroundColor: '#2563EB', color: '#FFFFFF' }}>
                          Cấp 0
                        </span>
                        <h4 style={{ fontSize: 15, fontWeight: 700, color: '#1E3A8A', margin: 0 }}>
                          Công Việc Tiêu Chuẩn (Nhiệm vụ, Câu chuyện, Lỗi...) — Tầng Thực Thi Cốt Lõi
                        </h4>
                      </div>
                      <p style={{ fontSize: 13, color: '#1E40AF', margin: '0 0 10px 0', lineHeight: 1.5 }}>
                        Là đơn vị công việc trọng tâm phục vụ lập kế hoạch chu kỳ, phân công thành viên phụ trách và theo dõi tiến độ hàng tuần.
                      </p>
                      <ul style={{ margin: 0, paddingLeft: 20, fontSize: 13, color: '#172554', lineHeight: 1.6 }}>
                        <li><strong>Vị trí cây phân cấp:</strong> Có thể trực thuộc một Hạng mục lớn (Cấp 1) hoặc có thể đứng độc lập không cần cha.</li>
                        <li><strong>Phân công & Ước lượng:</strong> Được gán trực tiếp cho người phụ trách, có điểm ước lượng, ngày bắt đầu và hạn chót hoàn thành.</li>
                        <li><strong>Quan hệ con:</strong> Có thể chứa nhiều Nhiệm vụ con (**Cấp -1**) bên dưới để phân rã kỹ thuật.</li>
                        <li><strong>Ví dụ:</strong> <em>Tích hợp cổng thanh toán</em>, <em>Sửa lỗi ứng dụng khi tải tệp</em>, <em>Đổ bê tông dầm sàn tầng 1</em>.</li>
                      </ul>
                    </div>

                    {/* Level -1 Card */}
                    <div
                      style={{
                        padding: 16,
                        borderRadius: 10,
                        border: '1px solid #BAE6FD',
                        backgroundColor: '#F8FAFC',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                        <span style={{ padding: '3px 10px', borderRadius: 6, fontSize: 12, fontWeight: 700, backgroundColor: '#0284C7', color: '#FFFFFF' }}>
                          Cấp -1
                        </span>
                        <h4 style={{ fontSize: 15, fontWeight: 700, color: '#0369A1', margin: 0 }}>
                          Nhiệm Vụ Con — Tầng Tác Vụ Vi Mô Chi Tiết
                        </h4>
                      </div>
                      <p style={{ fontSize: 13, color: '#0284C7', margin: '0 0 10px 0', lineHeight: 1.5 }}>
                        Dành cho các tác vụ kỹ thuật nhỏ từ 1-8 giờ để phân chia cụ thể các bước thực hiện cho từng cá nhân.
                      </p>
                      <ul style={{ margin: 0, paddingLeft: 20, fontSize: 13, color: '#0C4A6E', lineHeight: 1.6 }}>
                        <li><strong>Vị trí cây phân cấp:</strong> Bắt buộc <strong>phải có công việc cha thuộc Cấp 0</strong>. Không thể đứng độc lập một mình.</li>
                        <li><strong>Giới hạn phân cấp:</strong> Không thể chia thêm cấp con bên dưới (không có cấp cháu).</li>
                        <li><strong>Ví dụ:</strong> <em>Viết hàm mã hóa bảo mật</em>, <em>Cắt giao diện cửa sổ thông báo</em>, <em>Kiểm tra chất lượng vật tư</em>.</li>
                      </ul>
                    </div>
                  </div>
                </div>
              )}

              {/* ─── TAB 2: WORKFLOW & STATUS ─── */}
              {explanationTab === 'workflow' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {/* Intro Banner */}
                  <div
                    style={{
                      padding: 16,
                      borderRadius: 10,
                      backgroundColor: '#F8FAFC',
                      border: '1px solid #E2E8F0',
                      lineHeight: 1.6,
                      fontSize: 13,
                      color: '#334155',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                      <GitBranch size={18} color="#2563EB" />
                      <strong style={{ fontSize: 14, color: '#0F172A' }}>Tại sao cần phân tách Sơ đồ Quy trình theo Loại Công việc?</strong>
                    </div>
                    Trong thực tế, mỗi loại hình công việc có một vòng đời và quy trình kiểm duyệt hoàn toàn khác nhau. Hệ thống cho phép gán mỗi <strong>Loại công việc</strong> với một sơ đồ trạng thái riêng biệt để phản ánh đúng thực tế vận hành.
                  </div>

                  {/* Workflow Comparison Grid */}
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
                    {/* Bug Flow */}
                    <div style={{ padding: 14, borderRadius: 8, border: '1px solid #FECACA', backgroundColor: '#FEF2F2' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8, color: '#DC2626', fontWeight: 700, fontSize: 13 }}>
                        <AlertCircle size={16} />
                        <span>Quy trình Xử lý Lỗi</span>
                      </div>
                      <div style={{ fontSize: 12, color: '#991B1B', lineHeight: 1.5 }}>
                        Cần kiểm thử và xác minh nghiêm ngặt qua nhiều bước:
                        <div style={{ marginTop: 8, padding: 8, backgroundColor: '#FFFFFF', borderRadius: 6, border: '1px solid #FCA5A5', fontSize: 11 }}>
                          Mới tạo → Đã xác nhận → Đang sửa lỗi → Kiểm tra lại → Đã hoàn tất
                        </div>
                      </div>
                    </div>

                    {/* Standard Flow */}
                    <div style={{ padding: 14, borderRadius: 8, border: '1px solid #BFDBFE', backgroundColor: '#EFF6FF' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8, color: '#2563EB', fontWeight: 700, fontSize: 13 }}>
                        <CheckSquare size={16} />
                        <span>Quy trình Nhiệm vụ / Câu chuyện</span>
                      </div>
                      <div style={{ fontSize: 12, color: '#1E40AF', lineHeight: 1.5 }}>
                        Dòng chảy công việc linh hoạt tinh gọn:
                        <div style={{ marginTop: 8, padding: 8, backgroundColor: '#FFFFFF', borderRadius: 6, border: '1px solid #93C5FD', fontSize: 11 }}>
                          Cần làm → Đang thực hiện → Chờ duyệt → Hoàn thành
                        </div>
                      </div>
                    </div>

                    {/* Epic Flow */}
                    <div style={{ padding: 14, borderRadius: 8, border: '1px solid #E9D5FF', backgroundColor: '#FAF5FF' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8, color: '#9333EA', fontWeight: 700, fontSize: 13 }}>
                        <Zap size={16} />
                        <span>Quy trình Hạng mục lớn Vĩ mô</span>
                      </div>
                      <div style={{ fontSize: 12, color: '#6B21A8', lineHeight: 1.5 }}>
                        Theo dõi tiến độ phát triển giai đoạn cấp quản lý:
                        <div style={{ marginTop: 8, padding: 8, backgroundColor: '#FFFFFF', borderRadius: 6, border: '1px solid #D8B4FE', fontSize: 11 }}>
                          Bản thảo → Kế hoạch → Đang thực hiện → Hoàn tất
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Guards & Validators */}
                  <div style={{ padding: 16, borderRadius: 10, border: '1px solid #E2E8F0', backgroundColor: '#FFFFFF' }}>
                    <h4 style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', margin: '0 0 10px 0', display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Shield size={16} color="#059669" />
                      <span>Các Điều Kiện Chặn Chuyển Trạng Thái</span>
                    </h4>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: 13, color: '#475569' }}>
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                        <CheckCircle2 size={16} color="#059669" style={{ marginTop: 2, flexShrink: 0 }} />
                        <div>
                          <strong>Toàn bộ việc con phải hoàn thành trước:</strong> Công việc cha (Cấp 0) chỉ được phép kéo sang <code>Hoàn thành</code> khi tất cả các Nhiệm vụ con (Cấp -1) trực thuộc đã hoàn thành. Ngăn chặn việc đóng việc khi chưa hoàn tất.
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                        <CheckCircle2 size={16} color="#059669" style={{ marginTop: 2, flexShrink: 0 }} />
                        <div>
                          <strong>Đúng người được giao việc:</strong> Chỉ người thực hiện chính được phân công (hoặc Quản trị viên) mới có quyền bấm chuyển công việc sang bước tiếp theo.
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                        <CheckCircle2 size={16} color="#059669" style={{ marginTop: 2, flexShrink: 0 }} />
                        <div>
                          <strong>Bắt buộc nhập dữ liệu:</strong> Yêu cầu nhập lý do xử lý hoặc mô tả nghiệm thu trước khi chuyển sang trạng thái kết thúc.
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* 3 Categories Note */}
                  <div style={{ padding: 14, borderRadius: 8, backgroundColor: '#F8FAFC', border: '1px solid #E2E8F0', fontSize: 12, color: '#64748B' }}>
                    <strong>Chuẩn hóa 3 Nhóm Danh mục Trạng thái:</strong> Mọi trạng thái dù có tên gọi tùy biến thế nào cũng được phân về đúng 1 trong 3 nhóm chuẩn: <code>Cần làm</code>, <code>Đang thực hiện</code>, và <code>Hoàn thành</code> để hệ thống tự động tính toán báo cáo tiến độ và biểu đồ tiến độ chu kỳ.
                  </div>
                </div>
              )}

              {/* ─── TAB 3: TIME RELATIONSHIPS ─── */}
              {explanationTab === 'time' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {/* Rollup Engine Card */}
                  <div
                    style={{
                      padding: 16,
                      borderRadius: 10,
                      backgroundColor: '#F0FDF4',
                      border: '1px solid #BBF7D0',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, color: '#166534' }}>
                      <TrendingUp size={18} />
                      <h4 style={{ fontSize: 15, fontWeight: 700, margin: 0 }}>
                        Cơ Chế Cộng Dồn Thời Gian Tự Động
                      </h4>
                    </div>
                    <p style={{ fontSize: 13, color: '#15803D', margin: '0 0 12px 0', lineHeight: 1.5 }}>
                      Hệ thống tự động đồng bộ thời gian từ cấp nhiệm vụ con lên cấp cha và hạng mục lớn theo thời gian thực:
                    </p>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 13, color: '#14532D' }}>
                      <div style={{ padding: 10, backgroundColor: '#FFFFFF', borderRadius: 8, border: '1px solid #86EFAC' }}>
                        <strong>1. Ghi nhận thời gian tại Nhiệm vụ con (Cấp -1):</strong> Thành viên trực tiếp ghi nhận thời gian làm việc thực tế và thời gian ước lượng chi tiết tại từng nhiệm vụ con cụ thể.
                      </div>
                      <div style={{ padding: 10, backgroundColor: '#FFFFFF', borderRadius: 8, border: '1px solid #86EFAC' }}>
                        <strong>2. Tự động lũy kế lên Công việc cha (Cấp 0):</strong> Tổng thời gian thực tế và ước lượng của công việc cha sẽ tự động cộng dồn từ toàn bộ các nhiệm vụ con bên dưới:
                        <div style={{ marginTop: 4, fontFamily: 'monospace', color: '#15803D', fontWeight: 600 }}>
                          Tổng thời gian = Thời gian ghi nhận trực tiếp + ∑(Thời gian của các nhiệm vụ con)
                        </div>
                      </div>
                      <div style={{ padding: 10, backgroundColor: '#FFFFFF', borderRadius: 8, border: '1px solid #86EFAC' }}>
                        <strong>3. Tiến độ tổng thể Hạng mục lớn (Cấp 1):</strong> Hệ thống tính toán tỷ lệ % hoàn thành dựa trên tổng thời gian đã ghi nhận và số lượng công việc trực thuộc đã hoàn thành.
                      </div>
                    </div>
                  </div>

                  {/* Dates Constraints Card */}
                  <div
                    style={{
                      padding: 16,
                      borderRadius: 10,
                      backgroundColor: '#EFF6FF',
                      border: '1px solid #BFDBFE',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, color: '#1E40AF' }}>
                      <Calendar size={18} />
                      <h4 style={{ fontSize: 15, fontWeight: 700, margin: 0 }}>
                        Ràng Buộc Thời Gian Cây Công Việc
                      </h4>
                    </div>
                    <ul style={{ margin: 0, paddingLeft: 20, fontSize: 13, color: '#1E3A8A', lineHeight: 1.6 }}>
                      <li><strong>Thời hạn Nhiệm vụ con (Cấp -1):</strong> Ngày bắt đầu và hạn chót của nhiệm vụ con <strong>phải nằm trọn vẹn</strong> trong khoảng thời gian diễn ra của công việc cha. Hệ thống tự động chặn không cho nhiệm vụ con kết thúc sau công việc cha.</li>
                      <li><strong>Thời hạn Công việc cha (Cấp 0):</strong> Phải nằm trong chu kỳ làm việc đang chạy hoặc nằm trong khung thời gian tổng thể của Hạng mục lớn (Cấp 1).</li>
                      <li><strong>Biểu đồ tiến độ & Dòng thời gian:</strong> Thanh thời gian của Hạng mục lớn sẽ tự động co giãn bao trùm từ ngày bắt đầu sớm nhất đến ngày kết thúc muộn nhất của các công việc trực thuộc bên dưới.</li>
                    </ul>
                  </div>

                  {/* Completed Timestamp & Metrics */}
                  <div
                    style={{
                      padding: 16,
                      borderRadius: 10,
                      backgroundColor: '#FAF5FF',
                      border: '1px solid #E9D5FF',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, color: '#6B21A8' }}>
                      <Clock size={18} />
                      <h4 style={{ fontSize: 15, fontWeight: 700, margin: 0 }}>
                        Chốt Mốc Hoàn Tất & Chỉ Số Hiệu Suất
                      </h4>
                    </div>
                    <div style={{ fontSize: 13, color: '#581C87', lineHeight: 1.5 }}>
                      Khi một công việc được kéo sang trạng thái thuộc nhóm <code>Hoàn thành</code>, hệ thống tự động ghi nhận thời điểm hoàn tất thực tế, từ đó tính toán tự động:
                      <ul style={{ margin: '8px 0 0 0', paddingLeft: 20 }}>
                        <li><strong>Thời gian chu trình tổng thể:</strong> Tổng thời gian từ thời điểm công việc được tạo đến khi hoàn thành xong.</li>
                        <li><strong>Thời gian xử lý thực tế:</strong> Thời gian thực tế đội ngũ tập trung làm việc (tính từ lúc bắt đầu thực hiện đến khi hoàn thành).</li>
                      </ul>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                padding: '14px 24px',
                borderTop: '1px solid #E2E8F0',
                backgroundColor: '#F8FAFC',
              }}
            >
              <button
                type="button"
                onClick={() => setIsExplanationModalOpen(false)}
                style={{
                  padding: '8px 22px',
                  borderRadius: 6,
                  border: 'none',
                  backgroundColor: '#2563EB',
                  color: '#FFFFFF',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                  boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
                }}
              >
                Đã hiểu, Đóng hướng dẫn
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
