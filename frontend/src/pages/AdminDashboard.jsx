import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { userApi, authApi } from '../api';
import toast from 'react-hot-toast';
import {
  Users, ShieldCheck, Search, RefreshCw, LogOut, UserCheck,
  Shield, Edit3, X, Check, Server, KeyRound, AlertCircle, LayoutDashboard,
  FolderKanban, Sparkles, Key, Trash2, UserPlus
} from 'lucide-react';
import AdminProjectManagement from '../components/admin/AdminProjectManagement.jsx';
import ConfirmModal from '../components/modals/ConfirmModal.jsx';

export default function AdminDashboard({ onLogout }) {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('projects'); // 'projects' | 'users'
  const [currentUser, setCurrentUser] = useState(null);
  const [users, setUsers] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Reusable Pagination & Sorting state
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [sort, setSort] = useState('createdAt,desc');
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [debouncedSearch, setDebouncedSearch] = useState('');

  // Role Modal state
  const [selectedUser, setSelectedUser] = useState(null);
  const [selectedRoles, setSelectedRoles] = useState([]);
  const [updatingRole, setUpdatingRole] = useState(false);
  const [modalSuccess, setModalSuccess] = useState(null);
  const [modalError, setModalError] = useState(null);

  const availableRoles = ['ADMIN', 'USER', 'MANAGER'];

  // Fetch Current Logged-in User Profile
  const fetchMe = async () => {
    try {
      const meRes = await userApi.me();
      setCurrentUser(meRes?.data);
    } catch (err) {
      console.error('Error fetching current user:', err);
    }
  };

  // Fetch Paginated, Sorted, and Searched User list
  const fetchUsers = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await userApi.listAll({
        page,
        size,
        sort,
        search: debouncedSearch
      });
      const pageData = res?.data || {};
      setUsers(pageData.content || []);
      setTotalPages(pageData.totalPages || 0);
      setTotalElements(pageData.totalElements || 0);
    } catch (err) {
      console.error('Error fetching users:', err);
      if (err.status === 403) {
        setError('403 Forbidden: Bạn cần có quyền ADMIN để truy cập danh sách và quản lý người dùng.');
      } else {
        setError(err.message || 'Lỗi khi kết nối với máy chủ.');
      }
    } finally {
      setLoading(false);
    }
  };

  // Load profile on mount
  useEffect(() => {
    fetchMe();
  }, []);

  // Debounce search query
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(searchQuery);
      setPage(0); // Reset page to 0 on search
    }, 400);
    return () => clearTimeout(handler);
  }, [searchQuery]);

  // Fetch users when parameters change
  useEffect(() => {
    if (activeTab === 'users') {
      fetchUsers();
    }
  }, [page, size, sort, debouncedSearch, activeTab]);

  // Handle Logout
  const handleLogout = () => {
    if (onLogout) {
      onLogout();
    } else {
      adminTokenStore.clear();
      navigate('/admin/login');
    }
  };

  // Open Update Role Modal
  const handleOpenRoleModal = (user) => {
    setSelectedUser(user);
    setSelectedRoles(user.roles || []);
    setModalSuccess(null);
    setModalError(null);
  };

  // Toggle role selection
  const handleToggleRole = (role) => {
    if (selectedRoles.includes(role)) {
      setSelectedRoles(selectedRoles.filter((r) => r !== role));
    } else {
      setSelectedRoles([...selectedRoles, role]);
    }
  };

  // Submit Role Update
  const handleSaveRoles = async () => {
    if (!selectedUser) return;
    if (selectedRoles.length === 0) {
      setModalError('Vui lòng chọn ít nhất 1 vai trò cho người dùng.');
      return;
    }

    setUpdatingRole(true);
    setModalError(null);
    setModalSuccess(null);

    try {
      await userApi.updateRoles(selectedUser.id, selectedRoles);
      setModalSuccess(`Cập nhật vai trò cho ${selectedUser.username} thành công!`);

      // Refresh list
      await fetchUsers();

      setTimeout(() => {
        setSelectedUser(null);
      }, 1200);
    } catch (err) {
      setModalError(err.message || 'Cập nhật vai trò thất bại.');
    } finally {
      setUpdatingRole(false);
    }
  };

  // Add User modal state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createUsername, setCreateUsername] = useState('');
  const [createPassword, setCreatePassword] = useState('');
  const [creatingUser, setCreatingUser] = useState(false);

  // Reset Password modal state
  const [resetUser, setResetUser] = useState(null);
  const [newPassword, setNewPassword] = useState('');
  const [resettingPassword, setResettingPassword] = useState(false);

  // Reusable Confirmation Modal Config state
  const [confirmConfig, setConfirmConfig] = useState({
    isOpen: false,
    title: '',
    message: '',
    confirmText: '',
    cancelText: '',
    type: 'danger',
    loading: false,
    onConfirm: () => { }
  });

  // Admin create user
  const handleCreateUser = async () => {
    if (!createUsername.trim()) {
      toast.error('Vui lòng nhập tên đăng nhập.');
      return;
    }
    if (!createPassword || createPassword.length < 6) {
      toast.error('Mật khẩu phải từ 6 ký tự.');
      return;
    }
    setCreatingUser(true);
    try {
      await userApi.adminCreate(createUsername.trim(), createPassword);
      toast.success('Tạo người dùng thành công!');
      setShowCreateModal(false);
      setCreateUsername('');
      setCreatePassword('');
      await fetchUsers(); // Refresh list
    } catch (err) {
      toast.error(err.message || 'Tạo người dùng thất bại.');
    } finally {
      setCreatingUser(false);
    }
  };

  // Reset password
  const handleResetPassword = async () => {
    if (!resetUser) return;
    if (!newPassword || newPassword.length < 6) {
      toast.error('Mật khẩu phải từ 6 ký tự.');
      return;
    }
    setResettingPassword(true);
    try {
      await userApi.resetPassword(resetUser.id, newPassword);
      toast.success(`Đổi mật khẩu cho ${resetUser.username} thành công!`);
      setResetUser(null);
      setNewPassword('');
    } catch (err) {
      toast.error(err.message || 'Đổi mật khẩu thất bại.');
    } finally {
      setResettingPassword(false);
    }
  };

  // Soft delete user via Reusable ConfirmModal
  const handleDeleteUser = (user) => {
    setConfirmConfig({
      isOpen: true,
      title: 'Xóa tài khoản',
      message: `Bạn có chắc chắn muốn xóa tài khoản "${user.username}"? Hành động này sẽ khóa tài khoản trên Keycloak và thu hồi quyền thành viên nhóm/task liên quan.`,
      confirmText: 'Xóa tài khoản',
      cancelText: 'Hủy',
      type: 'danger',
      loading: false,
      onConfirm: async () => {
        setConfirmConfig((prev) => ({ ...prev, loading: true }));
        try {
          await userApi.delete(user.id);
          toast.success(`Xóa tài khoản "${user.username}" thành công!`);
          setConfirmConfig((prev) => ({ ...prev, isOpen: false }));
          await fetchUsers(); // Refresh list
        } catch (err) {
          toast.error(err.message || 'Xóa tài khoản thất bại.');
          setConfirmConfig((prev) => ({ ...prev, loading: false }));
        }
      }
    });
  };

  // Confirm logout
  const handleLogoutClick = () => {
    setConfirmConfig({
      isOpen: true,
      title: 'Đăng xuất',
      message: 'Bạn có chắc chắn muốn đăng xuất khỏi trang quản trị hệ thống không?',
      confirmText: 'Đăng xuất',
      cancelText: 'Hủy',
      type: 'warning',
      loading: false,
      onConfirm: () => {
        setConfirmConfig((prev) => ({ ...prev, isOpen: false }));
        handleLogout();
      }
    });
  };

  // Helper to change sorting field/direction
  const handleSortChange = (field) => {
    const currentField = sort.split(',')[0];
    const currentDir = sort.split(',')[1] || 'asc';
    let newDir = 'asc';
    if (currentField === field && currentDir === 'asc') {
      newDir = 'desc';
    }
    setSort(`${field},${newDir}`);
    setPage(0); // Reset to first page
  };

  // Helper to render sort direction indicator
  const getSortIcon = (field) => {
    const currentField = sort.split(',')[0];
    const currentDir = sort.split(',')[1] || 'asc';
    if (currentField !== field) {
      return <span style={{ color: '#94a3b8', marginLeft: 6, fontSize: '11px', fontWeight: 'normal' }}>⇅</span>;
    }
    return currentDir === 'asc' ?
      <span style={{ color: 'var(--accent, #ea580c)', marginLeft: 6, fontSize: '11px' }}>▲</span> :
      <span style={{ color: 'var(--accent, #ea580c)', marginLeft: 6, fontSize: '11px' }}>▼</span>;
  };

  return (
    <div className="admin-container">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-header">
          <ShieldCheck size={26} />
          <span>Quản trị DATN</span>
        </div>

        <nav className="sidebar-nav">
          <button
            type="button"
            className={`nav-item ${activeTab === 'projects' ? 'active' : ''}`}
            onClick={() => setActiveTab('projects')}
            style={{ width: '100%', background: 'none', border: 'none', textAlign: 'left', cursor: 'pointer' }}
          >
            <FolderKanban size={18} />
            <span>Quản Lý Dự Án</span>
          </button>

          <button
            type="button"
            className={`nav-item ${activeTab === 'users' ? 'active' : ''}`}
            onClick={() => setActiveTab('users')}
            style={{ width: '100%', background: 'none', border: 'none', textAlign: 'left', cursor: 'pointer' }}
          >
            <Users size={18} />
            <span>Quản Lý Người Dùng</span>
          </button>

          <a href="#keycloak" className="nav-item" onClick={(e) => { e.preventDefault(); alert('Trang cấu hình Keycloak IAM'); }}>
            <KeyRound size={18} />
            <span>Xác thực Keycloak</span>
          </a>
          <a href="#gateway" className="nav-item" onClick={(e) => { e.preventDefault(); alert('Trang thông số API Gateway'); }}>
            <Server size={18} />
            <span>Nhật ký Cổng kết nối</span>
          </a>
        </nav>

        <div className="sidebar-footer">
          <button
            onClick={handleLogoutClick}
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 10,
              padding: '10px 16px',
              background: '#1e293b',
              color: '#f8fafc',
              border: '1px solid #334155',
              borderRadius: 8,
              cursor: 'pointer',
              fontWeight: 500,
              fontSize: 14
            }}
          >
            <LogOut size={16} />
            <span>Đăng xuất</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="main-content">
        {/* Top Header */}
        <header className="admin-header">
          <div>
            <span style={{ fontSize: 13, color: '#64748b', fontWeight: 500 }}>Bảng Quản Trị Hệ Thống</span>
            <h1 style={{ fontSize: 20, fontWeight: 700, fontFamily: 'var(--font-heading)' }}>
              {activeTab === 'projects' ? 'Quản lý Dự án & Mẫu quy trình' : 'Quản lý Người dùng & Phân quyền'}
            </h1>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            {currentUser && (
              <div className="user-badge">
                <div className="user-avatar-small">
                  {currentUser.username ? currentUser.username.charAt(0).toUpperCase() : 'U'}
                </div>
                <div className="user-info-text">
                  <div style={{ fontWeight: 600 }}>{currentUser.username}</div>
                  <div style={{ fontSize: 11, color: '#64748b' }}>{currentUser.email}</div>
                </div>
                {currentUser.roles?.map((r) => (
                  <span key={r} className={`role-tag ${r.toLowerCase()}`}>
                    {r}
                  </span>
                ))}
              </div>
            )}

            <button
              onClick={handleLogoutClick}
              title="Đăng xuất khỏi hệ thống quản trị"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '6px 14px',
                borderRadius: 8,
                border: '1px solid #fee2e2',
                backgroundColor: '#fff5f5',
                color: '#dc2626',
                cursor: 'pointer',
                fontSize: 13,
                fontWeight: 600,
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = '#fee2e2';
                e.currentTarget.style.borderColor = '#fca5a5';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = '#fff5f5';
                e.currentTarget.style.borderColor = '#fee2e2';
              }}
            >
              <LogOut size={14} />
              <span>Đăng xuất</span>
            </button>
          </div>
        </header>

        {/* Body Content */}
        <div className="admin-body" style={{ padding: 24 }}>
          {activeTab === 'projects' ? (
            <AdminProjectManagement />
          ) : (
            <>
              {error && (
                <div style={{
                  background: '#fef2f2',
                  color: '#dc2626',
                  padding: '16px 20px',
                  borderRadius: 12,
                  fontSize: 14,
                  marginBottom: 24,
                  border: '1px solid #fecaca',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12
                }}>
                  <AlertCircle size={24} />
                  <div>
                    <div style={{ fontWeight: 700 }}>Không thể tải danh sách người dùng</div>
                    <div>{error}</div>
                  </div>
                </div>
              )}

              {/* Table Container Card */}
              <div className="table-card">
                <div className="table-toolbar">
                  <div className="search-box">
                    <Search size={16} className="search-icon" />
                    <input
                      type="text"
                      placeholder="Tìm theo tên đăng nhập, thư điện tử hoặc họ tên..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                    />
                  </div>

                  <div style={{ display: 'flex', gap: 12 }}>
                    <button
                      className="btn-primary"
                      style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '0 16px', height: 38, fontSize: 13, cursor: 'pointer', border: 'none', borderRadius: 8, background: 'var(--accent, #ea580c)', color: '#fff', fontWeight: 600 }}
                      onClick={() => setShowCreateModal(true)}
                    >
                      <UserPlus size={15} />
                      <span>Thêm người dùng</span>
                    </button>
                    <button className="action-btn" onClick={fetchUsers} disabled={loading}>
                      <RefreshCw size={15} className={loading ? 'spin' : ''} />
                      <span>Làm mới</span>
                    </button>
                  </div>
                </div>

                {loading ? (
                  <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>
                    Đang tải dữ liệu từ Identity Service...
                  </div>
                ) : users.length === 0 ? (
                  <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>
                    {error ? 'Không có quyền truy cập danh sách' : 'Không tìm thấy người dùng phù hợp.'}
                  </div>
                ) : (
                  <>
                    <div style={{ overflowX: 'auto', width: '100%' }}>
                      <table className="custom-table" style={{ width: '100%', minWidth: '950px', tableLayout: 'auto' }}>
                        <thead>
                          <tr>
                            <th onClick={() => handleSortChange('username')} style={{ cursor: 'pointer', userSelect: 'none' }}>
                              Người dùng {getSortIcon('username')}
                            </th>
                            <th>Email</th>
                            <th>Họ & Tên</th>
                            <th>Quyền Hạn</th>
                            <th>Trạng Thái</th>
                            <th onClick={() => handleSortChange('createdAt')} style={{ cursor: 'pointer', userSelect: 'none' }}>
                              Ngày tham gia {getSortIcon('createdAt')}
                            </th>
                            <th style={{ textAlign: 'right', width: '130px', minWidth: '130px' }}>Thao Tác</th>
                          </tr>
                        </thead>
                        <tbody>
                          {users.map((user) => (
                            <tr key={user.id}>
                              <td>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                                  <div className="user-avatar-small" style={{ background: '#0070f3' }}>
                                    {user.username ? user.username.charAt(0).toUpperCase() : 'U'}
                                  </div>
                                  <div>
                                    <div style={{ fontWeight: 600 }}>{user.username}</div>
                                    <div style={{ fontSize: 11, color: '#94a3b8' }}>Mã: {user.id}</div>
                                  </div>
                                </div>
                              </td>

                              <td>{user.email || '—'}</td>

                              <td>
                                {user.firstName || user.lastName
                                  ? `${user.firstName || ''} ${user.lastName || ''}`.trim()
                                  : 'Chưa cập nhật'}
                              </td>

                              <td>
                                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                                  {user.roles && user.roles.length > 0 ? (
                                    user.roles.map((role) => (
                                      <span key={role} className={`role-tag ${role.toLowerCase()}`}>
                                        {role === 'ADMIN' ? 'QUẢN TRỊ VIÊN' : role === 'MANAGER' ? 'QUẢN LÝ' : 'THÀNH VIÊN'}
                                      </span>
                                    ))
                                  ) : (
                                    <span className="role-tag user">THÀNH VIÊN</span>
                                  )}
                                </div>
                              </td>

                              <td>
                                <span style={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  padding: '4px 8px',
                                  borderRadius: '6px',
                                  fontSize: '11px',
                                  fontWeight: '700',
                                  textTransform: 'uppercase',
                                  letterSpacing: '0.05em',
                                  backgroundColor: user.status === 'ACTIVE' ? '#ECFDF5' : '#FEF2F2',
                                  color: user.status === 'ACTIVE' ? '#10B981' : '#EF4444',
                                  border: `1px solid ${user.status === 'ACTIVE' ? '#A7F3D0' : '#FCA5A5'}`,
                                  minWidth: '65px',
                                  textAlign: 'center'
                                }}>
                                  {user.status === 'ACTIVE' ? 'Hoạt động' : 'Tạm khóa'}
                                </span>
                              </td>

                              <td style={{ fontSize: '13px', color: '#64748b' }}>
                                {user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN', {
                                  day: '2-digit',
                                  month: '2-digit',
                                  year: 'numeric'
                                }) : '—'}
                              </td>

                              <td style={{ textAlign: 'right', whiteSpace: 'nowrap', width: '130px', minWidth: '130px' }}>
                                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', alignItems: 'center' }}>
                                  <button
                                    title="Cập nhật vai trò"
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                      width: '32px',
                                      height: '32px',
                                      borderRadius: '6px',
                                      border: '1px solid #DBEAFE',
                                      backgroundColor: '#FFFFFF',
                                      color: '#3B82F6',
                                      cursor: 'pointer',
                                      transition: 'all 0.15s ease',
                                    }}
                                    onClick={() => handleOpenRoleModal(user)}
                                    onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#EFF6FF'; e.currentTarget.style.borderColor = '#BFDBFE'; }}
                                    onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#FFFFFF'; e.currentTarget.style.borderColor = '#DBEAFE'; }}
                                  >
                                    <Edit3 size={15} />
                                  </button>
                                  <button
                                    title="Đặt lại mật khẩu"
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                      width: '32px',
                                      height: '32px',
                                      borderRadius: '6px',
                                      border: '1px solid #E0F2FE',
                                      backgroundColor: '#FFFFFF',
                                      color: '#0EA5E9',
                                      cursor: 'pointer',
                                      transition: 'all 0.15s ease',
                                    }}
                                    onClick={() => { setResetUser(user); setNewPassword(''); }}
                                    onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#F0F9FF'; e.currentTarget.style.borderColor = '#BAE6FD'; }}
                                    onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#FFFFFF'; e.currentTarget.style.borderColor = '#E0F2FE'; }}
                                  >
                                    <Key size={15} />
                                  </button>
                                  <button
                                    title="Xóa tài khoản"
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      justifyContent: 'center',
                                      width: '32px',
                                      height: '32px',
                                      borderRadius: '6px',
                                      border: '1px solid #FEE2E2',
                                      backgroundColor: '#FFFFFF',
                                      color: '#EF4444',
                                      cursor: 'pointer',
                                      transition: 'all 0.15s ease',
                                    }}
                                    onClick={() => handleDeleteUser(user)}
                                    onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#FEF2F2'; e.currentTarget.style.borderColor = '#FCA5A5'; }}
                                    onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#FFFFFF'; e.currentTarget.style.borderColor = '#FEE2E2'; }}
                                  >
                                    <Trash2 size={15} />
                                  </button>
                                </div>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    {/* Pagination Controls */}
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '16px 24px',
                      borderTop: '1px solid #e2e8f0',
                      backgroundColor: '#ffffff',
                      flexWrap: 'wrap',
                      gap: 12
                    }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>

                        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#64748b' }}>
                          <span>Hiển thị:</span>
                          <select
                            value={size}
                            onChange={(e) => {
                              setSize(Number(e.target.value));
                              setPage(0); // Reset to first page
                            }}
                            style={{
                              padding: '4px 8px',
                              borderRadius: '6px',
                              border: '1px solid #cbd5e1',
                              backgroundColor: '#fff',
                              color: '#334155',
                              fontSize: '13px',
                              fontWeight: '500',
                              outline: 'none',
                              cursor: 'pointer'
                            }}
                          >
                            <option value={10}>10 dòng</option>
                            <option value={25}>25 dòng</option>
                            <option value={50}>50 dòng</option>
                          </select>
                        </div>
                      </div>

                      <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                        <button
                          className="action-btn"
                          disabled={page === 0}
                          onClick={() => setPage(p => Math.max(0, p - 1))}
                          style={{
                            cursor: page === 0 ? 'not-allowed' : 'pointer',
                            opacity: page === 0 ? 0.5 : 1,
                            padding: '6px 12px',
                            borderRadius: 6,
                            border: '1px solid #cbd5e1',
                            backgroundColor: '#fff',
                            fontSize: 13,
                            color: '#334155',
                            fontWeight: 500
                          }}
                        >
                          Trước
                        </button>

                        {Array.from({ length: totalPages }, (_, i) => (
                          <button
                            key={i}
                            style={{
                              minWidth: 32,
                              height: 32,
                              padding: 0,
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              borderRadius: 6,
                              background: page === i ? 'var(--accent, #ea580c)' : '#fff',
                              color: page === i ? '#fff' : '#64748b',
                              border: page === i ? 'none' : '1px solid #cbd5e1',
                              cursor: 'pointer',
                              fontWeight: 600,
                              fontSize: 13
                            }}
                            onClick={() => setPage(i)}
                          >
                            {i + 1}
                          </button>
                        ))}

                        <button
                          className="action-btn"
                          disabled={page >= totalPages - 1}
                          onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                          style={{
                            cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                            opacity: page >= totalPages - 1 ? 0.5 : 1,
                            padding: '6px 12px',
                            borderRadius: 6,
                            border: '1px solid #cbd5e1',
                            backgroundColor: '#fff',
                            fontSize: 13,
                            color: '#334155',
                            fontWeight: 500
                          }}
                        >
                          Sau
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </div>
            </>
          )}
        </div>
      </main>

      {/* UPDATE ROLE MODAL */}
      {selectedUser && (
        <div className="modal-overlay" onClick={() => setSelectedUser(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Shield color="#0070f3" size={22} />
                <h3 className="modal-title">Phân Quyền Người Dùng</h3>
              </div>
              <button className="close-btn" onClick={() => setSelectedUser(null)}>
                <X size={20} />
              </button>
            </div>

            <div style={{ fontSize: 14, color: '#64748b', marginBottom: 12 }}>
              Đang chỉnh sửa quyền hạn cho tài khoản: <strong style={{ color: '#0f172a' }}>{selectedUser.username}</strong>
            </div>

            {modalError && (
              <div style={{ background: '#fef2f2', color: '#dc2626', padding: 10, borderRadius: 8, fontSize: 13, marginBottom: 12 }}>
                {modalError}
              </div>
            )}

            {modalSuccess && (
              <div style={{ background: '#f0fdf4', color: '#16a34a', padding: 10, borderRadius: 8, fontSize: 13, marginBottom: 12 }}>
                {modalSuccess}
              </div>
            )}

            <div className="role-checkbox-group">
              {availableRoles.map((role) => {
                const isSelected = selectedRoles.includes(role);
                return (
                  <div
                    key={role}
                    className={`role-option ${isSelected ? 'selected' : ''}`}
                    onClick={() => handleToggleRole(role)}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => { }} // Handled by div click
                      style={{ cursor: 'pointer', width: 18, height: 18 }}
                    />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: 14 }}>
                        {role === 'ADMIN' ? 'Quản trị viên' : role === 'MANAGER' ? 'Quản lý' : 'Thành viên'}
                      </div>
                      <div style={{ fontSize: 12, color: '#64748b' }}>
                        {role === 'ADMIN' && 'Quyền quản trị cao nhất hệ thống'}
                        {role === 'USER' && 'Quyền người dùng tiêu chuẩn'}
                        {role === 'MANAGER' && 'Quyền quản lý danh mục và tài nguyên'}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
              <button
                className="action-btn"
                onClick={() => setSelectedUser(null)}
                disabled={updatingRole}
              >
                Hủy bỏ
              </button>

              <button
                className="btn-primary"
                style={{ padding: '0 20px', height: 40, fontSize: 14 }}
                onClick={handleSaveRoles}
                disabled={updatingRole}
              >
                {updatingRole ? 'Đang lưu...' : 'Lưu Thay Đổi'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ADD USER MODAL */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <UserPlus color="#ea580c" size={22} />
                <h3 className="modal-title">Thêm Người Dùng Mới</h3>
              </div>
              <button className="close-btn" onClick={() => setShowCreateModal(false)}>
                <X size={20} />
              </button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginBottom: 20 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Tên đăng nhập</label>
                <input
                  type="text"
                  placeholder="Nhập tên tài khoản..."
                  value={createUsername}
                  onChange={(e) => setCreateUsername(e.target.value)}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #cbd5e1', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }}
                  disabled={creatingUser}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Mật Khẩu</label>
                <input
                  type="password"
                  placeholder="Nhập mật khẩu (tối thiểu 6 ký tự)..."
                  value={createPassword}
                  onChange={(e) => setCreatePassword(e.target.value)}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #cbd5e1', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }}
                  disabled={creatingUser}
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
              <button
                className="action-btn"
                onClick={() => { setShowCreateModal(false); setCreateUsername(''); setCreatePassword(''); }}
                disabled={creatingUser}
              >
                Hủy bỏ
              </button>

              <button
                className="btn-primary"
                style={{ padding: '0 20px', height: 40, fontSize: 14, background: 'var(--accent, #ea580c)', border: 'none', color: '#fff', cursor: 'pointer', borderRadius: 8, fontWeight: 600 }}
                onClick={handleCreateUser}
                disabled={creatingUser}
              >
                {creatingUser ? 'Đang tạo...' : 'Tạo Tài Khoản'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* RESET PASSWORD MODAL */}
      {resetUser && (
        <div className="modal-overlay" onClick={() => setResetUser(null)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <Key color="#0ea5e9" size={22} />
                <h3 className="modal-title">Đặt Lại Mật Khẩu</h3>
              </div>
              <button className="close-btn" onClick={() => setResetUser(null)}>
                <X size={20} />
              </button>
            </div>

            <div style={{ fontSize: 14, color: '#64748b', marginBottom: 16 }}>
              Nhập mật khẩu mới cho tài khoản: <strong style={{ color: '#0f172a' }}>{resetUser.username}</strong>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginBottom: 20 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#475569', marginBottom: 6 }}>Mật Khẩu Mới</label>
                <input
                  type="password"
                  placeholder="Nhập mật khẩu mới (tối thiểu 6 ký tự)..."
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  style={{ width: '100%', padding: '10px 12px', border: '1px solid #cbd5e1', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' }}
                  disabled={resettingPassword}
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
              <button
                className="action-btn"
                onClick={() => { setResetUser(null); setNewPassword(''); }}
                disabled={resettingPassword}
              >
                Hủy bỏ
              </button>

              <button
                className="btn-primary"
                style={{ padding: '0 20px', height: 40, fontSize: 14, background: '#0ea5e9', border: 'none', color: '#fff', cursor: 'pointer', borderRadius: 8, fontWeight: 600 }}
                onClick={handleResetPassword}
                disabled={resettingPassword}
              >
                {resettingPassword ? 'Đang cập nhật...' : 'Cập Nhật Mật Khẩu'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* CONFIRMATION DIALOG */}
      <ConfirmModal
        {...confirmConfig}
        onCancel={() => setConfirmConfig((prev) => ({ ...prev, isOpen: false }))}
      />
    </div>
  );
}
