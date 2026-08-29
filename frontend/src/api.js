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
    // Trường hợp Jackson serialize đa hình: ["java.util.ArrayList", [items...]]
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

    // Public auth endpoints MUST NOT have Authorization header attached (avoids 401 on expired tokens)
    if (isAuthRoute && !url.includes('/logout')) {
      return config;
    }

    const isAdminContext =
      url.includes('/admin') ||
      (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin'));

    if (isAdminContext) {
      if (_adminToken) {
        config.headers.Authorization = `Bearer ${_adminToken}`;
      } else if (_userToken) {
        config.headers.Authorization = `Bearer ${_userToken}`;
      }
    } else {
      if (_userToken) {
        config.headers.Authorization = `Bearer ${_userToken}`;
      } else if (_adminToken) {
        config.headers.Authorization = `Bearer ${_adminToken}`;
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ─── Silent Refresh Queue State ───────────────────────────────────────────────
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

// ─── Response Interceptor: Normalize Data & Silent Refresh on 401 ─────────────
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && body.data !== undefined) {
      body.data = normalizeResponseData(body.data);
    }
    return body; // Trả về ApiResponse<T> chuẩn
  },
  async (error) => {
    const originalRequest = error.config;
    const isAuthRoute =
      originalRequest?.url?.includes('/identity/api/v1/auth/login') ||
      originalRequest?.url?.includes('/identity/api/v1/auth/register') ||
      originalRequest?.url?.includes('/identity/api/v1/auth/refresh');

    // Nếu gặp lỗi 401 và không phải request refresh hay login
    if (error.response?.status === 401 && !originalRequest?._retry && !isAuthRoute) {
      const isAdminReq =
        originalRequest?.url?.includes('/admin') ||
        (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin'));
      const store = isAdminReq ? adminTokenStore : userTokenStore;
      const refreshToken = store.getRefreshToken();

      if (!refreshToken) {
        store.clear();
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((newToken) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshRes = await axios.post('/identity/api/v1/auth/refresh', {
          refreshToken,
        });

        const newAccessToken = refreshRes.data?.data?.access_token;
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
    
    // Tự động thông báo Toast cho mọi phản hồi lỗi từ API (trừ trường hợp refresh token nền)
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

// ─── Work Service: Team API ───────────────────────────────────────────────────
const WORK_BASE = '/work/api/v1/work';

export const teamApi = {
  listAll: () => apiClient.get(`${WORK_BASE}/teams`),
  getById: (id) => apiClient.get(`${WORK_BASE}/teams/${id}`),
  create: (data) => apiClient.post(`${WORK_BASE}/teams`, data),
  getMembers: (teamId) => apiClient.get(`${WORK_BASE}/teams/${teamId}/members`),
  addMember: (teamId, data) => apiClient.post(`${WORK_BASE}/teams/${teamId}/members`, data),
  updateMemberRole: (teamId, userId, roleInTeam) =>
    apiClient.put(`${WORK_BASE}/teams/${teamId}/members/${userId}/role`, { roleInTeam }),
  getMemberRole: (teamId, userId) =>
    apiClient.get(`${WORK_BASE}/teams/${teamId}/members/${userId}/role`),
  getMyRole: (teamId) =>
    apiClient.get(`${WORK_BASE}/teams/${teamId}/my-role`),
  removeMember: (teamId, userId) =>
    apiClient.delete(`${WORK_BASE}/teams/${teamId}/members/${userId}`),
};

// ─── Work Service: Project API ────────────────────────────────────────────────
export const projectApi = {
  listAll: (params) =>
    apiClient.get(`${WORK_BASE}/projects`, {
      params: typeof params === 'object' && params !== null ? params : (params ? { teamId: params } : {}),
    }),
  listMyProjects: (params) =>
    apiClient.get(`${WORK_BASE}/projects/my-projects`, {
      params: typeof params === 'object' && params !== null ? params : (params ? { teamId: params } : {}),
    }),
  listTemplates: () => apiClient.get(`${WORK_BASE}/projects/templates`),
  getById: (id) => apiClient.get(`${WORK_BASE}/projects/${id}`),
  create: (data) => apiClient.post(`${WORK_BASE}/projects`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/projects/${id}`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/projects/${id}`),
  clone: (sourceProjectId, data) =>
    apiClient.post(`${WORK_BASE}/projects/${sourceProjectId}/clone`, data),
};

// ─── Work Service: Milestone API ──────────────────────────────────────────────
export const milestoneApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/milestones`),
  getById: (id) => apiClient.get(`${WORK_BASE}/milestones/${id}`),
  create: (data) => apiClient.post(`${WORK_BASE}/milestones`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/milestones/${id}`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/milestones/${id}`),
};

// ─── Work Service: Task API ───────────────────────────────────────────────────
export const taskApi = {
  listByProject: (projectId, params = {}) =>
    apiClient.get(`${WORK_BASE}/tasks`, {
      params: {
        projectId,
        ...params,
      },
    }),
  getById: (id) => apiClient.get(`${WORK_BASE}/tasks/${id}`),
  getSubtasks: (parentTaskId) => apiClient.get(`${WORK_BASE}/tasks/${parentTaskId}/subtasks`),
  create: (data) => apiClient.post(`${WORK_BASE}/tasks`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/tasks/${id}`, data),
  assign: (id, assigneeId) => apiClient.put(`${WORK_BASE}/tasks/${id}/assignee`, { assigneeId }),
  delete: (id) => apiClient.delete(`${WORK_BASE}/tasks/${id}`),
};

export default apiClient;
