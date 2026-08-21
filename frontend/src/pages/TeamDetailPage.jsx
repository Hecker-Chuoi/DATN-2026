import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import {
  Users,
  UserPlus,
  Trash2,
  Shield,
  ShieldAlert,
  Crown,
  Eye,
  User,
  Calendar,
  Mail,
  CheckCircle2,
  AlertCircle,
  AtSign,
  UserCheck,
  ChevronDown,
  Check,
  Lock,
  Info
} from 'lucide-react';
import { teamApi, userApi } from '../api.js';
import toast from 'react-hot-toast';

export const TEAM_ROLES = [
  {
    code: 'TEAM_LEAD',
    label: 'Trưởng nhóm (Leader)',
    shortLabel: 'LEAD',
    description: 'Quản lý toàn bộ hoạt động của Team: duyệt/thêm/xóa thành viên trong Team, tạo và phân bổ các Project cho Team, quản lý tài nguyên chung của Team.',
    icon: (s = 14) => <Crown size={s} color="#2563EB" />,
    color: '#2563EB',
    bg: '#EFF6FF',
    border: '#BFDBFE',
    badgeText: 'Trưởng nhóm',
  },
  {
    code: 'TEAM_MEMBER',
    label: 'Thành viên Team',
    shortLabel: 'MEMBER',
    description: 'Nhân sự thuộc Team, có thể tham gia và thao tác trong các Project được gán cho Team.',
    icon: (s = 14) => <User size={s} color="#0F766E" />,
    color: '#0F766E',
    bg: '#F0FDFA',
    border: '#99F6E4',
    badgeText: 'Thành viên',
  },
  {
    code: 'TEAM_VIEWER',
    label: 'Người quan sát Team',
    shortLabel: 'VIEWER',
    description: 'Được theo dõi danh sách thành viên và các dự án của Team nhưng không có quyền chỉnh sửa cấu hình hay thành viên của Team.',
    icon: (s = 14) => <Eye size={s} color="#B45309" />,
    color: '#B45309',
    bg: '#FEF3C7',
    border: '#FDE68A',
    badgeText: 'Người xem',
  },
];

