import { useNavigate } from 'react-router-dom';
import { Building2, Plus, Users, ArrowRight } from 'lucide-react';
import { useWorkspace } from '../context/WorkspaceContext';
import { wsPath } from '../utils/workspacePath';
import RoleBadge from '../components/workspace/RoleBadge';

const primaryBtn = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  background: '#2563EB',
  color: '#FFFFFF',
  border: 'none',
  padding: '9px 16px',
  borderRadius: 8,
  fontSize: 13,
  fontWeight: 600,
  cursor: 'pointer',
  flexShrink: 0,
};

const cardStyle = {
  textAlign: 'left',
  background: '#FFFFFF',
  border: '1px solid #E2E8F0',
  borderRadius: 12,
  padding: 16,
  cursor: 'pointer',
  display: 'flex',
  flexDirection: 'column',
  gap: 10,
};

/**
 * Route /workspaces - danh sách workspace của người dùng, kiêm luôn trạng thái
 * onboarding khi chưa thuộc workspace nào.
 *
 * Trang này nằm NGOÀI WorkspaceLayout: chưa chọn workspace thì chưa có sidebar,
 * vì sidebar cần workspaceId để render switcher và danh sách team.
 */
export default function WorkspaceListPage() {
  const { workspaces, loading, error, openCreateWorkspace } = useWorkspace();
  const navigate = useNavigate();

  return (
    <div style={{ minHeight: '100vh', background: '#F8FAFC', padding: '48px 24px' }}>
      <div style={{ maxWidth: 880, margin: '0 auto' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: 16,
            marginBottom: 28,
          }}
        >
          <div>
            <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0F172A', margin: 0 }}>
              Workspace của bạn
            </h1>
            <p style={{ fontSize: 13, color: '#64748B', margin: '6px 0 0' }}>
              Workspace là cấp cao nhất: mỗi workspace chứa nhiều nhóm, mỗi nhóm chứa nhiều dự án.
            </p>
          </div>
          <button type="button" onClick={openCreateWorkspace} style={primaryBtn}>
            <Plus size={15} />
            <span>Tạo workspace</span>
          </button>
        </div>

        {loading && (
          <div style={{ fontSize: 13, color: '#94A3B8' }}>Đang tải danh sách workspace...</div>
        )}

        {!loading && error && (
          <div
            style={{
              padding: '12px 16px',
              borderRadius: 8,
              background: '#FEF2F2',
              border: '1px solid #FEE2E2',
              color: '#DC2626',
              fontSize: 13,
            }}
          >
            {error}
          </div>
        )}

        {!loading && !error && workspaces.length === 0 && (
          <div
            style={{
              padding: '48px 24px',
              textAlign: 'center',
              background: '#FFFFFF',
              border: '1px solid #E2E8F0',
              borderRadius: 12,
            }}
          >
            <div
              style={{
                width: 56,
                height: 56,
                borderRadius: 14,
                background: '#EFF6FF',
                color: '#2563EB',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 16px',
              }}
            >
              <Building2 size={28} />
            </div>
            <h2 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: '0 0 6px' }}>
              Bạn chưa thuộc workspace nào
            </h2>
            <p style={{ fontSize: 13, color: '#64748B', margin: '0 auto 20px', maxWidth: 420 }}>
              Tạo workspace đầu tiên để bắt đầu tổ chức nhóm và dự án, hoặc chờ được người khác mời
              vào workspace của họ.
            </p>
            <button
              type="button"
              onClick={openCreateWorkspace}
              style={{ ...primaryBtn, padding: '10px 20px' }}
            >
              <Plus size={15} />
              <span>Tạo workspace đầu tiên</span>
            </button>
          </div>
        )}

        {!loading && workspaces.length > 0 && (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
              gap: 16,
            }}
          >
            {workspaces.map((w) => (
              <button
                key={w.id}
                type="button"
                onClick={() => navigate(wsPath(w.id, '/projects'))}
                style={cardStyle}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div
                    style={{
                      width: 36,
                      height: 36,
                      borderRadius: 9,
                      background: '#2563EB',
                      color: '#FFFFFF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: 15,
                      flexShrink: 0,
                    }}
                  >
                    {(w.name || '?').trim().charAt(0).toUpperCase()}
                  </div>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div
                      style={{
                        fontSize: 14,
                        fontWeight: 600,
                        color: '#0F172A',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {w.name}
                    </div>
                    <div style={{ fontSize: 11, color: '#94A3B8' }}>/{w.slug}</div>
                  </div>
                </div>

                {w.description && (
                  <p style={{ fontSize: 12, color: '#64748B', margin: 0 }}>{w.description}</p>
                )}

                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 8,
                    marginTop: 'auto',
                    paddingTop: 8,
                    borderTop: '1px solid #F1F5F9',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <RoleBadge role={w.myRole} />
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                        fontSize: 11,
                        color: '#94A3B8',
                      }}
                    >
                      <Users size={12} />
                      {w.memberCount}
                    </span>
                  </div>
                  <ArrowRight size={14} color="#94A3B8" />
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
