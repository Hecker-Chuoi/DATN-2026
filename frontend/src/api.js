/**
 * api.js – Centralized HTTP client using Axios with Silent Token Refresh
 *
 * Vite dev server proxies /identity/** and /work/** → API Gateway :8081
 * (see vite.config.js → server.proxy).
 */
import axios from 'axios';
import toast from 'react-hot-toast';

// ─── Token storage (Lưu trữ localStorage duy trì phiên làm việc lâu dài) ───────
let _userToken = localStorage.getItem('user_access_token') || null;
let _userRefreshToken = localStorage.getItem('user_refresh_token') || null;
let _adminToken = localStorage.getItem('admin_access_token') || null;
let _adminRefreshToken = localStorage.getItem('admin_refresh_token') || null;

export const userTokenStore = {
  get: () => _userToken,
  getRefreshToken: () => _userRefreshToken,
  set: (token, refreshToken) => {
    _userToken = token;
    localStorage.setItem('user_access_token', token);
    if (refreshToken) {
      _userRefreshToken = refreshToken;
      localStorage.setItem('user_refresh_token', refreshToken);
    }
  },
  clear: () => {
    _userToken = null;
    _userRefreshToken = null;
    localStorage.removeItem('user_access_token');
    localStorage.removeItem('user_refresh_token');
  },
};

export const adminTokenStore = {
  get: () => _adminToken,
  getRefreshToken: () => _adminRefreshToken,
  set: (token, refreshToken) => {
    _adminToken = token;
    localStorage.setItem('admin_access_token', token);
    if (refreshToken) {
      _adminRefreshToken = refreshToken;
      localStorage.setItem('admin_refresh_token', refreshToken);
    }
  },
  clear: () => {
    _adminToken = null;
    _adminRefreshToken = null;
    localStorage.removeItem('admin_access_token');
    localStorage.removeItem('admin_refresh_token');
  },
};

export const tokenStore = userTokenStore;

