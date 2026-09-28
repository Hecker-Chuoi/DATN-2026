/**
 * avatarColor.js - Sinh mau avatar on dinh tu ten nguoi dung.
 *
 * Tach ra tu MemberSelect.jsx de AvatarStack, the tren Board va cac hang Backlog
 * dung chung mot bang mau, tranh moi noi mot kieu.
 */
export const AVATAR_COLORS = [
  '#2563EB', '#7C3AED', '#DB2777', '#EA580C', '#059669', '#0891B2', '#4F46E5', '#D97706',
];

export function getAvatarColor(name = '') {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

/** Lay chu cai dau de hien thi trong avatar tron. */
export function getInitials(name = '', max = 2) {
  const parts = String(name).trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  return parts.slice(0, max).map((p) => p[0].toUpperCase()).join('');
}
