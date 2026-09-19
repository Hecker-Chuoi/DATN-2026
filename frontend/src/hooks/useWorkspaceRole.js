/**
 * useWorkspaceRole.js - Gate UI theo vai trò trong workspace đang xem.
 *
 * Cùng hình dạng trả về với useProjectPermission (cờ loading + các boolean đặt
 * tên) nhưng KHÔNG tốn request nào ở đường thường: vai trò đã về kèm
 * workspaceApi.listMine() dưới dạng field myRole, nên switcher render badge cho
 * N workspace mà không cần N lời gọi.
 *
 * Chỉ khi workspaceId không có trong danh sách (deep-link vào workspace mình
 * không thuộc) thì mới gọi getMyMembership một lần, để phân biệt "không phải
 * thành viên" với "danh sách chưa tải xong".
 */
import { useEffect, useState } from 'react';
import { useWorkspace } from '../context/WorkspaceContext';
import { workspaceApi } from '../api/workspaceServiceApi';
import { roleAtLeast } from '../utils/workspaceRole';

export function useWorkspaceRole() {
  const { currentWorkspaceId, currentWorkspace, loading: listLoading } = useWorkspace();
  const [probedRole, setProbedRole] = useState(null);
  const [probing, setProbing] = useState(false);
  const [notMember, setNotMember] = useState(false);

  const roleFromList = currentWorkspace?.myRole || null;

  useEffect(() => {
    setProbedRole(null);
    setNotMember(false);

    if (!currentWorkspaceId || listLoading || roleFromList) return;

    let cancelled = false;
    setProbing(true);
    workspaceApi
      .getMyMembership(currentWorkspaceId)
      .then((res) => {
        if (!cancelled) setProbedRole(res?.data?.role || null);
      })
      .catch(() => {
        // Backend trả 404 cho người ngoài (cố tình không 403 để không rò rỉ
        // sự tồn tại của workspace).
        if (!cancelled) setNotMember(true);
      })
      .finally(() => {
        if (!cancelled) setProbing(false);
      });

    return () => {
      cancelled = true;
    };
  }, [currentWorkspaceId, listLoading, roleFromList]);

  const role = roleFromList || probedRole;
  const loading = listLoading || probing;

  return {
    role,
    loading,
    notMember: notMember && !loading,
    isOwner: role === 'OWNER',
    isAdmin: roleAtLeast(role, 'ADMIN'),
    canView: !!role,
    canCreateTeam: roleAtLeast(role, 'MEMBER'),
    canCreateProject: roleAtLeast(role, 'MEMBER'),
    canManageMembers: roleAtLeast(role, 'ADMIN'),
    canManageWorkspace: roleAtLeast(role, 'ADMIN'),
    canDeleteWorkspace: role === 'OWNER',
    atLeast: (min) => roleAtLeast(role, min),
  };
}

export default useWorkspaceRole;
