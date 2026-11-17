/**
 * workspaceRole.js - Thang bậc vai trò trong workspace.
 *
 * Khác useProjectPermission (kiểm tra thuộc tập hợp permission key, có
 * PROJECT_ADMIN làm override), vai trò workspace là so sánh CÓ THỨ TỰ.
 * Cùng hình dạng, khác đại số - đừng nhầm hai cái.
 *
 * Thứ tự phải khớp enum WorkspaceRole của backend.
 */
export const ROLE_RANK = {
  GUEST: 0,
  MEMBER: 1,
  ADMIN: 2,
  OWNER: 3,
};

export const ROLE_LABELS = {
  OWNER: 'Chủ sở hữu',
  ADMIN: 'Quản trị viên',
  MEMBER: 'Thành viên',
  GUEST: 'Khách',
};

/** Màu badge, lấy từ bộ token màu đang dùng inline trong các trang hiện có. */
export const ROLE_COLORS = {
  OWNER: { bg: '#EFF6FF', fg: '#2563EB', border: '#BFDBFE' },
  ADMIN: { bg: '#F5F3FF', fg: '#7C3AED', border: '#DDD6FE' },
  MEMBER: { bg: '#F8FAFC', fg: '#475569', border: '#E2E8F0' },
  GUEST: { bg: '#FFFBEB', fg: '#B45309', border: '#FDE68A' },
};

export const ALL_ROLES = ['OWNER', 'ADMIN', 'MEMBER', 'GUEST'];

/**
 * Vai trò `role` có bậc bằng hoặc cao hơn `min` hay không.
 * Trả về false khi role không xác định (chưa tải xong / không phải thành viên),
 * để UI mặc định là ẩn chứ không phải hiện.
 */
export function roleAtLeast(role, min) {
  const actual = ROLE_RANK[role];
  const required = ROLE_RANK[min];
  if (actual === undefined || required === undefined) return false;
  return actual >= required;
}

export function roleLabel(role) {
  return ROLE_LABELS[role] || role || '—';
}
