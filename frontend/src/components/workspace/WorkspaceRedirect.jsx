import { Navigate } from 'react-router-dom';
import { useWorkspace, LAST_WORKSPACE_KEY } from '../../context/WorkspaceContext';
import { wsPath } from '../../utils/workspacePath';

/**
 * Điều hướng từ route gốc (và các đường dẫn phẳng cũ) về workspace phù hợp.
 *
 * Thứ tự chọn: workspace vừa xem lần trước (nếu vẫn còn quyền) -> workspace đầu
 * tiên -> trang /workspaces để tạo mới.
 *
 * QUAN TRỌNG: khi đang loading thì render spinner, KHÔNG redirect. Nếu redirect
 * ngay lúc danh sách chưa về, lần paint đầu sẽ nhảy sang /workspaces rồi bật
 * ngược lại - người dùng thấy màn hình giật và mất một entry trong history.
 */
export default function WorkspaceRedirect({ sub = '/projects' }) {
  const { workspaces, loading } = useWorkspace();

  if (loading) {
    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#F8FAFC',
          fontSize: 13,
          color: '#94A3B8',
        }}
      >
        Đang tải workspace...
      </div>
    );
  }

  if (workspaces.length === 0) {
    return <Navigate to="/workspaces" replace />;
  }

  let remembered = null;
  try {
    remembered = localStorage.getItem(LAST_WORKSPACE_KEY);
  } catch {
    // Chế độ ẩn danh hoặc chặn site data: bỏ qua, dùng workspace đầu tiên.
  }

  const target = workspaces.find((w) => w.id === remembered) || workspaces[0];
  return <Navigate to={wsPath(target.id, sub)} replace />;
}
