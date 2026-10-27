import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, LogOut, Trash2, UserPlus, Users } from 'lucide-react';
import toast from 'react-hot-toast';
import { workspaceApi } from '../api/workspaceServiceApi';
import { userApi } from '../api';
import { useWorkspace } from '../context/WorkspaceContext';
import { useWorkspaceRole } from '../hooks/useWorkspaceRole';
import { ALL_ROLES, roleLabel } from '../utils/workspaceRole';
import RoleBadge from '../components/workspace/RoleBadge';
import PendingInvitations from '../components/common/PendingInvitations';

const inputStyle = {
  padding: '8px 12px',
  borderRadius: 6,
  border: '1px solid #CBD5E1',
  fontSize: 13,
  color: '#0F172A',
  outline: 'none',
};

const thStyle = {
  textAlign: 'left',
  fontSize: 11,
  fontWeight: 600,
  color: '#94A3B8',
  textTransform: 'uppercase',
  letterSpacing: 0.4,
  padding: '10px 12px',
  borderBottom: '1px solid #E2E8F0',
};

const tdStyle = {
  padding: '12px',
  fontSize: 13,
  color: '#334155',
  borderBottom: '1px solid #F1F5F9',
};

const dangerHint = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '10px 14px',
  borderRadius: 8,
  background: '#FEF2F2',
  border: '1px solid #FEE2E2',
  color: '#DC2626',
  fontSize: 12,
};

/**
 * Route /w/:workspaceId/members - quản trị thành viên workspace.
 *
 * Bảo vệ OWNER cuối cùng được tính NGAY TẠI CLIENT từ danh sách thành viên đã
 * có (không cần endpoint mới): backend vẫn là nơi thực thi, nhưng UI phải
 * disable kèm lý do nhìn thấy được, chứ không để người dùng bấm rồi nhận một
 * toast 400 không có ngữ cảnh.
 *
 * Quy ước gate: ẩn control mà vai trò không bao giờ dùng được; disable + giải
 * thích control mà vai trò thường có nhưng không dùng được trong trường hợp này.
 */
