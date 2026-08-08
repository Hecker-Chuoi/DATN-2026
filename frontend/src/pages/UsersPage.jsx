import { useState, useEffect, useCallback } from 'react';
import { userApi } from '../api.js';

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
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
