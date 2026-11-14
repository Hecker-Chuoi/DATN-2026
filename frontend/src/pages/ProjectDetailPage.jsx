import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useOutletContext, useSearchParams, NavLink, Outlet } from 'react-router-dom';
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
  BarChart3,
  Star,
  MoreHorizontal,
  Link2,
  SlidersHorizontal,
  Plus,
  Trash2,
  BookmarkPlus,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi, sprintApi } from '../api.js';
import { useProjectPermission } from '../hooks/useProjectPermission.js';
import { ProjectProvider } from '../context/ProjectContext.jsx';
import { isScrumProject } from '../utils/projectType.js';
import { wsPath } from '../utils/workspacePath.js';
import CreateWorkItemModal from '../components/items/CreateWorkItemModal.jsx';
import WorkItemDetailModal from '../components/items/WorkItemDetailModal.jsx';
import DeleteProjectModal from '../components/project/DeleteProjectModal.jsx';
import SaveAsTemplateModal from '../components/template/SaveAsTemplateModal.jsx';
import AccessDenied from '../components/common/AccessDenied.jsx';

export default function ProjectDetailPage() {
  const { projectId, workspaceId } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorStatus, setErrorStatus] = useState(null); // 403 / 404 từ GET /projects/{id}
  const [isStarred, setIsStarred] = useState(false);
  const [members, setMembers] = useState([]);
  const [sprints, setSprints] = useState([]);

  // Work Item Modals State
  const [isCreateItemModalOpen, setIsCreateItemModalOpen] = useState(false);
  const [createPrefill, setCreatePrefill] = useState(null);
  const [selectedItemId, setSelectedItemId] = useState(null);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);
  const [boardRefreshKey, setBoardRefreshKey] = useState(0);

  // Link từ thông báo: ?item={id}[&comment={id}] mở sẵn chi tiết công việc (và cuộn tới bình luận)
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedItemId = searchParams.get('item');
  const linkedCommentId = searchParams.get('comment');
  const modalItemId = linkedItemId || selectedItemId;
  const modalOpen = Boolean(linkedItemId) || isDetailModalOpen;

  const outletContext = useOutletContext() || {};
  const currentUser = outletContext.currentUser;

  // Project Action Menu & Delete Modal State
  const [isActionMenuOpen, setIsActionMenuOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isSaveTemplateOpen, setIsSaveTemplateOpen] = useState(false);
  const actionMenuRef = useRef(null);

  const { isAdmin, canCreateIssue, canManageSprint, permissions, forbidden, notFound } = useProjectPermission(projectId);

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
      const [projRes, membersRes, sprintRes] = await Promise.all([
        projectApi.getById(projectId, { skipErrorToast: true }),
        projectApi.getMembers(projectId, { skipErrorToast: true }).catch(() => null),
        sprintApi.listByProject(projectId, undefined, { skipErrorToast: true }).catch(() => null),
      ]);
      if (projRes && projRes.data) {
        setProject(projRes.data);
      }
      if (membersRes && membersRes.data) {
        setMembers(membersRes.data);
      }
      if (sprintRes && sprintRes.data) {
        setSprints(sprintRes.data);
      }
    } catch (err) {
      if (err?.status === 403 || err?.status === 404) {
        setErrorStatus(err.status);
      } else {
        toast.error(err?.message || 'Không tải được thông tin dự án');
      }
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

  const handleOpenCreateItem = (prefill) => {
    setCreatePrefill(prefill || null);
    setIsCreateItemModalOpen(true);
  };

  const handleItemCreated = (newItem) => {
    setBoardRefreshKey((prev) => prev + 1);
  };

  const handleItemUpdated = (updatedItem) => {
    setBoardRefreshKey((prev) => prev + 1);
  };

  // State dung chung cap cho moi tab con qua ProjectContext.
  const projectContextValue = {
    projectId,
    project,
    members,
    sprints,
    reloadProject: fetchProject,
    permissions,
    isAdmin,
    canCreateIssue,
    canManageSprint,
    refreshKey: boardRefreshKey,
    bumpRefresh: () => setBoardRefreshKey((prev) => prev + 1),
    openItemDetail: handleOpenDetailModal,
    openCreateItem: handleOpenCreateItem,
  };

  // Người ngoài dự án / dự án không tồn tại: thay toàn bộ nội dung bằng trang lỗi (không render khung trang + toast)
  if (errorStatus === 404 || notFound) return <AccessDenied status={404} title="Không tìm thấy dự án" message="Dự án không tồn tại hoặc đã bị xóa." />;
  if (errorStatus === 403 || forbidden) return <AccessDenied status={403} />;

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
            {project?.isTemplate && (
              <span
                style={{
                  fontSize: 10.5,
                  fontWeight: 700,
                  color: '#B45309',
                  backgroundColor: '#FFFBEB',
                  border: '1px solid #FDE68A',
                  padding: '1px 6px',
                  borderRadius: 4,
                }}
                title="Dự án mẫu: dùng làm bản thiết kế, không thể kích hoạt chu kỳ"
              >
                MẪU
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
                  {(isAdmin || isSystemAdmin) && (
                    <button
                      type="button"
                      onClick={() => {
                        setIsActionMenuOpen(false);
                        setIsSaveTemplateOpen(true);
                      }}
                      style={{
                        width: '100%',
                        padding: '8px 12px',
                        borderRadius: 6,
                        border: 'none',
                        backgroundColor: 'transparent',
                        color: '#0F172A',
                        fontSize: 13,
                        fontWeight: 500,
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        textAlign: 'left',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.backgroundColor = '#F1F5F9';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.backgroundColor = 'transparent';
                      }}
                    >
                      <BookmarkPlus size={14} color="#D97706" />
                      <span>Lưu thành dự án mẫu</span>
                    </button>
                  )}
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
            onClick={() => handleOpenCreateItem()}
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
        {/* Navigation Pills - dieu huong bang nested route de deep-link va nut Back hoat dong */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          {[
            { to: 'board', label: 'Bảng tiến độ', icon: Kanban, visible: true },
            { to: 'backlog', label: 'Kho việc & Kế hoạch', icon: ListTodo, visible: isScrumProject(project) },
            { to: 'members', label: 'Thành viên & Vai trò', icon: Users, visible: true },
            // "Báo cáo" gop ca Dong gop lan Tien do (2 tab ben trong ProjectReportsPage).
            { to: 'reports', label: 'Báo cáo', icon: BarChart3, visible: true },
            { to: 'workflow', label: 'Cấu hình Dự án', icon: Settings, visible: true },
          ]
            .filter((tab) => tab.visible)
            .map((tab) => {
              const TabIcon = tab.icon;
              return (
                <NavLink
                  key={tab.to}
                  to={tab.to}
                  style={({ isActive }) => ({
                    padding: '6px 14px',
                    borderRadius: 20,
                    fontSize: 13,
                    fontWeight: isActive ? 600 : 500,
                    border: 'none',
                    cursor: 'pointer',
                    textDecoration: 'none',
                    backgroundColor: isActive ? '#F1F5F9' : 'transparent',
                    color: isActive ? '#0F172A' : '#64748B',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    transition: 'all 0.15s ease',
                  })}
                >
                  <TabIcon size={14} />
                  <span>{tab.label}</span>
                </NavLink>
              );
            })}
        </div>
      </div>

      {/* ─── Body Content: cac tab con render qua <Outlet/> ─── */}
      <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        {(loading && !project) ? (
          <div style={{ padding: 48, textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Đang tải thông tin dự án...
          </div>
        ) : (
          <ProjectProvider value={projectContextValue}>
            <Outlet />
          </ProjectProvider>
        )}
      </div>


      {/* ─── Create Work Item Modal ─── */}
      <CreateWorkItemModal
        isOpen={isCreateItemModalOpen}
        onClose={() => {
          setIsCreateItemModalOpen(false);
          setCreatePrefill(null);
        }}
        defaultSprintId={createPrefill?.sprintId ?? null}
        defaultTypeId={createPrefill?.typeId ?? null}
        defaultParentId={createPrefill?.parentId ?? null}
        projectId={projectId}
        projectKey={project?.key}
        projectTypes={project?.workItemTypes || []}
        members={members}
        sprints={sprints}
        onItemCreated={handleItemCreated}
      />

      {/* ─── Work Item Detail Modal (2 Columns Jira Spec) ─── */}
      <WorkItemDetailModal
        itemId={modalItemId}
        isOpen={modalOpen}
        focusCommentId={linkedItemId ? linkedCommentId : null}
        onClose={() => {
          setIsDetailModalOpen(false);
          setSelectedItemId(null);
          if (linkedItemId || linkedCommentId) {
            setSearchParams((prev) => {
              const next = new URLSearchParams(prev);
              next.delete('item');
              next.delete('comment');
              return next;
            }, { replace: true });
          }
        }}
        onItemUpdated={handleItemUpdated}
        members={members}
        sprints={sprints}
        canManageSprint={canManageSprint || isAdmin}
      />

      {/* ─── Delete Project Modal ─── */}
      <DeleteProjectModal
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        project={project}
        onDeleted={() => {
          navigate(wsPath(workspaceId, '/projects'));
        }}
      />

      <SaveAsTemplateModal
        isOpen={isSaveTemplateOpen}
        onClose={() => setIsSaveTemplateOpen(false)}
        project={project}
        onSaved={(tpl) => {
          if (tpl?.id) navigate(wsPath(workspaceId, `/projects/${tpl.id}`));
        }}
      />
    </div>
  );
}

