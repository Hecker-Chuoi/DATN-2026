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
  FileCheck
} from 'lucide-react';
import toast from 'react-hot-toast';
import { workflowApi } from '../../api.js';
import { STATUS_CATEGORY_CONFIG, WORK_ITEM_TYPE_CONFIG } from '../../constants/workConstants.js';

export default function ProjectWorkflowTab({ projectId, project, isAdmin }) {
  const [activeSubTab, setActiveSubTab] = useState('diagram'); // 'diagram' | 'scheme' | 'statuses'
  const [workflowDetail, setWorkflowDetail] = useState(null);
  const [workflowScheme, setWorkflowScheme] = useState(null);
  const [statuses, setStatuses] = useState([]);
  const [loading, setLoading] = useState(true);

  // Add Transition Modal State
  const [isAddTransitionOpen, setIsAddTransitionOpen] = useState(false);
  const [fromStatusId, setFromStatusId] = useState('');
  const [toStatusId, setToStatusId] = useState('');
  const [transitionName, setTransitionName] = useState('');
  const [requireAssigneeOnly, setRequireAssigneeOnly] = useState(false);
  const [requireSubtasksDone, setRequireSubtasksDone] = useState(false);
  const [requireResolution, setRequireResolution] = useState(false);
  const [submittingTransition, setSubmittingTransition] = useState(false);

  // Add Status Modal State
  const [isAddStatusOpen, setIsAddStatusOpen] = useState(false);
  const [newStatusName, setNewStatusName] = useState('');
  const [newStatusCategory, setNewStatusCategory] = useState('IN_PROGRESS');
  const [newStatusColor, setNewStatusColor] = useState('#3B82F6');
  const [newStatusDesc, setNewStatusDesc] = useState('');
  const [submittingStatus, setSubmittingStatus] = useState(false);

  const fetchWorkflowData = async () => {
    try {
      setLoading(true);
      const [wfRes, schemeRes, statusesRes] = await Promise.all([
        workflowApi.getProjectWorkflow(projectId),
        workflowApi.getProjectWorkflowScheme(projectId),
        workflowApi.getProjectStatuses(projectId),
      ]);

      if (wfRes && wfRes.data) setWorkflowDetail(wfRes.data);
      if (schemeRes && schemeRes.data) setWorkflowScheme(schemeRes.data);
      if (statusesRes && statusesRes.data) setStatuses(statusesRes.data);
    } catch (err) {
      console.error('Failed to fetch workflow details:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (projectId) {
      fetchWorkflowData();
    }
  }, [projectId]);

  const handleCreateTransition = async (e) => {
    e.preventDefault();
    if (!toStatusId || !transitionName.trim()) {
      toast.error('Vui lòng chọn trạng thái đích và nhập tên bước chuyển');
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
        fromStatusId: fromStatusId || null,
        toStatusId,
        name: transitionName.trim(),
        conditionsConfig,
        validatorsConfig,
        postFunctionsConfig,
      };

      await workflowApi.createTransition(workflowDetail.id, payload);
      toast.success('Tạo bước chuyển quy trình thành công!');
      setIsAddTransitionOpen(false);
      setFromStatusId('');
      setToStatusId('');
      setTransitionName('');
      setRequireAssigneeOnly(false);
      setRequireSubtasksDone(false);
      setRequireResolution(false);
      fetchWorkflowData();
    } catch (err) {
      console.error('Failed to create transition:', err);
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
      fetchWorkflowData();
    } catch (err) {
      console.error('Failed to delete transition:', err);
    }
  };

  const handleCreateStatus = async (e) => {
    e.preventDefault();
    if (!newStatusName.trim()) {
      toast.error('Vui lòng nhập tên trạng thái');
      return;
    }

    try {
      setSubmittingStatus(true);
      await workflowApi.createStatus({
        name: newStatusName.trim(),
        category: newStatusCategory,
        colorHex: newStatusColor,
        description: newStatusDesc.trim(),
        projectId, // backend yêu cầu PROJECT_ADMIN của dự án này để tạo trạng thái dùng chung
      });
      toast.success('Tạo trạng thái mới thành công!');
      setIsAddStatusOpen(false);
      setNewStatusName('');
      setNewStatusDesc('');
      fetchWorkflowData();
    } catch (err) {
      console.error('Failed to create status:', err);
    } finally {
      setSubmittingStatus(false);
    }
  };

  if (loading) {
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
      {/* ─── Workflow Header Bar ─── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '18px 24px',
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          border: '1px solid #E2E8F0',
          boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          marginBottom: 20,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div
            style={{
              width: 44,
              height: 44,
              borderRadius: 10,
              backgroundColor: '#EFF6FF',
              color: '#2563EB',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <GitBranch size={24} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <h2 style={{ fontSize: 18, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                {workflowDetail?.name || 'Quy trình Làm việc Chuẩn'}
              </h2>
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
            <p style={{ fontSize: 13, color: '#64748B', margin: '4px 0 0 0' }}>
              {workflowDetail?.description || 'Máy trạng thái FSM kiểm soát vòng đời của Epic, Story, Task, Bug và Sub-task.'}
            </p>
          </div>
        </div>

        {isAdmin && (
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              type="button"
              onClick={() => setIsAddStatusOpen(true)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '8px 14px',
                borderRadius: 8,
                backgroundColor: '#FFFFFF',
                color: '#334155',
                border: '1px solid #CBD5E1',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              <Plus size={15} />
              <span>Thêm Trạng Thái</span>
            </button>

            <button
              type="button"
              onClick={() => setIsAddTransitionOpen(true)}
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
                boxShadow: '0 2px 4px rgba(37,99,235,0.2)',
              }}
            >
              <Plus size={15} />
              <span>Thêm Bước Chuyển</span>
            </button>
          </div>
        )}
      </div>

      {/* ─── Navigation Sub-Tabs ─── */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20, borderBottom: '1px solid #E2E8F0', paddingBottom: 8 }}>
        <button
          type="button"
          onClick={() => setActiveSubTab('diagram')}
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
          <span>Sơ đồ & Bước chuyển FSM ({transitions.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveSubTab('scheme')}
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
          <span>Phân bổ Sơ đồ theo Loại Công việc</span>
        </button>

        <button
          type="button"
          onClick={() => setActiveSubTab('statuses')}
          style={{
            padding: '8px 16px',
            borderRadius: 6,
            border: 'none',
            backgroundColor: activeSubTab === 'statuses' ? '#EFF6FF' : 'transparent',
            color: activeSubTab === 'statuses' ? '#2563EB' : '#64748B',
            fontWeight: activeSubTab === 'statuses' ? 700 : 500,
            fontSize: 13,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <Sliders size={15} />
          <span>Danh mục Trạng thái ({allStatuses.length})</span>
        </button>
      </div>

      {/* ─── TAB 1: Visual FSM Diagram & Transitions Table ─── */}
      {activeSubTab === 'diagram' && (
        <div>
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
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: '#475569', marginBottom: 12 }}>
                <Clock size={15} color="#64748B" />
                <span>1. CẦN LÀM (TODO)</span>
                <span style={{ fontSize: 11, backgroundColor: '#E2E8F0', padding: '1px 6px', borderRadius: 10, marginLeft: 'auto' }}>
                  {todoStatuses.length}
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {todoStatuses.map((st) => (
                  <div
                    key={st.id}
                    style={{
                      padding: '10px 12px',
                      backgroundColor: '#FFFFFF',
                      borderRadius: 8,
                      border: '1px solid #CBD5E1',
                      borderLeft: `4px solid ${st.colorHex || '#94A3B8'}`,
                    }}
                  >
                    <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{st.name}</div>
                    <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>{st.description || 'Trạng thái khởi tạo'}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* IN PROGRESS Lane */}
            <div
              style={{
                backgroundColor: '#EFF6FF',
                borderRadius: 10,
                border: '1px solid #BFDBFE',
                padding: '14px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: '#1D4ED8', marginBottom: 12 }}>
                <Sparkles size={15} color="#2563EB" />
                <span>2. ĐANG XỬ LÝ (IN PROGRESS)</span>
                <span style={{ fontSize: 11, backgroundColor: '#DBEAFE', padding: '1px 6px', borderRadius: 10, marginLeft: 'auto' }}>
                  {inProgressStatuses.length}
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {inProgressStatuses.map((st) => (
                  <div
                    key={st.id}
                    style={{
                      padding: '10px 12px',
                      backgroundColor: '#FFFFFF',
                      borderRadius: 8,
                      border: '1px solid #93C5FD',
                      borderLeft: `4px solid ${st.colorHex || '#3B82F6'}`,
                    }}
                  >
                    <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{st.name}</div>
                    <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>{st.description || 'Đang thực hiện'}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* DONE Lane */}
            <div
              style={{
                backgroundColor: '#F0FDF4',
                borderRadius: 10,
                border: '1px solid #BBF7D0',
                padding: '14px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: '#15803D', marginBottom: 12 }}>
                <CheckCircle2 size={15} color="#16A34A" />
                <span>3. HOÀN THÀNH (DONE)</span>
                <span style={{ fontSize: 11, backgroundColor: '#DCFCE7', padding: '1px 6px', borderRadius: 10, marginLeft: 'auto' }}>
                  {doneStatuses.length}
                </span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {doneStatuses.map((st) => (
                  <div
                    key={st.id}
                    style={{
                      padding: '10px 12px',
                      backgroundColor: '#FFFFFF',
                      borderRadius: 8,
                      border: '1px solid #86EFAC',
                      borderLeft: `4px solid ${st.colorHex || '#10B981'}`,
                    }}
                  >
                    <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{st.name}</div>
                    <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>{st.description || 'Đã nghiệm thu'}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Transitions Table */}
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 10,
              border: '1px solid #E2E8F0',
              overflow: 'hidden',
              boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
            }}
          >
            <div
              style={{
                padding: '14px 20px',
                borderBottom: '1px solid #E2E8F0',
                backgroundColor: '#F8FAFC',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>
                Bảng Cấu hình Các Bước Chuyển Trạng thái ({transitions.length} transitions)
              </div>
              <span style={{ fontSize: 12, color: '#64748B' }}>
                Kiểm soát luồng di chuyển của task từ trạng thái gốc tới trạng thái đích
              </span>
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1.5fr 1.5fr 1.5fr 2fr 60px',
                padding: '10px 20px',
                backgroundColor: '#F1F5F9',
                borderBottom: '1px solid #E2E8F0',
                fontSize: 12,
                fontWeight: 700,
                color: '#475569',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
              }}
            >
              <span>Từ Trạng Thái (From)</span>
              <span>Tên Nút Bấm Chuyển</span>
              <span>Tới Trạng Thái (To)</span>
              <span>Điều kiện & Ràng buộc (Rules)</span>
              <span></span>
            </div>

            {transitions.length === 0 ? (
              <div style={{ padding: '30px', textAlign: 'center', color: '#64748B', fontSize: 13 }}>
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
                      gridTemplateColumns: '1.5fr 1.5fr 1.5fr 2fr 60px',
                      padding: '12px 20px',
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
                        {t.fromStatusName}
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
                        {t.toStatusName}
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
                          <Shield size={11} /> Condition
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
                          <FileCheck size={11} /> Validator
                        </span>
                      )}
                      {!hasConditions && !hasValidators && (
                        <span style={{ fontSize: 11, color: '#94A3B8' }}>Không ràng buộc</span>
                      )}
                    </div>

                    {/* Actions */}
                    <div style={{ textAlign: 'right' }}>
                      {isAdmin && (
                        <button
                          type="button"
                          onClick={() => handleDeleteTransition(t.id)}
                          style={{
                            border: 'none',
                            background: 'transparent',
                            color: '#94A3B8',
                            cursor: 'pointer',
                            padding: 4,
                            borderRadius: 4,
                          }}
                          onMouseEnter={(e) => (e.currentTarget.style.color = '#EF4444')}
                          onMouseLeave={(e) => (e.currentTarget.style.color = '#94A3B8')}
                          title="Xóa bước chuyển"
                        >
                          <Trash2 size={15} />
                        </button>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* ─── TAB 2: Workflow Scheme Mappings ─── */}
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
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <Layers size={18} color="#2563EB" />
            <h3 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: 0 }}>
              Sơ đồ Phân bổ Quy trình theo Loại Công việc (Workflow Scheme)
            </h3>
          </div>
          <p style={{ fontSize: 13, color: '#64748B', marginBottom: 20 }}>
            Mỗi loại công việc (Epic, Story, Task, Bug, Sub-task) có thể sử dụng quy trình làm việc riêng biệt hoặc dùng chung quy trình mặc định.
          </p>

          <div style={{ borderRadius: 8, border: '1px solid #E2E8F0', overflow: 'hidden' }}>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '2fr 1fr 2fr',
                padding: '10px 16px',
                backgroundColor: '#F8FAFC',
                borderBottom: '1px solid #E2E8F0',
                fontSize: 12,
                fontWeight: 700,
                color: '#475569',
              }}
            >
              <span>Loại Công Việc (Work Item Type)</span>
              <span>Cấp Bậc (Level)</span>
              <span>Quy Trình Làm Việc Áp Dụng (Workflow)</span>
            </div>

            {['EPIC', 'STORY', 'TASK', 'BUG', 'SUB_TASK'].map((typeCode) => {
              const cfg = WORK_ITEM_TYPE_CONFIG[typeCode];
              return (
                <div
                  key={typeCode}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '2fr 1fr 2fr',
                    padding: '12px 16px',
                    borderBottom: '1px solid #F1F5F9',
                    alignItems: 'center',
                    fontSize: 13,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span
                      style={{
                        padding: '2px 8px',
                        borderRadius: 4,
                        fontSize: 11,
                        fontWeight: 700,
                        backgroundColor: cfg.bgColor,
                        color: cfg.color,
                        border: `1px solid ${cfg.borderColor}`,
                      }}
                    >
                      {cfg.name}
                    </span>
                    <span style={{ fontSize: 11, color: '#64748B' }}>({cfg.code})</span>
                  </div>

                  <div>
                    <span style={{ fontSize: 12, color: '#475569', fontWeight: 600 }}>
                      Level {cfg.hierarchyLevel}
                    </span>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#0F172A', fontWeight: 600 }}>
                    <GitBranch size={14} color="#2563EB" />
                    <span>{workflowDetail?.name || 'Standard Scrum Workflow'}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ─── TAB 3: Project Statuses List ─── */}
      {activeSubTab === 'statuses' && (
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 10,
            border: '1px solid #E2E8F0',
            padding: '20px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
            <Sliders size={18} color="#2563EB" />
            <h3 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: 0 }}>
              Danh mục Tất cả Trạng thái ({allStatuses.length})
            </h3>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12 }}>
            {allStatuses.map((st) => {
              const catCfg = STATUS_CATEGORY_CONFIG[st.category] || STATUS_CATEGORY_CONFIG.TODO;
              return (
                <div
                  key={st.id}
                  style={{
                    padding: '12px 16px',
                    borderRadius: 8,
                    border: '1px solid #E2E8F0',
                    backgroundColor: '#FAFAFA',
                    borderLeft: `4px solid ${st.colorHex || catCfg.color}`,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                    <span style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{st.name}</span>
                    <span
                      style={{
                        fontSize: 10,
                        fontWeight: 700,
                        padding: '1px 6px',
                        borderRadius: 10,
                        backgroundColor: catCfg.bgColor,
                        color: catCfg.color,
                        border: `1px solid ${catCfg.borderColor}`,
                      }}
                    >
                      {catCfg.label}
                    </span>
                  </div>
                  <div style={{ fontSize: 12, color: '#64748B' }}>{st.description || 'Không có mô tả'}</div>
                </div>
              );
            })}
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
                <span>Thêm Bước Chuyển Trạng Thái</span>
              </div>
              <button
                type="button"
                onClick={() => setIsAddTransitionOpen(false)}
                style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#64748B' }}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateTransition}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Trạng thái Gốc (From Status)
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
                  <option value="">-- Bất kỳ trạng thái nào (Any / Initial) --</option>
                  {allStatuses.map((st) => (
                    <option key={st.id} value={st.id}>
                      {st.name} ({st.category})
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Trạng thái Đích (To Status) *
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
                    <option key={st.id} value={st.id}>
                      {st.name} ({st.category})
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Tên Nút Bấm Chuyển (Action Name) *
                </label>
                <input
                  type="text"
                  placeholder="VD: Start Progress, Submit for Review, Approve & Done"
                  value={transitionName}
                  onChange={(e) => setTransitionName(e.target.value)}
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
                  Ràng buộc & Điều kiện FSM (Rules)
                </div>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: '#475569', marginBottom: 6, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={requireAssigneeOnly}
                    onChange={(e) => setRequireAssigneeOnly(e.target.checked)}
                  />
                  <span>Chỉ người được giao việc (Assignee) mới được chuyển</span>
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: '#475569', marginBottom: 6, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={requireSubtasksDone}
                    onChange={(e) => setRequireSubtasksDone(e.target.checked)}
                  />
                  <span>Bắt buộc tất cả Subtasks phải hoàn thành trước (All Subtasks Done)</span>
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: '#475569', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={requireResolution}
                    onChange={(e) => setRequireResolution(e.target.checked)}
                  />
                  <span>Bắt buộc chọn lý do giải quyết (Resolution Required)</span>
                </label>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button
                  type="button"
                  onClick={() => setIsAddTransitionOpen(false)}
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
                  {submittingTransition ? 'Đang lưu...' : 'Thêm Bước Chuyển'}
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
                <span>Tạo Trạng Thái Công Việc Mới</span>
              </div>
              <button
                type="button"
                onClick={() => setIsAddStatusOpen(false)}
                style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#64748B' }}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateStatus}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Tên Trạng Thái *
                </label>
                <input
                  type="text"
                  placeholder="VD: In Code Review, Testing, Ready for QA"
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
                  Danh Mục Chuẩn (Status Category) *
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
                  <option value="TODO">Cần làm (TODO)</option>
                  <option value="IN_PROGRESS">Đang thực hiện (IN_PROGRESS)</option>
                  <option value="DONE">Hoàn thành (DONE)</option>
                </select>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Mã Màu Hiển Thị (Color Hex)
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
                  onClick={() => setIsAddStatusOpen(false)}
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
                  {submittingStatus ? 'Đang tạo...' : 'Tạo Trạng Thái'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
