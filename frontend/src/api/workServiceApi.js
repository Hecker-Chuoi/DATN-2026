/**
 * workServiceApi.js - Comprehensive API Client for Jira-grade Work Service
 * Integrates with central apiClient from api.js with Bearer token & refresh interceptors.
 */
import apiClient from '../api';

const WORK_BASE = '/work/api/v1/work';

// ─── 1. Projects API ──────────────────────────────────────────────────────────
export const workProjectApi = {
  listAll: (params) => apiClient.get(`${WORK_BASE}/projects`, { params }),
  getById: (id) => apiClient.get(`${WORK_BASE}/projects/${id}`),
  getByKey: (key) => apiClient.get(`${WORK_BASE}/projects/key/${key}`),
  create: (data) => apiClient.post(`${WORK_BASE}/projects`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/projects/${id}`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/projects/${id}`),
  listTemplates: () => apiClient.get(`${WORK_BASE}/projects/templates`),
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
  assignRole: (projectId, data) =>
    apiClient.post(`${WORK_BASE}/projects/${projectId}/roles/assign`, data),
  removeRole: (projectId, userId, roleId) =>
    apiClient.delete(`${WORK_BASE}/projects/${projectId}/roles/assign/${userId}/${roleId}`),
};

// ─── 2. Teams API ─────────────────────────────────────────────────────────────
export const workTeamApi = {
  listAll: (params) => apiClient.get(`${WORK_BASE}/teams`, { params }),
  listMine: (params) => apiClient.get(`${WORK_BASE}/teams/my`, { params }),
  getById: (id) => apiClient.get(`${WORK_BASE}/teams/${id}`),
  create: (data) => apiClient.post(`${WORK_BASE}/teams`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/teams/${id}`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/teams/${id}`),
  leaveTeam: (teamId) => apiClient.post(`${WORK_BASE}/teams/${teamId}/leave`),
  getMembers: (teamId) => apiClient.get(`${WORK_BASE}/teams/${teamId}/members`),
  addMember: (teamId, data) => apiClient.post(`${WORK_BASE}/teams/${teamId}/members`, data),
  removeMember: (teamId, userId) =>
    apiClient.delete(`${WORK_BASE}/teams/${teamId}/members/${userId}`),
  updateMemberRole: (teamId, userId, role) =>
    apiClient.put(`${WORK_BASE}/teams/${teamId}/members/${userId}/role`, { role }),
};

// ─── 3. Work Items (Jira Issues) API ──────────────────────────────────────────
export const workItemApi = {
  listAll: (params) => apiClient.get(`${WORK_BASE}/items`, { params }),
  getById: (id) => apiClient.get(`${WORK_BASE}/items/${id}`),
  getByCode: (code) => apiClient.get(`${WORK_BASE}/items/code/${code}`),
  create: (data) => apiClient.post(`${WORK_BASE}/items`, data),
  update: (id, data) => apiClient.put(`${WORK_BASE}/items/${id}`, data),
  delete: (id) => apiClient.delete(`${WORK_BASE}/items/${id}`),
  updateRank: (id, data) => apiClient.patch(`${WORK_BASE}/items/${id}/rank`, data),

  // Workflow transitions & FSM
  getTransitions: (id) => apiClient.get(`${WORK_BASE}/items/${id}/transitions`),
  executeTransition: (id, data) => apiClient.post(`${WORK_BASE}/items/${id}/transitions`, data),

  // Subtasks & Hierarchy
  getSubtasks: (id) => apiClient.get(`${WORK_BASE}/items/${id}/subtasks`),
  createSubtask: (parentId, data) =>
    apiClient.post(`${WORK_BASE}/items/${parentId}/subtasks`, data),

  // Dependency links
  getLinks: (id) => apiClient.get(`${WORK_BASE}/items/${id}/links`),
  createLink: (id, data) => apiClient.post(`${WORK_BASE}/items/${id}/links`, data),
  deleteLink: (id, linkId) => apiClient.delete(`${WORK_BASE}/items/${id}/links/${linkId}`),

  // Comments
  getComments: (id) => apiClient.get(`${WORK_BASE}/items/${id}/comments`),
  addComment: (id, data) => apiClient.post(`${WORK_BASE}/items/${id}/comments`, data),
  updateComment: (id, commentId, data) =>
    apiClient.put(`${WORK_BASE}/items/${id}/comments/${commentId}`, data),
  deleteComment: (id, commentId) =>
    apiClient.delete(`${WORK_BASE}/items/${id}/comments/${commentId}`),

  // Attachments
  getAttachments: (id) => apiClient.get(`${WORK_BASE}/items/${id}/attachments`),
  uploadAttachment: (id, formData) =>
    apiClient.post(`${WORK_BASE}/items/${id}/attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  deleteAttachment: (id, attachmentId) =>
    apiClient.delete(`${WORK_BASE}/items/${id}/attachments/${attachmentId}`),

  // Changelogs & Activity audit
  getChangelogs: (id) => apiClient.get(`${WORK_BASE}/items/${id}/changelogs`),
};

// ─── 4. Boards & Backlog API ──────────────────────────────────────────────────
export const workBoardApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/boards`),
  getById: (boardId) => apiClient.get(`${WORK_BASE}/boards/${boardId}`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/boards`, data),
  update: (boardId, data) => apiClient.put(`${WORK_BASE}/boards/${boardId}`, data),
  delete: (boardId) => apiClient.delete(`${WORK_BASE}/boards/${boardId}`),
  getBoardData: (boardId) => apiClient.get(`${WORK_BASE}/boards/${boardId}/data`),
  moveCard: (boardId, itemId, data) =>
    apiClient.put(`${WORK_BASE}/boards/${boardId}/items/${itemId}/move`, data),
  getBacklog: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/backlog`),

  // Board columns configuration & WIP limits
  getColumns: (boardId) => apiClient.get(`${WORK_BASE}/boards/${boardId}/columns`),
  updateColumns: (boardId, columns) =>
    apiClient.put(`${WORK_BASE}/boards/${boardId}/columns`, { columns }),

  // Board swimlanes configuration
  getSwimlanes: (boardId) => apiClient.get(`${WORK_BASE}/boards/${boardId}/swimlanes`),
  updateSwimlanes: (boardId, swimlanes) =>
    apiClient.put(`${WORK_BASE}/boards/${boardId}/swimlanes`, { swimlanes }),
};

