import { projectApi, workItemApi } from '../api.js';
import { isScrumProject } from '../utils/projectType.js';
import { wsPath } from '../utils/workspacePath.js';

/**
 * Link "/browse/{mã}[?comment=…]" trong thông báo và email không mang workspace/dự án
 * (công việc có thể đã chuyển chỗ). Hàm này tra mã → dự án → đường dẫn thật:
 * /w/{ws}/projects/{pid}/{backlog|board}?item={id}[&comment={cid}].
 *
 * Lỗi ném ra có `status` (403/404…) và `friendlyMessage` tiếng Việt.
 */
export async function resolveItemPath(code, { commentId } = {}) {
  try {
    const itemRes = await workItemApi.getByCode(code, { skipErrorToast: true });
    const item = itemRes?.data;
    if (!item?.id || !item?.projectId) throw Object.assign(new Error('not found'), { status: 404 });
    const projectRes = await projectApi.getById(item.projectId, { skipErrorToast: true });
    const project = projectRes?.data;
    if (!project?.workspaceId) throw Object.assign(new Error('not found'), { status: 404 });
    const view = isScrumProject(project) ? 'backlog' : 'board';
    const params = new URLSearchParams({ item: item.id });
    if (commentId) params.set('comment', commentId);
    return wsPath(project.workspaceId, `/projects/${project.id}/${view}?${params.toString()}`);
  } catch (err) {
    err.friendlyMessage = err?.status === 404
      ? `Không tìm thấy công việc ${code}. Có thể nó đã bị xoá.`
      : err?.status === 403
        ? `Bạn không còn quyền xem công việc ${code}.`
        : `Không mở được công việc ${code}. Vui lòng thử lại.`;
    throw err;
  }
}

/** Tách "/browse/ABC-12?comment=…" → { code, commentId }; link khác trả null. */
export function parseBrowseLink(link) {
  const m = /^\/browse\/([^/?#]+)(?:\?([^#]*))?$/.exec(String(link || ''));
  if (!m) return null;
  let code = m[1];
  try {
    code = decodeURIComponent(code);
  } catch {
    // giữ nguyên
  }
  const commentId = new URLSearchParams(m[2] || '').get('comment') || undefined;
  return { code, commentId };
}
