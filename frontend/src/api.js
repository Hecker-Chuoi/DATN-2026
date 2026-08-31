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

    if (error.response?.status !== 401 || !originalRequest?._retry) {
      toast.error(msg, { id: msg });
    }

    const customErr = new Error(msg);
    customErr.status = error.response?.status;
    customErr.body = body;
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
  listAll: () => apiClient.get(`${WORK_BASE}/teams`),
  getById: (id) => apiClient.get(`${WORK_BASE}/teams/${id}`),
  create: (data) => apiClient.post(`${WORK_BASE}/teams`, data),
  getMembers: (teamId) => apiClient.get(`${WORK_BASE}/teams/${teamId}/members`),
  addMember: (teamId, data) => apiClient.post(`${WORK_BASE}/teams/${teamId}/members`, data),
  removeMember: (teamId, userId) =>
    apiClient.delete(`${WORK_BASE}/teams/${teamId}/members/${userId}`),
};

// ─── 2. Projects API ──────────────────────────────────────────────────────────
export const projectApi = {
  listAll: (params) => apiClient.get(`${WORK_BASE}/projects`, { params }),
  listTemplates: () => apiClient.get(`${WORK_BASE}/projects/templates`),
  getById: (id) => apiClient.get(`${WORK_BASE}/projects/${id}`),
  create: (data) => apiClient.post(`${WORK_BASE}/projects`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/projects/${id}`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/projects/${id}`),
  clone: (sourceProjectId, data) =>
    apiClient.post(`${WORK_BASE}/projects/${sourceProjectId}/clone`, data),
  getMembers: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/members`),
  getMyPermissions: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/permissions/my`),
  addMember: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/members`, data),
  updateMemberRole: (projectId, userId, roleId) =>
    apiClient.put(`${WORK_BASE}/projects/${projectId}/members/${userId}/role`, { roleId }),
  removeMember: (projectId, userId) =>
    apiClient.delete(`${WORK_BASE}/projects/${projectId}/members/${userId}`),
  getRoles: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/roles`),
};

// ─── 3. Work Items API (Jira-grade) ───────────────────────────────────────────
export const workItemApi = {
  listAll: (params) => apiClient.get(`${WORK_BASE}/items`, { params }),
  getById: (id) => apiClient.get(`${WORK_BASE}/items/${id}`),
  create: (data) => apiClient.post(`${WORK_BASE}/items`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/items/${id}`, data),
  updateRank: (id, data) => apiClient.patch(`${WORK_BASE}/items/${id}/rank`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/items/${id}`),
  getTransitions: (id) => apiClient.get(`${WORK_BASE}/work-items/${id}/transitions`),
  executeTransition: (id, transitionId, data) =>
    apiClient.post(`${WORK_BASE}/work-items/${id}/transitions/${transitionId}`, data),
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
