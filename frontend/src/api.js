/**
 * api.js – Centralized HTTP client using Axios with Silent Token Refresh
 *
 * Vite dev server proxies /identity/** and /work/** → API Gateway :8081
 * (see vite.config.js → server.proxy).
 */
import axios from 'axios';
import toast from 'react-hot-toast';
import { translateServerMessage } from './utils/serverMessageVi.js';

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

/** Kiểm tra xem token (access hoặc refresh) đã hết hạn theo claim exp hay chưa */
export function isTokenExpired(token) {
  if (!token || typeof token !== 'string') return true;
  const payload = decodeJwtPayload(token);
  if (!payload || !payload.exp) return false;
  return payload.exp * 1000 <= Date.now();
}

/** Kiểm tra phiên của store (user hoặc admin) còn hiệu lực hay không */
export function isSessionValid(store = userTokenStore) {
  const token = store.get();
  if (!token) return false;
  const refreshToken = store.getRefreshToken();
  // Nếu có refreshToken: phiên còn sống chừng nào refreshToken chưa hết hạn
  if (refreshToken) {
    return !isTokenExpired(refreshToken);
  }
  // Nếu không có refreshToken: kiểm tra accessToken
  return !isTokenExpired(token);
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

// Hết phiên (không có / không refresh được token): tự động đá về trang đăng nhập tương ứng thay vì trang lỗi 401
const PUBLIC_AUTH_PAGES = /^\/(login|register|verify-email|forgot-password|reset-password|auth\/callback|admin\/login|admin\/auth\/callback)/;
export const redirectToSessionExpired = (isAdminRoute) => {
  if (typeof window === 'undefined') return;
  const currentPath = window.location.pathname;
  if (PUBLIC_AUTH_PAGES.test(currentPath)) return;

  const target = isAdminRoute ? '/admin/login' : '/login';

  try {
    sessionStorage.setItem('session_expired_notice', 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    if (currentPath && currentPath !== '/' && currentPath !== '/401' && !PUBLIC_AUTH_PAGES.test(currentPath)) {
      sessionStorage.setItem('redirect_after_login', currentPath + window.location.search);
    }
  } catch {
    // ignore
  }

  window.location.replace(target);
};

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
        redirectToSessionExpired(isAdminRoute);
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
        redirectToSessionExpired(isAdminRoute);
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    }

    const body = error.response?.data;
    // Việt hóa câu lỗi của BE (chỉ hiển thị). Ghi đè luôn body.message vì nhiều call site
    // đọc err.response.data.message thay vì err.message.
    if (body && typeof body.message === 'string') {
      body.message = translateServerMessage(body.message);
    }
    const msg = body?.message || error.message || 'Lỗi kết nối máy chủ';

    // Call site tự xử lý lỗi bằng cách gửi { skipErrorToast: true }:
    // trang 403/404 hiển thị màn "Không có quyền", kéo thả trên Board cần thông điệp riêng cho 409.
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
  // Xác thực email & quên mật khẩu (link trong email trỏ tới /verify-email và /reset-password của frontend)
  verifyEmail: (token) => apiClient.get(`${AUTH_BASE}/verify-email`, { params: { token }, skipErrorToast: true }),
  resendVerification: (identifier) => apiClient.post(`${AUTH_BASE}/resend-verification`, { identifier }),
  forgotPassword: (email) => apiClient.post(`${AUTH_BASE}/forgot-password`, { email }),
  resetPassword: (token, newPassword) => apiClient.post(`${AUTH_BASE}/reset-password`, { token, newPassword }),
};

// ─── User API ─────────────────────────────────────────────────────────────────
export const userApi = {
  listAll: (params) => apiClient.get('/identity/api/v1/admin/users', { params }),
  search: (query, config = {}) =>
    apiClient.get('/identity/api/v1/users/search', { params: { query }, ...config }),
  getByEmail: (email, config = {}) =>
    apiClient.get('/identity/api/v1/users/by-email', { params: { email }, ...config }),
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


// ─── 2. Projects API ──────────────────────────────────────────────────────────
export const invitationApi = {
  my: () => apiClient.get(`${WORK_BASE}/invitations/my`, { skipErrorToast: true }),
  forProject: (projectId) => apiClient.get(`${WORK_BASE}/invitations`, { params: { projectId }, skipErrorToast: true }),
  accept: (id) => apiClient.post(`${WORK_BASE}/invitations/${id}/accept`),
  decline: (id) => apiClient.post(`${WORK_BASE}/invitations/${id}/decline`),
  cancel: (id) => apiClient.delete(`${WORK_BASE}/invitations/${id}`),
};

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

// ─── 2.0. Project Templates API (Dự án mẫu) ───────────────────────────────────
export const templateApi = {
  /** Thư viện mẫu: params { workspaceId, category, search } → TemplateSummaryResponse[] */
  list: (params) => apiClient.get(`${WORK_BASE}/templates`, { params }),
  /** Bản thiết kế đầy đủ của mẫu (trạng thái, workflow, board, sprint, cây công việc, kiểm tra toàn vẹn). */
  preview: (templateId) => apiClient.get(`${WORK_BASE}/templates/${templateId}/preview`),
  /** Tạo dự án thật từ mẫu → { project, stats, dateOffsetDays, integrity } */
  createProject: (templateId, data) => apiClient.post(`${WORK_BASE}/templates/${templateId}/projects`, data),
  saveAsTemplate: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/save-as-template`, data),
  checkIntegrity: (projectId) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/template-check`, { skipErrorToast: true }),
};

