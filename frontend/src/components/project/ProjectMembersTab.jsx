import React, { useState, useEffect } from 'react';
import {
  Users,
  UserPlus,
  Shield,
  Trash2,
  Check,
  Search,
  X,
  AlertCircle,
  Sparkles,
  Info
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi, userApi } from '../../api.js';

const ROLE_COLORS = {
  ADMINISTRATOR: { bg: '#FAF5FF', text: '#7E22CE', border: '#E9D5FF' },
  MEMBER: { bg: '#EFF6FF', text: '#1D4ED8', border: '#BFDBFE' },
  VIEWER: { bg: '#F8FAFC', text: '#475569', border: '#E2E8F0' },
};

export default function ProjectMembersTab({ projectId, project, isAdmin, onMembersUpdated }) {
  const [members, setMembers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  // Add Member Modal State
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [systemUsers, setSystemUsers] = useState([]);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [selectedRoleId, setSelectedRoleId] = useState('');
  const [addingMember, setAddingMember] = useState(false);

  const fetchMembersAndRoles = async () => {
    try {
      setLoading(true);
      const [membersRes, rolesRes] = await Promise.all([
        projectApi.getMembers(projectId),
        projectApi.getRoles(projectId),
      ]);

      if (membersRes && membersRes.data) {
        setMembers(Array.isArray(membersRes.data) ? membersRes.data : []);
      }
      if (rolesRes && rolesRes.data) {
        setRoles(Array.isArray(rolesRes.data) ? rolesRes.data : []);
      }
    } catch (err) {
      console.error('Failed to load project members or roles:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (projectId) {
      fetchMembersAndRoles();
    }
  }, [projectId]);

  const openAddMemberModal = async () => {
    setIsAddModalOpen(true);
    setSelectedUserId('');
    setSelectedRoleId(roles[1]?.id || roles[0]?.id || '');

    try {
      const res = await userApi.listAll();
      if (res && res.data) {
        setSystemUsers(Array.isArray(res.data) ? res.data : (res.data.content || []));
      }
    } catch (err) {
      console.warn('Could not fetch system users for picker:', err);
    }
  };

  const handleAddMemberSubmit = async (e) => {
    e.preventDefault();
    if (!selectedUserId) {
      toast.error('Vui lòng chọn người dùng muốn thêm vào dự án');
      return;
    }
    if (!selectedRoleId) {
      toast.error('Vui lòng chọn vai trò cho thành viên');
      return;
    }

    try {
      setAddingMember(true);
      await projectApi.addMember(projectId, {
        userId: selectedUserId,
        roleId: selectedRoleId,
      });

      toast.success('Đã thêm thành viên vào dự án thành công!');
      setIsAddModalOpen(false);
      fetchMembersAndRoles();
      if (onMembersUpdated) onMembersUpdated();
    } catch (err) {
      console.error('Failed to add member:', err);
    } finally {
      setAddingMember(false);
    }
  };

  const handleRoleChange = async (userId, newRoleId) => {
    try {
      await projectApi.updateMemberRole(projectId, userId, newRoleId);
      toast.success('Đã cập nhật vai trò thành viên thành công!');
      fetchMembersAndRoles();
    } catch (err) {
      console.error('Failed to update member role:', err);
    }
  };

  const handleRemoveMember = async (userId, memberName) => {
    if (userId === project?.leadUserId) {
      toast.error('Không thể xóa Trưởng dự án (Project Lead) khỏi dự án');
      return;
    }

    if (!window.confirm(`Bạn có chắc chắn muốn xóa thành viên "${memberName || 'này'}" khỏi dự án?`)) {
      return;
    }

    try {
      await projectApi.removeMember(projectId, userId);
      toast.success('Đã xóa thành viên khỏi dự án');
      fetchMembersAndRoles();
      if (onMembersUpdated) onMembersUpdated();
    } catch (err) {
      console.error('Failed to remove member:', err);
    }
  };

  const filteredMembers = members.filter((m) => {
    if (!searchTerm.trim()) return true;
    const term = searchTerm.toLowerCase();
    const matchName = m.fullName?.toLowerCase().includes(term);
    const matchUsername = m.username?.toLowerCase().includes(term);
    const matchEmail = m.email?.toLowerCase().includes(term);
    const matchRole = m.roleName?.toLowerCase().includes(term);
    return matchName || matchUsername || matchEmail || matchRole;
  });

  return (
    <div style={{ padding: '24px', maxWidth: 1000, margin: '0 auto', width: '100%', boxSizing: 'border-box' }}>
      {/* ─── Header & Action Bar ─── */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <h2 style={{ fontSize: 18, fontWeight: 700, color: '#0F172A', margin: '0 0 4px 0' }}>
            Thành viên & Phân quyền Dự án
          </h2>
          <p style={{ fontSize: 13, color: '#64748B', margin: 0 }}>
            Quản lý danh sách người dùng được phép truy cập và cấp quyền thao tác nội bộ dự án.
          </p>
        </div>

        {isAdmin && (
          <button
            onClick={openAddMemberModal}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              backgroundColor: '#2563EB',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: 8,
              padding: '8px 16px',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              boxShadow: '0 2px 4px rgba(37,99,235,0.2)',
            }}
          >
            <UserPlus size={16} />
            <span>Thêm thành viên</span>
          </button>
        )}
      </div>

      {/* ─── Search Toolbar ─── */}
      <div style={{ marginBottom: 16, position: 'relative', maxWidth: 360 }}>
        <Search size={14} color="#94A3B8" style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }} />
        <input
          type="text"
          placeholder="Tìm thành viên theo tên, email, vai trò..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          style={{
            width: '100%',
            padding: '7px 12px 7px 32px',
            borderRadius: 8,
            border: '1px solid #CBD5E1',
            fontSize: 13,
            color: '#0F172A',
            outline: 'none',
            backgroundColor: '#FFFFFF',
            boxSizing: 'border-box',
          }}
        />
      </div>

      {/* ─── Members List Table ─── */}
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 10,
          border: '1px solid #E2E8F0',
          overflow: 'hidden',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '2fr 1.5fr 1.5fr 80px',
            padding: '12px 18px',
            backgroundColor: '#F8FAFC',
            borderBottom: '1px solid #E2E8F0',
            fontSize: 12,
            fontWeight: 700,
            color: '#475569',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          <span>Thành viên</span>
          <span>Email</span>
          <span>Vai trò trong dự án</span>
          <span style={{ textAlign: 'right' }}>Thao tác</span>
        </div>

        {loading ? (
          <div style={{ padding: '40px 0', textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Đang tải danh sách thành viên...
          </div>
        ) : filteredMembers.length === 0 ? (
          <div style={{ padding: '40px 20px', textAlign: 'center', color: '#64748B', fontSize: 13 }}>
            Không tìm thấy thành viên nào.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {filteredMembers.map((m) => {
              const isLead = m.userId === project?.leadUserId;
              const roleStyle = ROLE_COLORS[m.roleName] || ROLE_COLORS.MEMBER;

              return (
                <div
                  key={m.id || m.userId}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '2fr 1.5fr 1.5fr 80px',
                    padding: '14px 18px',
                    borderBottom: '1px solid #F1F5F9',
                    alignItems: 'center',
                    fontSize: 13,
                    color: '#0F172A',
                  }}
                >
                  {/* User Avatar + Name */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div
                      style={{
                        width: 32,
                        height: 32,
                        borderRadius: '50%',
                        backgroundColor: '#EFF6FF',
                        color: '#2563EB',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 700,
                        fontSize: 13,
                        flexShrink: 0,
                      }}
                    >
                      {m.fullName ? m.fullName.charAt(0).toUpperCase() : (m.username ? m.username.charAt(0).toUpperCase() : 'U')}
                    </div>
                    <div>
                      <div style={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span>{m.fullName || m.username || 'Thành viên'}</span>
                        {isLead && (
                          <span
                            style={{
                              padding: '1px 6px',
                              borderRadius: 4,
                              fontSize: 10,
                              fontWeight: 700,
                              backgroundColor: '#FEF3C7',
                              color: '#B45309',
                            }}
                          >
                            LEAD
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: 11, color: '#94A3B8' }}>@{m.username || 'user'}</div>
                    </div>
                  </div>

                  {/* Email */}
                  <div style={{ color: '#475569', fontSize: 13 }}>
                    {m.email || '—'}
                  </div>

                  {/* Role Selector / Badge */}
                  <div>
                    {isAdmin && !isLead ? (
                      <select
                        value={m.roleId}
                        onChange={(e) => handleRoleChange(m.userId, e.target.value)}
                        style={{
                          padding: '4px 8px',
                          borderRadius: 6,
                          border: '1px solid #CBD5E1',
                          fontSize: 12,
                          fontWeight: 600,
                          backgroundColor: roleStyle.bg,
                          color: roleStyle.text,
                          cursor: 'pointer',
                          outline: 'none',
                        }}
                      >
                        {roles.map((r) => (
                          <option key={r.id} value={r.id}>
                            {r.name}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <span
                        style={{
                          display: 'inline-block',
                          padding: '3px 9px',
                          borderRadius: 12,
                          fontSize: 12,
                          fontWeight: 600,
                          backgroundColor: roleStyle.bg,
                          color: roleStyle.text,
                          border: `1px solid ${roleStyle.border}`,
                        }}
                      >
                        {m.roleName}
                      </span>
                    )}
                  </div>

                  {/* Actions */}
                  <div style={{ textAlign: 'right' }}>
                    {isAdmin && !isLead && (
                      <button
                        onClick={() => handleRemoveMember(m.userId, m.fullName || m.username)}
                        title="Xóa thành viên"
                        style={{
                          background: 'none',
                          border: 'none',
                          color: '#94A3B8',
                          cursor: 'pointer',
                          padding: 6,
                          borderRadius: 6,
                          display: 'inline-flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          transition: 'all 0.15s ease',
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.backgroundColor = '#FEE2E2';
                          e.currentTarget.style.color = '#EF4444';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.backgroundColor = 'transparent';
                          e.currentTarget.style.color = '#94A3B8';
                        }}
                      >
                        <Trash2 size={15} />
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ─── Permission Matrix Legend Info Card ─── */}
      <div
        style={{
          marginTop: 24,
          padding: '16px 20px',
          borderRadius: 10,
          backgroundColor: '#FFFFFF',
          border: '1px solid #E2E8F0',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 700, color: '#0F172A', marginBottom: 8 }}>
          <Shield size={16} color="#2563EB" />
          <span>Bảng tóm tắt Quyền hạn theo Vai trò (Granular RBAC)</span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, fontSize: 12 }}>
          <div style={{ padding: 10, backgroundColor: '#FAF5FF', borderRadius: 8, border: '1px solid #E9D5FF' }}>
            <div style={{ fontWeight: 700, color: '#7E22CE', marginBottom: 4 }}>Administrator (10/10)</div>
            <div style={{ color: '#6B21A8', lineHeight: 1.4 }}>
              Toàn quyền cấu hình dự án, thành viên, workflow, board, sprint và xóa công việc.
            </div>
          </div>
          <div style={{ padding: 10, backgroundColor: '#EFF6FF', borderRadius: 8, border: '1px solid #BFDBFE' }}>
            <div style={{ fontWeight: 700, color: '#1D4ED8', marginBottom: 4 }}>Member (6 quyền)</div>
            <div style={{ color: '#1E40AF', lineHeight: 1.4 }}>
              Xem dự án, tạo task, chỉnh sửa, kéo thả trạng thái và thêm bình luận.
            </div>
          </div>
          <div style={{ padding: 10, backgroundColor: '#F8FAFC', borderRadius: 8, border: '1px solid #E2E8F0' }}>
            <div style={{ fontWeight: 700, color: '#475569', marginBottom: 4 }}>Viewer (1 quyền)</div>
            <div style={{ color: '#64748B', lineHeight: 1.4 }}>
              Chỉ đọc: Xem danh sách công việc, bảng Kanban và báo cáo mà không thể chỉnh sửa.
            </div>
          </div>
        </div>
      </div>

      {/* ─── Add Member Modal ─── */}
      {isAddModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.65)',
            backdropFilter: 'blur(4px)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 9999,
            padding: 16,
          }}
          onClick={() => setIsAddModalOpen(false)}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
              width: '100%',
              maxWidth: 480,
              overflow: 'hidden',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div
              style={{
                padding: '16px 20px',
                borderBottom: '1px solid #E2E8F0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                backgroundColor: '#FAFAFC',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontWeight: 700, fontSize: 15, color: '#0F172A' }}>
                <UserPlus size={18} color="#2563EB" />
                <span>Thêm Thành viên vào Dự án</span>
              </div>
              <button
                onClick={() => setIsAddModalOpen(false)}
                style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', padding: 4 }}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleAddMemberSubmit} style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* Select User */}
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Chọn người dùng <span style={{ color: '#EF4444' }}>*</span>
                </label>
                <select
                  value={selectedUserId}
                  onChange={(e) => setSelectedUserId(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    borderRadius: 8,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    color: '#0F172A',
                    backgroundColor: '#FFFFFF',
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                >
                  <option value="">-- Chọn thành viên từ hệ thống --</option>
                  {systemUsers.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.fullName ? `${u.fullName} (@${u.username})` : u.username} ({u.email || 'No email'})
                    </option>
                  ))}
                </select>
              </div>

              {/* Select Role */}
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Vai trò trong dự án <span style={{ color: '#EF4444' }}>*</span>
                </label>
                <select
                  value={selectedRoleId}
                  onChange={(e) => setSelectedRoleId(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    borderRadius: 8,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    color: '#0F172A',
                    backgroundColor: '#FFFFFF',
                    outline: 'none',
                    boxSizing: 'border-box',
                  }}
                >
                  {roles.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name} - {r.description || ''}
                    </option>
                  ))}
                </select>
              </div>

              {/* Submit Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 8 }}>
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  style={{
                    padding: '7px 14px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    backgroundColor: '#FFFFFF',
                    color: '#475569',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={addingMember}
                  style={{
                    padding: '7px 18px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: addingMember ? 'not-allowed' : 'pointer',
                  }}
                >
                  {addingMember ? 'Đang thêm...' : 'Thêm vào dự án'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
