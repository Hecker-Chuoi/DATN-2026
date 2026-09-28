import React from 'react';
import { usePermission } from '../../context/PermissionContext';

/**
 * Guard component ẩn/hiện JSX theo quyền trong dự án.
 * Usage: <Can projectId={projectId} perform="ISSUE_CREATE"><Button>Tạo</Button></Can>
 *
 * Lưu ý: quyền của dự án phải được tải trước (useProjectPermission(projectId) ở trang cha),
 * nếu chưa tải thì mặc định từ chối.
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
