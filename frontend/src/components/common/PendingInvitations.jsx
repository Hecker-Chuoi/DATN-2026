import { useEffect, useState } from 'react';
import { Mail, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { invitationApi } from '../../api.js';
import { workspaceInvitationApi } from '../../api/workspaceServiceApi';

/**
 * Danh sách lời mời đang chờ của một workspace (workspaceId), nhóm (teamId) hoặc dự án (projectId), kèm nút hủy.
 * Chỉ người quản trị gọi được API này, nên chỉ render khi canManage.
 * refreshKey: đổi giá trị để tải lại (vd. sau khi gửi lời mời mới).
 */
export default function PendingInvitations({ workspaceId, teamId, projectId, canManage = false, refreshKey = 0 }) {
  const [items, setItems] = useState([]);
  const [busyId, setBusyId] = useState(null);
  const api = workspaceId ? workspaceInvitationApi : invitationApi;

  const load = async () => {
    try {
      const res = workspaceId
        ? await workspaceInvitationApi.forWorkspace(workspaceId)
        : teamId ? await invitationApi.forTeam(teamId) : await invitationApi.forProject(projectId);
      setItems(Array.isArray(res?.data) ? res.data : []);
    } catch {
      setItems([]);
    }
  };

  useEffect(() => {
    if (canManage && (workspaceId || teamId || projectId)) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId, teamId, projectId, canManage, refreshKey]);

  const cancel = async (inv) => {
    try {
      setBusyId(inv.id);
      await api.cancel(inv.id);
      toast.success('Đã hủy lời mời');
      load();
    } catch {
      // interceptor đã hiện toast lỗi
    } finally {
      setBusyId(null);
    }
  };

  if (!canManage || items.length === 0) return null;

  return (
    <div style={{ margin: '12px 0 16px', padding: '12px 14px', border: '1px dashed #FCD34D', backgroundColor: '#FFFBEB', borderRadius: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700, color: '#92400E', marginBottom: 8 }}>
        <Mail size={14} /> Lời mời đang chờ ({items.length})
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {items.map((inv) => (
          <div key={inv.id} style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 13, color: '#1F2937' }}>
            <span style={{ flex: 1 }}>
              <b>{inv.inviteeName || inv.inviteeEmail || inv.inviteeUserId}</b>
              {inv.inviteeEmail && inv.inviteeName ? ` (${inv.inviteeEmail})` : ''}
              {' · '}vai trò <b>{inv.roleName || inv.role || 'MEMBER'}</b>
              {inv.inviterName ? ` · mời bởi ${inv.inviterName}` : ''}
            </span>
            <button
              onClick={() => cancel(inv)}
              disabled={busyId === inv.id}
              title="Hủy lời mời"
              style={{ display: 'inline-flex', alignItems: 'center', gap: 4, border: '1px solid #FCA5A5', backgroundColor: '#FFF', color: '#B91C1C', borderRadius: 6, padding: '3px 8px', fontSize: 12, cursor: 'pointer' }}
            >
              <X size={12} /> Hủy
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
