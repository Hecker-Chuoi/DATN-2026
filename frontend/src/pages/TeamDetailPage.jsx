import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { Users, UserPlus, Shield, RefreshCw } from 'lucide-react';
import { teamApi } from '../api.js';

export default function TeamDetailPage() {
  const { teamId } = useParams();
  const [team, setTeam] = useState(null);
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);

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

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        overflow: 'hidden',
      }}
    >
      {/* ─── Header ─── */}
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
          <Users size={16} color="#6366F1" />
          <span>{team?.name || 'Team Details'}</span>
        </div>
        <button
          onClick={fetchTeamData}
          title="Làm mới"
          style={{
            background: 'none',
            border: 'none',
            color: '#64748B',
            cursor: 'pointer',
            padding: '6px',
            borderRadius: 6,
          }}
        >
          <RefreshCw size={15} />
        </button>
      </div>

      {/* ─── Body ─── */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>
        <h3 style={{ fontSize: 16, fontWeight: 600, color: '#0F172A', marginBottom: 16 }}>
          Thành viên trong nhóm ({members.length})
        </h3>
        {loading ? (
          <div style={{ color: '#64748B', fontSize: 13 }}>Đang tải thông tin nhóm...</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {members.map((m, idx) => (
              <div
                key={m.userId || `member-${idx}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 16px',
                  border: '1px solid #E2E8F0',
                  borderRadius: 6,
                }}
              >
                <div style={{ fontSize: 13, fontWeight: 500, color: '#0F172A' }}>
                  {m.fullName || m.username || m.userId}
                </div>
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    color: '#6366F1',
                    backgroundColor: '#EEF2FF',
                    padding: '2px 8px',
                    borderRadius: 12,
                  }}
                >
                  {m.roleInTeam || 'MEMBER'}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
