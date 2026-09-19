import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, ChevronDown, Plus, Building2, Compass } from 'lucide-react';
import { useWorkspace } from '../../context/WorkspaceContext';
import { wsPath } from '../../utils/workspacePath';
import RoleBadge from './RoleBadge';

const footerBtn = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  width: '100%',
  padding: '8px 10px',
  background: 'transparent',
  border: 'none',
  borderRadius: 6,
  cursor: 'pointer',
  textAlign: 'left',
};

/**
 * Switcher thay cho block header hardcode cũ của Sidebar (avatar chữ "D", chữ
 * "DATN Workspace", ChevronDown trang trí đang trơ, và "Free Plan"). Block đó
 * vốn đã có đúng hình dạng của một switcher trigger.
 *
 * Pattern dropdown (containerRef + listener mousedown ngoài vùng) copy từ
 * MemberSelect.jsx để hành vi nhất quán với phần còn lại của app.
 */
export default function WorkspaceSwitcher() {
  const { workspaces, currentWorkspace, currentWorkspaceId, openCreateWorkspace } = useWorkspace();
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!open) return;
    const onMouseDown = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onMouseDown);
    return () => document.removeEventListener('mousedown', onMouseDown);
  }, [open]);

  const name = currentWorkspace?.name || 'Chọn workspace';
  const initial = (currentWorkspace?.name || '?').trim().charAt(0).toUpperCase();

  const select = (id) => {
    setOpen(false);
    if (id === currentWorkspaceId) return;
    // Chỉ cần điều hướng: param workspaceId đổi sẽ tự kéo theo WorkspaceLayout
    // fetch lại team và Outlet remount. Không refetch thủ công, và nút Back quay
    // về đúng workspace trước đó.
    navigate(wsPath(id, '/projects'));
  };

  return (
    <div ref={containerRef} style={{ position: 'relative', padding: '12px 12px 8px' }}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          width: '100%',
          padding: 8,
          background: open ? '#F1F5F9' : 'transparent',
          border: '1px solid #E2E8F0',
          borderRadius: 8,
          cursor: 'pointer',
          textAlign: 'left',
        }}
      >
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 8,
            background: '#2563EB',
            color: '#FFFFFF',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            fontSize: 14,
            flexShrink: 0,
          }}
        >
          {initial}
        </div>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: '#0F172A',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {name}
          </div>
          <div style={{ marginTop: 2 }}>
            <RoleBadge role={currentWorkspace?.myRole} />
          </div>
        </div>
        <ChevronDown size={16} color="#94A3B8" style={{ flexShrink: 0 }} />
      </button>

      {open && (
        <div
          style={{
            position: 'absolute',
            top: '100%',
            left: 12,
            right: 12,
            zIndex: 40,
            marginTop: 4,
            background: '#FFFFFF',
            border: '1px solid #E2E8F0',
            borderRadius: 10,
            boxShadow: '0 10px 30px rgba(15, 23, 42, 0.12)',
            overflow: 'hidden',
          }}
        >
          <div style={{ maxHeight: 260, overflowY: 'auto', padding: 4 }}>
            {workspaces.length === 0 && (
              <div style={{ padding: 12, fontSize: 12, color: '#94A3B8' }}>
                Bạn chưa thuộc workspace nào
              </div>
            )}
            {workspaces.map((w) => (
              <button
                key={w.id}
                type="button"
                onClick={() => select(w.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  width: '100%',
                  padding: '8px 10px',
                  background: w.id === currentWorkspaceId ? '#EFF6FF' : 'transparent',
                  border: 'none',
                  borderRadius: 6,
                  cursor: 'pointer',
                  textAlign: 'left',
                }}
              >
                <Building2 size={14} color="#64748B" style={{ flexShrink: 0 }} />
                <span
                  style={{
                    flex: 1,
                    minWidth: 0,
                    fontSize: 13,
                    color: '#334155',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {w.name}
                </span>
                <RoleBadge role={w.myRole} />
                {w.id === currentWorkspaceId && <Check size={14} color="#2563EB" />}
              </button>
            ))}
          </div>

          <div style={{ borderTop: '1px solid #F1F5F9', padding: 4 }}>
            <button
              type="button"
              onClick={() => {
                setOpen(false);
                openCreateWorkspace();
              }}
              style={footerBtn}
            >
              <Plus size={14} color="#2563EB" />
              <span style={{ fontSize: 13, color: '#2563EB', fontWeight: 500 }}>
                Tạo workspace mới
              </span>
            </button>
            <button
              type="button"
              onClick={() => {
                setOpen(false);
                navigate('/workspaces');
              }}
              style={footerBtn}
            >
              <Compass size={14} color="#64748B" />
              <span style={{ fontSize: 13, color: '#475569' }}>Xem tất cả workspace</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