export default function WorkspaceMembersPage() {
  const { workspaceId } = useParams();
  const { currentWorkspace, refreshWorkspaces } = useWorkspace();
  const { canManageMembers, isOwner, loading: roleLoading } = useWorkspaceRole();
  const navigate = useNavigate();

  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [myUserId, setMyUserId] = useState(null);

  const [identifier, setIdentifier] = useState('');
  const [newRole, setNewRole] = useState('MEMBER');
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState('');
  const [inviteTick, setInviteTick] = useState(0);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const res = await workspaceApi.getMembers(workspaceId, { size: 200 });
      setMembers(res?.data?.content || []);
      setError('');
    } catch (err) {
      setError(err.body?.message || err.message);
      setMembers([]);
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    load();
  }, [load]);

  // Cần biết "tôi là ai" để nhận ra hàng của chính mình trong bảng.
  useEffect(() => {
    let cancelled = false;
    workspaceApi
      .getMyMembership(workspaceId)
      .then((res) => {
        if (!cancelled) setMyUserId(res?.data?.userId || null);
      })
      .catch(() => {
        if (!cancelled) {
          userApi
            .me()
            .then((res) => {
              if (!cancelled) setMyUserId(res?.data?.id || null);
            })
            .catch(() => {});
        }
      });
    return () => {
      cancelled = true;
    };
  }, [workspaceId]);

  const owners = useMemo(() => members.filter((m) => m.role === 'OWNER'), [members]);
  const isLastOwner = owners.length === 1;
  const iAmLastOwner = isLastOwner && myUserId && owners[0]?.userId === myUserId;

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!identifier.trim()) {
      setAddError('Nhập email hoặc tên đăng nhập của người cần mời');
      return;
    }
    try {
      setAdding(true);
      setAddError('');
      // Backend tạo lời mời, chưa thêm vào bảng thành viên: chỉ cần reload block lời mời chờ.
      await workspaceApi.addMember(workspaceId, { identifier: identifier.trim(), role: newRole });
      toast.success('Đã gửi lời mời. Người được mời cần chấp nhận để trở thành thành viên.');
      setIdentifier('');
      setNewRole('MEMBER');
      setInviteTick((t) => t + 1);
    } catch (err) {
      // Interceptor của api.js đã toast lỗi; ở đây chỉ hiển thị inline để không
      // bị hai toast trùng nhau.
      setAddError(err.body?.message || err.message);
    } finally {
      setAdding(false);
    }
  };

  const handleRoleChange = async (userId, role) => {
    try {
      await workspaceApi.updateMemberRole(workspaceId, userId, role);
      toast.success('Đã cập nhật vai trò');
      await load();
      await refreshWorkspaces();
    } catch {
      await load();
    }
  };

  const handleRemove = async (member) => {
    const label = member.fullName || member.username || member.userId;
    if (!window.confirm(`Xóa ${label} khỏi không gian làm việc này?`)) return;
    try {
      await workspaceApi.removeMember(workspaceId, member.userId);
      toast.success('Đã xóa thành viên');
      await load();
      await refreshWorkspaces();
    } catch {
      await load();
    }
  };

  const handleLeave = async () => {
    if (!window.confirm('Rời khỏi không gian làm việc này?')) return;
    try {
      await workspaceApi.leave(workspaceId);
      toast.success('Đã rời không gian làm việc');
      await refreshWorkspaces();
      navigate('/workspaces');
    } catch {
      await load();
    }
  };

  return (
    <div
      style={{
        flex: 1,
        overflowY: 'auto',
        backgroundColor: '#FFFFFF',
        minHeight: '100%',
        width: '100%',
      }}
    >
      <div
        style={{
          maxWidth: 1100,
          width: '100%',
          margin: '0 auto',
          padding: '32px 40px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <Users size={20} color="#2563EB" />
          <h1 style={{ fontSize: 20, fontWeight: 700, color: '#0F172A', margin: 0 }}>
            Thành viên không gian làm việc
          </h1>
        </div>
        <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 20px' }}>
          {currentWorkspace?.name
            ? `Quản lý ai được truy cập ${currentWorkspace.name} và với vai trò gì.`
            : 'Quản lý ai được truy cập không gian làm việc này và với vai trò gì.'}
        </p>

        {canManageMembers && (
          <form
            onSubmit={handleAdd}
            style={{
              background: '#FFFFFF',
              border: '1px solid #E2E8F0',
              borderRadius: 10,
              padding: 16,
              marginBottom: 20,
            }}
          >
            <div style={{ fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 10 }}>
              Mời thành viên
            </div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-start' }}>
              <input
                type="text"
                value={identifier}
                onChange={(e) => {
                  setIdentifier(e.target.value);
                  if (addError) setAddError('');
                }}
                placeholder="Email hoặc tên đăng nhập"
                style={{ ...inputStyle, flex: 1, minWidth: 220 }}
              />
              <select
                value={newRole}
                onChange={(e) => setNewRole(e.target.value)}
                style={{ ...inputStyle, minWidth: 140 }}
              >
                {ALL_ROLES.map((r) => (
                  // Chỉ OWNER được cấp vai trò OWNER (backend cũng chặn).
                  <option key={r} value={r} disabled={r === 'OWNER' && !isOwner}>
                    {roleLabel(r)}
                  </option>
                ))}
              </select>
              <button
                type="submit"
                disabled={adding || !identifier.trim()}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  background: adding || !identifier.trim() ? '#94A3B8' : '#2563EB',
                  color: '#FFFFFF',
                  border: 'none',
                  padding: '8px 16px',
                  borderRadius: 6,
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: adding || !identifier.trim() ? 'not-allowed' : 'pointer',
                }}
              >
                <UserPlus size={14} />
                <span>{adding ? 'Đang gửi...' : 'Mời'}</span>
              </button>
            </div>
            {addError && (
              <div style={{ ...dangerHint, marginTop: 10 }}>
                <AlertCircle size={14} />
                <span>{addError}</span>
              </div>
            )}
          </form>
        )}

        <PendingInvitations workspaceId={workspaceId} canManage={canManageMembers} refreshKey={inviteTick} />

        <div
          style={{
            background: '#FFFFFF',
            border: '1px solid #E2E8F0',
            borderRadius: 10,
            overflow: 'hidden',
          }}
        >
          {loading && <div style={{ padding: 16, fontSize: 13, color: '#94A3B8' }}>Đang tải...</div>}
          {!loading && error && (
            <div style={{ padding: 16, fontSize: 13, color: '#DC2626' }}>{error}</div>
          )}
          {!loading && !error && (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 600 }}>
                <thead>
                  <tr>
                    <th style={thStyle}>Thành viên</th>
                    <th style={thStyle}>Email</th>
                    <th style={thStyle}>Vai trò</th>
                    <th style={{ ...thStyle, textAlign: 'right' }} />
                  </tr>
                </thead>
                <tbody>
                  {members.map((m) => {
                    const isMe = myUserId && m.userId === myUserId;
                    const isThisLastOwner = m.role === 'OWNER' && isLastOwner;
                    return (
                      <tr key={m.id}>
                        <td style={tdStyle}>
                          <div style={{ fontWeight: 600, color: '#0F172A' }}>
                            {m.fullName || m.username || m.userId}
                            {isMe && (
                              <span style={{ marginLeft: 6, fontSize: 11, color: '#94A3B8' }}>
                                (bạn)
                              </span>
                            )}
                          </div>
                          {m.username && m.fullName && (
                            <div style={{ fontSize: 11, color: '#94A3B8' }}>@{m.username}</div>
                          )}
                        </td>
                        <td style={{ ...tdStyle, color: '#64748B' }}>{m.email || '—'}</td>
                        <td style={tdStyle}>
                          {canManageMembers && !isMe ? (
                            <select
                              value={m.role}
                              onChange={(e) => handleRoleChange(m.userId, e.target.value)}
                              style={{ ...inputStyle, padding: '5px 8px', fontSize: 12 }}
                            >
                              {ALL_ROLES.map((r) => (
                                <option
                                  key={r}
                                  value={r}
                                  // Hạ OWNER cuối cùng là bất khả: để nguyên OWNER
                                  // là lựa chọn duy nhất còn bật, thay vì disable cả
                                  // select (giá trị hiện tại vẫn cần đọc được).
                                  disabled={
                                    (isThisLastOwner && r !== 'OWNER') ||
                                    ((r === 'OWNER' || m.role === 'OWNER') && !isOwner)
                                  }
                                >
                                  {roleLabel(r)}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <RoleBadge role={m.role} size="md" />
                          )}
                        </td>
                        <td style={{ ...tdStyle, textAlign: 'right' }}>
                          {/* Xóa OWNER cuối cùng không bao giờ hợp lệ -> bỏ hẳn nút. */}
                          {canManageMembers && !isMe && !isThisLastOwner && (
                            <button
                              type="button"
                              onClick={() => handleRemove(m)}
                              title="Xóa khỏi không gian làm việc"
                              style={{
                                background: 'transparent',
                                border: 'none',
                                cursor: 'pointer',
                                color: '#EF4444',
                                padding: 4,
                              }}
                            >
                              <Trash2 size={15} />
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                  {members.length === 0 && (
                    <tr>
                      <td colSpan={4} style={{ ...tdStyle, color: '#94A3B8', textAlign: 'center' }}>
                        Chưa có thành viên nào
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {!roleLoading && (
          <div style={{ marginTop: 20 }}>
            {iAmLastOwner && (
              <div style={{ ...dangerHint, marginBottom: 10 }}>
                <AlertCircle size={14} />
                <span>
                  Bạn là Chủ sở hữu duy nhất. Hãy chuyển quyền Chủ sở hữu cho thành viên khác trước khi rời
                  không gian làm việc.
                </span>
              </div>
            )}
            <button
              type="button"
              onClick={handleLeave}
              disabled={!!iAmLastOwner}
              title={
                iAmLastOwner
                  ? 'Bạn là Chủ sở hữu duy nhất của không gian làm việc này'
                  : 'Rời khỏi không gian làm việc này'
              }
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                background: iAmLastOwner ? '#F1F5F9' : '#FEF2F2',
                color: iAmLastOwner ? '#94A3B8' : '#DC2626',
                border: `1px solid ${iAmLastOwner ? '#E2E8F0' : '#FEE2E2'}`,
                padding: '8px 14px',
                borderRadius: 6,
                fontSize: 13,
                fontWeight: 600,
                cursor: iAmLastOwner ? 'not-allowed' : 'pointer',
              }}
            >
              <LogOut size={14} />
              <span>Rời không gian làm việc</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
