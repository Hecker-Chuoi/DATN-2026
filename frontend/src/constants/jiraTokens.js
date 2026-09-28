/**
 * jiraTokens.js - Bang mau chuan Jira Cloud duoi dang object JavaScript.
 *
 * Du an KHONG dung TailwindCSS (khong cai, khong config), moi thanh phan deu style inline,
 * nen cac token trong tai lieu UI spec duoc giu o day thay vi trong tailwind.config.
 */

// Be mat & chu
export const SURFACE = {
  page: '#FFFFFF',
  subtle: '#F8FAFC',
  track: '#F1F5F9',
  border: '#E2E8F0',
  borderStrong: '#CBD5E1',
  text: '#0F172A',
  textMuted: '#64748B',
  textLight: '#94A3B8',
  primary: '#2563EB',
};

// Nhom trang thai theo StatusCategory
export const STATUS_TOKENS = {
  TODO: { label: 'Cần làm', color: '#42526E', bg: '#DFE1E6', border: '#C1C7D0' },
  IN_PROGRESS: { label: 'Đang làm', color: '#0052CC', bg: '#DEEBFF', border: '#B3D4FF' },
  DONE: { label: 'Hoàn thành', color: '#00875A', bg: '#E3FCEF', border: '#ABF5D1' },
};

// Do uu tien
export const PRIORITY_TOKENS = {
  HIGHEST: { label: 'Cao nhất', color: '#DE350B' },
  HIGH: { label: 'Cao', color: '#FF5630' },
  MEDIUM: { label: 'Trung bình', color: '#FFAB00' },
  LOW: { label: 'Thấp', color: '#36B37E' },
  LOWEST: { label: 'Thấp nhất', color: '#6B778C' },
};

// Loai cong viec
export const TYPE_TOKENS = {
  EPIC: { label: 'Epic', color: '#6554C0', bg: '#F3F0FF', border: '#DDD6FE' },
  STORY: { label: 'Story', color: '#36B37E', bg: '#ECFDF5', border: '#A7F3D0' },
  TASK: { label: 'Task', color: '#0052CC', bg: '#EFF6FF', border: '#BFDBFE' },
  BUG: { label: 'Bug', color: '#E5493A', bg: '#FEF2F2', border: '#FECACA' },
  SUBTASK: { label: 'Sub-task', color: '#4C9AFF', bg: '#F0F9FF', border: '#BAE6FD' },
  SUB_TASK: { label: 'Sub-task', color: '#4C9AFF', bg: '#F0F9FF', border: '#BAE6FD' },
};

export const FONT_STACK =
  'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';

export function statusToken(category) {
  return STATUS_TOKENS[category] || STATUS_TOKENS.TODO;
}

export function typeToken(typeCode, typeName) {
  const raw = String(typeCode || typeName || 'TASK').toUpperCase().replace('-', '_');
  return TYPE_TOKENS[raw] || TYPE_TOKENS[raw.replace('_', '')] || TYPE_TOKENS.TASK;
}

export function priorityToken(priority) {
  return PRIORITY_TOKENS[priority] || PRIORITY_TOKENS.MEDIUM;
}
