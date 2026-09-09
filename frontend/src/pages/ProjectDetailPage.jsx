import React, { useState, useEffect } from 'react';
import { useParams, useOutletContext } from 'react-router-dom';
import {
  FolderKanban,
  Kanban,
  ListTodo,
  Users,
  GitBranch,
  Star,
  MoreHorizontal,
  Link2,
  Plus,
  ShieldCheck
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi } from '../api.js';
import { useProjectPermission } from '../hooks/useProjectPermission.js';
import AccessDenied from '../components/common/AccessDenied.jsx';
import ProjectMembersTab from '../components/project/ProjectMembersTab.jsx';
import ProjectWorkflowTab from '../components/project/ProjectWorkflowTab.jsx';
import ProjectBoardView from '../components/boards/ProjectBoardView.jsx';
import CreateWorkItemModal from '../components/items/CreateWorkItemModal.jsx';
import WorkItemDetailModal from '../components/items/WorkItemDetailModal.jsx';

const ROLE_LABELS = {
  ADMINISTRATOR: { label: 'Administrator', bg: '#FAF5FF', color: '#7E22CE', border: '#E9D5FF' },
  MEMBER: { label: 'Member', bg: '#EFF6FF', color: '#1D4ED8', border: '#BFDBFE' },
  VIEWER: { label: 'Viewer', bg: '#F8FAFC', color: '#475569', border: '#E2E8F0' },
  ADMIN: { label: 'System Admin', bg: '#FEF2F2', color: '#B91C1C', border: '#FECACA' },
};

