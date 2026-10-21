import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import { wsPath } from '../utils/workspacePath.js';
import {
  Users,
  UserPlus,
  Star,
  MoreHorizontal,
  Link2,
  SlidersHorizontal,
  ArrowUpDown,
  Mail,
  Trash2,
  X,
  Sparkles,
  AlertCircle,
  FolderKanban,
  FileText,
  Home,
  Check,
  Search,
  UserCheck,
  LogOut,
  ShieldAlert,
  Crown
} from 'lucide-react';
import toast from 'react-hot-toast';
import { teamApi, userApi } from '../api.js';
import { workspaceApi } from '../api/workspaceServiceApi.js';
import PendingInvitations from '../components/common/PendingInvitations.jsx';
import { useWorkspace } from '../context/WorkspaceContext.jsx';

export default function TeamDetailPage() {
  const { teamId, workspaceId } = useParams();
  const navigate = useNavigate();
  const outletContext = useOutletContext();
  const refreshTeams = outletContext?.refreshTeams;

  const [currentUser, setCurrentUser] = useState(null);
  const [team, setTeam] = useState(null);
  const [members, setMembers] = useState([]);
  const [inviteTick, setInviteTick] = useState(0); // đổi để PendingInvitations tải lại sau khi gửi lời mời
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('members'); // 'overview' | 'documents' | 'members'
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [memberIdentifier, setMemberIdentifier] = useState('');
  const [selectedUser, setSelectedUser] = useState(null);
  const [userSuggestions, setUserSuggestions] = useState([]);
  const [isSearchingUsers, setIsSearchingUsers] = useState(false);
  const [memberRole, setMemberRole] = useState('MEMBER');
  const [submitting, setSubmitting] = useState(false);
  const [addError, setAddError] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [isStarred, setIsStarred] = useState(false);

  const searchTimeoutRef = useRef(null);

  useEffect(() => {
    const fetchMe = async () => {
      try {
        const res = await userApi.me();
        if (res && res.data) {
          setCurrentUser(res.data);
        }
      } catch (err) {
        console.warn('Could not load current user profile:', err);
      }
    };
    fetchMe();
  }, []);

  const { myRole: workspaceRole } = useWorkspace();
  const isWorkspaceOwner = workspaceRole === 'OWNER';
  const isWorkspaceAdmin = workspaceRole === 'ADMIN';

  // Modal đổi Trưởng nhóm (dành cho Workspace Owner / Admin)
  const [isAssignLeaderModalOpen, setIsAssignLeaderModalOpen] = useState(false);
  const [selectedLeaderUser, setSelectedLeaderUser] = useState(null);
  const [leaderSearchQuery, setLeaderSearchQuery] = useState('');
  const [leaderSuggestions, setLeaderSuggestions] = useState([]);
  const [isSearchingLeader, setIsSearchingLeader] = useState(false);
  const [isAssigningLeader, setIsAssigningLeader] = useState(false);

  const isCurrentUserOwner = Boolean(
    currentUser &&
      team?.ownerId &&
      (team.ownerId === currentUser.id ||
        (currentUser.keycloakId && team.ownerId === currentUser.keycloakId))
  );

  const isCurrentUserLeader = Boolean(
    isWorkspaceOwner ||
    (currentUser &&
      members.some(
        (m) =>
          (m.userId === currentUser.id ||
            (currentUser.keycloakId && m.userId === currentUser.keycloakId)) &&
          m.role === 'LEADER'
      ))
  );

  const isCurrentUserMember = Boolean(
    isWorkspaceOwner ||
    isWorkspaceAdmin ||
    isCurrentUserOwner ||
      (currentUser &&
        members.some(
          (m) =>
            m.userId === currentUser.id ||
            (currentUser.keycloakId && m.userId === currentUser.keycloakId)
        ))
  );

  const canManageTeam = isCurrentUserLeader || isWorkspaceOwner || isWorkspaceAdmin;

  const handleUpdateRole = async (userId, newRole) => {
    if (members.length <= 1) {
      toast.error('Không thể thay đổi vai trò khi nhóm chỉ có 1 thành viên duy nhất');
      return;
    }
    try {
      await teamApi.updateMemberRole(teamId, userId, newRole);
      toast.success('Đã cập nhật vai trò thành công!');
      fetchTeamData();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Lỗi khi cập nhật vai trò';
      toast.error(msg);
    }
  };

  const handleLeaveTeam = async () => {
    if (!window.confirm(`Bạn có chắc chắn muốn rời khỏi nhóm "${team?.name || ''}" không?`)) {
      return;
    }
    try {
      setSubmitting(true);
      await teamApi.leaveTeam(teamId);
      toast.success('Bạn đã rời khỏi nhóm thành công');
      if (refreshTeams) refreshTeams();
      navigate(wsPath(workspaceId, '/projects'));
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Lỗi khi rời nhóm';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteTeam = async () => {
    if (
      !window.confirm(
        `Bạn có chắc chắn muốn xóa nhóm "${team?.name || ''}" không? Tất cả thành viên sẽ bị dọn dẹp và dự án sẽ được tách rời. Hành động này KHÔNG THỂ HOÀN TÁC.`
      )
    ) {
      return;
    }
    try {
      setSubmitting(true);
      await teamApi.delete(teamId);
      toast.success('Đã xóa nhóm thành công');
      if (refreshTeams) refreshTeams();
      navigate(wsPath(workspaceId, '/projects'));
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Lỗi khi xóa nhóm';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const fetchTeamData = async () => {
    try {
      setLoading(true);
      const [teamRes, membersRes] = await Promise.all([
        teamApi.getById(teamId),
        teamApi.getMembers(teamId),
      ]);
      if (teamRes && teamRes.data) setTeam(teamRes.data);
      if (membersRes && membersRes.data) setMembers(membersRes.data);
    } catch (err) {
      console.warn('Could not load team details:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (teamId) {
      fetchTeamData();
    }
  }, [teamId]);

  const [wsMembers, setWsMembers] = useState([]);
  const [loadingWsMembers, setLoadingWsMembers] = useState(false);

  // Tải danh sách thành viên của Workspace khi mở Add Member Modal hoặc Assign Leader Modal
  useEffect(() => {
    if ((!isAddModalOpen && !isAssignLeaderModalOpen) || !workspaceId) return;

    let cancelled = false;
    const fetchWsMembers = async () => {
      try {
        setLoadingWsMembers(true);
        const res = await workspaceApi.getMembers(workspaceId, { size: 200 });
        if (!cancelled) {
          const list = res?.data?.content || [];
          setWsMembers(list);
        }
      } catch (err) {
        console.warn('Could not load workspace members:', err);
      } finally {
        if (!cancelled) setLoadingWsMembers(false);
      }
    };

    fetchWsMembers();
    return () => {
      cancelled = true;
    };
  }, [isAddModalOpen, isAssignLeaderModalOpen, workspaceId]);

  // Lọc gợi ý người dùng CHỈ từ danh sách thành viên trong Workspace
  useEffect(() => {
    if (!isAddModalOpen) {
      setUserSuggestions([]);
      return;
    }

    if (selectedUser) return; // User already picked

    const query = memberIdentifier.trim().toLowerCase();
    
    // Định dạng danh sách thành viên workspace thành dạng thống nhất
    const formattedWsUsers = wsMembers.map((m) => {
      const first = m.firstName || '';
      const last = m.lastName || '';
      const computedName = (first + ' ' + last).trim();
      return {
        id: m.userId,
        fullName: m.fullName || (computedName.length > 0 ? computedName : m.username) || 'Thành viên',
        username: m.username,
        email: m.email,
      };
    });

    if (!query) {
      // Mặc định hiển thị danh sách thành viên trong Workspace
      setUserSuggestions(formattedWsUsers);
      return;
    }

    // Lọc theo query từ danh sách thành viên Workspace
    const filtered = formattedWsUsers.filter((u) => {
      const matchName = u.fullName && u.fullName.toLowerCase().includes(query);
      const matchUsername = u.username && u.username.toLowerCase().includes(query);
      const matchEmail = u.email && u.email.toLowerCase().includes(query);
      return matchName || matchUsername || matchEmail;
    });

    setUserSuggestions(filtered);
  }, [memberIdentifier, isAddModalOpen, selectedUser, wsMembers]);

  // Lọc danh sách bổ nhiệm Trưởng nhóm CHỈ từ danh sách thành viên trong Workspace
  useEffect(() => {
    if (!isAssignLeaderModalOpen) {
      setLeaderSuggestions([]);
      return;
    }

    if (selectedLeaderUser) return;

    const query = leaderSearchQuery.trim().toLowerCase();

    // Định dạng danh sách thành viên workspace thành dạng thống nhất
    const formattedWsUsers = wsMembers.map((m) => {
      const first = m.firstName || '';
      const last = m.lastName || '';
      const computedName = (first + ' ' + last).trim();
      return {
        id: m.userId,
        fullName: m.fullName || (computedName.length > 0 ? computedName : m.username) || 'Thành viên',
        username: m.username,
        email: m.email,
      };
    });

    if (!query) {
      // Mặc định hiển thị các thành viên Workspace (trừ leader hiện tại nếu có)
      const currentLeaderId = team?.ownerId;
      const candidates = formattedWsUsers.filter((u) => u.id !== currentLeaderId);
      setLeaderSuggestions(candidates);
      return;
    }

    const filtered = formattedWsUsers.filter((u) => {
      const matchName = u.fullName && u.fullName.toLowerCase().includes(query);
      const matchUsername = u.username && u.username.toLowerCase().includes(query);
      const matchEmail = u.email && u.email.toLowerCase().includes(query);
      return matchName || matchUsername || matchEmail;
    });

    setLeaderSuggestions(filtered);
  }, [leaderSearchQuery, isAssignLeaderModalOpen, selectedLeaderUser, wsMembers, team?.ownerId]);

  const handleAssignLeader = async (e) => {
    if (e) e.preventDefault();
    if (!selectedLeaderUser) {
      toast.error('Vui lòng chọn thành viên để bổ nhiệm làm Trưởng nhóm');
      return;
    }

    const targetUserId = selectedLeaderUser.id || selectedLeaderUser.keycloakId;
    try {
      setIsAssigningLeader(true);
      await teamApi.assignLeader(teamId, targetUserId);
      const name = selectedLeaderUser.fullName || selectedLeaderUser.username || selectedLeaderUser.email;
      toast.success(`Đã bổ nhiệm ${name} làm Trưởng nhóm mới thành công!`);
      setIsAssignLeaderModalOpen(false);
      setSelectedLeaderUser(null);
      setLeaderSearchQuery('');
      fetchTeamData();
      if (refreshTeams) refreshTeams();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Lỗi khi bổ nhiệm Trưởng nhóm';
      toast.error(msg);
    } finally {
      setIsAssigningLeader(false);
    }
  };

  const handleOpenAddModal = () => {
    setMemberIdentifier('');
    setSelectedUser(null);
    setUserSuggestions([]);
    setAddError('');
    setMemberRole('MEMBER');
    setIsAddModalOpen(true);
  };

  const handleSelectUser = (user) => {
    setSelectedUser(user);
    setMemberIdentifier(user.email || user.username || '');
    setUserSuggestions([]);
    setAddError('');
  };

  const handleClearSelectedUser = () => {
    setSelectedUser(null);
    setMemberIdentifier('');
    setUserSuggestions([]);
  };

  const handleAddMember = async (e) => {
    e.preventDefault();
    let targetUserId = selectedUser ? selectedUser.id : null;

    if (!targetUserId && memberIdentifier.trim()) {
      const query = memberIdentifier.trim().toLowerCase();
      const matched = wsMembers.find(
        (m) =>
          (m.email && m.email.toLowerCase() === query) ||
          (m.username && m.username.toLowerCase() === query) ||
          (m.userId && m.userId.toLowerCase() === query)
      );

      if (!matched) {
        setAddError('Người dùng này không thuộc Không gian làm việc (Workspace). Chỉ có thể mời thành viên đang thuộc Không gian làm việc này vào nhóm.');
        toast.error('Chỉ có thể mời người dùng đang là thành viên của Không gian làm việc này vào nhóm');
        return;
      }
      targetUserId = matched.userId;
    }

    if (!targetUserId) {
      setAddError('Vui lòng chọn người dùng từ danh sách thành viên Workspace');
      return;
    }

    try {
      setSubmitting(true);
      setAddError('');
      await teamApi.addMember(teamId, {
        userId: targetUserId,
        role: memberRole,
      });

      toast.success('Đã gửi lời mời. Người được mời cần chấp nhận để trở thành thành viên.');
      setIsAddModalOpen(false);
      setInviteTick((t) => t + 1);
      fetchTeamData();
    } catch (err) {
      const msg = err.response?.data?.message || 'Không tìm thấy người dùng hoặc có lỗi xảy ra.';
      setAddError(msg);
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemoveMember = async (userId, memberName) => {
    if (!canManageTeam) {
      toast.error('Bạn không có quyền xóa thành viên khỏi nhóm');
      return;
    }
    if (currentUser?.id && userId === currentUser.id) {
      toast.error('Bạn không thể tự xóa chính mình khỏi nhóm');
      return;
    }
    if (!window.confirm(`Bạn có chắc chắn muốn xóa "${memberName}" khỏi nhóm?`)) {
      return;
    }
    try {
      await teamApi.removeMember(teamId, userId);
      toast.success('Đã xóa thành viên khỏi nhóm');
      fetchTeamData();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Lỗi khi xóa thành viên';
      toast.error(msg);
    }
  };

  const filteredMembers = members.filter((m) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      (m.fullName && m.fullName.toLowerCase().includes(q)) ||
      (m.username && m.username.toLowerCase().includes(q)) ||
      (m.email && m.email.toLowerCase().includes(q))
    );
  });

  const getRoleLabel = (role) => {
    switch (role) {
      case 'LEADER':
        return 'Trưởng nhóm';
      case 'VIEWER':
        return 'Người xem';
      case 'MEMBER':
      default:
        return 'Thành viên';
    }
  };

  const getInitials = (name) => {
    if (!name) return 'U';
    const parts = name.trim().split(' ');
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

  const getAvatarBg = (str) => {
    const colors = ['#2563EB', '#7C3AED', '#DB2777', '#059669', '#D97706', '#0284C7', '#4F46E5'];
    let hash = 0;
    for (let i = 0; i < (str || '').length; i++) {
      hash = str.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
  };

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        overflow: 'hidden',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      {/* ─── Top Header Breadcrumbs Bar ─── */}
      <div
        style={{
          height: 48,
          minHeight: 48,
          padding: '0 24px',
          borderBottom: '1px solid #F1F5F9',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{
              width: 24,
              height: 24,
              borderRadius: 6,
              backgroundColor: '#FDF2F8',
              color: '#DB2777',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Home size={14} />
          </div>
          <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
            {team?.name || 'Nhóm'}
          </span>
          <button
            onClick={() => setIsStarred(!isStarred)}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: isStarred ? '#EAB308' : '#94A3B8',
              padding: 2,
              display: 'flex',
              alignItems: 'center',
            }}
            title={isStarred ? 'Bỏ yêu thích' : 'Yêu thích'}
          >
            <Star size={15} fill={isStarred ? '#EAB308' : 'none'} />
          </button>
          <button
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#94A3B8',
              padding: 2,
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <MoreHorizontal size={16} />
          </button>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {/* Nút Bổ nhiệm Trưởng nhóm: Chỉ Quản trị viên & Chủ sở hữu Workspace */}
          {(isWorkspaceOwner || isWorkspaceAdmin) && (
            <button
              onClick={() => {
                setSelectedLeaderUser(null);
                setLeaderSearchQuery('');
                setIsAssignLeaderModalOpen(true);
              }}
              style={{
                backgroundColor: '#EFF6FF',
                color: '#2563EB',
                border: '1px solid #BFDBFE',
                padding: '5px 12px',
                borderRadius: 6,
                fontSize: 12,
                fontWeight: 600,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = '#DBEAFE';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = '#EFF6FF';
              }}
              title="Chỉ định Trưởng nhóm mới (Dành cho Quản trị viên Workspace)"
            >
              <Crown size={14} color="#D97706" />
              <span>Đổi Trưởng nhóm</span>
            </button>
          )}

          {canManageTeam && (
            <button
              onClick={handleDeleteTeam}
              disabled={submitting}
              style={{
                backgroundColor: '#FEF2F2',
                color: '#DC2626',
                border: '1px solid #FEE2E2',
                padding: '5px 10px',
                borderRadius: 6,
                fontSize: 12,
                fontWeight: 600,
                cursor: submitting ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = '#FEE2E2';
                e.currentTarget.style.borderColor = '#FCA5A5';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = '#FEF2F2';
                e.currentTarget.style.borderColor = '#FEE2E2';
              }}
              title="Xóa nhóm (Dành cho Trưởng nhóm hoặc Quản trị viên)"
            >
              <Trash2 size={13} />
              <span>Xóa nhóm</span>
            </button>
          )}

          {!isCurrentUserOwner && isCurrentUserMember && (
            <button
              onClick={handleLeaveTeam}
              disabled={submitting}
              style={{
                backgroundColor: '#FFF1F2',
                color: '#E11D48',
                border: '1px solid #FFE4E6',
                padding: '5px 10px',
                borderRadius: 6,
                fontSize: 12,
                fontWeight: 600,
                cursor: submitting ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = '#FFE4E6';
                e.currentTarget.style.borderColor = '#FDA4AF';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = '#FFF1F2';
                e.currentTarget.style.borderColor = '#FFE4E6';
              }}
              title="Rời khỏi nhóm này"
            >
              <LogOut size={13} />
              <span>Rời nhóm</span>
            </button>
          )}

          <button
            onClick={() => {
              navigator.clipboard.writeText(window.location.href);
              toast.success('Đã sao chép liên kết');
            }}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#64748B',
              padding: 6,
              borderRadius: 4,
              display: 'flex',
              alignItems: 'center',
            }}
            title="Sao chép liên kết"
          >
            <Link2 size={16} />
          </button>
        </div>
      </div>

      {/* ─── Subheader / Navigation Tabs Bar ─── */}
      <div
        style={{
          padding: '12px 24px 8px 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        {/* Navigation Pills */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <button
            onClick={() => setActiveTab('overview')}
            style={{
              padding: '6px 14px',
              borderRadius: 20,
              fontSize: 13,
              fontWeight: 500,
              border: 'none',
              cursor: 'pointer',
              backgroundColor: activeTab === 'overview' ? '#F1F5F9' : 'transparent',
              color: activeTab === 'overview' ? '#0F172A' : '#64748B',
              transition: 'all 0.15s ease',
            }}
          >
            Tổng quan
          </button>
          <button
            onClick={() => setActiveTab('documents')}
            style={{
              padding: '6px 14px',
              borderRadius: 20,
              fontSize: 13,
              fontWeight: 500,
              border: 'none',
              cursor: 'pointer',
              backgroundColor: activeTab === 'documents' ? '#F1F5F9' : 'transparent',
              color: activeTab === 'documents' ? '#0F172A' : '#64748B',
              transition: 'all 0.15s ease',
            }}
          >
            Tài liệu
          </button>
          <button
            onClick={() => setActiveTab('members')}
            style={{
              padding: '6px 14px',
              borderRadius: 20,
              fontSize: 13,
              fontWeight: 600,
              border: 'none',
              cursor: 'pointer',
              backgroundColor: activeTab === 'members' ? '#F1F5F9' : 'transparent',
              color: activeTab === 'members' ? '#0F172A' : '#64748B',
              transition: 'all 0.15s ease',
            }}
          >
            Thành viên
          </button>
        </div>

        {/* Action Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {canManageTeam && (
            <button
              onClick={handleOpenAddModal}
              style={{
                backgroundColor: '#FFFFFF',
                color: '#0F172A',
                border: '1px solid #E2E8F0',
                padding: '6px 12px',
                borderRadius: 6,
                fontSize: 13,
                fontWeight: 500,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
              }}
            >
              <span>+ Thêm thành viên</span>
            </button>
          )}
          <button
            style={{
              backgroundColor: '#FFFFFF',
              border: '1px solid #E2E8F0',
              padding: '6px 8px',
              borderRadius: 6,
              color: '#64748B',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
            title="Lọc"
          >
            <SlidersHorizontal size={14} />
          </button>
        </div>
      </div>

      {/* ─── Main Content Area ─── */}
      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
        {activeTab === 'members' && (
          <div style={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
            {/* Cảnh báo khi nhóm khuyết Trưởng nhóm (Leader cũ đã rời Workspace) */}
            {!loading && !members.some((m) => m.role === 'LEADER') && (
              <div
                style={{
                  margin: '12px 24px 0',
                  padding: '12px 16px',
                  borderRadius: 8,
                  backgroundColor: '#FFFBEB',
                  border: '1px solid #FDE68A',
                  color: '#92400E',
                  fontSize: 13,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 12,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <AlertCircle size={18} color="#D97706" style={{ flexShrink: 0 }} />
                  <span>
                    <strong>Nhóm hiện khuyết Trưởng nhóm:</strong> Trưởng nhóm trước đây không còn là thành viên của Không gian làm việc (Workspace) này.
                  </span>
                </div>
                {canManageTeam && (
                  <button
                    onClick={() => setIsAssignLeaderModalOpen(true)}
                    style={{
                      backgroundColor: '#D97706',
                      color: '#FFFFFF',
                      border: 'none',
                      padding: '6px 12px',
                      borderRadius: 6,
                      fontSize: 12,
                      fontWeight: 600,
                      cursor: 'pointer',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    Bổ nhiệm Trưởng nhóm mới
                  </button>
                )}
              </div>
            )}
            <div style={{ padding: '0 24px' }}>
              <PendingInvitations teamId={teamId} canManage={canManageTeam} refreshKey={inviteTick} />
            </div>
            {/* Table Header */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'minmax(280px, 2fr) minmax(240px, 2fr) minmax(180px, 1fr) 40px',
                padding: '10px 24px',
                fontSize: 12,
                fontWeight: 600,
                color: '#64748B',
                borderBottom: '1px solid #F1F5F9',
                backgroundColor: '#FFFFFF',
                alignItems: 'center',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, cursor: 'pointer' }}>
                <span>Họ và tên</span>
                <span style={{ fontSize: 11 }}>↓</span>
              </div>
              <div>Email</div>
              <div>Vai trò</div>
              <div></div>
            </div>

            {/* Members Rows */}
            {loading ? (
              <div style={{ padding: '32px 24px', color: '#64748B', fontSize: 13, textAlign: 'center' }}>
                Đang tải danh sách thành viên...
              </div>
            ) : filteredMembers.length === 0 ? (
              <div style={{ padding: '48px 24px', textAlign: 'center', color: '#94A3B8', fontSize: 13 }}>
                Chưa có thành viên nào trong nhóm. Bấm <b>+ Thêm thành viên</b> để mời thành viên.
              </div>
            ) : (
              <div>
                {filteredMembers.map((m, idx) => {
                  const displayName = m.fullName || m.username || 'Thành viên';
                  const initials = getInitials(displayName);
                  const avatarBg = getAvatarBg(m.email || displayName);

                  const isSelf = Boolean(
                    currentUser && (m.userId === currentUser.id || (currentUser.keycloakId && m.userId === currentUser.keycloakId))
                  );
                  const isOwner = Boolean(
                    team?.ownerId && (m.userId === team.ownerId)
                  );

                  return (
                    <div
                      key={m.userId || `member-${idx}`}
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(280px, 2fr) minmax(240px, 2fr) minmax(180px, 1fr) 40px',
                        padding: '10px 24px',
                        borderBottom: '1px solid #F8FAFC',
                        alignItems: 'center',
                        fontSize: 13,
                        transition: 'background-color 0.1s ease',
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                      onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                    >
                      {/* Name & Avatar */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <div
                          style={{
                            width: 32,
                            height: 32,
                            borderRadius: '50%',
                            backgroundColor: avatarBg,
                            color: '#FFFFFF',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: 12,
                            fontWeight: 700,
                            flexShrink: 0,
                          }}
                        >
                          {initials}
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                          <span style={{ fontWeight: 600, color: '#0F172A', lineHeight: 1.3 }}>
                            {displayName}
                          </span>
                          {m.username && (
                            <span style={{ fontSize: 11, color: '#94A3B8', lineHeight: 1.2 }}>
                              {m.username}
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Email */}
                      <div style={{ color: '#64748B', fontSize: 13 }}>
                        {m.email || '—'}
                      </div>

                      {/* Role */}
                      <div>
                        {canManageTeam && !isSelf ? (
                          members.length <= 1 ? (
                            <select
                              disabled
                              title="Không thể thay đổi vai trò khi nhóm chỉ có 1 thành viên duy nhất"
                              value={m.role || 'MEMBER'}
                              style={{
                                fontSize: 12,
                                fontWeight: 500,
                                color: '#94A3B8',
                                backgroundColor: '#F8FAFC',
                                border: '1px solid #E2E8F0',
                                padding: '4px 8px',
                                borderRadius: 6,
                                cursor: 'not-allowed',
                                outline: 'none',
                              }}
                            >
                              <option value="LEADER">Trưởng nhóm</option>
                              <option value="MEMBER">Thành viên</option>
                              <option value="VIEWER">Người xem</option>
                            </select>
                          ) : (
                            <select
                              value={m.role || 'MEMBER'}
                              onChange={(e) => handleUpdateRole(m.userId, e.target.value)}
                              style={{
                                fontSize: 12,
                                fontWeight: 600,
                                color: m.role === 'LEADER' ? '#1D4ED8' : '#334155',
                                backgroundColor: m.role === 'LEADER' ? '#EFF6FF' : '#F8FAFC',
                                border: '1px solid #CBD5E1',
                                padding: '4px 8px',
                                borderRadius: 6,
                                cursor: 'pointer',
                                outline: 'none',
                              }}
                            >
                              <option value="LEADER">Trưởng nhóm</option>
                              <option value="MEMBER">Thành viên</option>
                              <option value="VIEWER">Người xem</option>
                            </select>
                          )
                        ) : (
                          <span
                            title={members.length <= 1 ? 'Không thể thay đổi vai trò khi nhóm chỉ có 1 thành viên duy nhất' : undefined}
                            style={{
                              display: 'inline-block',
                              fontSize: 12,
                              fontWeight: 500,
                              color: m.role === 'LEADER' ? '#1D4ED8' : '#2563EB',
                              backgroundColor: m.role === 'LEADER' ? '#EFF6FF' : '#F1F5F9',
                              padding: '3px 10px',
                              borderRadius: 4,
                            }}
                          >
                            {getRoleLabel(m.role)}
                          </span>
                        )}
                      </div>

                      {/* Action */}
                      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                        {canManageTeam && !isSelf && !isOwner && (
                          <button
                            onClick={() => handleRemoveMember(m.userId, displayName)}
                            title="Xóa khỏi nhóm"
                            style={{
                              background: 'transparent',
                              border: 'none',
                              color: '#CBD5E1',
                              cursor: 'pointer',
                              padding: 4,
                              borderRadius: 4,
                              display: 'flex',
                              alignItems: 'center',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.color = '#EF4444')}
                            onMouseLeave={(e) => (e.currentTarget.style.color = '#CBD5E1')}
                          >
                            <Trash2 size={14} />
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {activeTab === 'overview' && (
          <div style={{ padding: 24, maxWidth: 800, display: 'flex', flexDirection: 'column', gap: 24 }}>
            <div style={{ backgroundColor: '#F8FAFC', borderRadius: 10, padding: 20, border: '1px solid #E2E8F0' }}>
              <h4 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', marginBottom: 12 }}>
                Thông tin Nhóm
              </h4>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 16 }}>
                <div>
                  <div style={{ fontSize: 12, color: '#64748B', marginBottom: 4 }}>Tên nhóm</div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>{team?.name || '—'}</div>
                </div>
                <div>
                  <div style={{ fontSize: 12, color: '#64748B', marginBottom: 4 }}>Số lượng thành viên</div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>{members.length} thành viên</div>
                </div>
                <div>
                  <div style={{ fontSize: 12, color: '#64748B', marginBottom: 4 }}>Vai trò của bạn</div>
                  <div style={{ fontSize: 14, fontWeight: 600, color: isCurrentUserOwner ? '#DC2626' : isCurrentUserLeader ? '#2563EB' : '#0F172A' }}>
                    {isCurrentUserOwner ? 'Trưởng nhóm (người tạo)' : isCurrentUserLeader ? 'Trưởng nhóm' : isCurrentUserMember ? 'Thành viên' : 'Người xem'}
                  </div>
                </div>
              </div>

              {team?.description && (
                <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid #E2E8F0' }}>
                  <div style={{ fontSize: 12, color: '#64748B', marginBottom: 4 }}>Mô tả</div>
                  <div style={{ fontSize: 13, color: '#334155', lineHeight: 1.5 }}>{team.description}</div>
                </div>
              )}
            </div>

            {/* Danger Zone */}
            <div style={{ backgroundColor: '#FEF2F2', borderRadius: 10, padding: 20, border: '1px solid #FEE2E2' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                <ShieldAlert size={18} color="#DC2626" />
                <h4 style={{ fontSize: 15, fontWeight: 700, color: '#991B1B', margin: 0 }}>
                  Khu vực Quản trị & Nguy hiểm
                </h4>
              </div>

              {isCurrentUserLeader ? (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: '#7F1D1D' }}>Xóa nhóm làm việc này</div>
                    <div style={{ fontSize: 12, color: '#991B1B' }}>
                      Toàn bộ thành viên sẽ bị xóa khỏi nhóm và các dự án sẽ được tách rời. Hành động không thể hoàn tác.
                    </div>
                  </div>
                  <button
                    onClick={handleDeleteTeam}
                    disabled={submitting}
                    style={{
                      backgroundColor: '#DC2626',
                      color: '#FFFFFF',
                      border: 'none',
                      padding: '8px 16px',
                      borderRadius: 6,
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: submitting ? 'not-allowed' : 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      flexShrink: 0,
                    }}
                  >
                    <Trash2 size={14} />
                    <span>{submitting ? 'Đang xóa...' : 'Xóa nhóm vĩnh viễn'}</span>
                  </button>
                </div>
              ) : isCurrentUserMember ? (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: '#7F1D1D' }}>Rời khỏi nhóm làm việc</div>
                    <div style={{ fontSize: 12, color: '#991B1B' }}>
                      Bạn sẽ không còn là thành viên và sẽ mất quyền truy cập vào các dự án nội bộ của nhóm.
                    </div>
                  </div>
                  <button
                    onClick={handleLeaveTeam}
                    disabled={submitting}
                    style={{
                      backgroundColor: '#E11D48',
                      color: '#FFFFFF',
                      border: 'none',
                      padding: '8px 16px',
                      borderRadius: 6,
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: submitting ? 'not-allowed' : 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      flexShrink: 0,
                    }}
                  >
                    <LogOut size={14} />
                    <span>{submitting ? 'Đang rời...' : 'Rời khỏi nhóm'}</span>
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        )}

        {activeTab === 'documents' && (
          <div style={{ padding: 24, color: '#64748B', fontSize: 13 }}>
            <h4 style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', marginBottom: 8 }}>
              Tài liệu & Ghi chú
            </h4>
            <p>Chưa có tài liệu đính kèm nào trong nhóm.</p>
          </div>
        )}
      </div>

      {/* ─── Add Member Modal (with Interactive Autocomplete Search) ─── */}
      {isAddModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            backdropFilter: 'blur(4px)',
            zIndex: 70,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 16,
          }}
          onClick={() => setIsAddModalOpen(false)}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              width: '100%',
              maxWidth: 480,
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div
              style={{
                padding: '16px 20px',
                borderBottom: '1px solid #E2E8F0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                backgroundColor: '#FFFFFF',
              }}
            >
              <span style={{ fontSize: 16, fontWeight: 700, color: '#0F172A' }}>
                Thêm thành viên vào {team?.name || 'nhóm'}
              </span>
              <button
                onClick={() => setIsAddModalOpen(false)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  cursor: 'pointer',
                  color: '#64748B',
                  padding: 4,
                }}
              >
                <X size={16} />
              </button>
            </div>

            {/* Modal Form */}
            <form onSubmit={handleAddMember} style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
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

              {/* User Search / Selected User Preview */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                  <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155' }}>
                    Tìm kiếm thành viên trong Workspace <span style={{ color: '#EF4444' }}>*</span>
                  </label>
                  <span style={{ fontSize: 11, color: '#2563EB', fontWeight: 500, backgroundColor: '#EFF6FF', padding: '2px 8px', borderRadius: 4 }}>
                    Chỉ thành viên cùng Workspace
                  </span>
                </div>

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
                        <div style={{ fontSize: 11, color: '#64748B' }}>
                          {selectedUser.email || selectedUser.username}
                        </div>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={handleClearSelectedUser}
                      style={{
                        background: 'transparent',
                        border: 'none',
                        color: '#64748B',
                        cursor: 'pointer',
                        padding: 4,
                        display: 'flex',
                        alignItems: 'center',
                      }}
                      title="Chọn người khác"
                    >
                      <X size={16} />
                    </button>
                  </div>
                ) : (
                  <div style={{ position: 'relative' }}>
                    <div style={{ position: 'relative' }}>
                      <Search
                        size={14}
                        color="#94A3B8"
                        style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
                      />
                      <input
                        type="text"
                        value={memberIdentifier}
                        onChange={(e) => {
                          setMemberIdentifier(e.target.value);
                          if (addError) setAddError('');
                        }}
                        placeholder="Nhập tên, username hoặc email của thành viên Workspace..."
                        autoFocus
                        style={{
                          width: '100%',
                          padding: '8px 12px 8px 32px',
                          borderRadius: 6,
                          border: '1px solid #CBD5E1',
                          fontSize: 13,
                          color: '#0F172A',
                          outline: 'none',
                        }}
                      />
                    </div>

                    {/* Autocomplete Dropdown List */}
                    {loadingWsMembers ? (
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
                          padding: '12px 16px',
                          fontSize: 12,
                          color: '#64748B',
                          zIndex: 80,
                        }}
                      >
                        Đang tải danh sách thành viên Workspace...
                      </div>
                    ) : userSuggestions.length > 0 ? (
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
                          maxHeight: 200,
                          overflowY: 'auto',
                        }}
                      >
                        {userSuggestions.map((u) => {
                          const fullName = (u.fullName || u.username || 'Thành viên').trim();
                          const isAlreadyInTeam = members.some(
                            (m) =>
                              m.userId === u.id ||
                              (u.keycloakId && m.userId === u.keycloakId) ||
                              (u.email && m.email && m.email.toLowerCase() === u.email.toLowerCase())
                          );

                          return (
                            <div
                              key={u.id}
                              onClick={() => !isAlreadyInTeam && handleSelectUser(u)}
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                padding: '8px 12px',
                                cursor: isAlreadyInTeam ? 'default' : 'pointer',
                                opacity: isAlreadyInTeam ? 0.6 : 1,
                                borderBottom: '1px solid #F8FAFC',
                                transition: 'background-color 0.1s ease',
                              }}
                              onMouseEnter={(e) => {
                                if (!isAlreadyInTeam) e.currentTarget.style.backgroundColor = '#F1F5F9';
                              }}
                              onMouseLeave={(e) => {
                                if (!isAlreadyInTeam) e.currentTarget.style.backgroundColor = '#FFFFFF';
                              }}
                            >
                              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                <div
                                  style={{
                                    width: 28,
                                    height: 28,
                                    borderRadius: '50%',
                                    backgroundColor: getAvatarBg(u.email || fullName),
                                    color: '#FFFFFF',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    fontSize: 11,
                                    fontWeight: 700,
                                  }}
                                >
                                  {getInitials(fullName)}
                                </div>
                                <div>
                                  <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>
                                    {fullName}
                                  </div>
                                  <div style={{ fontSize: 11, color: '#64748B' }}>
                                    {u.email || `@${u.username}`}
                                  </div>
                                </div>
                              </div>

                              {isAlreadyInTeam ? (
                                <span style={{ fontSize: 11, color: '#059669', fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4 }}>
                                  <Check size={12} />
                                  <span>Đã trong nhóm</span>
                                </span>
                              ) : (
                                <span style={{ fontSize: 11, color: '#2563EB', fontWeight: 500 }}>
                                  Chọn ➔
                                </span>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    ) : null}
                  </div>
                )}
              </div>

              {/* Role Selection */}
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Vai trò trong nhóm làm việc
                </label>
                <select
                  value={memberRole}
                  onChange={(e) => setMemberRole(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    color: '#0F172A',
                    outline: 'none',
                    backgroundColor: '#FFFFFF',
                  }}
                >
                  <option value="MEMBER">Thành viên (Có quyền tham gia làm việc trong các dự án)</option>
                  <option value="LEADER">Trưởng nhóm (Toàn quyền quản lý và điều phối nhóm)</option>
                  <option value="VIEWER">Người xem (Chỉ theo dõi tiến độ, không chỉnh sửa)</option>
                </select>
              </div>

              {/* Modal Actions */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'flex-end',
                  gap: 8,
                  marginTop: 6,
                  paddingTop: 14,
                  borderTop: '1px solid #F1F5F9',
                }}
              >
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  style={{
                    backgroundColor: '#FFFFFF',
                    color: '#475569',
                    border: '1px solid #E2E8F0',
                    padding: '7px 14px',
                    borderRadius: 6,
                    fontSize: 13,
                    fontWeight: 500,
                    cursor: 'pointer',
                  }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submitting || (!selectedUser && !memberIdentifier.trim())}
                  style={{
                    backgroundColor: submitting || (!selectedUser && !memberIdentifier.trim()) ? '#94A3B8' : '#2563EB',
                    color: '#FFFFFF',
                    border: 'none',
                    padding: '7px 18px',
                    borderRadius: 6,
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: submitting || (!selectedUser && !memberIdentifier.trim()) ? 'not-allowed' : 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                  }}
                >
                  <UserCheck size={15} />
                  <span>{submitting ? 'Đang thêm...' : 'Thêm vào nhóm'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* ─── Assign Leader Modal (Workspace Owner / Admin) ─── */}
      {isAssignLeaderModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.5)',
            backdropFilter: 'blur(4px)',
            zIndex: 80,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 16,
          }}
          onClick={() => setIsAssignLeaderModalOpen(false)}
        >
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              width: '100%',
              maxWidth: 480,
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Modal Header */}
            <div
              style={{
                padding: '16px 20px',
                borderBottom: '1px solid #E2E8F0',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                backgroundColor: '#FFFFFF',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div
                  style={{
                    width: 30,
                    height: 30,
                    borderRadius: 8,
                    backgroundColor: '#FEF3C7',
                    color: '#D97706',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Crown size={16} />
                </div>
                <div>
                  <h3 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                    Chỉ định Trưởng nhóm mới
                  </h3>
                  <p style={{ fontSize: 11, color: '#64748B', margin: 0 }}>
                    Nhóm: <strong>{team?.name}</strong>
                  </p>
                </div>
              </div>
              <button
                onClick={() => setIsAssignLeaderModalOpen(false)}
                style={{
                  border: 'none',
                  background: 'transparent',
                  cursor: 'pointer',
                  color: '#64748B',
                  padding: 4,
                }}
              >
                <X size={16} />
              </button>
            </div>

            {/* Modal Body */}
            <form onSubmit={handleAssignLeader} style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div
                style={{
                  padding: '10px 14px',
                  borderRadius: 8,
                  backgroundColor: '#FEF3C7',
                  border: '1px solid #FDE68A',
                  color: '#92400E',
                  fontSize: 12,
                  lineHeight: 1.5,
                }}
              >
                💡 <strong>Lưu ý:</strong> Khi bổ nhiệm Trưởng nhóm mới, Trưởng nhóm hiện tại sẽ tự động chuyển thành thành viên bình thường (Member). Quyền quản trị các dự án thuộc nhóm sẽ được chuyển giao.
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>
                  Chọn Trưởng nhóm mới:
                </label>

                {selectedLeaderUser ? (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '8px 12px',
                      backgroundColor: '#EFF6FF',
                      border: '1px solid #BFDBFE',
                      borderRadius: 8,
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <div
                        style={{
                          width: 28,
                          height: 28,
                          borderRadius: '50%',
                          backgroundColor: '#2563EB',
                          color: '#FFFFFF',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 11,
                          fontWeight: 700,
                        }}
                      >
                        {getInitials(selectedLeaderUser.fullName || selectedLeaderUser.username || selectedLeaderUser.email)}
                      </div>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: '#1E3A8A' }}>
                          {selectedLeaderUser.fullName || selectedLeaderUser.username}
                        </div>
                        <div style={{ fontSize: 11, color: '#3B82F6' }}>
                          {selectedLeaderUser.email}
                        </div>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => setSelectedLeaderUser(null)}
                      style={{
                        background: 'transparent',
                        border: 'none',
                        color: '#64748B',
                        cursor: 'pointer',
                        padding: 4,
                      }}
                    >
                      <X size={14} />
                    </button>
                  </div>
                ) : (
                  <div>
                    <div style={{ position: 'relative' }}>
                      <input
                        type="text"
                        placeholder="Tìm kiếm thành viên trong Workspace hoặc chọn bên dưới..."
                        value={leaderSearchQuery}
                        onChange={(e) => setLeaderSearchQuery(e.target.value)}
                        style={{
                          width: '100%',
                          padding: '8px 12px 8px 34px',
                          borderRadius: 8,
                          border: '1px solid #CBD5E1',
                          fontSize: 13,
                          outline: 'none',
                        }}
                      />
                      <Search
                        size={15}
                        style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }}
                      />
                    </div>

                    {/* Suggestions list */}
                    <div
                      style={{
                        marginTop: 8,
                        maxHeight: 180,
                        overflowY: 'auto',
                        border: '1px solid #E2E8F0',
                        borderRadius: 8,
                        backgroundColor: '#FFFFFF',
                      }}
                    >
                      {isSearchingLeader ? (
                        <div style={{ padding: '12px', textAlign: 'center', fontSize: 12, color: '#64748B' }}>
                          Đang tìm kiếm...
                        </div>
                      ) : leaderSuggestions.length === 0 ? (
                        <div style={{ padding: '12px', textAlign: 'center', fontSize: 12, color: '#94A3B8' }}>
                          {leaderSearchQuery ? 'Không tìm thấy người dùng phù hợp' : 'Không có gợi ý'}
                        </div>
                      ) : (
                        leaderSuggestions.map((u) => {
                          const name = u.fullName || u.username || u.email;
                          return (
                            <div
                              key={u.id || u.keycloakId}
                              onClick={() => setSelectedLeaderUser(u)}
                              style={{
                                padding: '8px 12px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                cursor: 'pointer',
                                borderBottom: '1px solid #F1F5F9',
                                transition: 'background-color 0.15s ease',
                              }}
                              onMouseEnter={(e) => {
                                e.currentTarget.style.backgroundColor = '#F8FAFC';
                              }}
                              onMouseLeave={(e) => {
                                e.currentTarget.style.backgroundColor = '#FFFFFF';
                              }}
                            >
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <div
                                  style={{
                                    width: 26,
                                    height: 26,
                                    borderRadius: '50%',
                                    backgroundColor: '#E0E7FF',
                                    color: '#4338CA',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    fontSize: 11,
                                    fontWeight: 700,
                                  }}
                                >
                                  {getInitials(name)}
                                </div>
                                <div>
                                  <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>{name}</div>
                                  <div style={{ fontSize: 11, color: '#64748B' }}>{u.email || `@${u.username}`}</div>
                                </div>
                              </div>
                              <span style={{ fontSize: 11, color: '#2563EB', fontWeight: 600 }}>
                                Chọn ➔
                              </span>
                            </div>
                          );
                        })
                      )}
                    </div>
                  </div>
                )}
              </div>

              {/* Action Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 10 }}>
                <button
                  type="button"
                  onClick={() => setIsAssignLeaderModalOpen(false)}
                  style={{
                    backgroundColor: '#FFFFFF',
                    color: '#475569',
                    border: '1px solid #E2E8F0',
                    padding: '8px 16px',
                    borderRadius: 6,
                    fontSize: 13,
                    fontWeight: 500,
                    cursor: 'pointer',
                  }}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={isAssigningLeader || !selectedLeaderUser}
                  style={{
                    backgroundColor: isAssigningLeader || !selectedLeaderUser ? '#94A3B8' : '#2563EB',
                    color: '#FFFFFF',
                    border: 'none',
                    padding: '8px 20px',
                    borderRadius: 6,
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: isAssigningLeader || !selectedLeaderUser ? 'not-allowed' : 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                  }}
                >
                  <Crown size={15} />
                  <span>{isAssigningLeader ? 'Đang bổ nhiệm...' : 'Xác nhận Bổ nhiệm'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
