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
        setError('403 Forbidden – You need ADMIN role to view this page. Gateway enforced the policy.');
      } else if (err.status === 401) {
        setError('401 Unauthorized – JWT missing or expired. Please log in again.');
      } else {
        setError(err.message || 'Failed to load users.');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  // Handlers for user actions
  const handleCreateUser = async () => {
    if (!createUsername.trim()) {
      toast.error('Username is required');
      return;
    }
    if (!createPassword || createPassword.length < 6) {
      toast.error('Password must be at least 6 characters');
      return;
    }
    setActionLoading(true);
    try {
      await userApi.adminCreate(createUsername.trim(), createPassword);
      toast.success('User created successfully');
      setShowCreateModal(false);
      setCreateUsername('');
      setCreatePassword('');
      fetchUsers();
    } catch (err) {
      toast.error(err.message || 'Failed to create user');
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
      toast.error('Password must be at least 6 characters');
      return;
    }
    setActionLoading(true);
    try {
      await userApi.resetPassword(resetUserId, resetPasswordVal);
      toast.success(`Password for ${resetUsername} reset successfully`);
      setShowResetModal(false);
      setResetPasswordVal('');
      setResetUserId('');
      setResetUsername('');
    } catch (err) {
      toast.error(err.message || 'Failed to reset password');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDelete = async (u) => {
    if (window.confirm(`Are you sure you want to delete user "${u.username}"?\nThis will disable their Keycloak account, mark them as suspended, and clean up team memberships/tasks.`)) {
      setActionLoading(true);
      try {
        await userApi.delete(u.id);
        toast.success(`User "${u.username}" deleted successfully`);
        fetchUsers();
      } catch (err) {
        toast.error(err.message || 'Failed to delete user');
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
          <h2 className="page-title">User <span>Management</span></h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', marginTop: '0.25rem' }}>
            Signed in as <strong style={{ color: 'var(--accent)' }}>{currentUser?.username}</strong>
            {' · '}JWT validated by Gateway · identity-service
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <button className="btn btn-ghost btn-sm" onClick={fetchUsers} disabled={loading}>
            {loading ? <span className="spinner" style={{borderTopColor:'var(--text)'}} /> : '↻'}
            {loading ? 'Refreshing…' : 'Refresh'}
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
            + Add User
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
            Sign out
          </button>
        </div>
      </div>

      {/* ── Request trace ── */}
      <div className="token-bar" style={{ marginBottom: '1.25rem' }}>
        <span style={{ color: 'var(--text-muted)', flexShrink: 0 }}>GET (Bearer)</span>
        <span>:8080/api/identity/v1/admin/users → Gateway validates JWT → :8082/v1/admin/users</span>
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
              <>{filtered.length} of {users.length} users</>
            ) : ''}
          </div>
          <input
            className="search-input"
            type="text"
            placeholder="Search by name or email…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        {loading && !fetched ? (
          <div className="state-box">
            <div className="icon"><span className="spinner" style={{ width: 36, height: 36, borderWidth: 3 }} /></div>
            <p>Loading users from identity-service…</p>
          </div>
        ) : !error && filtered.length === 0 ? (
          <div className="state-box">
            <div className="icon">🔍</div>
            <p>{search ? 'No users match your search.' : 'No users found.'}</p>
          </div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>User</th>
                <th>Username</th>
                <th>Roles</th>
                <th>Status</th>
                <th>Joined</th>
                <th>Actions</th>
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
                            <span key={r} className={`badge ${roleColor(r)}`}>{r}</span>
                          ))
                        : <span className="badge badge-gray">—</span>
                      }
                    </div>
                  </td>
                  <td>
                    <span className={`badge ${statusColor(u.status)}`}>
                      {u.status || '—'}
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
                          color: 'var(--accent, #ea580c)',
                          padding: '2px 8px',
                          fontSize: '0.75rem',
                          fontWeight: '500',
                          border: '1px solid currentColor',
                          borderRadius: '4px',
                        }}
                        onClick={() => alert('Feature incoming: Role mapping has not been finalized yet.')}
                      >
                        Edit Roles
                      </button>
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
                        Reset Pass
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
                        Delete
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
            <h3 style={{ margin: '0 0 1rem 0', color: '#fff', fontSize: '1.25rem', fontWeight: '600' }}>Add New User</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.8125rem', color: '#a8a29e', marginBottom: '0.375rem' }}>Username</label>
                <input
                  type="text"
                  className="search-input"
                  style={{ width: '100%', boxSizing: 'border-box', background: '#292524', border: '1px solid #44403c', borderRadius: '6px', padding: '0.5rem', color: '#fff' }}
                  value={createUsername}
                  onChange={(e) => setCreateUsername(e.target.value)}
                  placeholder="Enter username"
                  disabled={actionLoading}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.8125rem', color: '#a8a29e', marginBottom: '0.375rem' }}>Password</label>
                <input
                  type="password"
                  className="search-input"
                  style={{ width: '100%', boxSizing: 'border-box', background: '#292524', border: '1px solid #44403c', borderRadius: '6px', padding: '0.5rem', color: '#fff' }}
                  value={createPassword}
                  onChange={(e) => setCreatePassword(e.target.value)}
                  placeholder="Enter password (min 6 chars)"
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
                Cancel
              </button>
              <button
                className="btn btn-sm"
                style={{ background: 'var(--accent, #ea580c)', color: '#fff', border: 'none', borderRadius: '6px', padding: '0.375rem 0.75rem', cursor: 'pointer' }}
                onClick={handleCreateUser}
                disabled={actionLoading}
              >
                {actionLoading ? 'Creating...' : 'Create'}
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
            <h3 style={{ margin: '0 0 0.5rem 0', color: '#fff', fontSize: '1.25rem', fontWeight: '600' }}>Reset Password</h3>
            <p style={{ margin: '0 0 1rem 0', fontSize: '0.875rem', color: '#a8a29e' }}>
              Enter new password for user <strong style={{ color: 'var(--accent, #ea580c)' }}>{resetUsername}</strong>
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '1.5rem' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.8125rem', color: '#a8a29e', marginBottom: '0.375rem' }}>New Password</label>
                <input
                  type="password"
                  className="search-input"
                  style={{ width: '100%', boxSizing: 'border-box', background: '#292524', border: '1px solid #44403c', borderRadius: '6px', padding: '0.5rem', color: '#fff' }}
                  value={resetPasswordVal}
                  onChange={(e) => setResetPasswordVal(e.target.value)}
                  placeholder="Enter new password (min 6 chars)"
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
                Cancel
              </button>
              <button
                className="btn btn-sm"
                style={{ background: 'var(--accent, #ea580c)', color: '#fff', border: 'none', borderRadius: '6px', padding: '0.375rem 0.75rem', cursor: 'pointer' }}
                onClick={handleResetPassword}
                disabled={actionLoading}
              >
                {actionLoading ? 'Resetting...' : 'Reset Password'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
