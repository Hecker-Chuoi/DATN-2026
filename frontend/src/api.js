/**
 * api.js – Centralized HTTP client
 *
 * Vite dev server proxies /identity/** → API Gateway :8081
 * (see vite.config.js → server.proxy).
 * In production, a reverse proxy (nginx, etc.) handles this.
 *
 * Route mapping (API Gateway StripPrefix=1):
 *   /identity/api/v1/...  →  identity-service /api/v1/...
 *
 * Public  (no token):  /identity/api/v1/auth/register
 *                      /identity/api/v1/auth/login
 *                      /identity/api/v1/auth/google/init
 *                      /identity/api/v1/auth/google/callback
 * Private (JWT Bearer): /identity/api/v1/admin/users
 *                       /identity/api/v1/users/me
 */

// Empty string = use Vite proxy (relative path).
// Vite forwards /identity/** and /work/** → http://localhost:8081 (API Gateway).
const GATEWAY = '';

// ─── Token storage (Lưu trữ localStorage duy trì phiên làm việc lâu dài) ───────
let _userToken = localStorage.getItem('user_access_token') || null;
let _adminToken = localStorage.getItem('admin_access_token') || null;

export const userTokenStore = {
  get:    ()      => _userToken,
  set:    (token) => { _userToken = token; localStorage.setItem('user_access_token', token); },
  clear:  ()      => { _userToken = null;  localStorage.removeItem('user_access_token'); },
};

export const adminTokenStore = {
  get:    ()      => _adminToken,
  set:    (token) => { _adminToken = token; localStorage.setItem('admin_access_token', token); },
  clear:  ()      => { _adminToken = null;  localStorage.removeItem('admin_access_token'); },
};

// Tương thích ngược
export const tokenStore = userTokenStore;

// ─── Core fetch wrapper ───────────────────────────────────────────────────────
async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers };

  // Tự động gán Authorization Bearer tương ứng ngữ cảnh
  if (path.includes('/admin') && _adminToken) {
    headers['Authorization'] = `Bearer ${_adminToken}`;
  } else if (_userToken) {
    headers['Authorization'] = `Bearer ${_userToken}`;
  } else if (_adminToken) {
    headers['Authorization'] = `Bearer ${_adminToken}`;
  }

  // Set credentials: 'include' so the browser automatically sends the httpOnly cookie.
  const res = await fetch(`${GATEWAY}${path}`, {
    ...options,
    headers,
    credentials: 'include',
  });

  // parse body regardless of status
  let body;
  try { body = await res.json(); } catch { body = null; }

  if (!res.ok) {
    const msg = body?.message || `HTTP ${res.status}: ${res.statusText}`;
    const err  = new Error(msg);
    err.status = res.status;
    err.body   = body;
    throw err;
  }

  return body; // ApiResponse<T>
}

// ─── Auth API ─────────────────────────────────────────────────────────────────────────────────────
const AUTH_BASE = '/identity/api/v1/auth';

export const authApi = {
  /**
   * POST /identity/api/v1/auth/register
   * Body: { username, password, email, firstName?, lastName? }
   * Returns ApiResponse<UserResponse>
   */
  register: (data) =>
    request(`${AUTH_BASE}/register`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  /**
   * POST /identity/api/v1/auth/login
   * Body: { username, password }
   * Returns ApiResponse<TokenResponse>
   */
  login: (username, password) =>
    request(`${AUTH_BASE}/login`, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),

  /**
   * POST /identity/api/v1/auth/logout
   * Clears the httpOnly cookie on the backend.
   */
  logout: () =>
    request(`${AUTH_BASE}/logout`, {
      method: 'POST',
    }),

  /**
   * GET /identity/api/v1/auth/google/init?redirect_uri=...
   * Returns ApiResponse<{ authorizationUrl: string }>
   * The frontend should redirect the user to authorizationUrl to start Google login.
   */
  googleInit: (redirectUri) =>
    request(`${AUTH_BASE}/google/init?redirect_uri=${encodeURIComponent(redirectUri)}`),

  /**
   * POST /identity/api/v1/auth/google/callback
   * Body: { code, redirectUri }
   * Returns ApiResponse<TokenResponse>
   * Exchange the Keycloak authorization code for a JWT token pair.
   */
  googleCallback: (code, redirectUri) =>
    request(`${AUTH_BASE}/google/callback`, {
      method: 'POST',
      body: JSON.stringify({ code, redirectUri }),
    }),
};

// ─── User API ─────────────────────────────────────────────────────────────────────────────────────
export const userApi = {
  /**
   * GET /identity/api/v1/admin/users      (ADMIN role required)
   * Returns ApiResponse<UserResponse[]>
   */
  listAll: () => request('/identity/api/v1/admin/users'),

  /**
   * GET /identity/api/v1/users/me         (any authenticated user)
   * Returns ApiResponse<UserResponse>
   */
  me: () => request('/identity/api/v1/users/me'),

  /**
   * PUT /identity/api/v1/admin/users/{id}/roles (ADMIN role required)
   * Body: { roles: ["ADMIN", "USER"] }
   * Returns ApiResponse<UserResponse>
   */
  updateRoles: (id, roles) =>
    request(`/identity/api/v1/admin/users/${id}/roles`, {
      method: 'PUT',
      body: JSON.stringify({ roles }),
    }),
};

// ─── Work Service: Team API ───────────────────────────────────────────────────────────────────────
const WORK_BASE = '/work/api/v1/work';

export const teamApi = {
  listAll: () => request(`${WORK_BASE}/teams`),
  getById: (id) => request(`${WORK_BASE}/teams/${id}`),
  create: (data) =>
    request(`${WORK_BASE}/teams`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  getMembers: (teamId) => request(`${WORK_BASE}/teams/${teamId}/members`),
  addMember: (teamId, data) =>
    request(`${WORK_BASE}/teams/${teamId}/members`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  removeMember: (teamId, userId) =>
    request(`${WORK_BASE}/teams/${teamId}/members/${userId}`, {
      method: 'DELETE',
    }),
};

// ─── Work Service: Project API ────────────────────────────────────────────────────────────────────
export const projectApi = {
  listAll: (teamId) => {
    const query = teamId ? `?teamId=${teamId}` : '';
    return request(`${WORK_BASE}/projects${query}`);
  },
  listTemplates: () => request(`${WORK_BASE}/projects/templates`),
  getById: (id) => request(`${WORK_BASE}/projects/${id}`),
  create: (data) =>
    request(`${WORK_BASE}/projects`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id, data) =>
    request(`${WORK_BASE}/projects/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id) =>
    request(`${WORK_BASE}/projects/${id}`, {
      method: 'DELETE',
    }),
  clone: (sourceProjectId, data) =>
    request(`${WORK_BASE}/projects/${sourceProjectId}/clone`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};

