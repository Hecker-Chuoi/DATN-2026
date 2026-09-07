import { usePermission } from '../context/PermissionContext';

/**
 * Custom hook to check project-level permissions easily in components.
 * @param {string} projectId The UUID of the project
 * @returns {(permissionCode: string) => boolean} Function to evaluate if user has permission
 */
export const useProjectPermission = (projectId) => {
  const { hasPermission } = usePermission();
  return (permissionCode) => hasPermission(projectId, permissionCode);
};

export default useProjectPermission;
