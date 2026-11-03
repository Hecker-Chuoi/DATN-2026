import { useState } from 'react';
import { SURFACE } from '../../constants/jiraTokens.js';
import { contributionApi } from '../../api.js';
import CustomSelect from '../common/CustomSelect.jsx';

export default function ManualAdjustmentModal({ projectId, sprintId, members, onClose, onSuccess }) {
  const [userId, setUserId] = useState(members[0]?.userId || '');
  const [type, setType] = useState('add'); // 'add' or 'subtract'
  const [points, setPoints] = useState(3);
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!userId) {
      setError('Vui lòng chọn thành viên.');
      return;
    }
    if (!reason || reason.trim().length < 15) {
      setError('Lý do điều chỉnh phải có ít nhất 15 ký tự.');
      return;
    }
    const finalPoints = type === 'add' ? Math.abs(points) : -Math.abs(points);
    if (finalPoints < -8 || finalPoints > 8) {
      setError('Biên độ điều chỉnh điểm tối đa là +/- 8 điểm.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      await contributionApi.adjustPoints(projectId, {
        userId,
        sprintId: sprintId || null,
        points: finalPoints,
        reason: reason.trim(),
      });
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Không thể điều chỉnh điểm.');
    } finally {
      setLoading(false);
    }
  };

  const memberOptions = members.map((m) => ({
    value: m.userId,
    label: m.name,
    sublabel: m.email || '',
    icon: (
      <div
        style={{
          width: 24,
          height: 24,
          borderRadius: '50%',
          backgroundColor: '#2563EB',
          color: '#FFFFFF',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 11,
          fontWeight: 700,
          flexShrink: 0,
        }}
      >
        {(m.name || 'U').charAt(0).toUpperCase()}
      </div>
    ),
  }));

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1050,
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          width: '100%',
          maxWidth: 520,
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ padding: '16px 20px', borderBottom: `1px solid ${SURFACE.border}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: SURFACE.text }}>
            Điều Chỉnh Điểm Đóng Góp
          </h3>
          <button
            onClick={onClose}
            style={{ border: 'none', background: 'transparent', fontSize: 18, cursor: 'pointer', color: SURFACE.textMuted }}
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ padding: '20px' }}>
          {error && (
            <div style={{ padding: '8px 12px', borderRadius: 6, backgroundColor: '#FEE2E2', color: '#DC2626', fontSize: 13, marginBottom: 14 }}>
              {error}
            </div>
          )}

          {/* 1. Thanh vien */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: SURFACE.text, marginBottom: 6 }}>
              Thành viên thụ hưởng <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <CustomSelect
              value={userId}
              onChange={(val) => setUserId(val)}
              options={memberOptions}
              placeholder="Chọn thành viên..."
            />
          </div>

          {/* 2. Loai dieu chinh */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: SURFACE.text, marginBottom: 6 }}>
              Loại điều chỉnh <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <div style={{ display: 'flex', gap: 12 }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="adjustType"
                  value="add"
                  checked={type === 'add'}
                  onChange={() => setType('add')}
                />
                <span style={{ color: '#16A34A', fontWeight: 600 }}>Cộng điểm thưởng (+)</span>
              </label>
              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
                <input
                  type="radio"
                  name="adjustType"
                  value="subtract"
                  checked={type === 'subtract'}
                  onChange={() => setType('subtract')}
                />
                <span style={{ color: '#DC2626', fontWeight: 600 }}>Trừ điểm phạt (-)</span>
              </label>
            </div>
          </div>

          {/* 3. So diem */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: SURFACE.text, marginBottom: 6 }}>
              Số điểm thay đổi (tối đa ±8) <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <div style={{ display: 'flex', gap: 8 }}>
              {[1, 2, 3, 5, 8].map((val) => {
                const isSelected = points === val;
                const sign = type === 'add' ? '+' : '-';
                const activeBg = type === 'add' ? '#EFF6FF' : '#FEF2F2';
                const activeBorder = type === 'add' ? '#93C5FD' : '#FECACA';
                const activeColor = type === 'add' ? '#1D4ED8' : '#DC2626';

                return (
                  <button
                    key={val}
                    type="button"
                    onClick={() => setPoints(val)}
                    style={{
                      flex: 1,
                      padding: '8px 4px',
                      borderRadius: 8,
                      border: `1.5px solid ${isSelected ? activeBorder : SURFACE.border}`,
                      backgroundColor: isSelected ? activeBg : '#FFFFFF',
                      color: isSelected ? activeColor : SURFACE.text,
                      fontWeight: isSelected ? 700 : 500,
                      fontSize: 13,
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                  >
                    {sign}{val} điểm
                  </button>
                );
              })}
            </div>
          </div>

          {/* 4. Ly do */}
          <div style={{ marginBottom: 16 }}>
            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: SURFACE.text, marginBottom: 6 }}>
              Lý do điều chỉnh  <span style={{ color: '#EF4444' }}>*</span>
            </label>
            <textarea
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 12px',
                borderRadius: 6,
                border: `1px solid ${SURFACE.border}`,
                fontSize: 13,
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
          </div>



          {/* Actions */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              style={{
                padding: '8px 16px',
                borderRadius: 6,
                border: `1px solid ${SURFACE.border}`,
                backgroundColor: '#FFFFFF',
                color: SURFACE.text,
                fontSize: 13,
                cursor: 'pointer',
              }}
            >
              Hủy bỏ
            </button>
            <button
              type="submit"
              disabled={loading}
              style={{
                padding: '8px 16px',
                borderRadius: 6,
                border: 'none',
                backgroundColor: '#2563EB',
                color: '#FFFFFF',
                fontWeight: 600,
                fontSize: 13,
                cursor: 'pointer',
              }}
            >
              {loading ? 'Đang lưu...' : 'Xác nhận ghi vào sổ'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
