/**
 * WorkspaceContext.jsx - Danh sách workspace của người dùng + workspace đang xem.
 *
 * Vì sao là React context RIÊNG chứ không dùng Outlet context của
 * WorkspaceLayout: (1) Sidebar là SIBLING của <Outlet> nên cấu trúc không cho nó
 * đọc Outlet context, mà chính nó cần danh sách workspace để render switcher;
 * (2) CreateProjectModal được mount từ HAI parent khác nhau (WorkspaceLayout và
 * ProjectListPage), prop-drilling sẽ thành hai chỗ phải nối dây.
 *
 * Outlet context cũ vẫn được giữ và forward nguyên để các trang hiện có
 * (ProjectListPage, ProjectDetailPage) chạy không cần sửa.
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { workspaceApi } from '../api/workspaceServiceApi';

const WorkspaceContext = createContext(null);

export const LAST_WORKSPACE_KEY = 'last_workspace_id';

/**
 * Lấy workspace id từ pathname thay vì useParams().
 *
 * Provider được mount ở route CHA (để trang /workspaces - vốn chưa chọn
 * workspace - cũng đọc được danh sách). Ở vị trí đó useParams() chỉ trả về param
 * của chính route cha, KHÔNG thấy :workspaceId khai báo ở route con, nên
 * useParams() sẽ luôn rỗng. Parse pathname là cách đọc đúng ở mọi độ sâu.
 *
 * Pattern /w/:id ổn định vì wsPath() là nơi duy nhất sinh ra đường dẫn này.
 */
const WS_PATH_RE = /^\/w\/([^/]+)/;

export function WorkspaceProvider({ children }) {
  const { pathname } = useLocation();
  const workspaceId = WS_PATH_RE.exec(pathname)?.[1] || null;
  const [workspaces, setWorkspaces] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isCreateOpen, setCreateOpen] = useState(false);

  const refreshWorkspaces = useCallback(async () => {
    try {
      // Interceptor của api.js trả về ENVELOPE ApiResponse, nên payload nằm ở .data
      const res = await workspaceApi.listMine();
      setWorkspaces(Array.isArray(res?.data) ? res.data : []);
      setError(null);
    } catch (err) {
      // Interceptor của api.js đã toast lỗi rồi, ở đây chỉ giữ state để render.
      setError(err.body?.message || err.message);
      setWorkspaces([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshWorkspaces();
  }, [refreshWorkspaces]);

  // Ghi lại workspace vừa xem để lần đăng nhập sau quay về đúng chỗ. Chỉ là GỢI Ý
  // cho việc redirect ở route gốc - URL vẫn là nguồn sự thật.
  useEffect(() => {
    if (workspaceId) {
      try {
        localStorage.setItem(LAST_WORKSPACE_KEY, workspaceId);
      } catch {
        // Chế độ ẩn danh / chặn site data: bỏ qua, không ảnh hưởng chức năng.
      }
    }
  }, [workspaceId]);

  const currentWorkspace = useMemo(
    () => workspaces.find((w) => w.id === workspaceId) || null,
    [workspaces, workspaceId],
  );

  const value = useMemo(
    () => ({
      workspaces,
      loading,
      error,
      refreshWorkspaces,
      currentWorkspaceId: workspaceId || null,
      currentWorkspace,
      myRole: currentWorkspace?.myRole || null,
      isCreateOpen,
      openCreateWorkspace: () => setCreateOpen(true),
      closeCreateWorkspace: () => setCreateOpen(false),
    }),
    [workspaces, loading, error, refreshWorkspaces, workspaceId, currentWorkspace, isCreateOpen],
  );

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace() {
  const ctx = useContext(WorkspaceContext);
  if (!ctx) {
    throw new Error('useWorkspace phải được dùng bên trong <WorkspaceProvider>');
  }
  return ctx;
}

export default WorkspaceContext;
