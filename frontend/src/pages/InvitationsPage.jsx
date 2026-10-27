import { useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { Mail, FolderKanban, Building2, Check, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { invitationApi } from '../api.js';
import { workspaceInvitationApi } from '../api/workspaceServiceApi';
import { getRoleDisplayName } from '../constants/workConstants.js';
import { useWorkspace } from '../context/WorkspaceContext';
import { wsPath } from '../utils/workspacePath';

const KIND = {
  PROJECT: { label: 'Dự án', Icon: FolderKanban, bg: '#F0FDF4', color: '#16A34A' },
  WORKSPACE: { label: 'Không gian làm việc', Icon: Building2, bg: '#FEF3C7', color: '#D97706' },
};

/**
 * Lời mời tham gia workspace / dự án đang chờ người dùng hiện tại trả lời.
 * Danh sách lấy từ WorkspaceLayout (context) để badge trên sidebar và trang này dùng chung một nguồn.
 */
export default function InvitationsPage() {
  const navigate = useNavigate();
  const { workspaceId } = useParams();
  const { refreshWorkspaces } = useWorkspace();
  const { invitations = [], refreshInvitations, refreshProjects } = useOutletContext() || {};
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
        await refreshProjects?.();
        navigate(wsPath(workspaceId, `/projects/${inv.targetId}`));
      }
    } catch {
      // interceptor đã hiện toast lỗi
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ backgroundColor: '#FFFFFF', minHeight: '100%', display: 'flex', flexDirection: 'column', flex: 1, overflowY: 'auto' }}>
      <div style={{ height: 52, minHeight: 52, padding: '0 32px', borderBottom: '1px solid #E2E8F0', display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
        <Mail size={16} color="#2563EB" />
        <span>Lời mời</span>
        {invitations.length > 0 && (
          <span style={{ fontSize: 11, fontWeight: 700, backgroundColor: '#DBEAFE', color: '#1D4ED8', padding: '1px 7px', borderRadius: 10 }}>{invitations.length}</span>
        )}
      </div>

      <div style={{ width: '100%', maxWidth: 1100, margin: '0 auto', padding: '32px 40px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {invitations.length === 0 ? (
          <div
            style={{
              padding: '64px 32px',
              textAlign: 'center',
              backgroundColor: '#F8FAFC',
              borderRadius: 12,
              border: '1px dashed #CBD5E1',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 12,
              margin: '16px 0',
            }}
          >
            <div
              style={{
                width: 52,
                height: 52,
                borderRadius: 12,
                backgroundColor: '#EFF6FF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#2563EB',
              }}
            >
              <Mail size={24} />
            </div>
            <div>
              <div style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', marginBottom: 4 }}>
                Bạn không có lời mời nào đang chờ
              </div>
              <div style={{ fontSize: 13, color: '#64748B', maxWidth: 440, lineHeight: 1.5 }}>
                Khi được mời vào không gian làm việc hoặc dự án, lời mời sẽ hiển thị tại đây để bạn phản hồi.
              </div>
            </div>
          </div>
        ) : (
          invitations.map((inv) => {
            const kind = KIND[inv.type] || KIND.PROJECT;
            const Icon = kind.Icon;
            const busy = busyId === inv.id;
            return (
              <div
                key={inv.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 16,
                  padding: '16px 20px',
                  border: '1px solid #E2E8F0',
                  borderRadius: 10,
                  backgroundColor: '#FFFFFF',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                }}
              >
                <div
                  style={{
                    width: 42,
                    height: 42,
                    borderRadius: 10,
                    backgroundColor: kind.bg,
                    color: kind.color,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  <Icon size={20} />
                </div>

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <span
                      style={{
                        fontSize: 11,
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        padding: '2px 8px',
                        borderRadius: 4,
                        backgroundColor: kind.bg,
                        color: kind.color,
                        letterSpacing: '0.04em',
                      }}
                    >
                      {kind.label}
                    </span>
                    <span style={{ fontSize: 15, fontWeight: 700, color: '#0F172A' }}>
                      {inv.targetName || 'Mục tiêu'}
                    </span>
                  </div>

                  <div style={{ fontSize: 13, color: '#475569' }}>
                    {inv.inviterName ? (
                      <>
                        <b style={{ color: '#0F172A' }}>{inv.inviterName}</b> đã mời bạn tham gia với vai trò{' '}
                      </>
                    ) : (
                      'Bạn được mời tham gia với vai trò '
                    )}
                    <b style={{ color: '#0F172A' }}>{inv.roleName || getRoleDisplayName(inv.role) || 'Thành viên'}</b>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <button
                    onClick={() => respond(inv, false)}
                    disabled={busy}
                    style={{
                      height: 36,
                      padding: '0 14px',
                      borderRadius: 8,
                      border: '1px solid #E2E8F0',
                      backgroundColor: '#FFFFFF',
                      color: '#64748B',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: busy ? 'not-allowed' : 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                    }}
                  >
                    <X size={15} /> Từ chối
                  </button>
                  <button
                    onClick={() => respond(inv, true)}
                    disabled={busy}
                    style={{
                      height: 36,
                      padding: '0 16px',
                      borderRadius: 8,
                      border: 'none',
                      backgroundColor: '#2563EB',
                      color: '#FFFFFF',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: busy ? 'not-allowed' : 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                    }}
                  >
                    <Check size={15} /> Chấp nhận
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