export default function TeamDetailPage() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const { refreshTeams } = useOutletContext();

  const [team, setTeam] = useState(null);
  const [members, setMembers] = useState([]);
  const [currentUser, setCurrentUser] = useState(null);
  const [currentUserRole, setCurrentUserRole] = useState(null);
  const [loading, setLoading] = useState(true);

  // Add member form state
  const [identifier, setIdentifier] = useState('');
  const [newRole, setNewRole] = useState('TEAM_MEMBER');
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Active role change popover for a member
  const [activeRolePopoverUserId, setActiveRolePopoverUserId] = useState(null);
  const [updatingRoleUserId, setUpdatingRoleUserId] = useState(null);

  const fetchTeamData = async () => {
    try {
      setLoading(true);
      const [teamRes, membersRes, meRes, myRoleRes] = await Promise.all([
        teamApi.getById(teamId),
        teamApi.getMembers(teamId),
        userApi.me().catch(() => null),
        teamApi.getMyRole(teamId).catch(() => null),
      ]);

      if (teamRes && teamRes.data) setTeam(teamRes.data);
      if (membersRes && membersRes.data) setMembers(membersRes.data);
      if (meRes && meRes.data) setCurrentUser(meRes.data);
      if (myRoleRes && myRoleRes.data) setCurrentUserRole(myRoleRes.data);
    } catch (err) {
      console.error('Failed to load team:', err);
      setErrorMsg('Không tìm thấy thông tin nhóm.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTeamData();
  }, [teamId]);

  // Kiểm tra xem user hiện tại có phải là Team Lead hay không
  const isCurrentLeader = () => {
    if (!currentUser) return false;
    if (team?.ownerId === currentUser.id) return true;
    if (currentUserRole === 'TEAM_LEAD' || currentUserRole === 'LEADER') return true;

    const myMemberRecord = members.find((m) => m.userId === currentUser.id);
    if (myMemberRecord) {
      const r = (myMemberRecord.roleInTeam || '').toUpperCase();
      return r === 'TEAM_LEAD' || r === 'LEADER';
    }
    return false;
  };

  const handleAddMember = async (e) => {
    e.preventDefault();
    const cleanIdentifier = identifier.trim();
    if (!cleanIdentifier) return;

    try {
      setSubmitting(true);
      setErrorMsg('');
      setSuccessMsg('');

      await teamApi.addMember(teamId, {
        emailOrUsername: cleanIdentifier,
        roleInTeam: newRole,
      });

      const roleObj = TEAM_ROLES.find((r) => r.code === newRole) || TEAM_ROLES[1];
      toast.success(`Đã thêm "${cleanIdentifier}" vào nhóm với vai trò ${roleObj.label}!`);
      setSuccessMsg(`Đã thêm thành viên "${cleanIdentifier}" vào nhóm thành công!`);
      setIdentifier('');
      fetchTeamData();
      if (refreshTeams) refreshTeams();
    } catch (err) {
      const msg = err.message || 'Không thể thêm thành viên. Vui lòng kiểm tra lại Email hoặc Username.';
      setErrorMsg(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdateRole = async (member, targetRoleCode) => {
    const currentMemberRole = (member.roleInTeam || 'TEAM_MEMBER').toUpperCase();
    const currentClean = currentMemberRole === 'LEADER' ? 'TEAM_LEAD' : currentMemberRole === 'VIEWER' ? 'TEAM_VIEWER' : currentMemberRole;

    if (currentClean === targetRoleCode) {
      setActiveRolePopoverUserId(null);
      return;
    }

    const memberName = member.fullName || member.username || member.email || 'thành viên';
    const targetRoleObj = TEAM_ROLES.find((r) => r.code === targetRoleCode) || TEAM_ROLES[1];

    try {
      setUpdatingRoleUserId(member.userId);
      await teamApi.updateMemberRole(teamId, member.userId, targetRoleCode);
      toast.success(`Đã đổi vai trò của "${memberName}" thành ${targetRoleObj.label}!`);
      setActiveRolePopoverUserId(null);
      fetchTeamData();
      if (refreshTeams) refreshTeams();
    } catch (err) {
      toast.error(err.message || 'Chuyển đổi vai trò thất bại');
    } finally {
      setUpdatingRoleUserId(null);
    }
  };

  const handleRemoveMember = async (member) => {
    const memberName = member.fullName || member.username || member.email || 'thành viên này';
    if (!window.confirm(`Bạn có chắc chắn muốn xóa "${memberName}" khỏi nhóm?`)) return;
    try {
      await teamApi.removeMember(teamId, member.userId);
      toast.success(`Đã xóa "${memberName}" khỏi nhóm!`);
      fetchTeamData();
      if (refreshTeams) refreshTeams();
    } catch (err) {
      toast.error(err.message || 'Xóa thành viên thất bại.');
    }
  };

  const getRoleConfig = (roleStr) => {
    if (!roleStr) return TEAM_ROLES[1];
    const upper = roleStr.toUpperCase();
    if (upper === 'TEAM_LEAD' || upper === 'LEADER' || upper === 'LEAD') return TEAM_ROLES[0];
    if (upper === 'TEAM_VIEWER' || upper === 'VIEWER') return TEAM_ROLES[2];
    return TEAM_ROLES[1];
  };

  if (loading) {
    return (
      <div style={{ padding: 60, textAlign: 'center', color: '#64748B', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
        <div style={{ width: 24, height: 24, border: '3px solid #E2E8F0', borderTopColor: '#2563EB', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
        <span>Đang tải thông tin nhóm...</span>
      </div>
    );
  }

  if (!team) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <h3 style={{ fontSize: 18, color: '#0F172A' }}>Không tìm thấy nhóm</h3>
        <button
          onClick={() => navigate('/projects')}
          style={{
            marginTop: 12,
            padding: '8px 16px',
            backgroundColor: '#2563EB',
            color: '#FFFFFF',
            border: 'none',
            borderRadius: 6,
            cursor: 'pointer',
            fontWeight: 600,
            fontSize: 13,
          }}
        >
          Quay lại danh sách dự án
        </button>
      </div>
    );
  }

  const userIsLead = isCurrentLeader();

  return (
    <div style={{ backgroundColor: '#F8FAFC', minHeight: '100vh', display: 'flex', flexDirection: 'column', flex: 1 }}>
      {/* ════════ 1. TOP HEADER (Unified Header Bar) ════════ */}
      <div
        style={{
          height: 48,
          minHeight: 48,
          padding: '0 24px',
          borderBottom: '1px solid #E2E8F0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
          <span
            onClick={() => navigate('/projects')}
            style={{ color: '#64748B', fontWeight: 500, cursor: 'pointer' }}
          >
            Teams
          </span>
          <span style={{ color: '#94A3B8' }}>›</span>
          <span>{team.name}</span>
        </div>

        {userIsLead && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              fontSize: 12,
              fontWeight: 600,
              color: '#2563EB',
              backgroundColor: '#EFF6FF',
              padding: '4px 10px',
              borderRadius: 6,
              border: '1px solid #DBEAFE',
            }}
          >
            <Crown size={14} />
            <span>Bạn là Trưởng nhóm (Leader)</span>
          </div>
        )}
      </div>

      {/* ════════ Content Container ════════ */}
      <div style={{ padding: '28px 36px', maxWidth: 1000, width: '100%', margin: '0 auto' }}>
        {/* ─── Team Overview Card ─── */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 12,
            border: '1px solid #E2E8F0',
            padding: 24,
            marginBottom: 24,
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <h1 style={{ fontSize: 22, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                  {team.name}
                </h1>
                {team.key && (
                  <span
                    style={{
                      fontSize: 12,
                      fontWeight: 700,
                      color: '#2563EB',
                      backgroundColor: '#EFF6FF',
                      padding: '2px 8px',
                      borderRadius: 4,
                      border: '1px solid #DBEAFE',
                    }}
                  >
                    {team.key}
                  </span>
                )}
              </div>
              <p style={{ fontSize: 13, color: '#64748B', marginTop: 6, marginBottom: 0 }}>
                {team.description || 'Chưa có mô tả cho nhóm này.'}
              </p>
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                fontSize: 13,
                color: '#2563EB',
                backgroundColor: '#EFF6FF',
                padding: '6px 12px',
                borderRadius: 20,
                fontWeight: 600,
                border: '1px solid #DBEAFE',
              }}
            >
              <Users size={16} />
              <span>{members.length} thành viên</span>
            </div>
          </div>
        </div>

        {/* ─── Add Member Section ─── */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 12,
            border: '1px solid #E2E8F0',
            padding: 24,
            marginBottom: 24,
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: 6,
                backgroundColor: '#EFF6FF',
                color: '#2563EB',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <UserPlus size={16} />
            </div>
            <h3 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: 0 }}>
              Thêm thành viên mới vào nhóm
            </h3>
          </div>
          <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 16px 0' }}>
            Nhập <b>Email</b> hoặc <b>Username</b> của người dùng và chọn vai trò phân quyền tương ứng.
          </p>

          {errorMsg && (
            <div
              style={{
                padding: '10px 14px',
                borderRadius: 8,
                backgroundColor: '#FEF2F2',
                color: '#DC2626',
                fontSize: 13,
                marginBottom: 16,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                border: '1px solid #FEE2E2',
              }}
            >
              <AlertCircle size={16} />
              <span>{errorMsg}</span>
            </div>
          )}

          {successMsg && (
            <div
              style={{
                padding: '10px 14px',
                borderRadius: 8,
                backgroundColor: '#ECFDF5',
                color: '#059669',
                fontSize: 13,
                marginBottom: 16,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                border: '1px solid #D1FAE5',
              }}
            >
              <CheckCircle2 size={16} />
              <span>{successMsg}</span>
            </div>
          )}

          <form onSubmit={handleAddMember} style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
            <div style={{ flex: 1, minWidth: 260, position: 'relative' }}>
              <div
                style={{
                  position: 'absolute',
                  left: 12,
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: '#94A3B8',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                <AtSign size={16} />
              </div>
              <input
                type="text"
                placeholder="Nhập Email hoặc Username..."
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                required
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 36px',
                  borderRadius: 8,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  outline: 'none',
                  boxSizing: 'border-box',
                  transition: 'border-color 0.15s ease',
                }}
                onFocus={(e) => (e.target.style.borderColor = '#2563EB')}
                onBlur={(e) => (e.target.style.borderColor = '#CBD5E1')}
              />
            </div>

            <div style={{ minWidth: 200 }}>
              <select
                value={newRole}
                onChange={(e) => setNewRole(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  borderRadius: 8,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  backgroundColor: '#FFFFFF',
                  color: '#0F172A',
                  outline: 'none',
                  cursor: 'pointer',
                  fontWeight: 500,
                }}
              >
                {TEAM_ROLES.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.label}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="submit"
              disabled={submitting || !identifier.trim()}
              style={{
                backgroundColor: '#2563EB',
                color: '#FFFFFF',
                border: 'none',
                borderRadius: 8,
                padding: '9px 20px',
                fontSize: 13,
                fontWeight: 600,
                cursor: submitting || !identifier.trim() ? 'not-allowed' : 'pointer',
                opacity: submitting || !identifier.trim() ? 0.7 : 1,
                whiteSpace: 'nowrap',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                transition: 'background-color 0.15s ease',
              }}
            >
              <UserCheck size={16} />
              <span>{submitting ? 'Đang thêm...' : 'Thêm thành viên'}</span>
            </button>
          </form>
        </div>

        {/* ─── Members List Table ─── */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 12,
            border: '1px solid #E2E8F0',
            overflow: 'visible',
            boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
          }}
        >
          <div
            style={{
              padding: '16px 20px',
              borderBottom: '1px solid #F1F5F9',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', display: 'flex', alignItems: 'center', gap: 6 }}>
              <Users size={16} color="#2563EB" />
              <span>Danh sách thành viên ({members.length})</span>
            </div>

            <div style={{ fontSize: 12, color: '#64748B', display: 'flex', alignItems: 'center', gap: 4 }}>
              <Info size={13} color="#94A3B8" />
              <span>Chỉ <b>Trưởng nhóm (TEAM_LEAD)</b> có quyền chỉnh sửa quyền của thành viên.</span>
            </div>
          </div>

          {members.length === 0 ? (
            <div style={{ padding: '36px 20px', textAlign: 'center', color: '#64748B', fontSize: 13 }}>
              Chưa có thành viên nào trong nhóm này.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {members.map((member, index) => {
                const displayName = member.fullName || member.username || 'Thành viên ' + (index + 1);
                const roleConfig = getRoleConfig(member.roleInTeam);
                const isLeaderRole = roleConfig.code === 'TEAM_LEAD';
                const isUpdating = updatingRoleUserId === member.userId;
                const isPopoverOpen = activeRolePopoverUserId === member.userId;

                return (
                  <div
                    key={member.userId}
                    style={{
                      padding: '14px 20px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      borderBottom: index < members.length - 1 ? '1px solid #F1F5F9' : 'none',
                      backgroundColor: '#FFFFFF',
                      transition: 'background-color 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                      <div
                        style={{
                          width: 38,
                          height: 38,
                          borderRadius: '50%',
                          backgroundColor: roleConfig.bg,
                          color: roleConfig.color,
                          border: `1px solid ${roleConfig.border}`,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontWeight: 700,
                          fontSize: 14,
                        }}
                      >
                        {member.username ? member.username.charAt(0).toUpperCase() : 'U'}
                      </div>

                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
                            {displayName}
                          </span>
                          {member.username && (
                            <span style={{ fontSize: 12, color: '#64748B' }}>
                              @{member.username}
                            </span>
                          )}
                        </div>
                        <div style={{ fontSize: 12, color: '#64748B', display: 'flex', alignItems: 'center', gap: 12, marginTop: 3 }}>
                          {member.email && (
                            <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                              <Mail size={12} color="#94A3B8" />
                              <span>{member.email}</span>
                            </span>
                          )}
                          {member.joinedAt && (
                            <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#94A3B8' }}>
                              <Calendar size={12} />
                              <span>Tham gia: {new Date(member.joinedAt).toLocaleDateString('vi-VN')}</span>
                            </span>
                          )}
                        </div>
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                      {/* ─── Role Badge / Selector Popover ─── */}
                      <div style={{ position: 'relative' }}>
                        {userIsLead ? (
                          <button
                            type="button"
                            onClick={() => setActiveRolePopoverUserId(isPopoverOpen ? null : member.userId)}
                            disabled={isUpdating}
                            title="Bấm để thay đổi quyền của thành viên này"
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 6,
                              fontSize: 12,
                              fontWeight: 700,
                              padding: '5px 12px',
                              borderRadius: 20,
                              backgroundColor: roleConfig.bg,
                              color: roleConfig.color,
                              border: `1px solid ${roleConfig.border}`,
                              cursor: 'pointer',
                              transition: 'all 0.15s ease',
                              outline: 'none',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.filter = 'brightness(0.96)')}
                            onMouseLeave={(e) => (e.currentTarget.style.filter = 'none')}
                          >
                            {roleConfig.icon(13)}
                            <span>{roleConfig.badgeText}</span>
                            <ChevronDown size={13} style={{ opacity: 0.7 }} />
                          </button>
                        ) : (
                          <span
                            title="Chỉ Trưởng nhóm (TEAM_LEAD) mới có quyền đổi vai trò"
                            style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 6,
                              fontSize: 12,
                              fontWeight: 700,
                              padding: '5px 12px',
                              borderRadius: 20,
                              backgroundColor: roleConfig.bg,
                              color: roleConfig.color,
                              border: `1px solid ${roleConfig.border}`,
                            }}
                          >
                            {roleConfig.icon(13)}
                            <span>{roleConfig.badgeText}</span>
                          </span>
                        )}

                        {/* Dropdown Popover Chuyển Đổi Quyền */}
                        {isPopoverOpen && (
                          <>
                            {/* Backdrop click-outside */}
                            <div
                              onClick={() => setActiveRolePopoverUserId(null)}
                              style={{
                                position: 'fixed',
                                inset: 0,
                                zIndex: 90,
                              }}
                            />

                            <div
                              onClick={(e) => e.stopPropagation()}
                              style={{
                                position: 'absolute',
                                ...(index >= members.length - 2 && members.length > 2
                                  ? { bottom: '100%', marginBottom: 6 }
                                  : { top: '100%', marginTop: 6 }),
                                right: 0,
                                zIndex: 100,
                                backgroundColor: '#FFFFFF',
                                borderRadius: 10,
                                boxShadow: '0 10px 25px -5px rgba(0,0,0,0.18), 0 8px 10px -6px rgba(0,0,0,0.1)',
                                border: '1px solid #E2E8F0',
                                padding: 6,
                                minWidth: 300,
                              }}
                            >
                              <div
                                style={{
                                  padding: '6px 10px',
                                  fontSize: 11,
                                  fontWeight: 700,
                                  color: '#64748B',
                                  borderBottom: '1px solid #F1F5F9',
                                  textTransform: 'uppercase',
                                  letterSpacing: '0.04em',
                                  marginBottom: 4,
                                }}
                              >
                                Phân quyền vai trò trong Team
                              </div>

                              {TEAM_ROLES.map((r) => {
                                const isSelected = roleConfig.code === r.code;
                                return (
                                  <div
                                    key={r.code}
                                    onClick={() => handleUpdateRole(member, r.code)}
                                    style={{
                                      padding: '8px 10px',
                                      borderRadius: 6,
                                      cursor: 'pointer',
                                      backgroundColor: isSelected ? '#F8FAFC' : 'transparent',
                                      transition: 'background-color 0.15s ease',
                                      display: 'flex',
                                      flexDirection: 'column',
                                      gap: 2,
                                    }}
                                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = isSelected ? '#F1F5F9' : '#F8FAFC')}
                                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = isSelected ? '#F8FAFC' : 'transparent')}
                                  >
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                        {r.icon(14)}
                                        <span style={{ fontSize: 13, fontWeight: 600, color: r.color }}>
                                          {r.label}
                                        </span>
                                      </div>
                                      {isSelected && <Check size={14} color="#2563EB" />}
                                    </div>
                                    <div style={{ fontSize: 11, color: '#64748B', lineHeight: 1.4, paddingLeft: 20 }}>
                                      {r.description}
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                          </>
                        )}
                      </div>

                      {/* Nút Xóa Thành Viên */}
                      {userIsLead && (
                        <button
                          onClick={() => handleRemoveMember(member)}
                          title="Xóa khỏi nhóm"
                          style={{
                            background: 'none',
                            border: 'none',
                            color: '#94A3B8',
                            cursor: 'pointer',
                            padding: 6,
                            borderRadius: 6,
                            display: 'flex',
                            alignItems: 'center',
                            transition: 'color 0.15s ease, background-color 0.15s ease',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.color = '#DC2626';
                            e.currentTarget.style.backgroundColor = '#FEF2F2';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.color = '#94A3B8';
                            e.currentTarget.style.backgroundColor = 'transparent';
                          }}
                        >
                          <Trash2 size={16} />
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