export default function ProjectDetailPage() {
  const { projectId } = useParams();
  const { currentUser } = useOutletContext() || {};

  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [activeTab, setActiveTab] = useState('board'); // 'board' | 'backlog' | 'members' | 'workflow'
  const [isStarred, setIsStarred] = useState(false);
  const [members, setMembers] = useState([]);

  // Work Item Modals State
  const [isCreateItemModalOpen, setIsCreateItemModalOpen] = useState(false);
  const [selectedItemId, setSelectedItemId] = useState(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [boardRefreshKey, setBoardRefreshKey] = useState(0);

  // ─── Quyền của người dùng hiện tại trong dự án (backend là nguồn sự thật) ───
  const perm = useProjectPermission(projectId);
  const { can, isAdmin, isLead, isGlobalAdmin, role } = perm;

  // Keycloak id là định danh work-service dùng cho authorId / uploadedBy / assigneeId
  const currentUserId = currentUser?.keycloakId || currentUser?.id || null;

  const fetchProject = async () => {
    try {
      setLoading(true);
      setLoadError(null);
      const [projRes, membersRes] = await Promise.all([
        projectApi.getById(projectId, { skipErrorToast: true }),
        projectApi.getMembers(projectId, { skipErrorToast: true }).catch(() => null),
      ]);
      if (projRes && projRes.data) {
        setProject(projRes.data);
      }
      if (membersRes && membersRes.data) {
        setMembers(Array.isArray(membersRes.data) ? membersRes.data : []);
      }
    } catch (err) {
      console.warn('Could not load project detail:', err);
      setLoadError(err);
    } finally {
      setLoading(false);
    }
  };

  // Chỉ tải dữ liệu dự án sau khi backend xác nhận user có quyền xem
  useEffect(() => {
    if (projectId && perm.status === 'ready') {
      fetchProject();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, perm.status]);

  const handleOpenDetailModal = (itemId) => {
    setSelectedItemId(itemId);
    setIsDetailModalOpen(true);
  };

  const handleItemCreated = () => {
    setBoardRefreshKey((prev) => prev + 1);
  };

  const handleItemUpdated = () => {
    setBoardRefreshKey((prev) => prev + 1);
  };

  // ─── Trạng thái truy cập ───
  if (perm.forbidden || loadError?.status === 403) {
    return <AccessDenied status={403} />;
  }
  if (perm.notFound || loadError?.status === 404) {
    return <AccessDenied status={404} />;
  }
  if (perm.status === 'error') {
    return (
      <AccessDenied
        status={403}
        title="Không kiểm tra được quyền truy cập"
        message={perm.error?.message || 'Không thể kết nối tới máy chủ. Vui lòng thử lại sau.'}
      />
    );
  }

  const roleBadge = ROLE_LABELS[role] || null;
  const availableTabs = [
    { id: 'board', label: 'Active Board', icon: Kanban },
    { id: 'backlog', label: 'Backlog', icon: ListTodo },
    { id: 'members', label: 'Members & Roles', icon: Users },
    { id: 'workflow', label: 'Workflow & Statuses', icon: GitBranch },
  ];

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        overflow: 'hidden',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      {/* ─── Top Header Breadcrumbs Bar ─── */}
      <div
        style={{
          height: 48,
          minHeight: 48,
          padding: '0 24px',
          borderBottom: '1px solid #F1F5F9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{
              width: 24,
              height: 24,
              borderRadius: 6,
              backgroundColor: '#EFF6FF',
              color: '#2563EB',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <FolderKanban size={14} />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
            <span>{project?.name || 'Project Details'}</span>
            {project?.key && (
              <span
                style={{
                  fontSize: 11,
                  fontWeight: 700,
                  color: '#2563EB',
                  backgroundColor: '#EFF6FF',
                  padding: '1px 6px',
                  borderRadius: 4,
                }}
              >
                {project.key}
              </span>
            )}
          </div>

          {/* Vai trò của tôi trong dự án */}
          {roleBadge && (
            <span
              title={isLead ? 'Bạn là Trưởng dự án (Project Lead)' : isGlobalAdmin ? 'Quản trị viên hệ thống' : 'Vai trò của bạn trong dự án'}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
                fontSize: 11,
                fontWeight: 600,
                color: roleBadge.color,
                backgroundColor: roleBadge.bg,
                border: `1px solid ${roleBadge.border}`,
                padding: '2px 8px',
                borderRadius: 10,
              }}
            >
              <ShieldCheck size={11} />
              <span>{isLead ? 'Project Lead' : roleBadge.label}</span>
            </span>
          )}

          <button
            onClick={() => setIsStarred(!isStarred)}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: isStarred ? '#EAB308' : '#94A3B8',
              padding: 2,
              display: 'flex',
              alignItems: 'center',
            }}
            title={isStarred ? 'Bỏ yêu thích' : 'Yêu thích'}
          >
            <Star size={15} fill={isStarred ? '#EAB308' : 'none'} />
          </button>
          <button
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#94A3B8',
              padding: 2,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <MoreHorizontal size={16} />
          </button>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {/* Nút Tạo Issue: chỉ hiện khi có quyền ISSUE_CREATE */}
          {can('ISSUE_CREATE') && (
            <button
              onClick={() => setIsCreateItemModalOpen(true)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                backgroundColor: '#2563EB',
                color: '#FFFFFF',
                border: 'none',
                borderRadius: 6,
                padding: '5px 12px',
                fontSize: 12,
                fontWeight: 600,
                cursor: 'pointer',
                boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
            >
              <Plus size={14} />
              <span>Tạo công việc</span>
            </button>
          )}

          <button
            onClick={() => {
              navigator.clipboard.writeText(window.location.href);
              toast.success('Đã sao chép liên kết');
            }}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#64748B',
              padding: 6,
              borderRadius: 4,
              display: 'flex',
              alignItems: 'center',
            }}
            title="Sao chép liên kết"
          >
            <Link2 size={16} />
          </button>
        </div>
      </div>

      {/* ─── Subheader / Navigation Tabs Bar ─── */}
      <div
        style={{
          padding: '12px 24px 8px 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {availableTabs.map((tab) => {
            const active = activeTab === tab.id;
            const TabIcon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  padding: '6px 14px',
                  borderRadius: 20,
                  fontSize: 13,
                  fontWeight: active ? 600 : 500,
                  border: 'none',
                  cursor: 'pointer',
                  backgroundColor: active ? '#F1F5F9' : 'transparent',
                  color: active ? '#0F172A' : '#64748B',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  transition: 'all 0.15s ease',
                }}
              >
                <TabIcon size={14} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* ─── Body Content ─── */}
      <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        {perm.loading || loading ? (
          <div style={{ padding: 48, textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            {perm.loading ? 'Đang kiểm tra quyền truy cập...' : 'Đang tải thông tin dự án...'}
          </div>
        ) : (
          <>
            {activeTab === 'board' && (
              <ProjectBoardView
                projectId={projectId}
                projectKey={project?.key}
                canCreate={can('ISSUE_CREATE')}
                onOpenCreateModal={() => setIsCreateItemModalOpen(true)}
                onSelectWorkItem={handleOpenDetailModal}
                refreshKey={boardRefreshKey}
              />
            )}

            {activeTab === 'backlog' && (
              <div style={{ padding: '32px 24px', textAlign: 'center', color: '#64748B' }}>
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 12,
                    backgroundColor: '#EFF6FF',
                    color: '#2563EB',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    margin: '0 auto 16px auto',
                  }}
                >
                  <ListTodo size={24} />
                </div>
                <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', marginBottom: 4 }}>
                  Backlog & Lập kế hoạch Sprint
                </h3>
                <p style={{ fontSize: 13, color: '#64748B' }}>
                  Quản lý danh sách công việc tồn đọng (Product Backlog) và phân bổ Sprint.
                </p>
              </div>
            )}

            {activeTab === 'members' && (
              <div style={{ padding: 24, overflowY: 'auto', flex: 1 }}>
                <ProjectMembersTab
                  projectId={projectId}
                  project={project}
                  isAdmin={isAdmin}
                  currentUserId={currentUserId}
                  onMembersUpdated={fetchProject}
                />
              </div>
            )}

            {activeTab === 'workflow' && (
              <div style={{ padding: 24, overflowY: 'auto', flex: 1 }}>
                <ProjectWorkflowTab projectId={projectId} project={project} isAdmin={isAdmin} />
              </div>
            )}
          </>
        )}
      </div>

      {/* ─── Create Work Item Modal ─── */}
      <CreateWorkItemModal
        isOpen={isCreateItemModalOpen}
        onClose={() => setIsCreateItemModalOpen(false)}
        projectId={projectId}
        projectKey={project?.key}
        projectTypes={project?.workItemTypes || []}
        members={members}
        onItemCreated={handleItemCreated}
      />

      {/* ─── Work Item Detail Modal (2 Columns Jira Spec) ─── */}
      <WorkItemDetailModal
        itemId={selectedItemId}
        isOpen={isDetailModalOpen}
        onClose={() => {
          setIsDetailModalOpen(false);
          setSelectedItemId(null);
        }}
        onItemUpdated={handleItemUpdated}
        members={members}
        projectId={projectId}
        currentUserId={currentUserId}
        can={can}
      />
    </div>
  );
}
