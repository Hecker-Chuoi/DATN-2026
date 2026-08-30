/**
 * workConstants.js - Type Constants, Enums & Display Configurations for Jira-grade Work Service
 */

// ─── Hierarchy Levels (3-Level Hierarchy) ───────────────────────────────────
export const HIERARCHY_LEVELS = {
  EPIC: 1,
  STANDARD: 0,
  SUBTASK: -1,
};

export const HIERARCHY_LABELS = {
  [HIERARCHY_LEVELS.EPIC]: { label: 'Sáng kiến lớn / Epic', level: 1, code: 'EPIC' },
  [HIERARCHY_LEVELS.STANDARD]: { label: 'Công việc tiêu chuẩn', level: 0, code: 'STANDARD' },
  [HIERARCHY_LEVELS.SUBTASK]: { label: 'Nhiệm vụ con / Subtask', level: -1, code: 'SUBTASK' },
};

// ─── Work Item Types ──────────────────────────────────────────────────────────
export const WORK_ITEM_TYPES = {
  EPIC: 'EPIC',
  STORY: 'STORY',
  TASK: 'TASK',
  BUG: 'BUG',
  SUB_TASK: 'SUB_TASK',
};

export const WORK_ITEM_TYPE_CONFIG = {
  EPIC: {
    code: 'EPIC',
    name: 'Epic',
    hierarchyLevel: HIERARCHY_LEVELS.EPIC,
    color: '#9333ea', // Purple
    bgColor: '#f3e8ff',
    borderColor: '#d8b4fe',
    icon: 'Layers',
    isSubtask: false,
  },
  STORY: {
    code: 'STORY',
    name: 'Story',
    hierarchyLevel: HIERARCHY_LEVELS.STANDARD,
    color: '#16a34a', // Green
    bgColor: '#dcfce7',
    borderColor: '#86efac',
    icon: 'Bookmark',
    isSubtask: false,
  },
  TASK: {
    code: 'TASK',
    name: 'Task',
    hierarchyLevel: HIERARCHY_LEVELS.STANDARD,
    color: '#2563eb', // Blue
    bgColor: '#dbeafe',
    borderColor: '#93c5fd',
    icon: 'CheckSquare',
    isSubtask: false,
  },
  BUG: {
    code: 'BUG',
    name: 'Bug',
    hierarchyLevel: HIERARCHY_LEVELS.STANDARD,
    color: '#dc2626', // Red
    bgColor: '#fee2e2',
    borderColor: '#fca5a5',
    icon: 'AlertCircle',
    isSubtask: false,
  },
  SUB_TASK: {
    code: 'SUB_TASK',
    name: 'Sub-task',
    hierarchyLevel: HIERARCHY_LEVELS.SUBTASK,
    color: '#0284c7', // Sky
    bgColor: '#e0f2fe',
    borderColor: '#7dd3fc',
    icon: 'GitCommit',
    isSubtask: true,
  },
};

// ─── Status Categories (Jira 3 Core Categories) ─────────────────────────────
export const STATUS_CATEGORIES = {
  TO_DO: 'TODO',
  IN_PROGRESS: 'IN_PROGRESS',
  DONE: 'DONE',
};

export const STATUS_CATEGORY_CONFIG = {
  TODO: {
    code: 'TODO',
    label: 'Cần làm',
    color: '#64748b', // Slate
    bgColor: '#f1f5f9',
    borderColor: '#cbd5e1',
    badgeClass: 'bg-slate-100 text-slate-700 border-slate-300',
  },
  IN_PROGRESS: {
    code: 'IN_PROGRESS',
    label: 'Đang thực hiện',
    color: '#0284c7', // Sky blue
    bgColor: '#e0f2fe',
    borderColor: '#7dd3fc',
    badgeClass: 'bg-sky-100 text-sky-700 border-sky-300',
  },
  DONE: {
    code: 'DONE',
    label: 'Hoàn thành',
    color: '#16a34a', // Emerald
    bgColor: '#dcfce7',
    borderColor: '#86efac',
    badgeClass: 'bg-emerald-100 text-emerald-700 border-emerald-300',
  },
};

