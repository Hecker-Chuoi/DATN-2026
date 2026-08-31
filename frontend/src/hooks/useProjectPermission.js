import { useState, useEffect, useCallback } from 'react';
import { projectApi } from '../api.js';

export function useProjectPermission(projectId) {
  const [permissions, setPermissions] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchPermissions = useCallback(async () => {
    if (!projectId) {
      setPermissions([]);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const res = await projectApi.getMyPermissions(projectId);
      if (res && res.data) {
        setPermissions(Array.isArray(res.data) ? res.data : []);
      }
    } catch (err) {
      console.warn('Could not load user project permissions:', err);
      setPermissions([]);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchPermissions();
  }, [fetchPermissions]);

  const can = useCallback(
    (permissionKey) => {
      if (!permissionKey) return false;
      return permissions.includes('PROJECT_ADMIN') || permissions.includes(permissionKey);
    },
    [permissions]
  );

  return {
    permissions,
    loading,
    can,
    refetchPermissions: fetchPermissions,
    isAdmin: can('PROJECT_ADMIN'),
    canCreateIssue: can('ISSUE_CREATE'),
    canEditIssue: can('ISSUE_EDIT'),
    canDeleteIssue: can('ISSUE_DELETE'),
    canTransitionIssue: can('ISSUE_TRANSITION'),
    canAssignIssue: can('ISSUE_ASSIGN'),
    canManageSprint: can('SPRINT_MANAGE'),
    canManageRelease: can('RELEASE_MANAGE'),
    canComment: can('COMMENT_CREATE'),
  };
}
