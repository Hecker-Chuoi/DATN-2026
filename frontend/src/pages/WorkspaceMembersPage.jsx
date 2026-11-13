import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, CheckCircle2, LogOut, Mail, Search, Trash2, UserPlus, Users } from 'lucide-react';
import toast from 'react-hot-toast';
import { workspaceApi } from '../api/workspaceServiceApi';
import { userApi } from '../api';
import { useWorkspace } from '../context/WorkspaceContext';
import { useWorkspaceRole } from '../hooks/useWorkspaceRole';
import { ALL_ROLES, roleLabel } from '../utils/workspaceRole';
import RoleBadge from '../components/workspace/RoleBadge';
import PendingInvitations from '../components/common/PendingInvitations';
import CustomSelect from '../components/common/CustomSelect';

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

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Tra người dùng theo email, chỉ chấp nhận khi email trùng khớp 100%
 * (không phân biệt hoa thường). Ưu tiên endpoint by-email, lỗi thì
 * fallback sang search rồi tự lọc khớp tuyệt đối.
 */
async function findUserByExactEmail(email) {
  const target = email.toLowerCase();
  const isExact = (u) => u?.email && u.email.trim().toLowerCase() === target;

  try {
    const res = await userApi.getByEmail(email, { skipErrorToast: true });
    if (isExact(res?.data)) return res.data;
  } catch {
    // thử fallback bên dưới
  }
  try {
    const res = await userApi.search(email, { skipErrorToast: true });
    const list = Array.isArray(res?.data) ? res.data : [];
    return list.find(isExact) || null;
  } catch {
    return null;
  }
}

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

  // Mời 2 bước: tìm đúng email -> xác nhận người dùng -> chọn vai trò & gửi lời mời.
  const [searchEmail, setSearchEmail] = useState('');
  const [searching, setSearching] = useState(false);
  const [foundUser, setFoundUser] = useState(null);
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

  const resetInvite = () => {
    setSearchEmail('');
    setFoundUser(null);
    setNewRole('MEMBER');
    setAddError('');
  };

  const handleSearch = async () => {
    const email = searchEmail.trim();
    if (!email) {
      setAddError('Nhập email của người cần mời');
      return;
    }
    if (!EMAIL_REGEX.test(email)) {
      setAddError('Định dạng email không hợp lệ. Vui lòng nhập đúng email (ví dụ: user@example.com)');
      return;
    }
    try {
      setSearching(true);
      setAddError('');
      setFoundUser(null);
      const user = await findUserByExactEmail(email);
      if (!user) {
        setAddError('Không tìm thấy người dùng nào có email này. Vui lòng kiểm tra lại chính xác từng ký tự.');
        return;
      }
      if (members.some((m) => m.userId === user.id)) {
        setAddError('Người dùng này đã là thành viên của không gian làm việc.');
        return;
      }
      setFoundUser(user);
    } finally {
      setSearching(false);
    }
  };

  const handleInvite = async () => {
    if (!foundUser) return;
    try {
      setAdding(true);
      setAddError('');
      // Backend tạo lời mời, chưa thêm vào bảng thành viên: chỉ cần reload block lời mời chờ.
      await workspaceApi.addMember(workspaceId, { identifier: foundUser.email, role: newRole });
      toast.success('Đã gửi lời mời. Người được mời cần chấp nhận để trở thành thành viên.');
      resetInvite();
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
            onSubmit={(e) => {
              e.preventDefault();
              if (foundUser) handleInvite();
              else handleSearch();
            }}
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
              <div style={{ position: 'relative', flex: 1, minWidth: 220 }}>
                <Mail
                  size={15}
                  color="#94A3B8"
                  style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
                />
                <input
                  type="email"
                  value={searchEmail}
                  onChange={(e) => {
                    setSearchEmail(e.target.value);
                    if (addError) setAddError('');
                    if (foundUser) setFoundUser(null);
                  }}
                  placeholder="Nhập chính xác email (ví dụ: user@example.com)"
                  style={{ ...inputStyle, width: '100%', paddingLeft: 32, boxSizing: 'border-box' }}
                />
              </div>
              <button
                type="button"
                onClick={handleSearch}
                disabled={searching || !searchEmail.trim()}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  background: searching || !searchEmail.trim() ? '#94A3B8' : '#2563EB',
                  color: '#FFFFFF',
                  border: 'none',
                  padding: '8px 16px',
                  borderRadius: 6,
                  fontSize: 13,
                  fontWeight: 600,
                  whiteSpace: 'nowrap',
                  cursor: searching || !searchEmail.trim() ? 'not-allowed' : 'pointer',
                }}
              >
                <Search size={14} />
                <span>{searching ? 'Đang tìm...' : 'Tìm kiếm'}</span>
              </button>
            </div>
            <div style={{ fontSize: 11, color: '#64748B', marginTop: 6 }}>
              Chỉ người dùng có email trùng khớp 100% trong hệ thống mới được tìm thấy.
            </div>

            {foundUser && (
              <div
                style={{
                  marginTop: 12,
                  padding: '12px 14px',
                  borderRadius: 10,
                  background: '#F0FDF4',
                  border: '1px solid #86EFAC',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  flexWrap: 'wrap',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, flex: 1, minWidth: 220 }}>
                  <div
                    style={{
                      width: 38,
                      height: 38,
                      borderRadius: '50%',
                      background: '#2563EB',
                      color: '#FFFFFF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: 15,
                      flexShrink: 0,
                    }}
                  >
                    {(foundUser.fullName || foundUser.username || foundUser.email || 'U').charAt(0).toUpperCase()}
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                      <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
                        {foundUser.fullName || foundUser.username || foundUser.email}
                      </span>
                      {foundUser.username && foundUser.username !== foundUser.email && (
                        <span style={{ fontSize: 12, color: '#64748B' }}>@{foundUser.username}</span>
                      )}
                    </div>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 5,
                        fontSize: 12,
                        color: '#15803D',
                        marginTop: 2,
                        wordBreak: 'break-all',
                      }}
                    >
                      <CheckCircle2 size={13} style={{ flexShrink: 0 }} />
                      <span>{foundUser.email}</span>
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <CustomSelect
                    value={newRole}
                    onChange={(val) => setNewRole(val)}
                    options={ALL_ROLES.map((r) => ({
                      value: r,
                      label: roleLabel(r),
                      disabled: r === 'OWNER' && !isOwner,
                    }))}
                    width={150}
                  />
                  <button
                    type="submit"
                    disabled={adding}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      background: adding ? '#94A3B8' : '#2563EB',
                      color: '#FFFFFF',
                      border: 'none',
                      padding: '8px 16px',
                      borderRadius: 6,
                      fontSize: 13,
                      fontWeight: 600,
                      whiteSpace: 'nowrap',
                      cursor: adding ? 'not-allowed' : 'pointer',
                    }}
                  >
                    <UserPlus size={14} />
                    <span>{adding ? 'Đang gửi...' : 'Gửi lời mời'}</span>
                  </button>
                  <button
                    type="button"
                    onClick={resetInvite}
                    title="Nhập email khác"
                    style={{
                      background: '#FFFFFF',
                      border: '1px solid #CBD5E1',
                      borderRadius: 6,
                      color: '#475569',
                      padding: '7px 12px',
                      fontSize: 12,
                      fontWeight: 600,
                      whiteSpace: 'nowrap',
                      cursor: 'pointer',
                    }}
                  >
                    Đổi email
                  </button>
                </div>
              </div>
            )}

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
                            <CustomSelect
                              value={m.role}
                              onChange={(val) => handleRoleChange(m.userId, val)}
                              options={ALL_ROLES.map((r) => ({
                                value: r,
                                label: roleLabel(r),
                                disabled: (isThisLastOwner && r !== 'OWNER') || ((r === 'OWNER' || m.role === 'OWNER') && !isOwner),
                              }))}
                              width={140}
                            />
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
