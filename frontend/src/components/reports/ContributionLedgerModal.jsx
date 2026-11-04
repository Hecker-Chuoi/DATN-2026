import { useEffect, useState } from 'react';
import { SURFACE } from '../../constants/jiraTokens.js';
import { contributionApi } from '../../api.js';

export default function ContributionLedgerModal({ projectId, sprintId, isLeader, onOpenAdjust, onClose }) {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await contributionApi.getLedger(projectId, { sprintId: sprintId || undefined });
        if (!cancelled) setEntries(res?.data || []);
      } catch (err) {
        console.warn('Không tải được Sổ ghi công:', err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId, sprintId]);

  const formatDate = (isoStr) => {
    if (!isoStr) return '';
    const date = new Date(isoStr);
    const dayNames = ['CN', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'];
    const dayName = dayNames[date.getDay()];
    const time = date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    const dayMonth = date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
    return `${dayName}, ${dayMonth} (${time})`;
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1040,
        padding: 20,
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          width: '100%',
          maxWidth: 780,
          maxHeight: '85vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{ padding: '20px 24px', borderBottom: `1px solid ${SURFACE.border}` }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: '#0F172A' }}>
                Sổ ghi công: chỉ viết thêm, không xoá
              </h2>
              <p style={{ margin: '4px 0 0 0', fontSize: 13, color: SURFACE.textMuted }}>
                Mỗi lần ghi công là <strong>1 dòng</strong>. Sai thì <strong>ghi thêm dòng trừ</strong>.
              </p>
            </div>
            <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              {/* Nút Điều chỉnh điểm thủ công trong Ledger Modal (tạm ẩn theo yêu cầu) */}
              {/* {isLeader && (
                <button
                  onClick={() => {
                    onClose();
                    if (onOpenAdjust) onOpenAdjust();
                  }}
                  style={{
                    padding: '6px 14px',
                    borderRadius: 6,
                    border: 'none',
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    fontSize: 12,
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  + Điều chỉnh điểm
                </button>
              )} */}
              <button
                onClick={onClose}
                style={{ border: 'none', background: 'transparent', fontSize: 20, cursor: 'pointer', color: SURFACE.textMuted }}
              >
                ✕
              </button>
            </div>
          </div>
        </div>

        {/* Content Table */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px 24px 24px 24px' }}>
          {loading ? (
            <div style={{ padding: 40, textAlign: 'center', color: SURFACE.textMuted }}>Đang tải Sổ ghi công...</div>
          ) : entries.length === 0 ? (
            <div style={{ padding: 40, textAlign: 'center', color: SURFACE.textMuted }}>
              Chưa có giao dịch ghi công nào trong chu kỳ này.
            </div>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: 13 }}>
              <thead>
                <tr style={{ borderBottom: `2px solid ${SURFACE.border}`, color: SURFACE.textMuted }}>
                  <th style={{ padding: '12px 8px', fontWeight: 600, width: '22%' }}>LÚC</th>
                  <th style={{ padding: '12px 8px', fontWeight: 600, width: '20%' }}>GHI CHO</th>
                  <th style={{ padding: '12px 8px', fontWeight: 600, width: '40%' }}>CÔNG VIỆC</th>
                  <th style={{ padding: '12px 8px', fontWeight: 600, width: '18%', textAlign: 'right' }}>THAY ĐỔI</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((item) => {
                  const pts = Number(item.points);
                  const isPositive = pts > 0;
                  const isNegative = pts < 0;
                  const ptsColor = isPositive ? '#16A34A' : isNegative ? '#DC2626' : '#64748B';

                  let actionText = '';
                  if (item.entryType === 'REOPENED') {
                    actionText = ' · bị mở lại';
                  } else if (item.entryType === 'DELETED') {
                    actionText = ' · đã xóa';
                  } else if (item.entryType === 'MANUAL_ADJUSTMENT') {
                    actionText = ` · điều chỉnh (${item.note || ''})`;
                  } else {
                    actionText = ` · ${item.note || 'hoàn thành'}`;
                  }

                  return (
                    <tr
                      key={item.id}
                      style={{
                        borderBottom: `1px solid ${SURFACE.border}`,
                        backgroundColor: isNegative ? '#FFFDFD' : 'transparent',
                      }}
                    >
                      <td style={{ padding: '14px 8px', color: SURFACE.textMuted }}>
                        {formatDate(item.createdAt)}
                      </td>
                      <td style={{ padding: '14px 8px', fontWeight: 700, color: SURFACE.text }}>
                        {item.userName}
                      </td>
                      <td style={{ padding: '14px 8px', color: SURFACE.text }}>
                        {item.workItemCode ? <strong>[{item.workItemCode}] </strong> : ''}
                        <span>{item.workItemTitle || 'Công việc'}</span>
                        <span style={{ color: isNegative ? '#DC2626' : SURFACE.textMuted, fontWeight: isNegative ? 600 : 400 }}>
                          {actionText}
                        </span>
                      </td>
                      <td
                        style={{
                          padding: '14px 8px',
                          textAlign: 'right',
                          fontWeight: 700,
                          fontSize: 14,
                          color: ptsColor,
                        }}
                      >
                        {isPositive ? `+${pts}` : pts} điểm
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
