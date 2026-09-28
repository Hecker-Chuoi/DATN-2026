import { ROLE_COLORS, roleLabel } from '../../utils/workspaceRole';

/**
 * Pill hiển thị vai trò workspace. Dùng inline style theo đúng quy ước của các
 * component xung quanh: Sidebar, TeamDetailPage, CreateTeamModal... đều 100%
 * inline style, còn các class trong index.css chỉ dùng cho trang auth/admin.
 */
export default function RoleBadge({ role, size = 'sm' }) {
  if (!role) return null;
  const c = ROLE_COLORS[role] || ROLE_COLORS.MEMBER;
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: size === 'sm' ? '1px 6px' : '3px 10px',
        fontSize: size === 'sm' ? 10 : 12,
        fontWeight: 600,
        lineHeight: 1.6,
        borderRadius: 6,
        background: c.bg,
        color: c.fg,
        border: `1px solid ${c.border}`,
        whiteSpace: 'nowrap',
      }}
    >
      {roleLabel(role)}
    </span>
  );
}