// ─── 5. Sprints API (Scrum Lifecycle & Reports) ───────────────────────────────
export const workSprintApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/sprints`),
  getById: (sprintId) => apiClient.get(`${WORK_BASE}/sprints/${sprintId}`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/sprints`, data),
  update: (sprintId, data) => apiClient.put(`${WORK_BASE}/sprints/${sprintId}`, data),
  delete: (sprintId) => apiClient.delete(`${WORK_BASE}/sprints/${sprintId}`),
  start: (sprintId, data) => apiClient.post(`${WORK_BASE}/sprints/${sprintId}/start`, data),
  close: (sprintId, data) => apiClient.post(`${WORK_BASE}/sprints/${sprintId}/close`, data),
  getBurndown: (sprintId) => apiClient.get(`${WORK_BASE}/sprints/${sprintId}/burndown`),
  getVelocity: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/velocity`),
  addItemsToSprint: (sprintId, itemIds) =>
    apiClient.post(`${WORK_BASE}/sprints/${sprintId}/items`, { itemIds }),
  removeItemsFromSprint: (sprintId, itemIds) =>
    apiClient.delete(`${WORK_BASE}/sprints/${sprintId}/items`, { data: { itemIds } }),
};

// ─── 6. Components & Versions API ─────────────────────────────────────────────
export const workComponentApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/components`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/components`, data),
  update: (componentId, data) => apiClient.put(`${WORK_BASE}/components/${componentId}`, data),
  delete: (componentId) => apiClient.delete(`${WORK_BASE}/components/${componentId}`),
};

export const workVersionApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/versions`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/versions`, data),
  update: (versionId, data) => apiClient.put(`${WORK_BASE}/versions/${versionId}`, data),
  delete: (versionId) => apiClient.delete(`${WORK_BASE}/versions/${versionId}`),
  release: (versionId) => apiClient.post(`${WORK_BASE}/versions/${versionId}/release`),
};

// ─── 7. Labels & Saved Filters API ────────────────────────────────────────────
export const workLabelApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/labels`),
  search: (query) => apiClient.get(`${WORK_BASE}/labels/search`, { params: { q: query } }),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/labels`, data),
  delete: (labelId) => apiClient.delete(`${WORK_BASE}/labels/${labelId}`),
};

export const workSavedFilterApi = {
  listMine: () => apiClient.get(`${WORK_BASE}/filters/mine`),
  listShared: () => apiClient.get(`${WORK_BASE}/filters/shared`),
  getById: (filterId) => apiClient.get(`${WORK_BASE}/filters/${filterId}`),
  create: (data) => apiClient.post(`${WORK_BASE}/filters`, data),
  update: (filterId, data) => apiClient.put(`${WORK_BASE}/filters/${filterId}`, data),
  delete: (filterId) => apiClient.delete(`${WORK_BASE}/filters/${filterId}`),
};

// ─── 8. Custom Field Definitions API ──────────────────────────────────────────
export const workCustomFieldApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/custom-fields`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/custom-fields`, data),
  update: (fieldId, data) => apiClient.put(`${WORK_BASE}/custom-fields/${fieldId}`, data),
  delete: (fieldId) => apiClient.delete(`${WORK_BASE}/custom-fields/${fieldId}`),
};

// ─── 9. Automation Rules API ──────────────────────────────────────────────────
export const workAutomationApi = {
  listByProject: (projectId) => apiClient.get(`${WORK_BASE}/projects/${projectId}/automation/rules`),
  getById: (ruleId) => apiClient.get(`${WORK_BASE}/automation/rules/${ruleId}`),
  create: (projectId, data) => apiClient.post(`${WORK_BASE}/projects/${projectId}/automation/rules`, data),
  update: (ruleId, data) => apiClient.put(`${WORK_BASE}/automation/rules/${ruleId}`, data),
  toggleActive: (ruleId, isActive) =>
    apiClient.patch(`${WORK_BASE}/automation/rules/${ruleId}/status`, { isActive }),
  delete: (ruleId) => apiClient.delete(`${WORK_BASE}/automation/rules/${ruleId}`),
};

// ─── 10. JQL Search Engine API ────────────────────────────────────────────────
export const workJqlApi = {
  search: (jqlQuery, page = 0, size = 20) =>
    apiClient.post(`${WORK_BASE}/jql/search`, { jql: jqlQuery, page, size }),
  autocomplete: (query) => apiClient.get(`${WORK_BASE}/jql/autocomplete`, { params: { q: query } }),
  validate: (jqlQuery) => apiClient.post(`${WORK_BASE}/jql/validate`, { jql: jqlQuery }),
};

// ─── Export Default Bundle ────────────────────────────────────────────────────
const workServiceApi = {
  projects: workProjectApi,
  teams: workTeamApi,
  items: workItemApi,
  boards: workBoardApi,
  sprints: workSprintApi,
  components: workComponentApi,
  versions: workVersionApi,
  labels: workLabelApi,
  filters: workSavedFilterApi,
  customFields: workCustomFieldApi,
  automation: workAutomationApi,
  jql: workJqlApi,
};

export { workflowApi } from '../api';

export default workServiceApi;

