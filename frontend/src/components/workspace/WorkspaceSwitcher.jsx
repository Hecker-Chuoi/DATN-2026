import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Check,
  ChevronDown,
  ChevronRight,
  Plus,
  Building2,
  Compass,
  Settings,
  Users,
  LogOut,
} from 'lucide-react';
import { useWorkspace } from '../../context/WorkspaceContext';
import { wsPath } from '../../utils/workspacePath';

const WORKSPACE_GRADIENTS = [
  'linear-gradient(135deg, #6366F1, #8B5CF6)',
  'linear-gradient(135deg, #3B82F6, #06B6D4)',
  'linear-gradient(135deg, #10B981, #059669)',
  'linear-gradient(135deg, #F59E0B, #D97706)',
  'linear-gradient(135deg, #EC4899, #DB2777)',
  'linear-gradient(135deg, #8B5CF6, #6D28D9)',
  'linear-gradient(135deg, #0EA5E9, #2563EB)',
  'linear-gradient(135deg, #14B8A6, #0D9488)',
];

function getWorkspaceGradient(text = '') {
  let hash = 0;
  for (let i = 0; i < text.length; i++) {
    hash = text.charCodeAt(i) + ((hash << 5) - hash);
  }
  return WORKSPACE_GRADIENTS[Math.abs(hash) % WORKSPACE_GRADIENTS.length];
}

/**
 * WorkspaceSwitcher - Thiết kế theo chuẩn giao diện Linear:
 * Trigger dạng ghost button tinh giản (Avatar gradient + Tên workspace + ChevronDown),
 * Popover gồm các mục thao tác chính:
 * 1. Cài đặt workspace (Settings)
 * 2. Mời & quản lý thành viên (Invite and manage members)
 * 3. Chuyển workspace (Switch workspace) -> Flyout mở danh sách workspace + Tài khoản
 * 4. Đăng xuất (Log out)
 */
