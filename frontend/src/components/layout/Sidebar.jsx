import React, { useState, useEffect } from 'react';
import { NavLink, useNavigate, useLocation, useParams } from 'react-router-dom';
import {
  FolderKanban,
  CheckCircle2,
  Plus,
  ChevronDown,
  ChevronRight,
  Layers,
  Users,
  Settings,
  Mail,
  LogOut,
  LayoutGrid,
  ListTodo,
  Bell
} from 'lucide-react';
import WorkspaceSwitcher from '../workspace/WorkspaceSwitcher.jsx';
import { useWorkspaceRole } from '../../hooks/useWorkspaceRole.js';
import { wsPath } from '../../utils/workspacePath.js';
import { isScrumProject } from '../../utils/projectType.js';
import NotificationBell from '../../notifications/NotificationBell.jsx';
import { useNotifications } from '../../notifications/NotificationContext.jsx';

const PROJECT_COLORS = [
  '#2563EB', // Blue
  '#8B5CF6', // Purple
  '#10B981', // Green
  '#F59E0B', // Amber
  '#EC4899', // Pink
  '#06B6D4', // Cyan
  '#6366F1'  // Indigo
];

export default function Sidebar({
  projects = [],
  loading = false,
  onOpenCreateProject,
  invitationCount = 0,
  currentUser,
  onLogout
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const { workspaceId } = useParams();
  const { canCreateProject, canManageWorkspace } = useWorkspaceRole();
  const { counts: notificationCounts } = useNotifications();
  const [expandedProjects, setExpandedProjects] = useState({});

  useEffect(() => {
    // Tự động mở accordion của dự án đầu tiên hoặc dự án đang xem
    const activeProject = projects.find((p) => location.pathname.includes(`/projects/${p.id}`));
    if (activeProject) {
      setExpandedProjects({ [activeProject.id]: true });
    } else if (projects[0]?.id) {
      setExpandedProjects({ [projects[0].id]: true });
    } else {
      setExpandedProjects({});
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId]);

  const ws = (sub) => wsPath(workspaceId, sub);

  const toggleProject = (projectId) => {
    setExpandedProjects((prev) => ({
      ...prev,
      [projectId]: !prev[projectId],
    }));
  };

  return (
    <aside
      style={{
        width: 256,
        minWidth: 256,
        height: '100%',
        backgroundColor: '#FFFFFF',
        borderRight: '1px solid #E2E8F0',
        display: 'flex',
        flexDirection: 'column',
        userSelect: 'none',
      }}
    >
      {/* ─── 1. Workspace Header ─── */}
      <div
        style={{
          padding: '10px 12px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid #F1F5F9',
          position: 'relative',
        }}
      >
        <div style={{ flex: 1, minWidth: 0 }}>
          <WorkspaceSwitcher currentUser={currentUser} onLogout={onLogout} />
        </div>

        <NotificationBell />

        {canCreateProject && (
          <button
            onClick={() => onOpenCreateProject && onOpenCreateProject()}
            title="Tạo dự án mới"
            style={{
              width: 28,
              height: 28,
              borderRadius: 6,
              border: 'none',
              backgroundColor: 'transparent',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              color: '#64748B',
              transition: 'all 0.15s ease',
              flexShrink: 0,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = '#F1F5F9';
              e.currentTarget.style.color = '#0F172A';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = 'transparent';
              e.currentTarget.style.color = '#64748B';
            }}
          >
            <Plus size={16} />
          </button>
        )}
      </div>

      {/* ─── 2. Main Navigation Links ─── */}
      <div style={{ padding: '14px 12px 6px 12px', display: 'flex', flexDirection: 'column', gap: 2 }}>
        <NavItem
          to={ws('/projects')}
          icon={<FolderKanban size={17} color="#2563EB" />}
          label="Tất cả dự án"
          active={location.pathname === ws('/projects')}
        />
        <NavItem
          to={ws('/my-issues')}
          icon={<CheckCircle2 size={17} color="#10B981" />}
          label="Công việc của tôi"
          active={location.pathname === ws('/my-issues')}
        />
        <NavItem
          to={ws('/notifications')}
          icon={<Bell size={17} color="#DC2626" />}
          label="Thông báo"
          badge={notificationCounts.total}
          badgeColor="#DC2626"
          active={location.pathname === ws('/notifications')}
        />
        <NavItem
          to={ws('/invitations')}
          icon={<Mail size={17} color="#F59E0B" />}
          label="Lời mời"
          badge={invitationCount}
          active={location.pathname === ws('/invitations')}
        />
        <NavItem
          to={ws('/members')}
          icon={<Users size={17} color="#8B5CF6" />}
          label="Thành viên Không gian"
          active={location.pathname === ws('/members')}
        />
        {canManageWorkspace && (
          <NavItem
            to={ws('/settings')}
            icon={<Settings size={17} color="#64748B" />}
            label="Cài đặt không gian"
            active={location.pathname === ws('/settings')}
          />
        )}
      </div>

      <div style={{ margin: '8px 16px', height: 1, backgroundColor: '#F1F5F9' }} />

      {/* ─── 3. Projects Section (Direct Workspace Projects) ─── */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 12px' }}>
        <div
          style={{
            padding: '6px 8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            fontSize: 11,
            fontWeight: 700,
            color: '#64748B',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          <span>Dự án của bạn</span>
          {canCreateProject && (
            <button
              onClick={() => onOpenCreateProject && onOpenCreateProject()}
              title="Thêm dự án mới"
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                color: '#94A3B8',
                display: 'flex',
                alignItems: 'center',
                padding: 2,
              }}
              onMouseEnter={(e) => (e.currentTarget.style.color = '#2563EB')}
              onMouseLeave={(e) => (e.currentTarget.style.color = '#94A3B8')}
            >
              <Plus size={14} />
            </button>
          )}
        </div>

        {loading ? (
          <div style={{ padding: '12px 8px', fontSize: 12, color: '#94A3B8' }}>Đang tải danh sách dự án...</div>
        ) : projects.length === 0 ? (
          <div style={{ padding: '12px 8px', fontSize: 12, color: '#94A3B8' }}>
            Chưa có dự án nào.{' '}
            {canCreateProject && (
              <span
                onClick={() => onOpenCreateProject && onOpenCreateProject()}
                style={{ color: '#2563EB', cursor: 'pointer', fontWeight: 600 }}
              >
                Tạo dự án ngay
              </span>
            )}
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {projects.map((project, idx) => {
              const isExpanded = !!expandedProjects[project.id];
              const color = PROJECT_COLORS[idx % PROJECT_COLORS.length];
              const isCurrentProject = location.pathname.includes(`/projects/${project.id}`);

              return (
                <div key={project.id || `proj-${idx}`} style={{ display: 'flex', flexDirection: 'column' }}>
                  {/* Project Trigger */}
                  <div
                    onClick={() => toggleProject(project.id)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '7px 8px',
                      borderRadius: 6,
                      fontSize: 13,
                      fontWeight: 600,
                      color: isCurrentProject ? '#0F172A' : '#334155',
                      backgroundColor: isCurrentProject ? '#F1F5F9' : 'transparent',
                      cursor: 'pointer',
                      transition: 'background-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => {
                      if (!isCurrentProject) e.currentTarget.style.backgroundColor = '#F8FAFC';
                    }}
                    onMouseLeave={(e) => {
                      if (!isCurrentProject) e.currentTarget.style.backgroundColor = 'transparent';
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden' }}>
                      <div
                        style={{
                          width: 14,
                          height: 14,
                          borderRadius: 4,
                          backgroundColor: color,
                          flexShrink: 0,
                        }}
                      />
                      <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {project.name}
                      </span>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      <span style={{ fontSize: 11, color: '#94A3B8' }}>{project.key || ''}</span>
                      {isExpanded ? <ChevronDown size={14} color="#94A3B8" /> : <ChevronRight size={14} color="#94A3B8" />}
                    </div>
                  </div>

                  {/* Project Sub-items */}
                  {isExpanded && (
                    <div style={{ paddingLeft: 22, display: 'flex', flexDirection: 'column', gap: 2, marginTop: 2 }}>
                      <SubNavItem
                        to={ws(`/projects/${project.id}/board`)}
                        icon={<LayoutGrid size={13} />}
                        label="Bảng làm việc"
                        active={location.pathname === ws(`/projects/${project.id}/board`)}
                      />
                      {isScrumProject(project) && (
                        <SubNavItem
                          to={ws(`/projects/${project.id}/backlog`)}
                          icon={<ListTodo size={13} />}
                          label="Kho việc & Kế hoạch"
                          active={location.pathname === ws(`/projects/${project.id}/backlog`)}
                        />
                      )}
                      <SubNavItem
                        to={ws(`/projects/${project.id}/members`)}
                        icon={<Users size={13} />}
                        label="Thành viên"
                        active={location.pathname === ws(`/projects/${project.id}/members`)}
                      />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ─── 4. User Profile Footer ─── */}
      <div
        style={{
          padding: '10px 12px',
          borderTop: '1px solid #F1F5F9',
          display: 'flex',
          alignItems: 'center',
          gap: 6,
        }}
      >
        <div
          onClick={() => navigate(ws('/profile'))}
          title="Xem thông tin tài khoản"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            flex: 1,
            minWidth: 0,
            padding: '6px 8px',
            borderRadius: 8,
            cursor: 'pointer',
            backgroundColor: location.pathname === ws('/profile') ? '#EFF6FF' : 'transparent',
            transition: 'all 0.15s ease',
          }}
          onMouseEnter={(e) => {
            if (location.pathname !== ws('/profile')) {
              e.currentTarget.style.backgroundColor = '#F8FAFC';
            }
          }}
          onMouseLeave={(e) => {
            if (location.pathname !== ws('/profile')) {
              e.currentTarget.style.backgroundColor = 'transparent';
            }
          }}
        >
          <div
            style={{
              width: 32,
              height: 32,
              borderRadius: '50%',
              backgroundColor: '#E2E8F0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 600,
              fontSize: 13,
              color: '#334155',
              flexShrink: 0,
            }}
          >
            {currentUser?.username ? currentUser.username.charAt(0).toUpperCase() : 'U'}
          </div>
          <div style={{ overflow: 'hidden', flex: 1, minWidth: 0 }}>
            <div
              style={{
                fontSize: 12,
                fontWeight: 600,
                color: location.pathname === ws('/profile') ? '#2563EB' : '#0F172A',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {currentUser?.fullName || currentUser?.username || 'Người dùng'}
            </div>
            <div
              style={{
                fontSize: 11,
                color: '#94A3B8',
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {currentUser?.email || 'user@work.datn'}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
          {onLogout && (
            <button
              onClick={onLogout}
              title="Đăng xuất"
              style={{
                background: 'none',
                border: 'none',
                color: '#64748B',
                cursor: 'pointer',
                padding: '6px',
                borderRadius: 6,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = '#FEE2E2';
                e.currentTarget.style.color = '#DC2626';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = 'transparent';
                e.currentTarget.style.color = '#64748B';
              }}
            >
              <LogOut size={16} />
            </button>
          )}
        </div>
      </div>
    </aside>
  );
}

function NavItem({ to, icon, label, active, badge = 0, badgeColor = '#F59E0B' }) {
  return (
    <NavLink
      to={to}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '7px 10px',
        borderRadius: 6,
        fontSize: 13,
        fontWeight: active ? 600 : 500,
        color: active ? '#2563EB' : '#475569',
        backgroundColor: active ? '#EFF6FF' : 'transparent',
        textDecoration: 'none',
        transition: 'all 0.15s ease',
      }}
      onMouseEnter={(e) => {
        if (!active) {
          e.currentTarget.style.backgroundColor = '#F8FAFC';
          e.currentTarget.style.color = '#0F172A';
        }
      }}
      onMouseLeave={(e) => {
        if (!active) {
          e.currentTarget.style.backgroundColor = 'transparent';
          e.currentTarget.style.color = '#475569';
        }
      }}
    >
      {icon}
      <span style={{ flex: 1 }}>{label}</span>
      {badge > 0 && (
        <span
          style={{
            fontSize: 11,
            fontWeight: 700,
            backgroundColor: badgeColor,
            color: '#FFFFFF',
            padding: '1px 7px',
            borderRadius: 10,
          }}
        >
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </NavLink>
  );
}

function SubNavItem({ to, icon, label, active }) {
  return (
    <NavLink
      to={to}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '5px 8px',
        borderRadius: 5,
        fontSize: 12,
        fontWeight: active ? 600 : 400,
        color: active ? '#2563EB' : '#64748B',
        backgroundColor: active ? '#EFF6FF' : 'transparent',
        textDecoration: 'none',
        transition: 'all 0.15s ease',
      }}
      onMouseEnter={(e) => {
        if (!active) e.currentTarget.style.backgroundColor = '#F8FAFC';
      }}
      onMouseLeave={(e) => {
        if (!active) e.currentTarget.style.backgroundColor = 'transparent';
      }}
    >
      {icon}
      <span>{label}</span>
    </NavLink>
  );
}
