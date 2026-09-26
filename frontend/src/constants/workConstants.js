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
  [HIERARCHY_LEVELS.EPIC]: { label: 'Hạng mục lớn', level: 1, code: 'EPIC' },
  [HIERARCHY_LEVELS.STANDARD]: { label: 'Công việc tiêu chuẩn', level: 0, code: 'STANDARD' },
  [HIERARCHY_LEVELS.SUBTASK]: { label: 'Nhiệm vụ con', level: -1, code: 'SUBTASK' },
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
    name: 'Hạng mục lớn',
    hierarchyLevel: HIERARCHY_LEVELS.EPIC,
    color: '#9333ea', // Purple
    bgColor: '#f3e8ff',
    borderColor: '#d8b4fe',
    icon: 'Layers',
    isSubtask: false,
  },
  STORY: {
    code: 'STORY',
    name: 'Câu chuyện người dùng',
    hierarchyLevel: HIERARCHY_LEVELS.STANDARD,
    color: '#16a34a', // Green
    bgColor: '#dcfce7',
    borderColor: '#86efac',
    icon: 'Bookmark',
    isSubtask: false,
  },
  TASK: {
    code: 'TASK',
    name: 'Nhiệm vụ',
    hierarchyLevel: HIERARCHY_LEVELS.STANDARD,
    color: '#2563eb', // Blue
    bgColor: '#dbeafe',
    borderColor: '#93c5fd',
    icon: 'CheckSquare',
    isSubtask: false,
  },
  BUG: {
    code: 'BUG',
    name: 'Lỗi phần mềm',
    hierarchyLevel: HIERARCHY_LEVELS.STANDARD,
    color: '#dc2626', // Red
    bgColor: '#fee2e2',
    borderColor: '#fca5a5',
    icon: 'AlertCircle',
    isSubtask: false,
  },
  SUB_TASK: {
    code: 'SUB_TASK',
    name: 'Nhiệm vụ con',
    hierarchyLevel: HIERARCHY_LEVELS.SUBTASK,
    color: '#0284c7', // Sky
    bgColor: '#e0f2fe',
    borderColor: '#7dd3fc',
    icon: 'GitCommit',
    isSubtask: true,
  },
};

// ─── Vietnamese Display Helpers ───────────────────────────────────────────────
export const getTypeDisplayName = (typeCodeOrName) => {
  if (!typeCodeOrName) return 'Nhiệm vụ';
  const t = String(typeCodeOrName).trim().toUpperCase().replace('-', '_');
  if (t === 'EPIC') return 'Hạng mục lớn';
  if (t === 'STORY' || t === 'USER_STORY') return 'Câu chuyện người dùng';
  if (t === 'TASK') return 'Nhiệm vụ';
  if (t === 'BUG') return 'Lỗi phần mềm';
  if (t === 'SUBTASK' || t === 'SUB_TASK') return 'Nhiệm vụ con';
  return typeCodeOrName;
};

export const getStatusDisplayName = (statusName) => {
  if (!statusName) return '';
  const s = String(statusName).trim().toUpperCase();
  if (s === 'TO DO' || s === 'TODO') return 'Cần làm';
  if (s === 'IN PROGRESS' || s === 'IN_PROGRESS') return 'Đang thực hiện';
  if (s === 'IN REVIEW' || s === 'IN_REVIEW' || s === 'REVIEW') return 'Chờ duyệt';
  if (s === 'DONE') return 'Hoàn thành';
  return statusName;
};

export const getStatusCategoryDisplayName = (category) => {
  if (!category) return '';
  const c = String(category).trim().toUpperCase();
  if (c === 'TODO' || c === 'TO_DO') return 'Cần làm';
  if (c === 'IN_PROGRESS') return 'Đang thực hiện';
  if (c === 'DONE') return 'Hoàn thành';
  return category;
};

export const getPriorityDisplayName = (priority) => {
  if (!priority) return 'Trung bình';
  const p = String(priority).trim().toUpperCase();
  if (p === 'HIGHEST' || p === 'BLOCKER') return 'Khẩn cấp';
  if (p === 'HIGH') return 'Cao';
  if (p === 'MEDIUM') return 'Trung bình';
  if (p === 'LOW') return 'Thấp';
  if (p === 'LOWEST') return 'Rất thấp';
  return priority;
};