// ─── JWT helpers (đọc realm roles của Keycloak từ access token, không cần thư viện) ───
export function decodeJwtPayload(token) {
  if (!token || typeof token !== 'string') return null;
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/** Realm roles (VD: ["ADMIN", "USER"]) của token người dùng hiện tại. */
export function getUserRealmRoles() {
  const payload = decodeJwtPayload(userTokenStore.get());
  const roles = payload?.realm_access?.roles;
  return Array.isArray(roles) ? roles : [];
}

/** Keycloak user id (claim `sub`) – work-service dùng id này làm user_id ở mọi bảng. */
export function getUserKeycloakId() {
  const payload = decodeJwtPayload(userTokenStore.get());
  return payload?.sub || null;
}

/** Admin portal (/admin/**) dùng admin token cho mọi request, kể cả work-service. */
function isAdminPortalPage() {
  return typeof window !== 'undefined' && window.location.pathname.startsWith('/admin');
}

// ─── Recursive Normalizer for Spring Boot / Jackson DefaultTyping ────────────
function normalizeResponseData(data) {
  if (data === null || data === undefined) return data;
  if (Array.isArray(data)) {
    if (
      data.length === 2 &&
      typeof data[0] === 'string' &&
      data[0].startsWith('java.util.') &&
      Array.isArray(data[1])
    ) {
      return data[1].map(normalizeResponseData);
    }
    return data.map(normalizeResponseData);
  }
  if (typeof data === 'object') {
    const res = {};
    for (const key of Object.keys(data)) {
      res[key] = normalizeResponseData(data[key]);
    }
    return res;
  }
  return data;
}

// ─── Axios Instance ───────────────────────────────────────────────────────────
const apiClient = axios.create({
  baseURL: '', // Uses Vite proxy
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// ─── Request Interceptor: Attach JWT Token ────────────────────────────────────
apiClient.interceptors.request.use(
  (config) => {
    const url = config.url || '';
    const isAuthRoute =
      url.includes('/identity/api/v1/auth/') ||
      url.includes('/api/v1/auth/');

    if (isAuthRoute && !url.includes('/logout')) {
      return config;
    }

    const isAdminRoute =
      isAdminPortalPage() ||
      url.includes('/identity/api/v1/admin/') ||
      url.includes('/api/v1/admin/');

    const token = isAdminRoute ? adminTokenStore.get() : userTokenStore.get();

    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ─── Response Interceptor: Data Unwrap, Silent Refresh & Central Error Toast ───
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => {
    if (response.data && typeof response.data === 'object' && 'data' in response.data) {
      response.data.data = normalizeResponseData(response.data.data);
    } else {
      response.data = normalizeResponseData(response.data);
    }
    return response.data;
  },
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      const url = originalRequest.url || '';
      const isAuthEndpoint =
        url.includes('/identity/api/v1/auth/login') ||
        url.includes('/identity/api/v1/auth/refresh') ||
        url.includes('/api/v1/auth/login') ||
        url.includes('/api/v1/auth/refresh');

      if (isAuthEndpoint) {
        return Promise.reject(error);
      }

      const isAdminRoute =
        isAdminPortalPage() ||
        url.includes('/identity/api/v1/admin/') ||
        url.includes('/api/v1/admin/');
      const store = isAdminRoute ? adminTokenStore : userTokenStore;
      const refreshToken = store.getRefreshToken();

      if (!refreshToken) {
        store.clear();
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshRes = await axios.post(
          '/identity/api/v1/auth/refresh',
          { refreshToken },
          { headers: { 'Content-Type': 'application/json' } }
        );

        const newAccessToken =
          refreshRes.data?.data?.access_token ||
          refreshRes.data?.data?.accessToken ||
          refreshRes.data?.access_token;
        const newRefreshToken = refreshRes.data?.data?.refresh_token;

        if (newAccessToken) {
          store.set(newAccessToken, newRefreshToken);
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
          processQueue(null, newAccessToken);
          return apiClient(originalRequest);
        } else {
          throw new Error('No access token in refresh response');
        }
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        store.clear();
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    const body = error.response?.data;
    const msg = body?.message || error.message || 'Lỗi kết nối máy chủ';

    // Trang có thể tự xử lý 403/404 (VD: hiển thị màn "Không có quyền") bằng cách gửi { skipErrorToast: true }
    const skipToast = Boolean(originalRequest?.skipErrorToast);
    if (!skipToast && (error.response?.status !== 401 || !originalRequest?._retry)) {
      toast.error(msg, { id: msg });
    }

    const customErr = new Error(msg);
    customErr.status = error.response?.status;
    customErr.body = body;
    customErr.response = error.response;
    return Promise.reject(customErr);
  }
);

// ─── Auth API ─────────────────────────────────────────────────────────────────
const AUTH_BASE = '/identity/api/v1/auth';

export const authApi = {
  register: (data) => apiClient.post(`${AUTH_BASE}/register`, data),
  login: (username, password) => apiClient.post(`${AUTH_BASE}/login`, { username, password }),
  logout: (refreshToken) => apiClient.post(`${AUTH_BASE}/logout`, { refreshToken }),
  refresh: (refreshToken) => apiClient.post(`${AUTH_BASE}/refresh`, { refreshToken }),
  googleInit: (redirectUri) =>
    apiClient.get(`${AUTH_BASE}/google/init`, {
      params: { redirectUri, redirect_uri: redirectUri },
    }),
  googleCallback: (code, redirectUri) =>
    apiClient.post(`${AUTH_BASE}/google/callback`, { code, redirectUri }),
};

// ─── User API ─────────────────────────────────────────────────────────────────
export const userApi = {
  listAll: (params) => apiClient.get('/identity/api/v1/admin/users', { params }),
  search: (query) => apiClient.get('/identity/api/v1/users/search', { params: { query } }),
  me: () => apiClient.get('/identity/api/v1/users/me'),
  updateRoles: (id, roles) =>
    apiClient.put(`/identity/api/v1/admin/users/${id}/roles`, { roles }),
  adminCreate: (username, password) =>
    apiClient.post('/identity/api/v1/admin/users', { username, password }),
  resetPassword: (id, password) =>
    apiClient.put(`/identity/api/v1/admin/users/${id}/reset-password`, { password }),
  delete: (id) =>
    apiClient.delete(`/identity/api/v1/admin/users/${id}`),
};

// ─── Work Service: Base Endpoint ──────────────────────────────────────────────
const WORK_BASE = '/work/api/v1/work';

// ─── 1. Teams API ─────────────────────────────────────────────────────────────
export const teamApi = {
  listAll: (params) => apiClient.get(`${WORK_BASE}/teams`, { params }),
  listMine: (params) => apiClient.get(`${WORK_BASE}/teams/my`, { params }),
  getById: (id) => apiClient.get(`${WORK_BASE}/teams/${id}`),
  create: (data) => apiClient.post(`${WORK_BASE}/teams`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/teams/${id}`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/teams/${id}`),
  leaveTeam: (teamId) => apiClient.post(`${WORK_BASE}/teams/${teamId}/leave`),
  getMembers: (teamId) => apiClient.get(`${WORK_BASE}/teams/${teamId}/members`),
  addMember: (teamId, data) => apiClient.post(`${WORK_BASE}/teams/${teamId}/members`, data),
  updateMemberRole: (teamId, userId, role) =>
    apiClient.put(`${WORK_BASE}/teams/${teamId}/members/${userId}/role`, { role }),
  removeMember: (teamId, userId) =>
    apiClient.delete(`${WORK_BASE}/teams/${teamId}/members/${userId}`),
};

// ─── 2. Projects API ──────────────────────────────────────────────────────────
export const projectApi = {
  listAll: (params) => apiClient.get(`${WORK_BASE}/projects`, { params }),
  listTemplates: () => apiClient.get(`${WORK_BASE}/projects/templates`),
  getById: (id, config = {}) => apiClient.get(`${WORK_BASE}/projects/${id}`, config),
  create: (data) => apiClient.post(`${WORK_BASE}/projects`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/projects/${id}`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/projects/${id}`),
  clone: (sourceProjectId, data) =>
    apiClient.post(`${WORK_BASE}/projects/${sourceProjectId}/clone`, data),
  getMembers: (projectId, config = {}) => apiClient.get(`${WORK_BASE}/projects/${projectId}/members`, config),
  /** Trả về { roleName, isLead, isGlobalAdmin, permissions[] }; 403 nếu không phải thành viên (không toast). */
  getMyPermissions: (projectId) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/permissions/my`, { skipErrorToast: true }),
  addMember: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/members`, data),
  updateMemberRole: (projectId, userId, roleId) =>
    apiClient.put(`${WORK_BASE}/projects/${projectId}/members/${userId}/role`, { roleId }),
  removeMember: (projectId, userId) =>
    apiClient.delete(`${WORK_BASE}/projects/${projectId}/members/${userId}`),
  getRoles: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/roles`),
};

// ─── 3. Work Items API (Jira-grade) ───────────────────────────────────────────
export const workItemApi = {
  listAll: (params) => {
    const projectId = params?.projectId;
    if (projectId) {
      return apiClient.get(`${WORK_BASE}/projects/${projectId}/items`, { params });
    }
    return apiClient.get(`${WORK_BASE}/items`, { params });
  },
  listByProject: (projectId, params) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/items`, { params }),
  getById: (id) => apiClient.get(`${WORK_BASE}/items/${id}`),
  getByCode: (code) => apiClient.get(`${WORK_BASE}/items/code/${code}`),
  create: (projectIdOrData, maybeData) => {
    if (maybeData) {
      return apiClient.post(`${WORK_BASE}/projects/${projectIdOrData}/items`, maybeData);
    }
    const projectId = projectIdOrData?.projectId;
    return apiClient.post(`${WORK_BASE}/projects/${projectId}/items`, projectIdOrData);
  },
  update: (id, data) => apiClient.put(`${WORK_BASE}/items/${id}`, data),
  updateRank: (id, data) => apiClient.patch(`${WORK_BASE}/items/${id}/rank`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/items/${id}`),
  getSubtasks: (id) => apiClient.get(`${WORK_BASE}/items/${id}/subtasks`),
  createSubtask: (parentId, data) =>
    apiClient.post(`${WORK_BASE}/items/${parentId}/subtasks`, data),
  getTransitions: (id) => apiClient.get(`${WORK_BASE}/work-items/${id}/transitions`),
  executeTransition: (id, transitionIdOrData, maybeData) => {
    if (typeof transitionIdOrData === 'object' && transitionIdOrData !== null) {
      const tId = transitionIdOrData.transitionId || transitionIdOrData.id;
      if (tId) {
        return apiClient.post(`${WORK_BASE}/work-items/${id}/transitions/${tId}`, transitionIdOrData);
      }
      return apiClient.post(`${WORK_BASE}/work-items/${id}/transitions`, transitionIdOrData);
    }
    return apiClient.post(`${WORK_BASE}/work-items/${id}/transitions/${transitionIdOrData}`, maybeData || {});
  },
  getComments: (id) => apiClient.get(`${WORK_BASE}/items/${id}/comments`),
  addComment: (id, data) => apiClient.post(`${WORK_BASE}/items/${id}/comments`, data),
  updateComment: (id, commentId, data) =>
    apiClient.put(`${WORK_BASE}/items/${id}/comments/${commentId}`, data),
  deleteComment: (id, commentId) =>
    apiClient.delete(`${WORK_BASE}/items/${id}/comments/${commentId}`),

  getAttachments: (id) => apiClient.get(`${WORK_BASE}/items/${id}/attachments`),
  uploadAttachment: (id, formData) =>
    apiClient.post(`${WORK_BASE}/items/${id}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  deleteAttachment: (id, attachmentId) =>
    apiClient.delete(`${WORK_BASE}/items/${id}/attachments/${attachmentId}`),

  getLinks: (id) => apiClient.get(`${WORK_BASE}/items/${id}/links`),
  createLink: (id, data) => apiClient.post(`${WORK_BASE}/items/${id}/links`, data),
  deleteLink: (id, linkId) => apiClient.delete(`${WORK_BASE}/items/${id}/links/${linkId}`),

  getChangelogs: (id) => apiClient.get(`${WORK_BASE}/items/${id}/changelogs`),
};

// ─── 3.1. Workflow Engine & FSM Schemes API ──────────────────────────────────
export const workflowApi = {
  getProjectWorkflow: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/workflow`),
  getProjectWorkflows: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/workflows`),
  getProjectWorkflowScheme: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/workflow/scheme`),
  updateProjectWorkflowScheme: (projectId, data) => apiClient.put(`${WORK_BASE}/projects/${projectId}/workflow/scheme`, data),
  getProjectStatuses: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/statuses`),
  createStatus: (data) => apiClient.post(`${WORK_BASE}/statuses`, data),
  getWorkflowById: (workflowId) => apiClient.get(`${WORK_BASE}/workflows/${workflowId}`),
  createTransition: (workflowId, data) => apiClient.post(`${WORK_BASE}/workflows/${workflowId}/transitions`, data),
  deleteTransition: (workflowId, transitionId) => apiClient.delete(`${WORK_BASE}/workflows/${workflowId}/transitions/${transitionId}`),
  getTransitions: (workItemId) => apiClient.get(`${WORK_BASE}/work-items/${workItemId}/transitions`),
  executeTransition: (workItemId, transitionId, data) => apiClient.post(`${WORK_BASE}/work-items/${workItemId}/transitions/${transitionId}`, data),
};

// ─── 4. Boards & Backlog API ──────────────────────────────────────────────────
export const boardApi = {
  getBoardData: (boardId) => apiClient.get(`${WORK_BASE}/boards/${boardId}/data`),
  moveCard: (boardId, itemId, data) =>
    apiClient.put(`${WORK_BASE}/boards/${boardId}/items/${itemId}/move`, data),
  getBacklog: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/backlog`),
};

// ─── 5. Sprints API ───────────────────────────────────────────────────────────
export const sprintApi = {
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/sprints`, data),
  start: (sprintId) => apiClient.post(`${WORK_BASE}/sprints/${sprintId}/start`),
  close: (sprintId, data) => apiClient.post(`${WORK_BASE}/sprints/${sprintId}/close`, data),
  getBurndown: (sprintId) => apiClient.get(`${WORK_BASE}/sprints/${sprintId}/burndown`),
};

// ─── 6. Components & Versions API ─────────────────────────────────────────────
export const componentApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/components`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/components`, data),
};

export const versionApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/versions`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/versions`, data),
  release: (versionId) => apiClient.post(`${WORK_BASE}/versions/${versionId}/release`),
};

// ─── 7. JQL & Search API ──────────────────────────────────────────────────────
export const jqlApi = {
  search: (jqlQuery) => apiClient.post(`${WORK_BASE}/jql/search`, { jql: jqlQuery }),
};

export default apiClient;
