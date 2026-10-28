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
  Info,
  ChevronDown,
  ChevronUp,
  Mail,
  CheckCircle2,
  Loader2,
  Building2,
  UserCheck
} from 'lucide-react';
import toast from 'react-hot-toast';
import { projectApi, userApi } from '../../api.js';
import { workspaceApi } from '../../api/workspaceServiceApi.js';
import PendingInvitations from '../common/PendingInvitations.jsx';
import { useModalBackdrop } from '../../hooks/useModalBackdrop.js';

const ROLE_COLORS = {
  ADMINISTRATOR: { bg: '#FAF5FF', text: '#7E22CE', border: '#E9D5FF' },
  MEMBER: { bg: '#EFF6FF', text: '#1D4ED8', border: '#BFDBFE' },
  VIEWER: { bg: '#F8FAFC', text: '#475569', border: '#E2E8F0' },
};

const PERMISSION_DEFINITIONS = [
  {
    category: '1. Quản trị Dự án & Vòng đời Quy trình',
    permissions: [
      {
        key: 'PROJECT_ADMIN',
        name: 'Toàn quyền Quản trị Dự án',
        desc: 'Cấu hình thông tin dự án, quản lý thành viên, gán vai trò và xóa dự án.',
        roles: ['ADMINISTRATOR'],
      },
      {
        key: 'PROJECT_VIEW',
        name: 'Xem Dự án & Bảng làm việc',
        desc: 'Xem tổng quan dự án, danh sách công việc, bảng làm việc và báo cáo tiến độ.',
        roles: ['ADMINISTRATOR', 'MEMBER', 'VIEWER'],
      },
      {
        key: 'SPRINT_MANAGE',
        name: 'Quản lý Chu kỳ Làm việc',
        desc: 'Lập kế hoạch tồn đọng, tạo chu kỳ mới, Bắt đầu và Hoàn tất chu kỳ.',
        roles: ['ADMINISTRATOR'],
      },
      {
        key: 'RELEASE_MANAGE',
        name: 'Quản lý Phiên bản Phát hành',
        desc: 'Tạo đợt phát hành, chốt danh sách công việc đã xong và tạo ghi chú phát hành.',
        roles: ['ADMINISTRATOR'],
      },
    ],
  },
  {
    category: '2. Quản lý & Thao tác Công việc',
    permissions: [
      {
        key: 'ISSUE_CREATE',
        name: 'Tạo Công việc Mới',
        desc: 'Tạo mới Hạng mục lớn, Yêu cầu, Nhiệm vụ, Sự cố và Nhiệm vụ con trong dự án.',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ISSUE_EDIT',
        name: 'Chỉnh sửa Công việc',
        desc: 'Cập nhật tiêu đề, mô tả, điểm ước lượng, thời gian dự kiến và các trường tùy biến.',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ISSUE_TRANSITION',
        name: 'Chuyển Trạng thái / Kéo thả',
        desc: 'Kéo thả thẻ trên Bảng và thực hiện các bước chuyển quy trình (Cần làm -> Đang thực hiện -> Hoàn thành).',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ISSUE_ASSIGN',
        name: 'Phân công Người xử lý',
        desc: 'Gán hoặc đổi người chịu trách nhiệm cho công việc.',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
      {
        key: 'ISSUE_DELETE',
        name: 'Xóa Công việc',
        desc: 'Xóa vĩnh viễn hoặc chuyển công việc vào thùng rác dự án.',
        roles: ['ADMINISTRATOR'],
      },
    ],
  },
  {
    category: '3. Cộng tác & Thảo luận',
    permissions: [
      {
        key: 'COMMENT_CREATE',
        name: 'Bình luận & Tệp đính kèm',
        desc: 'Gửi bình luận trao đổi phân cấp và tải lên tài liệu đính kèm.',
        roles: ['ADMINISTRATOR', 'MEMBER'],
      },
    ],
  },
];

export default function ProjectMembersTab({ projectId, project, isAdmin, onMembersUpdated }) {
  const addModalBackdrop = useModalBackdrop(() => setIsAddModalOpen(false));
  const [members, setMembers] = useState([]);
  const [inviteTick, setInviteTick] = useState(0); // đổi để PendingInvitations tải lại sau khi gửi lời mời
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showDetailedMatrix, setShowDetailedMatrix] = useState(false);

  // Add Member Modal State
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [modalTab, setModalTab] = useState('workspace'); // 'workspace' | 'email'
  const [workspaceMembers, setWorkspaceMembers] = useState([]);
  const [loadingWorkspaceMembers, setLoadingWorkspaceMembers] = useState(false);
  const [candidateSearch, setCandidateSearch] = useState('');
  const [searchEmail, setSearchEmail] = useState('');
  const [searchingUser, setSearchingUser] = useState(false);
  const [foundUser, setFoundUser] = useState(null);
  const [searchError, setSearchError] = useState('');
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

  const openAddMemberModal = () => {
    setIsAddModalOpen(true);
    setSearchEmail('');
    setSearchingUser(false);
    setFoundUser(null);
    setSearchError('');
    setCandidateSearch('');
    setSelectedRoleId(roles[1]?.id || roles[0]?.id || '');

    // Nạp danh sách thành viên Không gian làm việc để chọn nhanh
    const wsId = project?.workspaceId;
    if (wsId) {
      setLoadingWorkspaceMembers(true);
      workspaceApi.getMembers(wsId, { size: 100 })
        .then((res) => {
          const list = res?.data?.content || res?.data || [];
          const allWsMembers = Array.isArray(list) ? list : [];
          setWorkspaceMembers(allWsMembers);
          // Nếu còn thành viên trong workspace chưa vào project -> ưu tiên tab 'workspace'
          const remaining = allWsMembers.filter((wm) => !members.some((m) => m.userId === wm.userId));
          setModalTab(remaining.length > 0 ? 'workspace' : 'email');
        })
        .catch((err) => {
          console.warn('Không thể nạp danh sách thành viên workspace:', err);
          setModalTab('email');
        })
        .finally(() => {
          setLoadingWorkspaceMembers(false);
        });
    } else {
      setModalTab('email');
    }
  };

  const handleSearchUser = async (e) => {
    if (e) e.preventDefault();
    const cleanEmail = searchEmail.trim();
    if (!cleanEmail) {
      setSearchError('Vui lòng nhập địa chỉ email của người dùng');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(cleanEmail)) {
      setSearchError('Định dạng email không hợp lệ. Vui lòng nhập đúng email (ví dụ: user@example.com)');
      return;
    }

    setSearchingUser(true);
    setSearchError('');
    setFoundUser(null);

    try {
      let matchedUser = null;
      try {
        const res = await userApi.getByEmail(cleanEmail, { skipErrorToast: true });
        if (res && res.data) {
          matchedUser = res.data;
        }
      } catch (err) {
        // Fallback sang API search
        try {
          const searchRes = await userApi.search(cleanEmail, { skipErrorToast: true });
          const userList = searchRes?.data || [];
          if (Array.isArray(userList)) {
            matchedUser = userList.find(
              (u) => u.email && u.email.trim().toLowerCase() === cleanEmail.toLowerCase()
            );
          }
        } catch (searchErr) {
          // ignore fallback err
        }
      }

      if (!matchedUser) {
        setSearchError('Không tìm thấy người dùng nào với email này. Vui lòng kiểm tra lại chính xác từng ký tự email.');
        return;
      }

      // Đảm bảo 100% email phải khớp chính xác
      if (!matchedUser.email || matchedUser.email.trim().toLowerCase() !== cleanEmail.toLowerCase()) {
        setSearchError('Không tìm thấy người dùng nào khớp 100% email này.');
        return;
      }

      // Kiểm tra xem đã là thành viên trong dự án chưa
      const isAlreadyMember = members.some((m) => m.userId === matchedUser.id);
      if (isAlreadyMember) {
        setSearchError('Người dùng này đã là thành viên của dự án.');
        return;
      }

      setFoundUser(matchedUser);
    } catch (err) {
      console.error('Lỗi khi tìm kiếm người dùng:', err);
      setSearchError('Đã có lỗi xảy ra khi tìm kiếm người dùng. Vui lòng thử lại.');
    } finally {
      setSearchingUser(false);
    }
  };

  const handleAddMemberSubmit = async (e) => {
    e.preventDefault();
    if (!foundUser) {
      toast.error('Vui lòng tìm và xác nhận người dùng qua email trước');
      return;
    }
    if (!selectedRoleId) {
      toast.error('Vui lòng chọn vai trò cho thành viên');
      return;
    }

    try {
      setAddingMember(true);
      await projectApi.addMember(projectId, {
        userId: foundUser.id,
        roleId: selectedRoleId,
      });

      toast.success('Đã gửi lời mời. Người được mời cần chấp nhận để trở thành thành viên.');
      setIsAddModalOpen(false);
      setInviteTick((t) => t + 1);
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
      toast.error('Không thể xóa Trưởng dự án khỏi dự án');
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

  // Tính toán danh sách thành viên trong Workspace chưa tham gia dự án
  const availableCandidates = (workspaceMembers || []).filter(
    (wm) => !(members || []).some((m) => m.userId === wm.userId)
  );

  const filteredCandidates = availableCandidates.filter((c) => {
    if (!candidateSearch.trim()) return true;
    const q = candidateSearch.toLowerCase();
    return (
      (c.fullName && c.fullName.toLowerCase().includes(q)) ||
      (c.username && c.username.toLowerCase().includes(q)) ||
      (c.email && c.email.toLowerCase().includes(q))
    );
  });

  const isFoundUserInWorkspace = Boolean(
    foundUser && (
      foundUser.isInWorkspace ||
      (workspaceMembers || []).some((wm) => wm.userId === foundUser.id)
    )
  );

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
        <div style={{ padding: '0 18px' }}>
          <PendingInvitations projectId={projectId} canManage={isAdmin} refreshKey={inviteTick} />
        </div>
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
                            TRƯỞNG DỰ ÁN
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
            <span>Phân quyền chi tiết theo Vai trò</span>
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
            <span>{showDetailedMatrix ? 'Thu gọn tóm tắt' : 'Xem chi tiết 10 quyền hạn'}</span>
            {showDetailedMatrix ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
          </button>
        </div>

        {/* Summary 3 Cards */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, fontSize: 12, marginBottom: showDetailedMatrix ? 16 : 0 }}>
          <div style={{ padding: 12, backgroundColor: '#FAF5FF', borderRadius: 8, border: '1px solid #E9D5FF' }}>
            <div style={{ fontWeight: 700, color: '#7E22CE', marginBottom: 4, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>Quản trị viên</span>
              <span style={{ fontSize: 11, backgroundColor: '#F3E8FF', padding: '1px 6px', borderRadius: 10 }}>10 / 10 quyền</span>
            </div>
            <div style={{ color: '#6B21A8', lineHeight: 1.4 }}>
              Toàn quyền cấu hình dự án, thành viên, quy trình, bảng làm việc, chu kỳ và xóa công việc.
            </div>
          </div>
          <div style={{ padding: 12, backgroundColor: '#EFF6FF', borderRadius: 8, border: '1px solid #BFDBFE' }}>
            <div style={{ fontWeight: 700, color: '#1D4ED8', marginBottom: 4, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>Thành viên</span>
              <span style={{ fontSize: 11, backgroundColor: '#DBEAFE', padding: '1px 6px', borderRadius: 10 }}>6 / 10 quyền</span>
            </div>
            <div style={{ color: '#1E40AF', lineHeight: 1.4 }}>
              Xem dự án, tạo công việc, chỉnh sửa, phân công, kéo thả trạng thái và thêm bình luận.
            </div>
          </div>
          <div style={{ padding: 12, backgroundColor: '#F8FAFC', borderRadius: 8, border: '1px solid #E2E8F0' }}>
            <div style={{ fontWeight: 700, color: '#475569', marginBottom: 4, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>Người xem</span>
              <span style={{ fontSize: 11, backgroundColor: '#E2E8F0', padding: '1px 6px', borderRadius: 10 }}>1 / 10 quyền</span>
            </div>
            <div style={{ color: '#64748B', lineHeight: 1.4 }}>
              Chỉ đọc: Xem danh sách công việc, bảng làm việc và báo cáo mà không thể chỉnh sửa.
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
              <span>Tên quyền hạn & Mô tả</span>
              <span style={{ textAlign: 'center', color: '#7E22CE' }}>Quản trị viên</span>
              <span style={{ textAlign: 'center', color: '#1D4ED8' }}>Thành viên</span>
              <span style={{ textAlign: 'center', color: '#475569' }}>Người xem</span>
            </div>

            {PERMISSION_DEFINITIONS.map((group, groupIdx) => (
              <div key={groupIdx}>
                {/* Category Header */}
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

                {/* Rows */}
                {group.permissions.map((p) => {
                  const hasAdmin = p.roles.includes('ADMINISTRATOR');
                  const hasMember = p.roles.includes('MEMBER');
                  const hasViewer = p.roles.includes('VIEWER');

                  return (
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
                      {/* Permission Name & Desc */}
                      <div>
                        <div style={{ fontWeight: 600, color: '#0F172A', display: 'flex', alignItems: 'center', gap: 6 }}>
                          <span>{p.name}</span>
                          <code style={{ fontSize: 10, backgroundColor: '#F1F5F9', padding: '1px 5px', borderRadius: 4, color: '#64748B' }}>
                            {p.key}
                          </code>
                        </div>
                        <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>{p.desc}</div>
                      </div>

                      {/* Admin Check */}
                      <div style={{ textAlign: 'center' }}>
                        {hasAdmin ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, color: '#16A34A', fontWeight: 600, fontSize: 11 }}>
                            <Check size={14} strokeWidth={2.5} /> Được phép
                          </span>
                        ) : (
                          <span style={{ color: '#CBD5E1' }}>—</span>
                        )}
                      </div>

                      {/* Member Check */}
                      <div style={{ textAlign: 'center' }}>
                        {hasMember ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, color: '#16A34A', fontWeight: 600, fontSize: 11 }}>
                            <Check size={14} strokeWidth={2.5} /> Được phép
                          </span>
                        ) : (
                          <span style={{ color: '#CBD5E1' }}>—</span>
                        )}
                      </div>

                      {/* Viewer Check */}
                      <div style={{ textAlign: 'center' }}>
                        {hasViewer ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, color: '#16A34A', fontWeight: 600, fontSize: 11 }}>
                            <Check size={14} strokeWidth={2.5} /> Được phép
                          </span>
                        ) : (
                          <span style={{ color: '#CBD5E1' }}>—</span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
        )}
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
          {...addModalBackdrop}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
              width: '100%',
              maxWidth: 520,
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

            {/* ─── Tabs chuyển đổi: Thành viên Workspace vs Mời qua Email ─── */}
            <div style={{ display: 'flex', borderBottom: '1px solid #E2E8F0', backgroundColor: '#F8FAFC' }}>
              <button
                type="button"
                onClick={() => {
                  setModalTab('workspace');
                  setSearchError('');
                }}
                style={{
                  flex: 1,
                  padding: '11px 16px',
                  fontSize: 13,
                  fontWeight: modalTab === 'workspace' ? 700 : 500,
                  color: modalTab === 'workspace' ? '#2563EB' : '#64748B',
                  backgroundColor: modalTab === 'workspace' ? '#FFFFFF' : 'transparent',
                  border: 'none',
                  borderBottom: modalTab === 'workspace' ? '2px solid #2563EB' : '2px solid transparent',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 6,
                  transition: 'all 0.15s ease',
                }}
              >
                <Building2 size={15} />
                <span>Từ Không gian làm việc ({availableCandidates.length})</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setModalTab('email');
                  setSearchError('');
                }}
                style={{
                  flex: 1,
                  padding: '11px 16px',
                  fontSize: 13,
                  fontWeight: modalTab === 'email' ? 700 : 500,
                  color: modalTab === 'email' ? '#2563EB' : '#64748B',
                  backgroundColor: modalTab === 'email' ? '#FFFFFF' : 'transparent',
                  border: 'none',
                  borderBottom: modalTab === 'email' ? '2px solid #2563EB' : '2px solid transparent',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 6,
                  transition: 'all 0.15s ease',
                }}
              >
                <Mail size={15} />
                <span>Mời người mới qua Email</span>
              </button>
            </div>

            <form onSubmit={handleAddMemberSubmit} style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* TAB 1: CHỌN NHANH TỪ WORKSPACE */}
              {modalTab === 'workspace' && (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                    <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>
                      Chọn thành viên từ Không gian làm việc
                    </label>
                    <span style={{ fontSize: 11, color: '#64748B' }}>
                      Chọn 1 người để thêm nhanh vào dự án
                    </span>
                  </div>

                  {/* Ô tìm kiếm nhanh trong danh sách ứng viên */}
                  {availableCandidates.length > 5 && (
                    <div style={{ position: 'relative', marginBottom: 10 }}>
                      <Search
                        size={15}
                        color="#94A3B8"
                        style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
                      />
                      <input
                        type="text"
                        value={candidateSearch}
                        onChange={(e) => setCandidateSearch(e.target.value)}
                        placeholder="Lọc theo tên hoặc email..."
                        style={{
                          width: '100%',
                          padding: '7px 10px 7px 32px',
                          borderRadius: 6,
                          border: '1px solid #CBD5E1',
                          fontSize: 12,
                          color: '#0F172A',
                          outline: 'none',
                          boxSizing: 'border-box',
                        }}
                      />
                    </div>
                  )}

                  {/* Danh sách ứng viên */}
                  {loadingWorkspaceMembers ? (
                    <div style={{ padding: 24, textAlign: 'center', color: '#64748B', fontSize: 13, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                      <Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} />
                      <span>Đang nạp thành viên Không gian làm việc...</span>
                    </div>
                  ) : availableCandidates.length === 0 ? (
                    <div
                      style={{
                        padding: '24px 16px',
                        textAlign: 'center',
                        borderRadius: 8,
                        backgroundColor: '#F8FAFC',
                        border: '1px dashed #CBD5E1',
                      }}
                    >
                      <CheckCircle2 size={24} color="#16A34A" style={{ margin: '0 auto 8px' }} />
                      <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', marginBottom: 4 }}>
                        Tất cả thành viên đã tham gia dự án
                      </div>
                      <div style={{ fontSize: 12, color: '#64748B', marginBottom: 12 }}>
                        Không còn thành viên nào trong Không gian làm việc chưa thuộc dự án này.
                      </div>
                      <button
                        type="button"
                        onClick={() => setModalTab('email')}
                        style={{
                          padding: '6px 12px',
                          borderRadius: 6,
                          border: '1px solid #2563EB',
                          backgroundColor: '#EFF6FF',
                          color: '#2563EB',
                          fontSize: 12,
                          fontWeight: 600,
                          cursor: 'pointer',
                        }}
                      >
                        Chuyển sang Mời người mới qua Email
                      </button>
                    </div>
                  ) : filteredCandidates.length === 0 ? (
                    <div style={{ padding: 16, textAlign: 'center', color: '#64748B', fontSize: 12 }}>
                      Không tìm thấy thành viên nào khớp với "{candidateSearch}"
                    </div>
                  ) : (
                    <div
                      style={{
                        maxHeight: 220,
                        overflowY: 'auto',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 6,
                        paddingRight: 4,
                      }}
                    >
                      {filteredCandidates.map((c) => {
                        const isSelected = foundUser?.id === c.userId;
                        const wsBadge = (() => {
                          switch (c.role) {
                            case 'OWNER':
                              return { text: 'Chủ sở hữu', bg: '#FEF3C7', color: '#B45309' };
                            case 'ADMIN':
                              return { text: 'Quản trị WS', bg: '#FAF5FF', color: '#7E22CE' };
                            case 'MEMBER':
                              return { text: 'Thành viên WS', bg: '#EFF6FF', color: '#1D4ED8' };
                            case 'GUEST':
                              return { text: 'Khách', bg: '#F1F5F9', color: '#475569' };
                            default:
                              return { text: c.role || 'Thành viên', bg: '#F1F5F9', color: '#475569' };
                          }
                        })();

                        return (
                          <div
                            key={c.userId}
                            onClick={() => {
                              if (isSelected) {
                                setFoundUser(null);
                              } else {
                                setFoundUser({
                                  id: c.userId,
                                  username: c.username,
                                  email: c.email,
                                  fullName: c.fullName,
                                  isInWorkspace: true,
                                  workspaceRole: c.role,
                                });
                                setSearchError('');
                              }
                            }}
                            style={{
                              padding: '10px 12px',
                              borderRadius: 8,
                              border: isSelected ? '2px solid #2563EB' : '1px solid #E2E8F0',
                              backgroundColor: isSelected ? '#EFF6FF' : '#FFFFFF',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'space-between',
                              gap: 12,
                              cursor: 'pointer',
                              transition: 'all 0.15s ease',
                            }}
                            onMouseEnter={(e) => {
                              if (!isSelected) e.currentTarget.style.backgroundColor = '#F8FAFC';
                            }}
                            onMouseLeave={(e) => {
                              if (!isSelected) e.currentTarget.style.backgroundColor = '#FFFFFF';
                            }}
                          >
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0, flex: 1 }}>
                              <div
                                style={{
                                  width: 34,
                                  height: 34,
                                  borderRadius: '50%',
                                  backgroundColor: isSelected ? '#2563EB' : '#E2E8F0',
                                  color: isSelected ? '#FFFFFF' : '#475569',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontWeight: 700,
                                  fontSize: 13,
                                  flexShrink: 0,
                                }}
                              >
                                {(c.fullName || c.username || c.email || 'U').charAt(0).toUpperCase()}
                              </div>
                              <div style={{ minWidth: 0, flex: 1 }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                                  <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>
                                    {c.fullName || c.username || c.email}
                                  </span>
                                  {c.username && c.username !== c.email && (
                                    <span style={{ fontSize: 11, color: '#64748B' }}>
                                      @{c.username}
                                    </span>
                                  )}
                                </div>
                                <div style={{ fontSize: 11, color: '#64748B', wordBreak: 'break-all' }}>
                                  {c.email}
                                </div>
                              </div>
                            </div>

                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                              <span
                                style={{
                                  fontSize: 10.5,
                                  fontWeight: 600,
                                  padding: '2px 7px',
                                  borderRadius: 10,
                                  backgroundColor: wsBadge.bg,
                                  color: wsBadge.color,
                                }}
                              >
                                {wsBadge.text}
                              </span>
                              <div
                                style={{
                                  width: 18,
                                  height: 18,
                                  borderRadius: '50%',
                                  border: isSelected ? '2px solid #2563EB' : '1px solid #CBD5E1',
                                  backgroundColor: isSelected ? '#2563EB' : '#FFFFFF',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                }}
                              >
                                {isSelected && <Check size={12} color="#FFFFFF" strokeWidth={3} />}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}

              {/* TAB 2: MỜI NGƯỜI MỚI QUA EMAIL */}
              {modalTab === 'email' && (
                <div>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                    Email người dùng <span style={{ color: '#EF4444' }}>*</span>
                  </label>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <div style={{ position: 'relative', flex: 1 }}>
                      <Mail
                        size={16}
                        color="#94A3B8"
                        style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
                      />
                      <input
                        type="email"
                        value={searchEmail}
                        onChange={(e) => {
                          setSearchEmail(e.target.value);
                          setSearchError('');
                          if (foundUser) setFoundUser(null);
                        }}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            handleSearchUser();
                          }
                        }}
                        placeholder="Nhập chính xác email (ví dụ: user@example.com)"
                        style={{
                          width: '100%',
                          padding: '9px 12px 9px 34px',
                          borderRadius: 8,
                          border: searchError ? '1px solid #EF4444' : '1px solid #CBD5E1',
                          fontSize: 13,
                          color: '#0F172A',
                          outline: 'none',
                          boxSizing: 'border-box',
                        }}
                      />
                    </div>
                    <button
                      type="button"
                      onClick={handleSearchUser}
                      disabled={searchingUser || !searchEmail.trim()}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                        padding: '8px 14px',
                        borderRadius: 8,
                        border: 'none',
                        backgroundColor: searchEmail.trim() ? '#2563EB' : '#94A3B8',
                        color: '#FFFFFF',
                        fontSize: 13,
                        fontWeight: 600,
                        cursor: searchingUser || !searchEmail.trim() ? 'not-allowed' : 'pointer',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {searchingUser ? (
                        <>
                          <Loader2 size={15} style={{ animation: 'spin 1s linear infinite' }} />
                          <span>Đang tìm...</span>
                        </>
                      ) : (
                        <>
                          <Search size={15} />
                          <span>Tìm kiếm</span>
                        </>
                      )}
                    </button>
                  </div>

                  <div style={{ fontSize: 11, color: '#64748B', marginTop: 4 }}>
                    Chỉ người dùng có địa chỉ email trùng khớp 100% trong hệ thống mới được hiển thị.
                  </div>

                  {/* Search Error Message */}
                  {searchError && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '8px 12px',
                        borderRadius: 6,
                        backgroundColor: '#FEF2F2',
                        border: '1px solid #FECACA',
                        color: '#DC2626',
                        fontSize: 12,
                        marginTop: 8,
                      }}
                    >
                      <AlertCircle size={15} style={{ flexShrink: 0 }} />
                      <span>{searchError}</span>
                    </div>
                  )}

                  {/* Found User Card */}
                  {foundUser && (
                    <div
                      style={{
                        marginTop: 12,
                        padding: '14px 16px',
                        borderRadius: 10,
                        backgroundColor: '#F0FDF4',
                        border: '1px solid #86EFAC',
                        display: 'flex',
                        alignItems: 'flex-start',
                        justifyContent: 'space-between',
                        gap: 12,
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, minWidth: 0, flex: 1 }}>
                        <div
                          style={{
                            width: 42,
                            height: 42,
                            borderRadius: '50%',
                            backgroundColor: '#2563EB',
                            color: '#FFFFFF',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontWeight: 700,
                            fontSize: 16,
                            flexShrink: 0,
                            marginTop: 2,
                            boxShadow: '0 2px 4px rgba(37, 99, 235, 0.2)',
                          }}
                        >
                          {(foundUser.fullName || foundUser.username || foundUser.email || 'U')
                            .charAt(0)
                            .toUpperCase()}
                        </div>

                        <div style={{ minWidth: 0, flex: 1 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 4 }}>
                            <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', wordBreak: 'break-all' }}>
                              {foundUser.fullName || foundUser.username || foundUser.email}
                            </span>
                            {foundUser.username && foundUser.username !== foundUser.email && (
                              <span style={{ fontSize: 12, color: '#64748B' }}>
                                (@{foundUser.username})
                              </span>
                            )}
                          </div>

                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 6,
                              fontSize: 12,
                              color: '#15803D',
                              fontWeight: 500,
                              marginBottom: 6,
                              wordBreak: 'break-all',
                            }}
                          >
                            <Mail size={13} style={{ flexShrink: 0 }} />
                            <span>{foundUser.email}</span>
                          </div>

                          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                            <span
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 4,
                                fontSize: 11,
                                fontWeight: 600,
                                color: '#166534',
                                backgroundColor: '#DCFCE7',
                                border: '1px solid #BBF7D0',
                                padding: '2px 8px',
                                borderRadius: 12,
                              }}
                            >
                              <CheckCircle2 size={12} color="#16A34A" /> Đã xác thực email
                            </span>

                            {isFoundUserInWorkspace ? (
                              <span
                                style={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: 4,
                                  fontSize: 11,
                                  fontWeight: 600,
                                  color: '#1D4ED8',
                                  backgroundColor: '#EFF6FF',
                                  border: '1px solid #BFDBFE',
                                  padding: '2px 8px',
                                  borderRadius: 12,
                                }}
                              >
                                <Building2 size={12} color="#2563EB" /> Thành viên Không gian làm việc
                              </span>
                            ) : null}
                          </div>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => {
                          setFoundUser(null);
                          setSearchEmail('');
                        }}
                        title="Nhập email khác"
                        style={{
                          whiteSpace: 'nowrap',
                          flexShrink: 0,
                          backgroundColor: '#FFFFFF',
                          border: '1px solid #CBD5E1',
                          borderRadius: 6,
                          color: '#475569',
                          cursor: 'pointer',
                          padding: '6px 12px',
                          fontSize: 12,
                          fontWeight: 600,
                        }}
                      >
                        Đổi email
                      </button>
                    </div>
                  )}

                  {/* Smart Auto-Cascade: 1 dòng ngắn gọn */}
                  {foundUser && !isFoundUserInWorkspace && (
                    <div
                      style={{
                        marginTop: 10,
                        padding: '8px 12px',
                        borderRadius: 6,
                        backgroundColor: '#FAF5FF',
                        border: '1px solid #E9D5FF',
                        color: '#7E22CE',
                        fontSize: 12,
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <Sparkles size={14} color="#9333EA" style={{ flexShrink: 0 }} />
                      <span>
                        Người này sẽ được <b>tự động thêm vào Không gian làm việc</b> khi chấp nhận lời mời.
                      </span>
                    </div>
                  )}
                </div>
              )}

              {/* Tóm tắt người được chọn (áp dụng cho Tab Workspace) */}
              {modalTab === 'workspace' && foundUser && (
                <div
                  style={{
                    padding: '8px 12px',
                    borderRadius: 6,
                    backgroundColor: '#EFF6FF',
                    border: '1px solid #BFDBFE',
                    fontSize: 12,
                    color: '#1E40AF',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <UserCheck size={14} color="#2563EB" />
                    <span>Đã chọn: <b>{foundUser.fullName || foundUser.username || foundUser.email}</b> ({foundUser.email})</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setFoundUser(null)}
                    style={{ background: 'none', border: 'none', color: '#64748B', cursor: 'pointer', fontSize: 11, fontWeight: 600 }}
                  >
                    Bỏ chọn
                  </button>
                </div>
              )}

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
                  disabled={addingMember || !foundUser}
                  title={!foundUser ? 'Vui lòng chọn hoặc tìm người dùng trước' : ''}
                  style={{
                    padding: '7px 18px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: foundUser && !addingMember ? '#2563EB' : '#94A3B8',
                    color: '#FFFFFF',
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: addingMember || !foundUser ? 'not-allowed' : 'pointer',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 6,
                  }}
                >
                  {addingMember ? 'Đang gửi lời mời...' : 'Gửi lời mời tham gia'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
