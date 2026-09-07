import React from 'react';
import { usePermission } from '../../context/PermissionContext';

/**
 * Guard Component for conditional rendering of JSX elements based on user permissions.
 * Usage: <Can projectId={projectId} perform="PROJECT_EDIT"><Button>Edit</Button></Can>
 */
export const Can = ({ projectId, perform, fallback = null, children }) => {
  const { hasPermission } = usePermission();
  const allowed = hasPermission(projectId, perform);

  if (!allowed) {
    return fallback;
  }

  return <>{children}</>;
};

export default Can;
