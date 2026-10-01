import React, { useState } from 'react';
import { useNavigate, useOutletContext } from 'react-router-dom';
import { Mail, Users, FolderKanban, Check, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { invitationApi } from '../api.js';

/**
 * Lời mời tham gia nhóm / dự án đang chờ người dùng hiện tại trả lời.
 * Danh sách lấy từ WorkspaceLayout (context) để badge trên sidebar và trang này dùng chung một nguồn.
 */
export default function InvitationsPage() {
  const navigate = useNavigate();
  const { invitations = [], refreshInvitations, refreshTeams } = useOutletContext() || {};
  const [busyId, setBusyId] = useState(null);

  const respond = async (inv, accept) => {
    try {
      setBusyId(inv.id);
      const res = accept ? await invitationApi.accept(inv.id) : await invitationApi.decline(inv.id);
      toast.success(res?.message || (accept ? 'Đã tham gia' : 'Đã từ chối'));
      await Promise.all([refreshInvitations?.(), accept ? refreshTeams?.() : null]);
      if (accept) navigate(inv.type === 'TEAM' ? `/teams/${inv.targetId}` : `/projects/${inv.targetId}`);
    } catch {
      // interceptor đã hiện toast lỗi
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ backgroundColor: '#FFFFFF', minHeight: '100%', display: 'flex', flexDirection: 'column', flex: 1, overflowY: 'auto' }}>
      <div style={{ height: 52, minHeight: 52, padding: '0 32px', borderBottom: '1px solid #E2E8F0', display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
        <Mail size={16} color="#2563EB" />
        <span>Lời mời</span>
        {invitations.length > 0 && (
          <span style={{ fontSize: 11, fontWeight: 700, backgroundColor: '#DBEAFE', color: '#1D4ED8', padding: '1px 7px', borderRadius: 10 }}>{invitations.length}</span>
        )}
      </div>

      <div style={{ width: '100%', maxWidth: 1100, margin: '0 auto', padding: '32px 40px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {invitations.length === 0 ? (
          <div
            style={{
              padding: '64px 32px',
              textAlign: 'center',
              backgroundColor: '#F8FAFC',
              borderRadius: 12,
              border: '1px dashed #CBD5E1',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 12,
              margin: '16px 0',
            }}
          >
            <div
              style={{
                width: 52,
                height: 52,
                borderRadius: 12,
                backgroundColor: '#EFF6FF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#2563EB',
              }}
            >
              <Mail size={24} />
            </div>
            <div>
              <div style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', marginBottom: 4 }}>
                Bạn không có lời mời nào đang chờ
              </div>
              <div style={{ fontSize: 13, color: '#64748B', maxWidth: 440, lineHeight: 1.5 }}>
                Khi trưởng nhóm hoặc quản trị viên dự án gửi lời mời tham gia, lời mời sẽ hiển thị tại đây để bạn phản hồi.
              </div>
            </div>
          </div>
        ) : (
          invitations.map((inv) => {
            const isTeam = inv.type === 'TEAM';
            const Icon = isTeam ? Users : FolderKanban;
            const busy = busyId === inv.id;
            return (
              <div
                key={inv.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 16,
                  padding: '16px 20px',
                  border: '1px solid #E2E8F0',
                  borderRadius: 10,
                  backgroundColor: '#FFFFFF',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                }}
              >
                <div
                  style={{
                    width: 42,
                    height: 42,
                    borderRadius: 10,
                    backgroundColor: isTeam ? '#EFF6FF' : '#F0FDF4',
                    color: isTeam ? '#2563EB' : '#16A34A',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  <Icon size={20} />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
                    {isTeam ? 'Nhóm' : 'Dự án'} <span style={{ color: '#2563EB' }}>{inv.targetName || '(không rõ tên)'}</span>
                  </div>
                  <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>
                    {inv.inviterName || 'Ai đó'} mời bạn với vai trò <b>{inv.roleName || 'MEMBER'}</b>
                    {inv.createdAt ? ` · ${new Date(inv.createdAt).toLocaleString('vi-VN')}` : ''}
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <button
                    onClick={() => respond(inv, true)}
                    disabled={busy}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      backgroundColor: '#2563EB',
                      color: '#FFF',
                      border: 'none',
                      borderRadius: 8,
                      padding: '8px 16px',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: busy ? 'not-allowed' : 'pointer',
                      transition: 'background-color 0.15s ease',
                    }}
                  >
                    <Check size={14} /> Chấp nhận
                  </button>
                  <button
                    onClick={() => respond(inv, false)}
                    disabled={busy}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      backgroundColor: '#FFF',
                      color: '#475569',
                      border: '1px solid #CBD5E1',
                      borderRadius: 8,
                      padding: '8px 16px',
                      fontSize: 13,
                      fontWeight: 600,
                      cursor: busy ? 'not-allowed' : 'pointer',
                      transition: 'background-color 0.15s ease',
                    }}
                  >
                    <X size={14} /> Từ chối
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