export const getHierarchyDisplayName = (level) => {
  if (level === 1) return 'Cấp 1 • Hạng mục lớn';
  if (level === -1) return 'Cấp -1 • Nhiệm vụ con';
  return 'Cấp 0 • Tiêu chuẩn';
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
  BLOCKS: { outward: 'Chặn tiến độ của', inward: 'Bị chặn tiến độ bởi', isBlocking: true },
  RELATES_TO: { outward: 'Liên quan tới', inward: 'Có liên quan tới', isBlocking: false },
  DUPLICATES: { outward: 'Trùng lặp với', inward: 'Bị trùng lặp bởi', isBlocking: false },
  CLONES: { outward: 'Nhân bản từ', inward: 'Bị nhân bản bởi', isBlocking: false },
  CAUSES: { outward: 'Gây ra lỗi', inward: 'Bị gây ra bởi', isBlocking: false },
};

// ─── Project Types ──────────────────────────────────────────────────────────
export const PROJECT_TYPES = {
  SCRUM: 'SCRUM',
  KANBAN: 'KANBAN',
};

export const PROJECT_TYPE_CONFIG = {
  SCRUM: {
    code: 'SCRUM',
    name: 'Quy trình Scrum',
    description: 'Quy trình lặp theo chu kỳ, quản lý danh sách tồn đọng, biểu đồ tiến độ và điểm ước lượng.',
    icon: 'RotateCw',
  },
  KANBAN: {
    code: 'KANBAN',
    name: 'Bảng Kanban',
    description: 'Luồng công việc liên tục với giới hạn việc đang làm để kiểm soát tiến độ.',
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
    name: 'Chỉ người được giao việc',
    desc: 'Chỉ thành viên đang được chỉ định xử lý công việc mới nhìn thấy và bấm được nút chuyển trạng thái.',
  },
  PERMISSION_REQUIRED: {
    code: 'PERMISSION_REQUIRED',
    name: 'Yêu cầu quyền hạn dự án',
    desc: 'Yêu cầu người thực hiện có quyền hạn cụ thể trong dự án.',
  },
  ROLE_REQUIRED: {
    code: 'ROLE_REQUIRED',
    name: 'Yêu cầu vai trò nội bộ',
    desc: 'Chỉ thành viên có vai trò cụ thể trong dự án (ví dụ: Quản trị viên, Trưởng nhóm) mới được phép.',
  },
  ONLY_CREATOR: {
    code: 'ONLY_CREATOR',
    name: 'Chỉ người tạo công việc',
    desc: 'Chỉ người khởi tạo công việc mới được phép thực hiện bước chuyển này.',
  },
};

export const WORKFLOW_VALIDATOR_TYPES = {
  FIELD_REQUIRED: {
    code: 'FIELD_REQUIRED',
    name: 'Bắt buộc điền trường dữ liệu',
    desc: 'Kiểm tra và bắt buộc nhập các trường chỉ định (ví dụ: lý do hoàn thành) trước khi chuyển.',
  },
  ALL_SUBTASKS_DONE: {
    code: 'ALL_SUBTASKS_DONE',
    name: 'Toàn bộ nhiệm vụ con phải hoàn thành',
    desc: 'Bắt buộc toàn bộ các nhiệm vụ con phải ở trạng thái Hoàn thành trước khi đóng công việc cha.',
  },
  ESTIMATE_REQUIRED: {
    code: 'ESTIMATE_REQUIRED',
    name: 'Bắt buộc có ước lượng',
    desc: 'Yêu cầu phải có điểm ước lượng hoặc thời gian dự kiến trước khi bắt đầu xử lý.',
  },
};