// ─── 2.1. Project Work Item Types API (Custom Types per Project) ───────────────
export const workItemTypeApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/types`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/types`, data),
  update: (projectId, typeId, data) => apiClient.put(`${WORK_BASE}/projects/${projectId}/types/${typeId}`, data),
  delete: (projectId, typeId) => apiClient.delete(`${WORK_BASE}/projects/${projectId}/types/${typeId}`),
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
  getByCode: (code, config = {}) => apiClient.get(`${WORK_BASE}/items/code/${encodeURIComponent(code)}`, config),
  create: (projectIdOrData, maybeData) => {
    if (maybeData) {
      return apiClient.post(`${WORK_BASE}/projects/${projectIdOrData}/items`, maybeData);
    }
    const projectId = projectIdOrData?.projectId;
    return apiClient.post(`${WORK_BASE}/projects/${projectId}/items`, projectIdOrData);
  },
  update: (id, data) => apiClient.put(`${WORK_BASE}/items/${id}`, data),
  // Giao / hủy giao việc (assigneeId = null là hủy giao) — luôn được ghi vào Lịch sử
  changeAssignee: (id, assigneeId) =>
    apiClient.put(`${WORK_BASE}/items/${id}/assignee`, { assigneeId: assigneeId || null }),
  // "Tạm hoàn thành" → Admin dự án xác nhận / từ chối
  listCompletionRequests: (projectId) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/completion-requests`),
  getCompletionHistory: (id) => apiClient.get(`${WORK_BASE}/items/${id}/completion-requests`),
  approveCompletion: (id) => apiClient.post(`${WORK_BASE}/items/${id}/completion/approve`),
  rejectCompletion: (id, reason) =>
    apiClient.post(`${WORK_BASE}/items/${id}/completion/reject`, { reason: reason || null }),
  cancelCompletion: (id) => apiClient.delete(`${WORK_BASE}/items/${id}/completion`),
  updateRank: (id, data) =>
    apiClient.patch(
      `${WORK_BASE}/items/${id}/rank`,
      typeof data === 'string' ? { rank: data } : data
    ),
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
  createStatus: (data, projectId) => {
    const pid = projectId || data?.projectId;
    return pid ? apiClient.post(`${WORK_BASE}/projects/${pid}/statuses`, data) : apiClient.post(`${WORK_BASE}/statuses`, data);
  },
  updateStatus: (statusId, data) => apiClient.put(`${WORK_BASE}/statuses/${statusId}`, data),
  deleteStatus: (statusId) => apiClient.delete(`${WORK_BASE}/statuses/${statusId}`),
  getWorkflowById: (workflowId) => apiClient.get(`${WORK_BASE}/workflows/${workflowId}`),
  createWorkflow: (data) => apiClient.post(`${WORK_BASE}/workflows`, data),
  updateWorkflow: (workflowId, data) => apiClient.put(`${WORK_BASE}/workflows/${workflowId}`, data),
  createTransition: (workflowId, data) => apiClient.post(`${WORK_BASE}/workflows/${workflowId}/transitions`, data),
  updateTransition: (workflowId, transitionId, data) => apiClient.put(`${WORK_BASE}/workflows/${workflowId}/transitions/${transitionId}`, data),
  deleteTransition: (workflowId, transitionId) => apiClient.delete(`${WORK_BASE}/workflows/${workflowId}/transitions/${transitionId}`),
  getTransitions: (workItemId) => apiClient.get(`${WORK_BASE}/work-items/${workItemId}/transitions`),
  executeTransition: (workItemId, transitionId, data) => apiClient.post(`${WORK_BASE}/work-items/${workItemId}/transitions/${transitionId}`, data),
};

// ─── 4. Boards & Backlog API ──────────────────────────────────────────────────
export const boardApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/boards`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/boards`, data),
  getById: (boardId) => apiClient.get(`${WORK_BASE}/boards/${boardId}`),
  update: (boardId, data) => apiClient.put(`${WORK_BASE}/boards/${boardId}`, data),
  delete: (boardId) => apiClient.delete(`${WORK_BASE}/boards/${boardId}`),

  // Du lieu day du de ve bang: columns + swimlanes + cards (phang, kem columnId/rank/version)
  getBoardData: (boardId, params) => apiClient.get(`${WORK_BASE}/boards/${boardId}/data`, { params }),

  // config cho phep truyen { skipErrorToast: true } khi keo tha de tu xu ly loi 409 / WIP
  moveCard: (boardId, itemId, data, config) =>
    apiClient.put(`${WORK_BASE}/boards/${boardId}/items/${itemId}/move`, data, config),

  getBacklog: (projectId, params) => apiClient.get(`${WORK_BASE}/projects/${projectId}/backlog`, { params }),

  getColumns: (boardId) => apiClient.get(`${WORK_BASE}/boards/${boardId}/columns`),
  updateColumns: (boardId, columns) =>
    apiClient.put(`${WORK_BASE}/boards/${boardId}/columns`, { columns }),
  getSwimlanes: (boardId) => apiClient.get(`${WORK_BASE}/boards/${boardId}/swimlanes`),
  updateSwimlanes: (boardId, swimlanes) =>
    apiClient.put(`${WORK_BASE}/boards/${boardId}/swimlanes`, { swimlanes }),
};

// ─── 5. Sprints API (Scrum Lifecycle & Reports) ───────────────────────────────
export const sprintApi = {
  listByProject: (projectId, params) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/sprints`, { params }),
  getById: (sprintId) => apiClient.get(`${WORK_BASE}/sprints/${sprintId}`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/sprints`, data),
  update: (sprintId, data) => apiClient.put(`${WORK_BASE}/sprints/${sprintId}`, data),
  delete: (sprintId) => apiClient.delete(`${WORK_BASE}/sprints/${sprintId}`),

  start: (sprintId, data) => apiClient.post(`${WORK_BASE}/sprints/${sprintId}/start`, data),
  close: (sprintId, data) => apiClient.post(`${WORK_BASE}/sprints/${sprintId}/close`, data),

  addItems: (sprintId, itemIds) =>
    apiClient.post(`${WORK_BASE}/sprints/${sprintId}/items`, { itemIds }),
  // Dung POST .../items/remove thay vi DELETE kem body: mot so gateway loai bo body cua DELETE
  removeItems: (sprintId, itemIds) =>
    apiClient.post(`${WORK_BASE}/sprints/${sprintId}/items/remove`, { itemIds }),

  // Bao cao Agile
  getBurndown: (sprintId, params) =>
    apiClient.get(`${WORK_BASE}/sprints/${sprintId}/burndown`, { params }),
  getReport: (sprintId) => apiClient.get(`${WORK_BASE}/sprints/${sprintId}/report`),
  getVelocity: (projectId, params) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/velocity`, { params }),
  getCfd: (projectId, params) => apiClient.get(`${WORK_BASE}/projects/${projectId}/cfd`, { params }),
};

