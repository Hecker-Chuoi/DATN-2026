/**
 * workspaceServiceApi.js - API Client cho workspace-service.
 *
 * Dùng chung instance apiClient của api.js: interceptor (gắn token, silent
 * refresh, bóc envelope, toast lỗi tập trung) sống trên chính instance nên
 * import là thừa hưởng hết.
 *
 * CHỈ khai báo endpoint mà backend ĐÃ có. Cố tình không lặp lại thói quen của
 * workServiceApi.js (khai báo sẵn workSprintApi/workBoardApi/workJqlApi trong
 * khi backend chưa implement, nên gọi vào là 404).
 *
 * BẪY: request interceptor của api.js chọn adminTokenStore khi URL chứa
 * '/api/v1/admin/'. Vì vậy mọi path workspace phải nằm dưới
 * /workspace/api/v1/workspaces/... - đừng bao giờ đặt tên nhóm là 'admin'.
 */
import apiClient from '../api';

const WORKSPACE_BASE = '/workspace/api/v1/workspaces';

export const workspaceApi = {
  /** Chỉ workspace mà người gọi là thành viên. Kèm myRole + ownerCount. */
  listMine: () => apiClient.get(`${WORKSPACE_BASE}/my`),
  getById: (id) => apiClient.get(`${WORKSPACE_BASE}/${id}`),
  create: (data) => apiClient.post(WORKSPACE_BASE, data),
  update: (id, data) => apiClient.put(`${WORKSPACE_BASE}/${id}`, data),
  delete: (id) => apiClient.delete(`${WORKSPACE_BASE}/${id}`),
  restore: (id) => apiClient.post(`${WORKSPACE_BASE}/${id}/restore`),
  leave: (id) => apiClient.post(`${WORKSPACE_BASE}/${id}/leave`),

  getMyMembership: (id) => apiClient.get(`${WORKSPACE_BASE}/${id}/members/me`),
  getMembers: (id, params) => apiClient.get(`${WORKSPACE_BASE}/${id}/members`, { params }),
  addMember: (id, data) => apiClient.post(`${WORKSPACE_BASE}/${id}/members`, data),
  updateMemberRole: (id, userId, role) =>
    apiClient.put(`${WORKSPACE_BASE}/${id}/members/${userId}/role`, { role }),
  removeMember: (id, userId) => apiClient.delete(`${WORKSPACE_BASE}/${id}/members/${userId}`),
};

/**
 * Lời mời workspace. addMember ở trên KHÔNG thêm thẳng nữa mà tạo lời mời;
 * người được mời chấp nhận qua accept mới thành thành viên.
 * Path riêng (/workspace-invitations) để "my" không bị bắt bởi GET /workspaces/{id}.
 */
const INV_BASE = '/workspace/api/v1/workspace-invitations';

export const workspaceInvitationApi = {
  my: () => apiClient.get(`${INV_BASE}/my`, { skipErrorToast: true }),
  forWorkspace: (id) => apiClient.get(`${WORKSPACE_BASE}/${id}/members/invitations`, { skipErrorToast: true }),
  accept: (id) => apiClient.post(`${INV_BASE}/${id}/accept`),
  decline: (id) => apiClient.post(`${INV_BASE}/${id}/decline`),
  cancel: (id) => apiClient.delete(`${INV_BASE}/${id}`),
};

export default workspaceApi;
