import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { userApi, authApi } from '../api';
import {
  Users, ShieldCheck, Search, RefreshCw, LogOut, UserCheck,
  Shield, Edit3, X, Check, Server, KeyRound, AlertCircle, LayoutDashboard,
  FolderKanban, Sparkles
} from 'lucide-react';
import AdminProjectManagement from '../components/admin/AdminProjectManagement.jsx';

export default function AdminDashboard({ onLogout }) {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('projects'); // 'projects' | 'users'
  const [currentUser, setCurrentUser] = useState(null);
  const [users, setUsers] = useState([]);
  const [filteredUsers, setFilteredUsers] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Role Modal state
  const [selectedUser, setSelectedUser] = useState(null);
  const [selectedRoles, setSelectedRoles] = useState([]);
  const [updatingRole, setUpdatingRole] = useState(false);
  const [modalSuccess, setModalSuccess] = useState(null);
  const [modalError, setModalError] = useState(null);

  const availableRoles = ['ADMIN', 'USER', 'MANAGER'];

  // Fetch Current User & User List
  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      // 1. Fetch current logged-in user
      const meRes = await userApi.me();
      setCurrentUser(meRes?.data);

      // 2. Fetch all users list (requires ADMIN role)
      const listRes = await userApi.listAll();
      const userList = listRes?.data || [];
      setUsers(userList);
      setFilteredUsers(userList);
    } catch (err) {
      console.error('Error fetching dashboard data:', err);
      if (err.status === 403) {
        setError('403 Forbidden: Bạn cần có quyền ADMIN để truy cập danh sách và quản lý người dùng.');
      } else {
        setError(err.message || 'Lỗi khi kết nối với máy chủ.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  // Search filter
  useEffect(() => {
    if (!searchQuery.trim()) {
      setFilteredUsers(users);
      return;
    }
    const q = searchQuery.toLowerCase();
    const filtered = users.filter(
      (u) =>
        u.username?.toLowerCase().includes(q) ||
        u.email?.toLowerCase().includes(q) ||
        u.firstName?.toLowerCase().includes(q) ||
        u.lastName?.toLowerCase().includes(q)
    );
    setFilteredUsers(filtered);
  }, [searchQuery, users]);

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
      setModalError('Vui lòng chọn ít nhất 1 role cho người dùng.');
      return;
    }

    setUpdatingRole(true);
    setModalError(null);
    setModalSuccess(null);

    try {
      await userApi.updateRoles(selectedUser.id, selectedRoles);
      setModalSuccess(`Cập nhật role cho ${selectedUser.username} thành công!`);

      // Refresh list
      await fetchData();

      setTimeout(() => {
        setSelectedUser(null);
      }, 1200);
    } catch (err) {
      setModalError(err.message || 'Cập nhật role thất bại.');
    } finally {
      setUpdatingRole(false);
    }
  };

  return (
    <div className="admin-container">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-header">
          <ShieldCheck size={26} />
          <span>DATN Admin</span>
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
            <span>Quản Lý User</span>
          </button>

          <a href="#keycloak" className="nav-item" onClick={(e) => { e.preventDefault(); alert('Trang cấu hình Keycloak IAM'); }}>
            <KeyRound size={18} />
            <span>Keycloak IAM</span>
          </a>
          <a href="#gateway" className="nav-item" onClick={(e) => { e.preventDefault(); alert('Trang thông số API Gateway'); }}>
            <Server size={18} />
            <span>API Gateway Logs</span>
          </a>
        </nav>

        <div className="sidebar-footer">
          <button
            onClick={handleLogout}
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
              {activeTab === 'projects' ? 'Project Management & Templates' : 'User Management & Roles'}
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
              onClick={handleLogout}
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
                  placeholder="Tìm theo Username, Email hoặc Tên..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', gap: 12 }}>
                <button className="action-btn" onClick={fetchData} disabled={loading}>
                  <RefreshCw size={15} className={loading ? 'spin' : ''} />
                  <span>Làm mới</span>
                </button>
              </div>
            </div>

            {loading ? (
              <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>
                Đang tải dữ liệu từ Identity Service...
              </div>
            ) : filteredUsers.length === 0 ? (
              <div style={{ padding: 40, textAlign: 'center', color: '#64748b' }}>
                {error ? 'Không có quyền truy cập danh sách' : 'Không tìm thấy người dùng phù hợp.'}
              </div>
            ) : (
              <table className="custom-table">
                <thead>
                  <tr>
                    <th>Người dùng</th>
                    <th>Email</th>
                    <th>Họ & Tên</th>
                    <th>Quyền Hạn (Roles)</th>
                    <th style={{ textAlign: 'right' }}>Thao Tác</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUsers.map((user) => (
                    <tr key={user.id}>
                      <td>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                          <div className="user-avatar-small" style={{ background: '#0070f3' }}>
                            {user.username ? user.username.charAt(0).toUpperCase() : 'U'}
                          </div>
                          <div>
                            <div style={{ fontWeight: 600 }}>{user.username}</div>
                            <div style={{ fontSize: 11, color: '#94a3b8' }}>ID: {user.id}</div>
                          </div>
                        </div>
                      </td>

                      <td>{user.email || 'N/A'}</td>

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
                                {role}
                              </span>
                            ))
                          ) : (
                            <span className="role-tag user">USER</span>
                          )}
                        </div>
                      </td>

                      <td style={{ textAlign: 'right' }}>
                        <button
                          className="action-btn edit"
                          onClick={() => handleOpenRoleModal(user)}
                        >
                          <Edit3 size={14} />
                          <span>Cập nhật Role</span>
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
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
                      onChange={() => {}} // Handled by div click
                      style={{ cursor: 'pointer', width: 18, height: 18 }}
                    />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 600, fontSize: 14 }}>ROLE_{role}</div>
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
    </div>
  );
}