// ─── Work Item Priorities ───────────────────────────────────────────────────
export const WORK_ITEM_PRIORITIES = {
  LOWEST: 'LOWEST',
  LOW: 'LOW',
  MEDIUM: 'MEDIUM',
  HIGH: 'HIGH',
  HIGHEST: 'HIGHEST',
  URGENT: 'URGENT',
};

export const PRIORITY_CONFIG = {
  LOWEST: {
    code: 'LOWEST',
    label: 'Rất thấp',
    color: '#94a3b8',
    bgColor: '#f8fafc',
    icon: 'ChevronsDown',
    level: 1,
  },
  LOW: {
    code: 'LOW',
    label: 'Thấp',
    color: '#059669',
    bgColor: '#ecfdf5',
    icon: 'ChevronDown',
    level: 2,
  },
  MEDIUM: {
    code: 'MEDIUM',
    label: 'Trung bình',
    color: '#d97706',
    bgColor: '#fffbeb',
    icon: 'Minus',
    level: 3,
  },
  HIGH: {
    code: 'HIGH',
    label: 'Cao',
    color: '#ea580c',
    bgColor: '#fff7ed',
    icon: 'ChevronUp',
    level: 4,
  },
  HIGHEST: {
    code: 'HIGHEST',
    label: 'Rất cao',
    color: '#dc2626',
    bgColor: '#fef2f2',
    icon: 'ChevronsUp',
    level: 5,
  },
  URGENT: {
    code: 'URGENT',
    label: 'Khẩn cấp',
    color: '#e11d48',
    bgColor: '#ffe4e6',
    icon: 'Flame',
    level: 6,
  },
};

// ─── Sprint States ────────────────────────────────────────────────────────────
export const SPRINT_STATES = {
  FUTURE: 'FUTURE',
  ACTIVE: 'ACTIVE',
  CLOSED: 'CLOSED',
};

export const SPRINT_STATE_CONFIG = {
  FUTURE: {
    code: 'FUTURE',
    label: 'Dự kiến',
    color: '#64748b',
    badgeClass: 'bg-slate-100 text-slate-600',
  },
  ACTIVE: {
    code: 'ACTIVE',
    label: 'Đang chạy',
    color: '#2563eb',
    badgeClass: 'bg-blue-100 text-blue-700 animate-pulse',
  },
  CLOSED: {
    code: 'CLOSED',
    label: 'Đã hoàn tất',
    color: '#16a34a',
    badgeClass: 'bg-emerald-100 text-emerald-700',
  },
};

// ─── Work Item Dependency Link Types ────────────────────────────────────────
export const LINK_TYPES = {
  BLOCKS: 'BLOCKS',
  IS_BLOCKED_BY: 'IS_BLOCKED_BY',
  RELATES_TO: 'RELATES_TO',
  DUPLICATES: 'DUPLICATES',
  IS_DUPLICATED_BY: 'IS_DUPLICATED_BY',
  CLONES: 'CLONES',
  IS_CLONED_BY: 'IS_CLONED_BY',
  CAUSES: 'CAUSES',
  IS_CAUSED_BY: 'IS_CAUSED_BY',
};

export const LINK_TYPE_CONFIG = {
  BLOCKS: { outward: 'Khóa / Chặn (Blocks)', inward: 'Bị khóa bởi (Is blocked by)', isBlocking: true },
  RELATES_TO: { outward: 'Liên quan tới (Relates to)', inward: 'Liên quan tới (Relates to)', isBlocking: false },
  DUPLICATES: { outward: 'Trùng lặp với (Duplicates)', inward: 'Bị trùng lặp bởi (Is duplicated by)', isBlocking: false },
  CLONES: { outward: 'Nhân bản từ (Clones)', inward: 'Bị nhân bản bởi (Is cloned by)', isBlocking: false },
  CAUSES: { outward: 'Gây ra (Causes)', inward: 'Gây ra bởi (Is caused by)', isBlocking: false },
};

