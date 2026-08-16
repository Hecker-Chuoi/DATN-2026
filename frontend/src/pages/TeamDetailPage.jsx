import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import {
  Users,
  UserPlus,
  Trash2,
  Shield,
  Layers,
  ArrowLeft,
  Calendar,
  Mail,
  CheckCircle2
} from 'lucide-react';
import { teamApi } from '../api.js';

export default function TeamDetailPage() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const { refreshTeams } = useOutletContext();

  const [team, setTeam] = useState(null);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [newUserId, setNewUserId] = useState('');
  const [newRole, setNewRole] = useState('member');
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

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
      console.error('Failed to load team:', err);
      setErrorMsg('Không tìm thấy thông tin nhóm.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTeamData();
  }, [teamId]);

  const handleAddMember = async (e) => {
    e.preventDefault();
    if (!newUserId.trim()) return;

    try {
      setSubmitting(true);
      setErrorMsg('');
      setSuccessMsg('');
      await teamApi.addMember(teamId, {
        userId: newUserId.trim(),
        roleInTeam: newRole,
      });
      setSuccessMsg('Đã thêm thành viên vào nhóm thành công!');
      setNewUserId('');
      fetchTeamData();
      refreshTeams();
    } catch (err) {
      setErrorMsg(err.message || 'Thêm thành viên thất bại. Vui lòng kiểm tra lại User ID.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemoveMember = async (userId) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa thành viên này khỏi nhóm?')) return;
    try {
      await teamApi.removeMember(teamId, userId);
      fetchTeamData();
      refreshTeams();
    } catch (err) {
      alert(err.message || 'Xóa thành viên thất bại.');
    }
  };

  if (loading) {
    return <div style={{ padding: 40, textAlign: 'center', color: '#64748B' }}>Đang tải thông tin nhóm...</div>;
  }

  if (!team) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <h3>Không tìm thấy nhóm</h3>
        <button onClick={() => navigate('/projects')} style={{ marginTop: 12, padding: '6px 14px' }}>
          Quay lại danh sách dự án
        </button>
      </div>
    );
  }

  return (
    <div style={{ backgroundColor: '#FFFFFF', minHeight: '100vh', display: 'flex', flexDirection: 'column', flex: 1 }}>
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
      </div>

      {/* ════════ Content Container ════════ */}
      <div style={{ padding: '24px 32px', maxWidth: 1000, width: '100%' }}>
        {/* ─── Team Overview Card ─── */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 12,
            border: '1px solid #E2E8F0',
            padding: 24,
            marginBottom: 24,
            boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
          }}
        >
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
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

          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#64748B' }}>
            <Users size={16} color="#2563EB" />
            <span><b>{members.length}</b> thành viên</span>
          </div>
        </div>
      </div>

      {/* ─── Add Member Section ─── */}
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          border: '1px solid #E2E8F0',
          padding: 20,
          marginBottom: 24,
        }}
      >
        <h3 style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', margin: '0 0 12px 0', display: 'flex', alignItems: 'center', gap: 6 }}>
          <UserPlus size={16} color="#2563EB" />
          <span>Thêm thành viên vào nhóm</span>
        </h3>

        {errorMsg && (
          <div style={{ padding: '8px 12px', borderRadius: 6, backgroundColor: '#FEF2F2', color: '#DC2626', fontSize: 12, marginBottom: 12 }}>
            {errorMsg}
          </div>
        )}
        {successMsg && (
          <div style={{ padding: '8px 12px', borderRadius: 6, backgroundColor: '#ECFDF5', color: '#059669', fontSize: 12, marginBottom: 12 }}>
            {successMsg}
          </div>
        )}

        <form onSubmit={handleAddMember} style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <input
            type="text"
            placeholder="Nhập User UUID (hoặc ID người dùng)..."
            value={newUserId}
            onChange={(e) => setNewUserId(e.target.value)}
            required
            style={{
              flex: 1,
              padding: '8px 12px',
              borderRadius: 6,
              border: '1px solid #CBD5E1',
              fontSize: 13,
              outline: 'none',
            }}
          />

          <select
            value={newRole}
            onChange={(e) => setNewRole(e.target.value)}
            style={{
              padding: '8px 12px',
              borderRadius: 6,
              border: '1px solid #CBD5E1',
              fontSize: 13,
              backgroundColor: '#FFFFFF',
              outline: 'none',
            }}
          >
            <option value="member">Thành viên (Member)</option>
            <option value="leader">Trưởng nhóm (Leader)</option>
            <option value="viewer">Người xem (Viewer)</option>
          </select>

          <button
            type="submit"
            disabled={submitting}
            style={{
              backgroundColor: '#2563EB',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: 6,
              padding: '8px 16px',
              fontSize: 13,
              fontWeight: 600,
              cursor: submitting ? 'not-allowed' : 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            {submitting ? 'Đang thêm...' : 'Thêm thành viên'}
          </button>
        </form>
      </div>

      {/* ─── Members List Table ─── */}
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          border: '1px solid #E2E8F0',
          overflow: 'hidden',
          boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
        }}
      >
        <div style={{ padding: '14px 18px', borderBottom: '1px solid #F1F5F9', fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
          Danh sách thành viên ({members.length})
        </div>

        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {members.map((member, index) => (
            <div
              key={member.userId}
              style={{
                padding: '12px 18px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                borderBottom: index < members.length - 1 ? '1px solid #F1F5F9' : 'none',
                backgroundColor: index % 2 === 0 ? '#FFFFFF' : '#FAFAFA',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: '50%',
                    backgroundColor: '#E0E7FF',
                    color: '#3730A3',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 700,
                    fontSize: 13,
                  }}
                >
                  {member.username ? member.username.charAt(0).toUpperCase() : 'U'}
                </div>

                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>
                    {member.fullName || member.username || 'User ID: ' + member.userId.substring(0, 8)}
                  </div>
                  <div style={{ fontSize: 11, color: '#64748B', display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Mail size={11} />
                    <span>{member.email || member.userId}</span>
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    padding: '2px 8px',
                    borderRadius: 12,
                    backgroundColor: member.roleInTeam === 'leader' ? '#EFF6FF' : '#F1F5F9',
                    color: member.roleInTeam === 'leader' ? '#2563EB' : '#475569',
                    border: member.roleInTeam === 'leader' ? '1px solid #BFDBFE' : '1px solid #E2E8F0',
                  }}
                >
                  {member.roleInTeam}
                </span>

                <button
                  onClick={() => handleRemoveMember(member.userId)}
                  title="Xóa khỏi nhóm"
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#94A3B8',
                    cursor: 'pointer',
                    padding: 4,
                    borderRadius: 4,
                    display: 'flex',
                    alignItems: 'center',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.color = '#DC2626')}
                  onMouseLeave={(e) => (e.currentTarget.style.color = '#94A3B8')}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  </div>
);
}
