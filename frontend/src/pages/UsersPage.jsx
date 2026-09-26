import { useState, useEffect, useCallback } from 'react';
import { userApi } from '../api.js';
import toast from 'react-hot-toast';

/**
 * UsersPage – Admin dashboard
 * GET via Gateway → identity-service (JWT + ADMIN role required)
 * Path: /api/identity/v1/admin/users
 *
 * Gateway validates JWT → strips prefix → forwards to identity-service
 * identity-service checks @PreAuthorize("hasRole('ADMIN')")
 */
export default function UsersPage({ currentUser, onLogout }) {
  const [users,   setUsers]   = useState([]);
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState('');
  const [search,  setSearch]  = useState('');
  const [fetched, setFetched] = useState(false);

  // Add User states
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createUsername, setCreateUsername] = useState('');
  const [createPassword, setCreatePassword] = useState('');

  // Reset Password states
  const [showResetModal, setShowResetModal] = useState(false);
  const [resetUserId, setResetUserId] = useState('');
  const [resetUsername, setResetUsername] = useState('');
  const [resetPasswordVal, setResetPasswordVal] = useState('');

  const [actionLoading, setActionLoading] = useState(false);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      // ApiResponse<UserResponse[]>
      const res = await userApi.listAll();
      setUsers(res.data || []);
      setFetched(true);
    } catch (err) {
      if (err.status === 403) {
        setError('403 Forbidden – Bạn cần có vai trò Quản trị viên để truy cập trang này.');
      } else if (err.status === 401) {
        setError('401 Unauthorized – Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      } else {
        setError(err.message || 'Không thể tải danh sách người dùng.');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  // Handlers for user actions
  const handleCreateUser = async () => {
    if (!createUsername.trim()) {
      toast.error('Vui lòng nhập tên đăng nhập');
      return;
    }
    if (!createPassword || createPassword.length < 6) {
      toast.error('Mật khẩu phải có ít nhất 6 ký tự');
      return;
    }
    setActionLoading(true);
    try {
      await userApi.adminCreate(createUsername.trim(), createPassword);
      toast.success('Tạo người dùng mới thành công');
      setShowCreateModal(false);
      setCreateUsername('');
      setCreatePassword('');
      fetchUsers();
    } catch (err) {
      toast.error(err.message || 'Không thể tạo người dùng');
    } finally {
      setActionLoading(false);
    }
  };

  const handleOpenReset = (u) => {
    setResetUserId(u.id);
    setResetUsername(u.username);
    setShowResetModal(true);
  };

  const handleResetPassword = async () => {
    if (!resetPasswordVal || resetPasswordVal.length < 6) {
      toast.error('Mật khẩu phải có ít nhất 6 ký tự');
      return;
    }
    setActionLoading(true);
    try {
      await userApi.resetPassword(resetUserId, resetPasswordVal);
      toast.success(`Đã đặt lại mật khẩu cho ${resetUsername} thành công`);
      setShowResetModal(false);
      setResetPasswordVal('');
      setResetUserId('');
      setResetUsername('');
    } catch (err) {
      toast.error(err.message || 'Không thể đặt lại mật khẩu');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDelete = async (u) => {
    if (window.confirm(`Bạn có chắc chắn muốn xóa người dùng "${u.username}" không?\nTài khoản này sẽ bị khóa trên hệ thống và thu hồi quyền trong các dự án/nhiệm vụ liên quan.`)) {
      setActionLoading(true);
      try {
        await userApi.delete(u.id);
        toast.success(`Đã xóa người dùng "${u.username}" thành công`);
        fetchUsers();
      } catch (err) {
        toast.error(err.message || 'Không thể xóa người dùng');
      } finally {
        setActionLoading(false);
      }
    }
  };

  // Client-side search filter
  const filtered = users.filter((u) => {
    const q = search.toLowerCase();
    return (
      u.username?.toLowerCase().includes(q) ||
      u.email?.toLowerCase().includes(q)    ||
      u.firstName?.toLowerCase().includes(q)||
      u.lastName?.toLowerCase().includes(q)
    );
  });

  const roleColor = (role) => {
    const r = role?.toUpperCase();
    if (r === 'ADMIN') return 'badge-orange';
    if (r === 'USER')  return 'badge-blue';
    return 'badge-gray';
  };

  const statusColor = (s) => s === 'ACTIVE' ? 'badge-green' : 'badge-gray';

  const initials = (u) => {
    const fn = u.firstName?.[0] || '';
    const ln = u.lastName?.[0]  || '';
    return (fn + ln).toUpperCase() || u.username?.[0]?.toUpperCase() || '?';
  };

  const fmt = (iso) =>
    iso ? new Date(iso).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

  return (
    <div className="page">
      {/* ── Header ── */}
      <div className="page-header">
        <div>
          <h2 className="page-title">Quản Lý <span>Người Dùng</span></h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginTop: '0.25rem' }}>
            Đăng nhập với tài khoản <strong style={{ color: 'var(--accent)' }}>{currentUser?.username}</strong>
            {' · '}Xác thực phiên qua Cổng kết nối
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <button className="btn btn-ghost btn-sm" onClick={fetchUsers} disabled={loading}>
            {loading ? <span className="spinner" style={{borderTopColor:'var(--text)'}} /> : '↻'}
            {loading ? 'Đang tải...' : 'Làm mới'}
          </button>
          <button
            className="btn btn-sm"
            onClick={() => setShowCreateModal(true)}
            style={{
              background: 'var(--accent, #ea580c)',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
              padding: '0.375rem 0.75rem',
              fontSize: '0.8125rem',
              fontWeight: '600',
              cursor: 'pointer',
              fontFamily: 'inherit',
            }}
          >
            + Thêm người dùng
          </button>
          <button
            className="btn btn-sm"
            onClick={onLogout}
            style={{
              background: 'transparent',
              border: '1px solid #d6d3d1',
              color: '#78716c',
              borderRadius: '6px',
              padding: '0.375rem 0.75rem',
              fontSize: '0.8125rem',
              fontWeight: '500',
              cursor: 'pointer',
              transition: 'border-color 0.15s, color 0.15s',
              fontFamily: 'inherit',
            }}
            onMouseEnter={(e) => { e.currentTarget.style.borderColor = '#a8a29e'; e.currentTarget.style.color = '#1c1917'; }}
            onMouseLeave={(e) => { e.currentTarget.style.borderColor = '#d6d3d1'; e.currentTarget.style.color = '#78716c'; }}
          >
            Đăng xuất
          </button>
        </div>
      </div>

      {/* ── Request trace ── */}
      <div className="token-bar" style={{ marginBottom: '1.25rem' }}>
        <span style={{ color: 'var(--text-muted)', flexShrink: 0 }}>Cổng kết nối</span>
        <span>Hệ thống phân quyền & xác thực người dùng tập trung</span>
      </div>

      {/* ── Error ── */}
      {error && (
        <div className="alert alert-error" style={{ marginBottom: '1rem' }}>
          <span>⚠</span> {error}
        </div>
      )}

      {/* ── Table ── */}
      <div className="table-wrap">
        <div className="table-toolbar">
          <div style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
            {fetched ? (
              <>{filtered.length} trên {users.length} người dùng</>
            ) : ''}
          </div>
          <input
            className="search-input"
            type="text"
            placeholder="Tìm kiếm theo họ tên hoặc thư điện tử..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        {loading && !fetched ? (
          <div className="state-box">
            <div className="icon"><span className="spinner" style={{ width: 36, height: 36, borderWidth: 3 }} /></div>
            <p>Đang tải danh sách người dùng...</p>
          </div>
        ) : !error && filtered.length === 0 ? (
          <div className="state-box">
            <div className="icon">🔍</div>
            <p>{search ? 'Không tìm thấy người dùng phù hợp.' : 'Chưa có người dùng nào.'}</p>
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Người dùng</th>
                <th>Tên đăng nhập</th>
                <th>Vai trò</th>
                <th>Trạng thái</th>
                <th>Ngày tham gia</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((u) => (
                <tr key={u.id}>
                  <td>
                    <div className="user-cell">
                      <div className="avatar-sm">{initials(u)}</div>
                      <div>
                        <div className="user-name">{u.firstName} {u.lastName}</div>
                        <div className="user-email">{u.email}</div>
                      </div>
                    </div>
                  </td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                    {u.username}
                  </td>
                  <td>
                    <div className="roles-cell">
                      {(u.roles && u.roles.length > 0)
                        ? [...u.roles].map((r) => (
                            <span key={r} className={`badge ${roleColor(r)}`}>
                              {r === 'ADMIN' ? 'QUẢN TRỊ VIÊN' : r === 'MANAGER' ? 'QUẢN LÝ' : 'THÀNH VIÊN'}
                            </span>
                          ))
                        : <span className="badge badge-gray">—</span>
                      }
                    </div>
                  </td>
                  <td>
                    <span className={`badge ${statusColor(u.status)}`}>
                      {u.status === 'ACTIVE' ? 'Hoạt động' : u.status || '—'}
                    </span>
                  </td>
                  <td style={{ color: 'var(--text-muted)', fontSize: '0.82rem' }}>
                    {fmt(u.createdAt)}
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button
                        className="btn btn-ghost btn-sm"
                        style={{
                          color: '#0ea5e9',
                          padding: '2px 8px',
                          fontSize: '0.75rem',
                          fontWeight: '500',
                          border: '1px solid currentColor',
                          borderRadius: '4px',
                        }}
                        onClick={() => handleOpenReset(u)}
                      >
                        Đổi mật khẩu
                      </button>
                      <button
                        className="btn btn-ghost btn-sm"
                        style={{
                          color: '#ef4444',
                          padding: '2px 8px',
                          fontSize: '0.75rem',
                          fontWeight: '500',
                          border: '1px solid currentColor',
                          borderRadius: '4px',
                        }}
                        onClick={() => handleDelete(u)}
                      >
                        Xóa
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
 
      {/* ── Add User Modal ── */}
      {showCreateModal && (
        <div className="modal-backdrop" style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0, 0, 0, 0.6)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div className="modal-content" style={{
            background: '#1c1917',
            border: '1px solid #44403c',
            borderRadius: '12px',
            padding: '1.5rem',
            width: '100%',
            maxWidth: '400px',
            boxShadow: '0 10px 25px -5px rgba(0,0,0,0.5)',
          }}>
            <h3 style={{ margin: '0 0 1rem 0', color: '#fff', fontSize: '1.25rem', fontWeight: '600' }}>Thêm Người Dùng Mới</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.8125rem', color: '#a8a29e', marginBottom: '0.375rem' }}>Tên đăng nhập</label>
                <input
                  type="text"
                  className="search-input"
                  style={{ width: '100%', boxSizing: 'border-box', background: '#292524', border: '1px solid #44403c', borderRadius: '6px', padding: '0.5rem', color: '#fff' }}
                  value={createUsername}
                  onChange={(e) => setCreateUsername(e.target.value)}
                  placeholder="Nhập tên đăng nhập..."
                  disabled={actionLoading}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.8125rem', color: '#a8a29e', marginBottom: '0.375rem' }}>Mật khẩu</label>
                <input
                  type="password"
                  className="search-input"
                  style={{ width: '100%', boxSizing: 'border-box', background: '#292524', border: '1px solid #44403c', borderRadius: '6px', padding: '0.5rem', color: '#fff' }}
                  value={createPassword}
                  onChange={(e) => setCreatePassword(e.target.value)}
                  placeholder="Nhập mật khẩu (tối thiểu 6 ký tự)..."
                  disabled={actionLoading}
                />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
              <button
                className="btn btn-ghost btn-sm"
                onClick={() => { setShowCreateModal(false); setCreateUsername(''); setCreatePassword(''); }}
                disabled={actionLoading}
              >
                Hủy bỏ
              </button>
              <button
                className="btn btn-sm"
                style={{ background: 'var(--accent, #ea580c)', color: '#fff', border: 'none', borderRadius: '6px', padding: '0.375rem 0.75rem', cursor: 'pointer' }}
                onClick={handleCreateUser}
                disabled={actionLoading}
              >
                {actionLoading ? 'Đang tạo...' : 'Tạo người dùng'}
              </button>
            </div>
          </div>
        </div>
      )}
 
      {/* ── Reset Password Modal ── */}
      {showResetModal && (
        <div className="modal-backdrop" style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0, 0, 0, 0.6)',
          backdropFilter: 'blur(4px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div className="modal-content" style={{
            background: '#1c1917',
            border: '1px solid #44403c',
            borderRadius: '12px',
            padding: '1.5rem',
            width: '100%',
            maxWidth: '400px',
            boxShadow: '0 10px 25px -5px rgba(0,0,0,0.5)',
          }}>
            <h3 style={{ margin: '0 0 0.5rem 0', color: '#fff', fontSize: '1.25rem', fontWeight: '600' }}>Đặt Lại Mật Khẩu</h3>
            <p style={{ margin: '0 0 1rem 0', fontSize: '0.875rem', color: '#a8a29e' }}>
              Nhập mật khẩu mới cho tài khoản <strong style={{ color: 'var(--accent, #ea580c)' }}>{resetUsername}</strong>
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.8125rem', color: '#a8a29e', marginBottom: '0.375rem' }}>Mật khẩu mới</label>
                <input
                  type="password"
                  className="search-input"
                  style={{ width: '100%', boxSizing: 'border-box', background: '#292524', border: '1px solid #44403c', borderRadius: '6px', padding: '0.5rem', color: '#fff' }}
                  value={resetPasswordVal}
                  onChange={(e) => setResetPasswordVal(e.target.value)}
                  placeholder="Nhập mật khẩu mới (tối thiểu 6 ký tự)..."
                  disabled={actionLoading}
                />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
              <button
                className="btn btn-ghost btn-sm"
                onClick={() => { setShowResetModal(false); setResetPasswordVal(''); setResetUserId(''); setResetUsername(''); }}
                disabled={actionLoading}
              >
                Hủy bỏ
              </button>
              <button
                className="btn btn-sm"
                style={{ background: 'var(--accent, #ea580c)', color: '#fff', border: 'none', borderRadius: '6px', padding: '0.375rem 0.75rem', cursor: 'pointer' }}
                onClick={handleResetPassword}
                disabled={actionLoading}
              >
                {actionLoading ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
