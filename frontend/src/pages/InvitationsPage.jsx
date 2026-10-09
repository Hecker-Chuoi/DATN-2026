import { useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { Mail, Users, FolderKanban, Building2, Check, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { invitationApi } from '../api.js';
import { workspaceInvitationApi } from '../api/workspaceServiceApi';
import { useWorkspace } from '../context/WorkspaceContext';
import { wsPath } from '../utils/workspacePath';

const KIND = {
  TEAM: { label: 'Nhóm', Icon: Users, bg: '#EFF6FF', color: '#2563EB' },
  PROJECT: { label: 'Dự án', Icon: FolderKanban, bg: '#F0FDF4', color: '#16A34A' },
  WORKSPACE: { label: 'Workspace', Icon: Building2, bg: '#FEF3C7', color: '#D97706' },
};

/**
 * Lời mời tham gia workspace / nhóm / dự án đang chờ người dùng hiện tại trả lời.
 * Danh sách lấy từ WorkspaceLayout (context) để badge trên sidebar và trang này dùng chung một nguồn.
 */
export default function InvitationsPage() {
  const navigate = useNavigate();
  const { workspaceId } = useParams();
  const { refreshWorkspaces } = useWorkspace();
  const { invitations = [], refreshInvitations, refreshTeams } = useOutletContext() || {};
  const [busyId, setBusyId] = useState(null);

  const respond = async (inv, accept) => {
    const api = inv.type === 'WORKSPACE' ? workspaceInvitationApi : invitationApi;
    try {
      setBusyId(inv.id);
      const res = accept ? await api.accept(inv.id) : await api.decline(inv.id);
      toast.success(res?.message || (accept ? 'Đã tham gia' : 'Đã từ chối'));
      await refreshInvitations?.();
      if (!accept) return;
      if (inv.type === 'WORKSPACE') {
        await refreshWorkspaces();
        navigate(wsPath(inv.targetId, '/projects'));
      } else {
        await refreshTeams?.();
        navigate(wsPath(workspaceId, inv.type === 'TEAM' ? `/teams/${inv.targetId}` : `/projects/${inv.targetId}`));
      }
    } catch {
      // interceptor đã hiện toast lỗi
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ backgroundColor: '#FFFFFF', minHeight: '100%', display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={{ height: 48, minHeight: 48, padding: '0 24px', borderBottom: '1px solid #E2E8F0', display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
        <Mail size={16} color="#2563EB" />
        <span>Lời mời</span>
        {invitations.length > 0 && (
          <span style={{ fontSize: 11, fontWeight: 700, backgroundColor: '#DBEAFE', color: '#1D4ED8', padding: '1px 7px', borderRadius: 10 }}>{invitations.length}</span>
        )}
      </div>

      <div style={{ padding: 24, maxWidth: 760, display: 'flex', flexDirection: 'column', gap: 12 }}>
        {invitations.length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Bạn không có lời mời nào đang chờ. Khi được mời vào workspace, nhóm hoặc dự án, lời mời sẽ hiện ở đây.
          </div>
        ) : (
          invitations.map((inv) => {
            const kind = KIND[inv.type] || KIND.TEAM;
            const Icon = kind.Icon;
            const busy = busyId === inv.id;
            return (
              <div key={inv.id} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 16px', border: '1px solid #E2E8F0', borderRadius: 10, backgroundColor: '#F8FAFC' }}>
                <div style={{ width: 40, height: 40, borderRadius: 10, backgroundColor: kind.bg, color: kind.color, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Icon size={20} />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
                    {kind.label} <span style={{ color: '#2563EB' }}>{inv.targetName || '(không rõ tên)'}</span>
                  </div>
                  <div style={{ fontSize: 12, color: '#64748B', marginTop: 2 }}>
                    {inv.inviterName || 'Ai đó'} mời bạn với vai trò <b>{inv.roleName || 'MEMBER'}</b>
                    {inv.createdAt ? ` · ${new Date(inv.createdAt).toLocaleString('vi-VN')}` : ''}
                  </div>
                </div>
                <button
                  onClick={() => respond(inv, true)}
                  disabled={busy}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 5, backgroundColor: '#2563EB', color: '#FFF', border: 'none', borderRadius: 8, padding: '7px 12px', fontSize: 13, fontWeight: 600, cursor: busy ? 'not-allowed' : 'pointer' }}
                >
                  <Check size={14} /> Chấp nhận
                </button>
                <button
                  onClick={() => respond(inv, false)}
                  disabled={busy}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 5, backgroundColor: '#FFF', color: '#475569', border: '1px solid #CBD5E1', borderRadius: 8, padding: '7px 12px', fontSize: 13, fontWeight: 600, cursor: busy ? 'not-allowed' : 'pointer' }}
                >
                  <X size={14} /> Từ chối
                </button>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