export const WORKFLOW_POST_FUNCTION_TYPES = {
  SET_RESOLVED_TIME: {
    code: 'SET_RESOLVED_TIME',
    name: 'Ghi nhận thời gian giải quyết',
    desc: 'Tự động gán thời điểm hoàn tất là thời điểm hiện tại khi công việc chuyển sang trạng thái Hoàn thành.',
  },
  CLEAR_RESOLUTION: {
    code: 'CLEAR_RESOLUTION',
    name: 'Xóa kết quả giải quyết',
    desc: 'Tự động xóa lý do và thời điểm giải quyết khi công việc được mở lại.',
  },
  AUTO_ASSIGN_LEAD: {
    code: 'AUTO_ASSIGN_LEAD',
    name: 'Tự động gán cho Trưởng dự án / Thành phần',
    desc: 'Tự động gán người thực hiện cho trưởng bộ phận khi chuyển trạng thái.',
  },
  AUTO_TRANSITION_PARENT: {
    code: 'AUTO_TRANSITION_PARENT',
    name: 'Tự động chuyển trạng thái công việc cha',
    desc: 'Tự động cập nhật trạng thái công việc cha khi các nhiệm vụ con hoàn thành.',
  },
  EMIT_AUDIT_LOG: {
    code: 'EMIT_AUDIT_LOG',
    name: 'Ghi nhật ký thay đổi',
    desc: 'Ghi vết lịch sử chuyển đổi trạng thái vào nhật ký hệ thống.',
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

// ─── Task Type Presets (Mẫu Cấu Hình Gợi Ý) ─────────────────────────────────
export const TASK_TYPE_PRESETS = [
  {
    id: 'SOFTWARE_DEV',
    name: 'Phát triển Phần mềm',
    description: 'Chuẩn quy trình lặp và bảng làm việc cho đội ngũ kỹ thuật phần mềm, tích hợp Hạng mục lớn, Câu chuyện người dùng, Nhiệm vụ, Lỗi phần mềm và Nhiệm vụ con.',
    icon: 'Layers',
    badgeColor: '#2563EB',
    types: [
      { name: 'Hạng mục lớn', code: 'EPIC', hierarchyLevel: 1, iconName: 'Zap', colorHex: '#9333EA', description: 'Mục tiêu lớn hoặc tập hợp tính năng phức tạp cần triển khai qua nhiều chu kỳ' },
      { name: 'Câu chuyện người dùng', code: 'STORY', hierarchyLevel: 0, iconName: 'Bookmark', colorHex: '#10B981', description: 'Yêu cầu nghiệp vụ hoặc tính năng nhìn từ góc độ người dùng cuối' },
      { name: 'Nhiệm vụ', code: 'TASK', hierarchyLevel: 0, iconName: 'CheckSquare', colorHex: '#3B82F6', description: 'Công việc kỹ thuật hoặc tác vụ phát triển tiêu chuẩn' },
      { name: 'Lỗi phần mềm', code: 'BUG', hierarchyLevel: 0, iconName: 'Bug', colorHex: '#EF4444', description: 'Lỗi phát sinh hoặc sự cố kỹ thuật cần khắc phục' },
      { name: 'Nhiệm vụ con', code: 'SUBTASK', hierarchyLevel: -1, iconName: 'GitCommit', colorHex: '#06B6D4', description: 'Nhiệm vụ con chia nhỏ từ công việc cha' },
    ],
  },
  {
    id: 'CONSTRUCTION',
    name: 'Xây dựng & Công trình',
    description: 'Chuyên biệt cho quản lý dự án xây dựng, quản lý gói thầu, thi công tại hiện trường và nghiệm thu.',
    icon: 'Layers',
    badgeColor: '#D97706',
    types: [
      { name: 'Hạng mục công trình', code: 'PHASE', hierarchyLevel: 1, iconName: 'Layers', colorHex: '#D97706', description: 'Gói thầu, giai đoạn xây dựng hoặc hạng mục công trình trọng điểm' },
      { name: 'Công tác thi công', code: 'CONSTRUCT', hierarchyLevel: 0, iconName: 'CheckSquare', colorHex: '#2563EB', description: 'Nội dung thi công thực tế tại công trường' },
      { name: 'Giám sát & Nghiệm thu', code: 'INSPECT', hierarchyLevel: 0, iconName: 'FileCheck', colorHex: '#059669', description: 'Kiểm tra chất lượng, nghiệm thu hồ sơ và khối lượng' },
      { name: 'Sự cố công trường', code: 'INCIDENT', hierarchyLevel: 0, iconName: 'AlertTriangle', colorHex: '#DC2626', description: 'Sự cố an toàn, hư hỏng vật tư hoặc trục trặc hiện trường' },
      { name: 'Chi tiết công tác', code: 'SUBTASK', hierarchyLevel: -1, iconName: 'GitCommit', colorHex: '#64748B', description: 'Tác vụ thi công chi tiết phân bổ cho từng đội thợ' },
    ],
  },
  {
    id: 'MARKETING',
    name: 'Marketing & Truyền thông',
    description: 'Dành cho chiến dịch tiếp thị, quảng bá truyền thông, sáng tạo nội dung và sản xuất ấn phẩm.',
    icon: 'Sparkles',
    badgeColor: '#7C3AED',
    types: [
      { name: 'Chiến dịch', code: 'CAMPAIGN', hierarchyLevel: 1, iconName: 'Sparkles', colorHex: '#7C3AED', description: 'Chiến dịch tiếp thị tổng thể hoặc chuỗi sự kiện thương hiệu' },
      { name: 'Sáng tạo nội dung', code: 'CONTENT', hierarchyLevel: 0, iconName: 'Bookmark', colorHex: '#0284C7', description: 'Bài viết truyền thông, kịch bản hoặc thông điệp quảng cáo' },
      { name: 'Thiết kế ấn phẩm', code: 'DESIGN', hierarchyLevel: 0, iconName: 'CheckSquare', colorHex: '#EA580C', description: 'Thiết kế biểu ngữ, áp phích, hình ảnh hoặc tài liệu đồ họa' },
      { name: 'Kênh phân phối', code: 'CHANNEL', hierarchyLevel: 0, iconName: 'Sliders', colorHex: '#10B981', description: 'Triển khai trên các kênh mạng xã hội, báo đài hoặc quảng cáo' },
      { name: 'Tác vụ biên tập', code: 'SUBTASK', hierarchyLevel: -1, iconName: 'GitCommit', colorHex: '#6B7280', description: 'Nhiệm vụ soạn thảo nháp, duyệt bài hoặc chỉnh sửa ảnh' },
    ],
  },
  {
    id: 'AGILE_BASIC',
    name: 'Mặc định cơ bản',
    description: 'Cấu hình tiêu chuẩn tối giản gồm đúng 3 cấp độ: Hạng mục lớn, Nhiệm vụ và Nhiệm vụ con.',
    icon: 'Zap',
    badgeColor: '#10B981',
    types: [
      { name: 'Hạng mục lớn', code: 'EPIC', hierarchyLevel: 1, iconName: 'Zap', colorHex: '#9333EA', description: 'Hạng mục lớn bao quát nhiều công việc' },
      { name: 'Nhiệm vụ', code: 'TASK', hierarchyLevel: 0, iconName: 'CheckSquare', colorHex: '#3B82F6', description: 'Công việc tiêu chuẩn' },
      { name: 'Nhiệm vụ con', code: 'SUBTASK', hierarchyLevel: -1, iconName: 'GitCommit', colorHex: '#06B6D4', description: 'Công việc con trực thuộc công việc cha' },
    ],
  },
];

export function resolveDynamicTypeConfig(type) {
  if (!type) return WORK_ITEM_TYPE_CONFIG.TASK;
  const hex = type.colorHex || '#3B82F6';
  return {
    id: type.id,
    name: type.name || 'Task',
    code: type.code || 'TASK',
    hierarchyLevel: type.hierarchyLevel ?? 0,
    color: hex,
    bgColor: `${hex}1A`, // 10% opacity
    borderColor: `${hex}4D`, // 30% opacity
    icon: type.iconName || 'CheckSquare',
    description: type.description || '',
    isSubtask: type.isSubtask || type.hierarchyLevel === -1,
  };
}

