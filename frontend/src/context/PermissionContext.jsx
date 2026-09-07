import React, { createContext, useContext } from 'react';

const PermissionContext = createContext(null);

/**
 * Provider providing permission evaluation capabilities across projects.
 * @param {Object} props
 * @param {Object} props.userPermissions Object containing globalRoles array and projectPermissions map (projectId -> permissionCodes array)
 */
export const PermissionProvider = ({ userPermissions, children }) => {
  const hasPermission = (projectId, permissionCode) => {
    if (!permissionCode) return true;

    // 1. Global Admin Bypass
    if (userPermissions?.globalRoles?.includes('ADMIN') || userPermissions?.roles?.includes('ADMIN')) {
      return true;
    }

    // 2. Project-scoped Permission Check
    if (projectId && userPermissions?.projectPermissions?.[projectId]) {
      const perms = userPermissions.projectPermissions[projectId];
      return Array.isArray(perms) && perms.includes(permissionCode);
    }

    return false;
  };

  return (
    <PermissionContext.Provider value={{ hasPermission, userPermissions }}>
      {children}
    </PermissionContext.Provider>
  );
};

export const usePermission = () => {
  const context = useContext(PermissionContext);
  if (!context) {
    // Fallback if rendered outside PermissionProvider
    return {
      hasPermission: () => true,
      userPermissions: null,
    };
  }
  return context;
};
