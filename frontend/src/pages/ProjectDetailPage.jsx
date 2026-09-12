import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import {
  FolderKanban,
  Kanban,
  ListTodo,
  Layers,
  Sparkles,
  Users,
  ShieldCheck,
  Settings,
  GitBranch,
  Star,
  MoreHorizontal,
  Link2,
  SlidersHorizontal,
  Plus,
  Trash2,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi } from '../api.js';
import { useProjectPermission } from '../hooks/useProjectPermission.js';
import ProjectMembersTab from '../components/project/ProjectMembersTab.jsx';
import ProjectWorkflowTab from '../components/project/ProjectWorkflowTab.jsx';
import ProjectBoardView from '../components/boards/ProjectBoardView.jsx';
import CreateWorkItemModal from '../components/items/CreateWorkItemModal.jsx';
import WorkItemDetailModal from '../components/items/WorkItemDetailModal.jsx';
import DeleteProjectModal from '../components/project/DeleteProjectModal.jsx';

export default function ProjectDetailPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState(() => {
    try {
      return sessionStorage.getItem(`project_${projectId}_active_tab`) || 'board';
    } catch {
      return 'board';
    }
  });
  const [isStarred, setIsStarred] = useState(false);
  const [members, setMembers] = useState([]);

  // Work Item Modals State
  const [isCreateItemModalOpen, setIsCreateItemModalOpen] = useState(false);
  const [selectedItemId, setSelectedItemId] = useState(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [boardRefreshKey, setBoardRefreshKey] = useState(0);

  const outletContext = useOutletContext() || {};
  const currentUser = outletContext.currentUser;

  // Project Action Menu & Delete Modal State
  const [isActionMenuOpen, setIsActionMenuOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const actionMenuRef = useRef(null);

  const { isAdmin, canCreateIssue, canManageSprint, permissions } = useProjectPermission(projectId);

  const isSystemAdmin = currentUser?.roles?.includes('ADMIN') || currentUser?.roles?.includes('ROLE_ADMIN');
  const isProjectLead = !!(currentUser && project?.leadUserId === currentUser.id);
  const isProjectCreator = !!(currentUser && project?.createdBy === currentUser.id);
  const canDelete = isAdmin || isSystemAdmin || isProjectLead || isProjectCreator;

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (actionMenuRef.current && !actionMenuRef.current.contains(event.target)) {
        setIsActionMenuOpen(false);
      }
    };
    if (isActionMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isActionMenuOpen]);

  const fetchProject = async (isBackground = false) => {
    try {
      if (!isBackground) setLoading(true);
      const [projRes, membersRes] = await Promise.all([
        projectApi.getById(projectId),
        projectApi.getMembers(projectId).catch(() => null),
      ]);
      if (projRes && projRes.data) {
        setProject(projRes.data);
      }
      if (membersRes && membersRes.data) {
        setMembers(membersRes.data);
      }
    } catch (err) {
      console.warn('Could not load project detail:', err);
    } finally {
      if (!isBackground) setLoading(false);
    }
  };

  useEffect(() => {
    if (projectId) {
      fetchProject();
    }
  }, [projectId]);

  const handleOpenDetailModal = (itemId) => {
    setSelectedItemId(itemId);
    setIsDetailModalOpen(true);
  };

  const handleItemCreated = (newItem) => {
    setBoardRefreshKey((prev) => prev + 1);
  };

  const handleItemUpdated = (updatedItem) => {
    setBoardRefreshKey((prev) => prev + 1);
  };

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
            <span>{project?.name || 'Chi tiết dự án'}</span>
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
          {canDelete && (
            <div style={{ position: 'relative' }} ref={actionMenuRef}>
              <button
                type="button"
                onClick={() => setIsActionMenuOpen(!isActionMenuOpen)}
                style={{
                  background: isActionMenuOpen ? '#F1F5F9' : 'transparent',
                  border: 'none',
                  cursor: 'pointer',
                  color: isActionMenuOpen ? '#0F172A' : '#94A3B8',
                  padding: 4,
                  borderRadius: 4,
                  display: 'flex',
                  alignItems: 'center',
                  transition: 'all 0.15s ease',
                }}
                title="Tùy chọn dự án"
              >
                <MoreHorizontal size={16} />
              </button>

              {isActionMenuOpen && (
                <div
                  style={{
                    position: 'absolute',
                    top: '100%',
                    left: 0,
                    marginTop: 6,
                    backgroundColor: '#FFFFFF',
                    borderRadius: 8,
                    boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1)',
                    border: '1px solid #E2E8F0',
                    minWidth: 200,
                    zIndex: 50,
                    overflow: 'hidden',
                    padding: '4px',
                  }}
                >
                  <button
                    type="button"
                    onClick={() => {
                      setIsActionMenuOpen(false);
                      setIsDeleteModalOpen(true);
                    }}
                    style={{
                      width: '100%',
                      padding: '8px 12px',
                      borderRadius: 6,
                      border: 'none',
                      backgroundColor: 'transparent',
                      color: '#EF4444',
                      fontSize: 13,
                      fontWeight: 500,
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      textAlign: 'left',
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.backgroundColor = '#FEF2F2';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.backgroundColor = 'transparent';
                    }}
                  >
                    <Trash2 size={14} color="#EF4444" />
                    <span>Xóa dự án (Thùng rác)</span>
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {/* Nút Tạo Issue nổi bật trên Header */}
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
        {/* Navigation Pills */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {[
            { id: 'board', label: 'Bảng làm việc', icon: Kanban },
            { id: 'backlog', label: 'Danh sách tồn đọng', icon: ListTodo },
            { id: 'members', label: 'Thành viên & Vai trò', icon: Users },
            { id: 'workflow', label: 'Cấu hình Dự án', icon: Settings },
          ].map((tab) => {
            const active = activeTab === tab.id;
            const TabIcon = tab.icon;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setActiveTab(tab.id);
                  try {
                    sessionStorage.setItem(`project_${projectId}_active_tab`, tab.id);
                  } catch (e) {}
                }}
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
        {(loading && !project) ? (
          <div style={{ padding: 48, textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Đang tải thông tin dự án...
          </div>
        ) : (
          <>
            {activeTab === 'board' && (
              <ProjectBoardView
                projectId={projectId}
                projectKey={project?.key}
                projectTypes={project?.workItemTypes || []}
                canCreate={canCreateIssue || isAdmin}
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
                  Danh sách tồn đọng & Kế hoạch Chu kỳ
                </h3>
                <p style={{ fontSize: 13, color: '#64748B' }}>
                  Quản lý danh sách công việc tồn đọng và phân bổ theo từng chu kỳ làm việc.
                </p>
              </div>
            )}

            {activeTab === 'members' && (
              <div style={{ padding: 24, overflowY: 'auto', flex: 1 }}>
                <ProjectMembersTab projectId={projectId} isAdmin={isAdmin} />
              </div>
            )}

            {activeTab === 'workflow' && (
              <div style={{ padding: 24, overflowY: 'auto', flex: 1 }}>
                <ProjectWorkflowTab projectId={projectId} isAdmin={isAdmin} onTypesUpdated={() => fetchProject(true)} />
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
      />

      {/* ─── Delete Project Modal ─── */}
      <DeleteProjectModal
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        project={project}
        onDeleted={() => {
          navigate('/projects');
        }}
      />
    </div>
  );
}

