import React, { useState, useEffect, useRef } from 'react';
import {
  UserPlus,
  Shield,
  Trash2,
  Check,
  Search,
  X,
  Sparkles,
  ChevronDown,
  ChevronUp,
  AlertCircle,
  Info
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi, userApi } from '../../api.js';

const ROLE_COLORS = {
  ADMINISTRATOR: { bg: '#FAF5FF', text: '#7E22CE', border: '#E9D5FF' },
  MEMBER: { bg: '#EFF6FF', text: '#1D4ED8', border: '#BFDBFE' },
  VIEWER: { bg: '#F8FAFC', text: '#475569', border: '#E2E8F0' },
};

/**
 * Ma trận quyền mặc định (khớp với PermissionKey + seed ở backend ProjectServiceImpl.seedProjectEcosystem).
 */
export const PERMISSION_DEFINITIONS = [
  {
    category: '1. Quản trị Dự án & Vòng đời Agile',
    permissions: [
      {
        key: 'PROJECT_ADMIN',
        name: 'Toàn quyền Quản trị Dự án (Project Admin)',
        desc: 'Cấu hình thông tin dự án, quản lý thành viên, gán vai trò, cấu hình workflow và xóa dự án.',
        roles: ['ADMINISTRATOR'],
      },
      {
        key: 'PROJECT_VIEW',
        name: 'Xem Dự án & Bảng biểu (Browse Project)',
        desc: 'Xem tổng quan dự án, danh sách công việc, bảng Kanban/Scrum, lịch sử và tệp đính kèm.',
        roles: ['ADMINISTRATOR', 'MEMBER', 'VIEWER'],
      },
      {
        key: 'SPRINT_MANAGE',
        name: 'Quản lý Sprint Agile (Sprint Manage)',
        desc: 'Lập kế hoạch Backlog, tạo Sprint mới, Bắt đầu và Hoàn thành Sprint.',
        roles: ['ADMINISTRATOR'],
      },
      {
        key: 'RELEASE_MANAGE',
        name: 'Quản lý Phiên bản Phát hành (Release Manage)',
        desc: 'Tạo đợt Release Version, chốt danh sách issue đã xong và sinh Release Notes.',
        roles: ['ADMINISTRATOR'],
      },
    ],
  },
  {
    category: '2. Quản lý & Thao tác Công việc (Work Items)',
    permissions: [
      {
        key: 'ISSUE_CREATE',
        name: 'Tạo Công việc Mới (Issue Create)',
        desc: 'Tạo mới Epic, Story, Task, Bug và Sub-task trong dự án.',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ISSUE_EDIT',
        name: 'Chỉnh sửa Công việc (Issue Edit)',
        desc: 'Cập nhật tiêu đề, mô tả, Story Points, hạn chót, liên kết và tải lên tệp đính kèm.',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ISSUE_TRANSITION',
        name: 'Chuyển Trạng thái / Kéo thả (Issue Transition)',
        desc: 'Kéo thả thẻ trên Board và thực hiện các bước chuyển quy trình (To Do -> In Progress -> Done).',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ISSUE_ASSIGN',
        name: 'Phân công Người xử lý (Issue Assign)',
        desc: 'Gán hoặc đổi người chịu trách nhiệm (Assignee) cho công việc.',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ISSUE_DELETE',
        name: 'Xóa Công việc (Issue Delete)',
        desc: 'Xóa vĩnh viễn hoặc chuyển công việc vào thùng rác dự án.',
        roles: ['ADMINISTRATOR'],
      },
    ],
  },
  {
    category: '3. Bình luận & Trao đổi',
    permissions: [
      {
        key: 'COMMENT_CREATE',
        name: 'Thêm Bình luận (Add Comments)',
        desc: 'Gửi bình luận trao đổi dạng cây phân cấp trên công việc.',
        roles: ['ADMINISTRATOR', 'MEMBER', 'VIEWER'],
      },
      {
        key: 'COMMENT_EDIT_OWN',
        name: 'Sửa Bình luận của mình (Edit Own Comments)',
        desc: 'Chỉnh sửa nội dung bình luận do chính mình viết.',
        roles: ['ADMINISTRATOR', 'MEMBER', 'VIEWER'],
      },
      {
        key: 'COMMENT_DELETE_OWN',
        name: 'Xóa Bình luận của mình (Delete Own Comments)',
        desc: 'Xóa bình luận do chính mình viết.',
        roles: ['ADMINISTRATOR', 'MEMBER', 'VIEWER'],
      },
      {
        key: 'COMMENT_DELETE_ALL',
        name: 'Xóa mọi Bình luận (Delete All Comments)',
        desc: 'Xóa bình luận của bất kỳ thành viên nào trong dự án.',
        roles: ['ADMINISTRATOR'],
      },
    ],
  },
  {
    category: '4. Tệp đính kèm',
    permissions: [
      {
        key: 'ATTACHMENT_DELETE_OWN',
        name: 'Xóa Tệp của mình (Delete Own Attachments)',
        desc: 'Xóa tệp đính kèm do chính mình tải lên.',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ATTACHMENT_DELETE_ALL',
        name: 'Xóa mọi Tệp (Delete All Attachments)',
        desc: 'Xóa tệp đính kèm của bất kỳ thành viên nào trong dự án.',
        roles: ['ADMINISTRATOR'],
      },
    ],
  },
];

