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
  },
  IN_PROGRESS: {
    code: 'IN_PROGRESS',
    label: 'Đang thực hiện',
    color: '#0284c7', // Sky blue
    bgColor: '#e0f2fe',
    borderColor: '#7dd3fc',
  },
  DONE: {
    code: 'DONE',
    label: 'Hoàn thành',
    color: '#16a34a', // Emerald
    bgColor: '#dcfce7',
    borderColor: '#86efac',
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
    color: '#64748B',
    bgColor: '#F1F5F9',
    borderColor: '#CBD5E1',
  },
  ACTIVE: {
    code: 'ACTIVE',
    label: 'Đang chạy',
    color: '#0052CC',
    bgColor: '#DEEBFF',
    borderColor: '#B3D4FF',
  },
  CLOSED: {
    code: 'CLOSED',
    label: 'Đã hoàn tất',
    color: '#00875A',
    bgColor: '#E3FCEF',
    borderColor: '#ABF5D1',
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
  SOFTWARE_SCRUM: 'SOFTWARE_SCRUM',
  SOFTWARE_KANBAN: 'SOFTWARE_KANBAN',
  BUSINESS: 'BUSINESS',
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

// ─── Workflow Engine Types & FSM Configuration ──────────────────────────────
export const WORKFLOW_CONDITION_TYPES = {
  ASSIGNEE_ONLY: {
    code: 'ASSIGNEE_ONLY',
    name: 'Chỉ người được giao việc (Assignee Only)',
    desc: 'Chỉ thành viên đang được chỉ định xử lý issue mới nhìn thấy và bấm được nút chuyển trạng thái.',
  },
  PERMISSION_REQUIRED: {
    code: 'PERMISSION_REQUIRED',
    name: 'Yêu cầu quyền hạn dự án (Permission Required)',
    desc: 'Yêu cầu người thực hiện có quyền hạn cụ thể (ví dụ: ISSUE_TRANSITION, SPRINT_MANAGE).',
  },
  ROLE_REQUIRED: {
    code: 'ROLE_REQUIRED',
    name: 'Yêu cầu vai trò nội bộ (Role Required)',
    desc: 'Chỉ thành viên có vai trò cụ thể trong dự án (ví dụ: ADMINISTRATOR, LEAD) mới được phép.',
  },
  ONLY_CREATOR: {
    code: 'ONLY_CREATOR',
    name: 'Chỉ người tạo (Reporter Only)',
    desc: 'Chỉ người khởi tạo issue mới được phép thực hiện bước chuyển này.',
  },
};

export const WORKFLOW_VALIDATOR_TYPES = {
  FIELD_REQUIRED: {
    code: 'FIELD_REQUIRED',
    name: 'Bắt buộc điền trường dữ liệu (Field Required)',
    desc: 'Kiểm tra và bắt buộc nhập các trường chỉ định (ví dụ: resolution, fixVersionId) trước khi chuyển.',
  },
  ALL_SUBTASKS_DONE: {
    code: 'ALL_SUBTASKS_DONE',
    name: 'Toàn bộ Subtask phải hoàn thành (All Subtasks Done)',
    desc: 'Bắt buộc toàn bộ các nhiệm vụ con (Sub-tasks) phải ở trạng thái DONE trước khi đóng Story/Task cha.',
  },
  ESTIMATE_REQUIRED: {
    code: 'ESTIMATE_REQUIRED',
    name: 'Bắt buộc có ước lượng (Estimate Required)',
    desc: 'Yêu cầu phải có điểm Story Points hoặc thời gian ước lượng trước khi bắt đầu xử lý.',
  },
};

export const WORKFLOW_POST_FUNCTION_TYPES = {
  SET_RESOLVED_TIME: {
    code: 'SET_RESOLVED_TIME',
    name: 'Ghi nhận thời gian giải quyết (Set Resolved Time)',
    desc: 'Tự động gán trường resolved_at = thời điểm hiện tại khi issue chuyển sang trạng thái DONE.',
  },
  CLEAR_RESOLUTION: {
    code: 'CLEAR_RESOLUTION',
    name: 'Xóa kết quả giải quyết (Clear Resolution)',
    desc: 'Tự động xóa resolution và resolved_at khi issue được mở lại (Reopened).',
  },
  AUTO_ASSIGN_LEAD: {
    code: 'AUTO_ASSIGN_LEAD',
    name: 'Tự động gán cho Trưởng dự án / Component (Auto Assign Lead)',
    desc: 'Tự động gán assignee cho người phụ trách khi chuyển trạng thái (ví dụ khi cần Review).',
  },
  AUTO_TRANSITION_PARENT: {
    code: 'AUTO_TRANSITION_PARENT',
    name: 'Tự động chuyển trạng thái Item Cha (Auto Transition Parent)',
    desc: 'Tự động cập nhật trạng thái Story cha khi các Sub-task con hoàn thành.',
  },
  EMIT_AUDIT_LOG: {
    code: 'EMIT_AUDIT_LOG',
    name: 'Ghi nhật ký thay đổi (Emit Changelog)',
    desc: 'Ghi vết lịch sử chuyển đổi trạng thái vào bảng work_item_changelogs.',
  },
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

