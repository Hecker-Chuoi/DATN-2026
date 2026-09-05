import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
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
  Plus
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi } from '../api.js';
import { useProjectPermission } from '../hooks/useProjectPermission.js';
import ProjectMembersTab from '../components/project/ProjectMembersTab.jsx';
import ProjectWorkflowTab from '../components/project/ProjectWorkflowTab.jsx';
import ProjectBoardView from '../components/boards/ProjectBoardView.jsx';
import CreateWorkItemModal from '../components/items/CreateWorkItemModal.jsx';
import WorkItemDetailModal from '../components/items/WorkItemDetailModal.jsx';

export default function ProjectDetailPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('board'); // 'board' | 'backlog' | 'members' | 'workflow'
  const [isStarred, setIsStarred] = useState(false);
  const [members, setMembers] = useState([]);

  // Work Item Modals State
  const [isCreateItemModalOpen, setIsCreateItemModalOpen] = useState(false);
  const [selectedItemId, setSelectedItemId] = useState(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [boardRefreshKey, setBoardRefreshKey] = useState(0);

  const { isAdmin, canCreateIssue, canManageSprint, permissions } = useProjectPermission(projectId);

  const fetchProject = async () => {
    try {
      setLoading(true);
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
      setLoading(false);
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
            { id: 'board', label: 'Active Board', icon: Kanban },
            { id: 'backlog', label: 'Backlog', icon: ListTodo },
            { id: 'members', label: 'Members & Roles', icon: Users },
            { id: 'workflow', label: 'Workflow & Statuses', icon: GitBranch },
          ].map((tab) => {
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
        {loading ? (
          <div style={{ padding: 48, textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Đang tải thông tin dự án...
          </div>
        ) : (
          <>
            {activeTab === 'board' && (
              <ProjectBoardView
                projectId={projectId}
                projectKey={project?.key}
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
                  Backlog & Lập kế hoạch Sprint
                </h3>
                <p style={{ fontSize: 13, color: '#64748B' }}>
                  Quản lý danh sách công việc tồn đọng (Product Backlog) và phân bổ Sprint.
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
                <ProjectWorkflowTab projectId={projectId} isAdmin={isAdmin} />
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
    </div>
  );
}