const ALL_PERMISSIONS = PERMISSION_DEFINITIONS.flatMap((g) => g.permissions);
const countFor = (role) => ALL_PERMISSIONS.filter((p) => p.roles.includes(role)).length;

export default function ProjectMembersTab({ projectId, project, isAdmin, currentUserId, onMembersUpdated }) {
  const [members, setMembers] = useState([]);
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showDetailedMatrix, setShowDetailedMatrix] = useState(false);

  // Add Member Modal State (autocomplete search – không cần quyền ADMIN hệ thống)
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [userQuery, setUserQuery] = useState('');
  const [userSuggestions, setUserSuggestions] = useState([]);
  const [isSearchingUsers, setIsSearchingUsers] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [selectedRoleId, setSelectedRoleId] = useState('');
  const [addingMember, setAddingMember] = useState(false);
  const [addError, setAddError] = useState('');
  const searchTimeoutRef = useRef(null);

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  // Live user search (debounce 400ms)
  useEffect(() => {
    if (!isAddModalOpen) {
      setUserSuggestions([]);
      return undefined;
    }
    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    if (selectedUser) return undefined;

    const query = userQuery.trim();
    if (!query) {
      setUserSuggestions([]);
      return undefined;
    }

    searchTimeoutRef.current = setTimeout(async () => {
      try {
        setIsSearchingUsers(true);
        const res = await userApi.search(query);
        const list = Array.isArray(res?.data) ? res.data : (res?.data?.content || []);
        setUserSuggestions(list);
      } catch {
        setUserSuggestions([]);
      } finally {
        setIsSearchingUsers(false);
      }
    }, 400);

    return () => {
      if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    };
  }, [userQuery, isAddModalOpen, selectedUser]);

  const openAddMemberModal = () => {
    const memberRole = roles.find((r) => r.name === 'MEMBER');
    setSelectedRoleId(memberRole?.id || roles[0]?.id || '');
    setSelectedUser(null);
    setUserQuery('');
    setUserSuggestions([]);
    setAddError('');
    setIsAddModalOpen(true);
  };

  const isSameUser = (a, b) => Boolean(a && b && String(a) === String(b));

  const memberMatchesUser = (m, u) =>
    isSameUser(m.userId, u.id) ||
    isSameUser(m.userId, u.keycloakId) ||
    (u.email && m.email && m.email.toLowerCase() === u.email.toLowerCase());

  const handleAddMemberSubmit = async (e) => {
    e.preventDefault();
    if (!selectedUser) {
      setAddError('Vui lòng chọn người dùng từ danh sách gợi ý');
      return;
    }
    if (!selectedRoleId) {
      setAddError('Vui lòng chọn vai trò cho thành viên');
      return;
    }

    try {
      setAddingMember(true);
      setAddError('');
      await projectApi.addMember(projectId, {
        // work-service lưu Keycloak id; backend vẫn tự chuẩn hóa nếu chỉ có DB id
        userId: selectedUser.keycloakId || selectedUser.id,
        roleId: selectedRoleId,
      });

      toast.success('Đã thêm thành viên vào dự án thành công!');
      setIsAddModalOpen(false);
      fetchMembersAndRoles();
      if (onMembersUpdated) onMembersUpdated();
    } catch (err) {
      setAddError(err?.message || 'Không thể thêm thành viên');
    } finally {
      setAddingMember(false);
    }
  };

  const handleRoleChange = async (userId, newRoleId) => {
    try {
      await projectApi.updateMemberRole(projectId, userId, newRoleId);
      toast.success('Đã cập nhật vai trò thành viên thành công!');
      fetchMembersAndRoles();
      if (onMembersUpdated) onMembersUpdated();
    } catch (err) {
      console.error('Failed to update member role:', err);
    }
  };

  const handleRemoveMember = async (userId, memberName) => {
    if (isSameUser(userId, project?.leadUserId)) {
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
    return (
      m.fullName?.toLowerCase().includes(term) ||
      m.username?.toLowerCase().includes(term) ||
      m.email?.toLowerCase().includes(term) ||
      m.roleName?.toLowerCase().includes(term)
    );
  });

  // Số Administrator hiện có (Lead luôn được tính là Administrator) – dùng để khóa thao tác hạ cấp/xóa admin cuối cùng
  const adminUserIds = new Set(
    members.filter((m) => m.roleName === 'ADMINISTRATOR').map((m) => String(m.userId))
  );
  if (project?.leadUserId) adminUserIds.add(String(project.leadUserId));
  const isLastAdministrator = (m) => m.roleName === 'ADMINISTRATOR' && adminUserIds.size <= 1;

  const getInitials = (name) => {
    if (!name) return 'U';
    const parts = name.trim().split(' ');
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

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

      {!isAdmin && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '8px 12px',
            marginBottom: 16,
            borderRadius: 8,
            backgroundColor: '#F8FAFC',
            border: '1px solid #E2E8F0',
            fontSize: 12,
            color: '#64748B',
          }}
        >
          <Info size={14} />
          <span>Chỉ Administrator hoặc Trưởng dự án mới có thể thêm thành viên và thay đổi vai trò.</span>
        </div>
      )}

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
              const isLead = isSameUser(m.userId, project?.leadUserId);
              const isSelf = isSameUser(m.userId, currentUserId);
              const roleStyle = ROLE_COLORS[m.roleName] || ROLE_COLORS.MEMBER;
              const lockedAdmin = isLastAdministrator(m);
              const canEditRow = isAdmin && !isLead && !lockedAdmin;

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
                        {isSelf && <span style={{ fontSize: 10, color: '#64748B', fontWeight: 500 }}>(bạn)</span>}
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
                    {canEditRow ? (
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
                        title={lockedAdmin ? 'Administrator cuối cùng của dự án – không thể hạ cấp' : undefined}
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
                    {canEditRow && (
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

      {/* ─── Permission Matrix Legend Info Card & Detailed View ─── */}
      <div
        style={{
          marginTop: 24,
          padding: '18px 20px',
          borderRadius: 10,
          backgroundColor: '#FFFFFF',
          border: '1px solid #E2E8F0',
          boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
            <Shield size={18} color="#2563EB" />
            <span>Phân quyền hạt mịn theo Vai trò (Granular Project RBAC)</span>
          </div>

          <button
            type="button"
            onClick={() => setShowDetailedMatrix(!showDetailedMatrix)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '5px 12px',
              borderRadius: 6,
              border: '1px solid #CBD5E1',
              backgroundColor: showDetailedMatrix ? '#EFF6FF' : '#FFFFFF',
              color: showDetailedMatrix ? '#2563EB' : '#475569',
              fontSize: 12,
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
          >
            <span>{showDetailedMatrix ? 'Thu gọn tóm tắt' : `Xem chi tiết ${ALL_PERMISSIONS.length} quyền hạn`}</span>
            {showDetailedMatrix ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>
        </div>

        {/* Summary 3 Cards */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, fontSize: 12, marginBottom: showDetailedMatrix ? 16 : 0 }}>
          <div style={{ padding: 12, backgroundColor: '#FAF5FF', borderRadius: 8, border: '1px solid #E9D5FF' }}>
            <div style={{ fontWeight: 700, color: '#7E22CE', marginBottom: 4, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>Administrator</span>
              <span style={{ fontSize: 11, backgroundColor: '#F3E8FF', padding: '1px 6px', borderRadius: 10 }}>
                {countFor('ADMINISTRATOR')} / {ALL_PERMISSIONS.length} quyền
              </span>
            </div>
            <div style={{ color: '#6B21A8', lineHeight: 1.4 }}>
              Toàn quyền cấu hình dự án, thành viên, workflow, sprint; xóa công việc, bình luận và tệp của mọi người.
            </div>
          </div>
          <div style={{ padding: 12, backgroundColor: '#EFF6FF', borderRadius: 8, border: '1px solid #BFDBFE' }}>
            <div style={{ fontWeight: 700, color: '#1D4ED8', marginBottom: 4, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>Member</span>
              <span style={{ fontSize: 11, backgroundColor: '#DBEAFE', padding: '1px 6px', borderRadius: 10 }}>
                {countFor('MEMBER')} / {ALL_PERMISSIONS.length} quyền
              </span>
            </div>
            <div style={{ color: '#1E40AF', lineHeight: 1.4 }}>
              Tạo, sửa, phân công, chuyển trạng thái công việc; bình luận và quản lý bình luận / tệp của chính mình.
            </div>
          </div>
          <div style={{ padding: 12, backgroundColor: '#F8FAFC', borderRadius: 8, border: '1px solid #E2E8F0' }}>
            <div style={{ fontWeight: 700, color: '#475569', marginBottom: 4, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>Viewer</span>
              <span style={{ fontSize: 11, backgroundColor: '#E2E8F0', padding: '1px 6px', borderRadius: 10 }}>
                {countFor('VIEWER')} / {ALL_PERMISSIONS.length} quyền
              </span>
            </div>
            <div style={{ color: '#64748B', lineHeight: 1.4 }}>
              Chỉ theo dõi: xem board, công việc, lịch sử; có thể bình luận trao đổi nhưng không chỉnh sửa dữ liệu.
            </div>
          </div>
        </div>

        {/* Detailed Permission Matrix Table */}
        {showDetailedMatrix && (
          <div
            style={{
              marginTop: 16,
              borderRadius: 8,
              border: '1px solid #E2E8F0',
              overflow: 'hidden',
              backgroundColor: '#FFFFFF',
            }}
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '2.5fr 1fr 1fr 1fr',
                padding: '10px 16px',
                backgroundColor: '#F1F5F9',
                borderBottom: '1px solid #E2E8F0',
                fontSize: 12,
                fontWeight: 700,
                color: '#334155',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
              }}
            >
              <span>Tên Quyền Hạn (Permission Key) & Mô Tả</span>
              <span style={{ textAlign: 'center', color: '#7E22CE' }}>Administrator</span>
              <span style={{ textAlign: 'center', color: '#1D4ED8' }}>Member</span>
              <span style={{ textAlign: 'center', color: '#475569' }}>Viewer</span>
            </div>

            {PERMISSION_DEFINITIONS.map((group, groupIdx) => (
              <div key={groupIdx}>
                <div
                  style={{
                    padding: '8px 16px',
                    backgroundColor: '#F8FAFC',
                    borderBottom: '1px solid #E2E8F0',
                    borderTop: groupIdx > 0 ? '1px solid #E2E8F0' : 'none',
                    fontSize: 12,
                    fontWeight: 700,
                    color: '#2563EB',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                  }}
                >
                  <Sparkles size={13} />
                  <span>{group.category}</span>
                </div>

                {group.permissions.map((p) => (
                  <div
                    key={p.key}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '2.5fr 1fr 1fr 1fr',
                      padding: '10px 16px',
                      borderBottom: '1px solid #F1F5F9',
                      alignItems: 'center',
                      fontSize: 12,
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 600, color: '#0F172A', display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span>{p.name}</span>
                        <code style={{ fontSize: 10, backgroundColor: '#F1F5F9', padding: '1px 5px', borderRadius: 4, color: '#64748B' }}>
                          {p.key}
                        </code>
                      </div>
                      <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>{p.desc}</div>
                    </div>

                    {['ADMINISTRATOR', 'MEMBER', 'VIEWER'].map((roleName) => (
                      <div key={roleName} style={{ textAlign: 'center' }}>
                        {p.roles.includes(roleName) ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, color: '#16A34A', fontWeight: 600, fontSize: 11 }}>
                            <Check size={14} strokeWidth={2.5} /> Được phép
                          </span>
                        ) : (
                          <span style={{ color: '#CBD5E1' }}>—</span>
                        )}
                      </div>
                    ))}
                  </div>
                ))}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ─── Add Member Modal (autocomplete search) ─── */}
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
              overflow: 'visible',
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
                borderTopLeftRadius: 12,
                borderTopRightRadius: 12,
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
              {addError && (
                <div
                  style={{
                    padding: '8px 12px',
                    borderRadius: 6,
                    backgroundColor: '#FEF2F2',
                    border: '1px solid #FEE2E2',
                    color: '#DC2626',
                    fontSize: 12,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                  }}
                >
                  <AlertCircle size={14} />
                  <span>{addError}</span>
                </div>
              )}

              {/* User search */}
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Tìm người dùng (tên, username hoặc email) <span style={{ color: '#EF4444' }}>*</span>
                </label>

                {selectedUser ? (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '8px 12px',
                      borderRadius: 8,
                      backgroundColor: '#EFF6FF',
                      border: '1.5px solid #3B82F6',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <div
                        style={{
                          width: 32,
                          height: 32,
                          borderRadius: '50%',
                          backgroundColor: '#2563EB',
                          color: '#FFFFFF',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 12,
                          fontWeight: 700,
                        }}
                      >
                        {getInitials(selectedUser.firstName || selectedUser.username)}
                      </div>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>
                          {selectedUser.firstName ? `${selectedUser.firstName} ${selectedUser.lastName || ''}`.trim() : selectedUser.username}
                        </div>
                        <div style={{ fontSize: 11, color: '#64748B' }}>{selectedUser.email || selectedUser.username}</div>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedUser(null);
                        setUserQuery('');
                      }}
                      style={{ background: 'transparent', border: 'none', color: '#64748B', cursor: 'pointer', padding: 4 }}
                      title="Chọn người khác"
                    >
                      <X size={16} />
                    </button>
                  </div>
                ) : (
                  <div style={{ position: 'relative' }}>
                    <Search size={14} color="#94A3B8" style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }} />
                    <input
                      type="text"
                      value={userQuery}
                      onChange={(e) => {
                        setUserQuery(e.target.value);
                        if (addError) setAddError('');
                      }}
                      placeholder="Nhập tên, username hoặc email để tìm kiếm..."
                      autoFocus
                      style={{
                        width: '100%',
                        padding: '9px 12px 9px 32px',
                        borderRadius: 8,
                        border: '1px solid #CBD5E1',
                        fontSize: 13,
                        color: '#0F172A',
                        outline: 'none',
                        boxSizing: 'border-box',
                      }}
                    />
                    {isSearchingUsers && (
                      <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 4 }}>Đang tìm kiếm...</div>
                    )}
                    {userSuggestions.length > 0 && (
                      <div
                        style={{
                          position: 'absolute',
                          top: '100%',
                          left: 0,
                          right: 0,
                          marginTop: 4,
                          backgroundColor: '#FFFFFF',
                          borderRadius: 8,
                          border: '1px solid #E2E8F0',
                          boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
                          zIndex: 80,
                          maxHeight: 220,
                          overflowY: 'auto',
                        }}
                      >
                        {userSuggestions.map((u) => {
                          const fullName = (u.firstName ? `${u.firstName} ${u.lastName || ''}` : u.username || 'User').trim();
                          const alreadyMember = members.some((m) => memberMatchesUser(m, u));
                          return (
                            <div
                              key={u.id}
                              onClick={() => {
                                if (alreadyMember) return;
                                setSelectedUser(u);
                                setUserSuggestions([]);
                              }}
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                padding: '8px 12px',
                                cursor: alreadyMember ? 'default' : 'pointer',
                                opacity: alreadyMember ? 0.6 : 1,
                                borderBottom: '1px solid #F8FAFC',
                              }}
                              onMouseEnter={(e) => {
                                if (!alreadyMember) e.currentTarget.style.backgroundColor = '#F1F5F9';
                              }}
                              onMouseLeave={(e) => {
                                if (!alreadyMember) e.currentTarget.style.backgroundColor = '#FFFFFF';
                              }}
                            >
                              <div>
                                <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>{fullName}</div>
                                <div style={{ fontSize: 11, color: '#64748B' }}>{u.email || `@${u.username}`}</div>
                              </div>
                              {alreadyMember ? (
                                <span style={{ fontSize: 11, color: '#059669', fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4 }}>
                                  <Check size={12} /> Đã trong dự án
                                </span>
                              ) : (
                                <span style={{ fontSize: 11, color: '#2563EB', fontWeight: 500 }}>Chọn ➔</span>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
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
                  disabled={addingMember || !selectedUser}
                  style={{
                    padding: '7px 18px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: addingMember || !selectedUser ? '#94A3B8' : '#2563EB',
                    color: '#FFFFFF',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: addingMember || !selectedUser ? 'not-allowed' : 'pointer',
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
