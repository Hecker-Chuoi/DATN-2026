import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, Save, Settings, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { workspaceApi } from '../api/workspaceServiceApi';
import { useWorkspace } from '../context/WorkspaceContext';
import { useWorkspaceRole } from '../hooks/useWorkspaceRole';

const inputStyle = {
  width: '100%',
  padding: '8px 12px',
  borderRadius: 6,
  border: '1px solid #CBD5E1',
  fontSize: 13,
  color: '#0F172A',
  outline: 'none',
};

const labelStyle = {
  display: 'block',
  fontSize: 13,
  fontWeight: 600,
  color: '#334155',
  marginBottom: 6,
};

const panelStyle = {
  background: '#FFFFFF',
  border: '1px solid #E2E8F0',
  borderRadius: 10,
  padding: 20,
  maxWidth: 560,
};

/**
 * Route /w/:workspaceId/settings - đổi tên/mô tả workspace và vùng nguy hiểm.
 *
 * Lưu ý về đặt tên route: trước đây /settings là alias trỏ tới ProfilePage.
 * Alias đó đụng khái niệm "cài đặt workspace" nên đã được retire; hồ sơ cá nhân
 * giờ nằm ở /w/:workspaceId/profile.
 */
export default function WorkspaceSettingsPage() {
  const { workspaceId } = useParams();
  const { currentWorkspace, refreshWorkspaces } = useWorkspace();
  const { canManageWorkspace, canDeleteWorkspace, loading: roleLoading } = useWorkspaceRole();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setName(currentWorkspace?.name || '');
    setDescription(currentWorkspace?.description || '');
  }, [currentWorkspace]);

  const handleSave = async (e) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Tên workspace không được để trống');
      return;
    }
    try {
      setSaving(true);
      setError('');
      await workspaceApi.update(workspaceId, {
        name: name.trim(),
        description: description.trim() || null,
        avatarUrl: currentWorkspace?.avatarUrl || null,
      });
      await refreshWorkspaces();
      toast.success('Đã lưu thay đổi');
    } catch (err) {
      setError(err.body?.message || err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    const confirmText = currentWorkspace?.name || 'workspace này';
    if (!window.confirm(`Chuyển "${confirmText}" vào thùng rác? Toàn bộ nhóm và dự án bên trong sẽ không còn truy cập được.`)) {
      return;
    }
    try {
      await workspaceApi.delete(workspaceId);
      toast.success('Đã chuyển workspace vào thùng rác');
      await refreshWorkspaces();
      navigate('/workspaces');
    } catch (err) {
      setError(err.body?.message || err.message);
    }
  };

  if (roleLoading) {
    return <div style={{ padding: 24, fontSize: 13, color: '#94A3B8' }}>Đang tải...</div>;
  }

  if (!canManageWorkspace) {
    return (
      <div style={{ padding: 24 }}>
        <div style={{ ...panelStyle, display: 'flex', alignItems: 'center', gap: 10 }}>
          <AlertCircle size={18} color="#B45309" />
          <span style={{ fontSize: 13, color: '#475569' }}>
            Bạn cần vai trò Quản trị trở lên để xem cài đặt workspace.
          </span>
        </div>
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <Settings size={20} color="#2563EB" />
        <h1 style={{ fontSize: 20, fontWeight: 700, color: '#0F172A', margin: 0 }}>
          Cài đặt workspace
        </h1>
      </div>
      <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 20px' }}>
        Thông tin chung của workspace. Đường dẫn (slug) không đổi được sau khi tạo.
      </p>

      <form onSubmit={handleSave} style={{ ...panelStyle, marginBottom: 20 }}>
        {error && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '10px 14px',
              borderRadius: 6,
              background: '#FEF2F2',
              border: '1px solid #FEE2E2',
              color: '#DC2626',
              fontSize: 13,
              marginBottom: 16,
            }}
          >
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        )}

        <div style={{ marginBottom: 16 }}>
          <label style={labelStyle}>
            Tên workspace <span style={{ color: '#EF4444' }}>*</span>
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              if (error) setError('');
            }}
            style={inputStyle}
          />
        </div>

        <div style={{ marginBottom: 16 }}>
          <label style={labelStyle}>Đường dẫn (slug)</label>
          <input
            type="text"
            value={currentWorkspace?.slug || ''}
            disabled
            style={{ ...inputStyle, background: '#F8FAFC', color: '#94A3B8' }}
          />
        </div>

        <div style={{ marginBottom: 20 }}>
          <label style={labelStyle}>Mô tả</label>
          <textarea
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            style={{ ...inputStyle, resize: 'vertical', fontFamily: 'inherit' }}
          />
        </div>

        <button
          type="submit"
          disabled={saving || !name.trim()}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            background: saving || !name.trim() ? '#94A3B8' : '#2563EB',
            color: '#FFFFFF',
            border: 'none',
            padding: '8px 16px',
            borderRadius: 6,
            fontSize: 13,
            fontWeight: 600,
            cursor: saving || !name.trim() ? 'not-allowed' : 'pointer',
          }}
        >
          <Save size={14} />
          <span>{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</span>
        </button>
      </form>

      {/* Vùng nguy hiểm: chỉ OWNER. Xóa là SOFT delete nên còn phục hồi được. */}
      {canDeleteWorkspace && (
        <div style={{ ...panelStyle, border: '1px solid #FEE2E2' }}>
          <div style={{ fontSize: 14, fontWeight: 700, color: '#DC2626', marginBottom: 6 }}>
            Vùng nguy hiểm
          </div>
          <p style={{ fontSize: 12, color: '#64748B', margin: '0 0 16px' }}>
            Xóa workspace sẽ chuyển nó vào thùng rác. Toàn bộ nhóm, dự án và công việc bên trong
            không còn truy cập được. Danh sách thành viên vẫn được giữ để có thể phục hồi.
          </p>
          <button
            type="button"
            onClick={handleDelete}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              background: '#FEF2F2',
              color: '#DC2626',
              border: '1px solid #FEE2E2',
              padding: '8px 14px',
              borderRadius: 6,
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Trash2 size={14} />
            <span>Xóa workspace</span>
          </button>
        </div>
      )}
    </div>
  );
}
