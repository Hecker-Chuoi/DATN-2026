/**
 * workspacePath.js - Sinh đường dẫn có tiền tố workspace.
 *
 * Workspace hiện tại nằm trong URL (/w/:workspaceId/...) và đó là NGUỒN SỰ THẬT
 * DUY NHẤT. Nhờ vậy refresh trang và deep-link tự đúng, nút Back undo được việc
 * đổi workspace, và mọi component con re-render qua useParams() mà không cần
 * code đồng bộ state.
 *
 * Toàn bộ link nội bộ phải đi qua helper này để việc thêm tiền tố không lan ra
 * rải rác khắp nơi.
 */
export function wsPath(workspaceId, sub = '') {
  if (!workspaceId) return sub || '/workspaces';
  const suffix = sub.startsWith('/') ? sub : `/${sub}`;
  return `/w/${workspaceId}${suffix === '/' ? '' : suffix}`;
}