// ─── 5b. Contribution API (Sổ ghi công & Báo cáo đóng góp) ────────────────────
export const contributionApi = {
  getOverview: (projectId, params) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/reports/contributions`, { params }),
  getLedger: (projectId, params) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/reports/contributions/ledger`, { params }),
  getMemberDetail: (projectId, targetUserId, params) =>
    apiClient.get(`${WORK_BASE}/projects/${projectId}/reports/contributions/members/${targetUserId}`, { params }),
  adjustPoints: (projectId, data) =>
    apiClient.post(`${WORK_BASE}/projects/${projectId}/reports/contributions/adjust`, data),
  getExportUrl: (projectId, sprintId) => {
    let url = `${WORK_BASE}/projects/${projectId}/reports/contributions/export`;
    if (sprintId) url += `?sprintId=${sprintId}`;
    return url;
  },
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

// ─── 8. Notification API (notification-service qua gateway) ───────────────────
// Thiết kế: docs/services/notification/2_KIEN_TRUC_VA_THIET_KE.md §6
const NOTIF_BASE = '/notification/api/v1/notifications';

export const notificationApi = {
  /** params: { filter: 'all'|'unread'|'action', category, projectId, cursor, size } */
  list: (params, config = {}) => apiClient.get(NOTIF_BASE, { params, ...config }),
  counts: (config = {}) => apiClient.get(`${NOTIF_BASE}/unread-count`, { skipErrorToast: true, ...config }),
  setRead: (id, read = true) => apiClient.patch(`${NOTIF_BASE}/${id}`, { read }),
  readAll: (params) => apiClient.post(`${NOTIF_BASE}/read-all`, null, { params }),
  remove: (id) => apiClient.delete(`${NOTIF_BASE}/${id}`),
  restore: (id) => apiClient.post(`${NOTIF_BASE}/${id}/restore`),
  removeAllRead: () => apiClient.delete(NOTIF_BASE, { params: { filter: 'read' } }),
};

/** Chạy ngay job thông báo (chỉ Global ADMIN, dùng khi demo). */
export const notificationJobApi = {
  run: (job = 'all', projectId) =>
    apiClient.post(`${WORK_BASE}/admin/notification-jobs/${job}/run`, null, { params: projectId ? { projectId } : {} }),
};

export default apiClient;