export default function WorkspaceSwitcher({ currentUser, onLogout }) {
  const { workspaces, currentWorkspace, currentWorkspaceId, openCreateWorkspace } = useWorkspace();
  const [open, setOpen] = useState(false);
  const [submenuOpen, setSubmenuOpen] = useState(false);
  const [triggerHovered, setTriggerHovered] = useState(false);
  const [isLogoutHovered, setIsLogoutHovered] = useState(false);

  const containerRef = useRef(null);
  const closeSubmenuTimerRef = useRef(null);
  const navigate = useNavigate();

  // Đóng popover khi click ngoài vùng
  useEffect(() => {
    if (!open) return;
    const onMouseDown = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
        setSubmenuOpen(false);
      }
    };
    document.addEventListener('mousedown', onMouseDown);
    return () => document.removeEventListener('mousedown', onMouseDown);
  }, [open]);

  // Đóng popover khi nhấn phím Escape
  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        setOpen(false);
        setSubmenuOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open]);

  const name = currentWorkspace?.name || 'Chọn không gian làm việc';
  const initial = (currentWorkspace?.name || '?').trim().charAt(0).toUpperCase();

  const handleSelectWorkspace = (id) => {
    setOpen(false);
    setSubmenuOpen(false);
    if (id === currentWorkspaceId) return;
    navigate(wsPath(id, '/projects'));
  };

  const handleOpenSettings = () => {
    setOpen(false);
    setSubmenuOpen(false);
    if (currentWorkspaceId) {
      navigate(wsPath(currentWorkspaceId, '/settings'));
    }
  };

  const handleOpenMembers = () => {
    setOpen(false);
    setSubmenuOpen(false);
    if (currentWorkspaceId) {
      navigate(wsPath(currentWorkspaceId, '/members'));
    }
  };

  const handleLogoutClick = () => {
    setOpen(false);
    setSubmenuOpen(false);
    if (onLogout) {
      onLogout();
    } else {
      navigate('/login');
    }
  };

  const handleMouseEnterSwitch = () => {
    if (closeSubmenuTimerRef.current) {
      clearTimeout(closeSubmenuTimerRef.current);
      closeSubmenuTimerRef.current = null;
    }
    setSubmenuOpen(true);
  };

  const handleMouseLeaveSwitch = () => {
    closeSubmenuTimerRef.current = setTimeout(() => {
      setSubmenuOpen(false);
    }, 180);
  };

  return (
    <div ref={containerRef} style={{ position: 'relative', display: 'inline-block', maxWidth: '100%' }}>
      {/* ─── 1. Linear-style Ghost Trigger Button ─── */}
      <button
        type="button"
        onClick={() => {
          setOpen((v) => !v);
          if (open) setSubmenuOpen(false);
        }}
        onMouseEnter={() => setTriggerHovered(true)}
        onMouseLeave={() => setTriggerHovered(false)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 7,
          padding: '4px 6px',
          borderRadius: 6,
          border: 'none',
          backgroundColor: open || triggerHovered ? '#F1F5F9' : 'transparent',
          cursor: 'pointer',
          textAlign: 'left',
          maxWidth: '100%',
          transition: 'background-color 0.15s ease',
        }}
        title={name}
      >
        {/* Workspace Avatar */}
        <div
          style={{
            width: 22,
            height: 22,
            borderRadius: 5,
            background: getWorkspaceGradient(name),
            color: '#FFFFFF',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            fontSize: 11,
            flexShrink: 0,
            boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
          }}
        >
          {initial}
        </div>

        {/* Workspace Name */}
        <span
          style={{
            fontSize: 14,
            fontWeight: 600,
            color: '#0F172A',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            maxWidth: 135,
            letterSpacing: '-0.01em',
          }}
        >
          {name}
        </span>

        {/* Chevron icon */}
        <ChevronDown
          size={13}
          color="#64748B"
          style={{
            flexShrink: 0,
            transition: 'transform 0.15s ease',
            transform: open ? 'rotate(180deg)' : 'none',
          }}
        />
      </button>

      {/* ─── 2. Main Linear-style Popover Menu ─── */}
      {open && (
        <div
          className="linear-menu-enter"
          style={{
            position: 'absolute',
            top: 'calc(100% + 5px)',
            left: 0,
            zIndex: 1000,
            minWidth: 236,
            maxWidth: 260,
            backgroundColor: '#FFFFFF',
            border: '1px solid #E2E8F0',
            borderRadius: 10,
            boxShadow: '0 10px 25px -5px rgba(15, 23, 42, 0.12), 0 8px 10px -6px rgba(15, 23, 42, 0.08)',
            padding: 5,
          }}
        >
          {/* Cài đặt workspace (Settings) */}
          <button
            type="button"
            onClick={handleOpenSettings}
            onMouseEnter={() => setSubmenuOpen(false)}
            style={{
              display: 'flex',
              alignItems: 'center',
              width: '100%',
              padding: '7px 8px',
              backgroundColor: 'transparent',
              border: 'none',
              borderRadius: 6,
              cursor: 'pointer',
              textAlign: 'left',
              transition: 'background-color 0.12s ease',
            }}
            onMouseEnterCapture={(e) => {
              e.currentTarget.style.backgroundColor = '#F1F5F9';
            }}
            onMouseLeaveCapture={(e) => {
              e.currentTarget.style.backgroundColor = 'transparent';
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
              <Settings size={15} color="#475569" />
              <span style={{ fontSize: 13, fontWeight: 500, color: '#1E293B' }}>
                Cài đặt không gian làm việc
              </span>
            </div>
          </button>

          {/* Mời & quản lý thành viên (Invite and manage members) */}
          <button
            type="button"
            onClick={handleOpenMembers}
            onMouseEnter={() => setSubmenuOpen(false)}
            style={{
              display: 'flex',
              alignItems: 'center',
              width: '100%',
              padding: '7px 8px',
              backgroundColor: 'transparent',
              border: 'none',
              borderRadius: 6,
              cursor: 'pointer',
              textAlign: 'left',
              transition: 'background-color 0.12s ease',
            }}
            onMouseEnterCapture={(e) => {
              e.currentTarget.style.backgroundColor = '#F1F5F9';
            }}
            onMouseLeaveCapture={(e) => {
              e.currentTarget.style.backgroundColor = 'transparent';
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
              <Users size={15} color="#475569" />
              <span style={{ fontSize: 13, fontWeight: 500, color: '#1E293B' }}>
                Mời & quản lý thành viên
              </span>
            </div>
          </button>

          {/* Divider */}
          <div style={{ height: 1, backgroundColor: '#F1F5F9', margin: '4px 2px' }} />

          {/* Chuyển workspace (Switch workspace) with Submenu Flyout */}
          <div
            style={{ position: 'relative' }}
            onMouseEnter={handleMouseEnterSwitch}
            onMouseLeave={handleMouseLeaveSwitch}
          >
            <button
              type="button"
              onClick={() => setSubmenuOpen((v) => !v)}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                width: '100%',
                padding: '7px 8px',
                backgroundColor: submenuOpen ? '#F1F5F9' : 'transparent',
                border: 'none',
                borderRadius: 6,
                cursor: 'pointer',
                textAlign: 'left',
                transition: 'background-color 0.12s ease',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
                <Building2 size={15} color="#475569" />
                <span style={{ fontSize: 13, fontWeight: 500, color: '#1E293B' }}>
                  Chuyển không gian làm việc
                </span>
              </div>
              <ChevronRight size={13} color="#94A3B8" />
            </button>

            {/* ─── 3. Flyout Submenu for Switching Workspace ─── */}
            {submenuOpen && (
              <div
                className="linear-menu-enter"
                style={{
                  position: 'absolute',
                  left: 'calc(100% + 4px)',
                  top: -4,
                  minWidth: 240,
                  maxWidth: 280,
                  backgroundColor: '#FFFFFF',
                  border: '1px solid #E2E8F0',
                  borderRadius: 10,
                  boxShadow: '0 10px 25px -5px rgba(15, 23, 42, 0.14), 0 8px 10px -6px rgba(15, 23, 42, 0.08)',
                  padding: 5,
                  zIndex: 1001,
                }}
              >
                {/* User email header */}
                <div
                  style={{
                    padding: '6px 8px 8px',
                    fontSize: 12,
                    fontWeight: 500,
                    color: '#64748B',
                    borderBottom: '1px solid #F1F5F9',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                  title={currentUser?.email || ''}
                >
                  {currentUser?.email || 'user@work.datn'}
                </div>

                {/* Workspace items */}
                <div style={{ maxHeight: 220, overflowY: 'auto', padding: '4px 0' }}>
                  {workspaces.length === 0 && (
                    <div style={{ padding: '8px 10px', fontSize: 12, color: '#94A3B8' }}>
                      Chưa có không gian làm việc nào
                    </div>
                  )}
                  {workspaces.map((w) => {
                    const isCurrent = w.id === currentWorkspaceId;
                    return (
                      <button
                        key={w.id}
                        type="button"
                        onClick={() => handleSelectWorkspace(w.id)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 8,
                          width: '100%',
                          padding: '6px 8px',
                          borderRadius: 6,
                          border: 'none',
                          backgroundColor: isCurrent ? '#EFF6FF' : 'transparent',
                          cursor: 'pointer',
                          textAlign: 'left',
                          transition: 'background-color 0.12s ease',
                        }}
                        onMouseEnter={(e) => {
                          if (!isCurrent) e.currentTarget.style.backgroundColor = '#F8FAFC';
                        }}
                        onMouseLeave={(e) => {
                          if (!isCurrent) e.currentTarget.style.backgroundColor = 'transparent';
                        }}
                      >
                        <div
                          style={{
                            width: 20,
                            height: 20,
                            borderRadius: 4,
                            background: getWorkspaceGradient(w.name),
                            color: '#FFFFFF',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: 10,
                            fontWeight: 700,
                            flexShrink: 0,
                          }}
                        >
                          {(w.name || '?').trim().charAt(0).toUpperCase()}
                        </div>
                        <span
                          style={{
                            flex: 1,
                            minWidth: 0,
                            fontSize: 13,
                            fontWeight: isCurrent ? 600 : 500,
                            color: isCurrent ? '#1D4ED8' : '#1E293B',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {w.name}
                        </span>
                        {isCurrent && <Check size={14} color="#2563EB" style={{ flexShrink: 0 }} />}
                      </button>
                    );
                  })}
                </div>

                {/* Account Section */}
                <div style={{ height: 1, backgroundColor: '#F1F5F9', margin: '4px 2px' }} />
                <div
                  style={{
                    padding: '4px 8px 2px',
                    fontSize: 11,
                    fontWeight: 600,
                    color: '#94A3B8',
                    textTransform: 'uppercase',
                    letterSpacing: '0.04em',
                  }}
                >
                  Tài khoản
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setOpen(false);
                    setSubmenuOpen(false);
                    openCreateWorkspace();
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    width: '100%',
                    padding: '6px 8px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: 'transparent',
                    cursor: 'pointer',
                    color: '#2563EB',
                    fontSize: 13,
                    fontWeight: 500,
                    textAlign: 'left',
                    transition: 'background-color 0.12s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#EFF6FF')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                >
                  <Plus size={14} color="#2563EB" />
                  <span>Tạo không gian làm việc mới...</span>
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setOpen(false);
                    setSubmenuOpen(false);
                    navigate('/workspaces');
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    width: '100%',
                    padding: '6px 8px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: 'transparent',
                    cursor: 'pointer',
                    color: '#475569',
                    fontSize: 13,
                    fontWeight: 500,
                    textAlign: 'left',
                    transition: 'background-color 0.12s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                >
                  <Compass size={14} color="#64748B" />
                  <span>Xem tất cả không gian làm việc</span>
                </button>
              </div>
            )}
          </div>

          {/* Divider */}
          <div style={{ height: 1, backgroundColor: '#F1F5F9', margin: '4px 2px' }} />

          {/* Đăng xuất (Log out) */}
          <button
            type="button"
            onClick={handleLogoutClick}
            onMouseEnter={() => {
              setSubmenuOpen(false);
              setIsLogoutHovered(true);
            }}
            onMouseLeave={() => setIsLogoutHovered(false)}
            style={{
              display: 'flex',
              alignItems: 'center',
              width: '100%',
              padding: '7px 8px',
              backgroundColor: isLogoutHovered ? '#FEF2F2' : 'transparent',
              border: 'none',
              borderRadius: 6,
              cursor: 'pointer',
              textAlign: 'left',
              transition: 'all 0.12s ease',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
              <LogOut size={15} color={isLogoutHovered ? '#DC2626' : '#64748B'} />
              <span
                style={{
                  fontSize: 13,
                  fontWeight: 500,
                  color: isLogoutHovered ? '#DC2626' : '#334155',
                  transition: 'color 0.12s ease',
                }}
              >
                Đăng xuất
              </span>
            </div>
          </button>
        </div>
      )}
    </div>
  );
}
