import { useCallback, useEffect } from 'react';
import { usePermission } from '../context/PermissionContext';

/**
 * Hook kiểm tra quyền của người dùng hiện tại trong một dự án.
 *
 * Tự động tải GET /projects/{id}/permissions/my lần đầu (cache trong PermissionContext).
 *
 * @param {string} projectId UUID dự án
 * @returns {{
 *   loading: boolean, status: string, forbidden: boolean, notFound: boolean, error: any,
 *   role: string|null, isLead: boolean, isGlobalAdmin: boolean, isAdmin: boolean,
 *   canCreateIssue: boolean, canManageSprint: boolean, permissions: string[],
 *   can: (permissionCode: string) => boolean, refresh: () => Promise<any>
 * }}
 */
export const useProjectPermission = (projectId) => {
  const ctx = usePermission();
  const entry = projectId ? ctx.projectPermissions[projectId] : undefined;
  const needsLoad = Boolean(projectId) && !entry;
  const { loadProjectPermissions } = ctx;

  useEffect(() => {
    if (needsLoad) {
      loadProjectPermissions(projectId);
    }
  }, [needsLoad, projectId, loadProjectPermissions]);

  const can = useCallback(
    (permissionCode) => ctx.hasPermission(projectId, permissionCode),
    [ctx, projectId]
  );

  const status = entry?.status || (projectId ? 'loading' : 'idle');
  const permissions = entry?.permissions || [];
  const isGlobalAdmin = ctx.isGlobalAdmin || Boolean(entry?.isGlobalAdmin);
  const isAdmin = isGlobalAdmin || permissions.includes('PROJECT_ADMIN');

  return {
    loading: status === 'loading',
    status,
    forbidden: status === 'forbidden',
    notFound: status === 'notfound',
    error: entry?.error || null,
    role: entry?.roleName || (isGlobalAdmin ? 'ADMIN' : null),
    isLead: Boolean(entry?.isLead),
    isGlobalAdmin,
    isAdmin,
    canCreateIssue: can('ISSUE_CREATE'),
    canManageSprint: can('SPRINT_MANAGE'),
    permissions,
    can,
    refresh: () => loadProjectPermissions(projectId, { force: true }),
  };
};

export default useProjectPermission;