// ─── Project Types ──────────────────────────────────────────────────────────
export const PROJECT_TYPES = {
  SCRUM: 'SCRUM',
  KANBAN: 'KANBAN',
};

export const PROJECT_TYPE_CONFIG = {
  SCRUM: {
    code: 'SCRUM',
    name: 'Scrum Agile',
    description: 'Quy trình lặp Sprint, quản lý Backlog, Burndown Chart và Point ước lượng.',
    icon: 'RotateCw',
  },
  KANBAN: {
    code: 'KANBAN',
    name: 'Kanban Board',
    description: 'Luồng công việc liên tục (Continuous flow) với WIP Limits kiểm soát tiến độ.',
    icon: 'Kanban',
  },
};

// ─── Board Swimlane Strategies ──────────────────────────────────────────────
export const SWIMLANE_STRATEGIES = {
  NONE: 'NONE',
  ASSIGNEE: 'BY_ASSIGNEE',
  EPIC: 'BY_EPIC',
  PRIORITY: 'BY_PRIORITY',
  CUSTOM_JQL: 'CUSTOM_JQL',
};

// ─── Team Roles ─────────────────────────────────────────────────────────────
export const TEAM_ROLES = {
  LEADER: 'LEADER',
  MEMBER: 'MEMBER',
  VIEWER: 'VIEWER',
};

// ─── Project Fine-grained Permission Keys ───────────────────────────────────
export const PERMISSION_KEYS = {
  VIEW_PROJECT: 'VIEW_PROJECT',
  EDIT_PROJECT: 'EDIT_PROJECT',
  DELETE_PROJECT: 'DELETE_PROJECT',
  CREATE_ISSUES: 'CREATE_ISSUES',
  EDIT_ISSUES: 'EDIT_ISSUES',
  DELETE_ISSUES: 'DELETE_ISSUES',
  TRANSITION_ISSUES: 'TRANSITION_ISSUES',
  ASSIGN_ISSUES: 'ASSIGN_ISSUES',
  MANAGE_SPRINTS: 'MANAGE_SPRINTS',
  MANAGE_BOARDS: 'MANAGE_BOARDS',
  MANAGE_MEMBERS: 'MANAGE_MEMBERS',
  MANAGE_WORKFLOWS: 'MANAGE_WORKFLOWS',
  ADMINISTER_PROJECT: 'ADMINISTER_PROJECT',
};

// ─── Custom Field Types ─────────────────────────────────────────────────────
export const CUSTOM_FIELD_TYPES = {
  TEXT: 'TEXT',
  NUMBER: 'NUMBER',
  DATE: 'DATE',
  SELECT: 'SELECT',
  MULTI_SELECT: 'MULTI_SELECT',
  USER: 'USER',
  CHECKBOX: 'CHECKBOX',
  URL: 'URL',
};

// ─── Helper Functions ───────────────────────────────────────────────────────
export function getPriorityConfig(priority) {
  return PRIORITY_CONFIG[priority] || PRIORITY_CONFIG.MEDIUM;
}

export function getStatusCategoryConfig(category) {
  return STATUS_CATEGORY_CONFIG[category] || STATUS_CATEGORY_CONFIG.TODO;
}

export function getWorkItemTypeConfig(typeCode) {
  return WORK_ITEM_TYPE_CONFIG[typeCode] || WORK_ITEM_TYPE_CONFIG.TASK;
}

export function getSprintStateConfig(state) {
  return SPRINT_STATE_CONFIG[state] || SPRINT_STATE_CONFIG.FUTURE;
}

export function isHierarchyValid(parentLevel, childLevel) {
  // Parent Epic (1) -> Standard (0)
  // Standard (0) -> Subtask (-1)
  if (parentLevel === HIERARCHY_LEVELS.EPIC && childLevel === HIERARCHY_LEVELS.STANDARD) return true;
  if (parentLevel === HIERARCHY_LEVELS.STANDARD && childLevel === HIERARCHY_LEVELS.SUBTASK) return true;
  return false;
}
