import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { projectApi, getUserRealmRoles } from '../api.js';

/**
 * PermissionContext – nguồn sự thật phía frontend về quyền của người dùng hiện tại.
 *
 * - Global roles: lấy từ realm_access.roles trong JWT (Keycloak) + roles trong /users/me.
 * - Project permissions: gọi GET /projects/{id}/permissions/my (cache theo projectId).
 *   Backend trả 403 nếu user không phải thành viên -> status 'forbidden'.
 *
 * Frontend chỉ dùng thông tin này để ẩn/hiện UI; backend luôn là nơi quyết định cuối cùng.
 */
const PermissionContext = createContext(null);

const normalizeRole = (r) => String(r || '').toUpperCase().replace(/^ROLE_/, '');

export const PermissionProvider = ({ currentUser, children }) => {
  // projectId -> { status: 'loading'|'ready'|'forbidden'|'notfound'|'error', roleName, isLead, isGlobalAdmin, permissions[] }
  const [projectPermissions, setProjectPermissions] = useState({});
  const inflight = useRef({});

  const globalRoles = useMemo(() => {
    const fromUser = Array.isArray(currentUser?.roles) ? currentUser.roles : [];
    const fromToken = getUserRealmRoles();
    return Array.from(new Set([...fromUser, ...fromToken].map(normalizeRole).filter(Boolean)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser?.id, currentUser?.roles]);

  const isGlobalAdmin = globalRoles.includes('ADMIN');

  // Đổi tài khoản -> xóa cache quyền dự án
  useEffect(() => {
    setProjectPermissions({});
    inflight.current = {};
  }, [currentUser?.id]);

  const loadProjectPermissions = useCallback(async (projectId, { force = false } = {}) => {
    if (!projectId) return null;
    if (!force && inflight.current[projectId]) return inflight.current[projectId];

    const promise = (async () => {
      setProjectPermissions((prev) => ({
        ...prev,
        [projectId]: { ...(prev[projectId] || {}), status: 'loading' },
      }));
      try {
        const res = await projectApi.getMyPermissions(projectId);
        const data = res?.data || {};
        const entry = {
          status: 'ready',
          roleName: data.roleName || null,
          isLead: Boolean(data.isLead),
          isGlobalAdmin: Boolean(data.isGlobalAdmin),
          permissions: Array.isArray(data.permissions) ? data.permissions : [],
          error: null,
        };
        setProjectPermissions((prev) => ({ ...prev, [projectId]: entry }));
        return entry;
      } catch (err) {
        let status = 'error';
        if (err?.status === 403) status = 'forbidden';
        else if (err?.status === 404) status = 'notfound';
        const entry = { status, roleName: null, isLead: false, isGlobalAdmin: false, permissions: [], error: err };
        setProjectPermissions((prev) => ({ ...prev, [projectId]: entry }));
        return entry;
      } finally {
        delete inflight.current[projectId];
      }
    })();

    inflight.current[projectId] = promise;
    return promise;
  }, []);

  const invalidateProject = useCallback((projectId) => {
    setProjectPermissions((prev) => {
      if (!projectId || !prev[projectId]) return prev;
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
  }, []);

  const hasPermission = useCallback(
    (projectId, permissionCode) => {
      if (!permissionCode) return true;
      if (isGlobalAdmin) return true;
      if (!projectId) return false;
      const entry = projectPermissions[projectId];
      if (!entry || entry.status !== 'ready') return false;
      if (entry.isGlobalAdmin) return true;
      return entry.permissions.includes(permissionCode);
    },
    [isGlobalAdmin, projectPermissions]
  );

  const value = useMemo(
    () => ({
      currentUser,
      globalRoles,
      isGlobalAdmin,
      projectPermissions,
      loadProjectPermissions,
      invalidateProject,
      hasPermission,
    }),
    [currentUser, globalRoles, isGlobalAdmin, projectPermissions, loadProjectPermissions, invalidateProject, hasPermission]
  );

  return <PermissionContext.Provider value={value}>{children}</PermissionContext.Provider>;
};

let warnedNoProvider = false;

export const usePermission = () => {
  const context = useContext(PermissionContext);
  if (!context) {
    // Ngoài PermissionProvider: từ chối mặc định (deny by default) thay vì cho phép tất cả
    if (!warnedNoProvider) {
      console.warn('usePermission() được gọi ngoài PermissionProvider – mọi quyền sẽ bị từ chối.');
      warnedNoProvider = true;
    }
    return {
      currentUser: null,
      globalRoles: [],
      isGlobalAdmin: false,
      projectPermissions: {},
      loadProjectPermissions: async () => null,
      invalidateProject: () => {},
      hasPermission: () => false,
    };
  }
  return context;
};
