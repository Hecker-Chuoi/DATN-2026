import { useEffect, useState } from 'react';
import { SURFACE } from '../../constants/jiraTokens.js';
import { contributionApi } from '../../api.js';

export default function MemberContributionDrawer({ projectId, userId, sprintId, onClose }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await contributionApi.getMemberDetail(projectId, userId, { sprintId: sprintId || undefined });
        if (!cancelled) setData(res?.data || null);
      } catch (err) {
        console.warn('Không tải được chi tiết thành viên:', err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId, userId, sprintId]);

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
          maxWidth: 900,
          maxHeight: '85vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{ padding: '16px 24px', borderBottom: `1px solid ${SURFACE.border}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: SURFACE.text }}>
              {data?.name || 'Chi tiết thành viên'} · {data?.sprintName || 'Chu kỳ'}
            </h2>
          </div>
          <button
            onClick={onClose}
            style={{ border: 'none', background: 'transparent', fontSize: 20, cursor: 'pointer', color: SURFACE.textMuted }}
          >
            ✕
          </button>
        </div>

        {/* Content Body (2 Columns as shown in Screenshot 3) */}
        <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
          {loading ? (
            <div style={{ padding: 40, textAlign: 'center', color: SURFACE.textMuted }}>Đang tải dữ liệu...</div>
          ) : !data ? (
            <div style={{ padding: 40, textAlign: 'center', color: SURFACE.textMuted }}>Không tìm thấy dữ liệu.</div>
          ) : (
            <div style={{ display: 'flex', gap: 28, flexWrap: 'wrap' }}>
              {/* Left Column: Metrics & Weekly Chart */}
              <div style={{ flex: '1 1 300px', maxWidth: 360 }}>
                {/* 3 mini cards */}
                <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
                  <div style={{ flex: 1, padding: '12px 10px', backgroundColor: '#F8FAFC', border: `1px solid ${SURFACE.border}`, borderRadius: 8, textAlign: 'center' }}>
                    <div style={{ fontSize: 20, fontWeight: 700, color: SURFACE.text }}>{data.totalPoints}</div>
                    <div style={{ fontSize: 11, color: SURFACE.textMuted, marginTop: 2 }}>Điểm</div>
                  </div>
                  <div style={{ flex: 1, padding: '12px 10px', backgroundColor: '#F8FAFC', border: `1px solid ${SURFACE.border}`, borderRadius: 8, textAlign: 'center' }}>
                    <div style={{ fontSize: 20, fontWeight: 700, color: SURFACE.text }}>{data.completedTasks}</div>
                    <div style={{ fontSize: 11, color: SURFACE.textMuted, marginTop: 2 }}>Việc xong</div>
                  </div>
                  <div style={{ flex: 1, padding: '12px 10px', backgroundColor: '#F8FAFC', border: `1px solid ${SURFACE.border}`, borderRadius: 8, textAlign: 'center' }}>
                    <div style={{ fontSize: 20, fontWeight: 700, color: '#16A34A' }}>{data.onTimeRatePercent}%</div>
                    <div style={{ fontSize: 11, color: SURFACE.textMuted, marginTop: 2 }}>Đúng hạn</div>
                  </div>
                </div>

                {/* Biểu đồ điểm theo tuần */}
                <div>
                  <h4 style={{ margin: '0 0 16px 0', fontSize: 13, fontWeight: 700, color: SURFACE.text }}>
                    Điểm theo tuần
                  </h4>

                  <div style={{ display: 'flex', alignItems: 'flex-end', gap: 20, height: 160, paddingBottom: 20, borderBottom: `1px solid ${SURFACE.border}` }}>
                    {data.weeklyPoints?.map((w, idx) => {
                      const maxPts = Math.max(...(data.weeklyPoints.map(p => Number(p.points)) || [1]), 10);
                      const barHeight = Math.max(8, (Number(w.points) / maxPts) * 120);

                      return (
                        <div key={idx} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                          <span style={{ fontSize: 12, fontWeight: 700, color: SURFACE.text, marginBottom: 4 }}>
                            {w.points}
                          </span>
                          <div
                            style={{
                              width: '100%',
                              maxWidth: 36,
                              height: barHeight,
                              backgroundColor: '#2563EB',
                              borderRadius: '4px 4px 0 0',
                              transition: 'height 0.3s ease',
                            }}
                          />
                          <span style={{ fontSize: 11, color: SURFACE.textMuted, marginTop: 8 }}>
                            {w.weekLabel}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>

              {/* Right Column: Work Items Table */}
              <div style={{ flex: '2 1 450px' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: 13 }}>
                  <thead>
                    <tr style={{ borderBottom: `2px solid ${SURFACE.border}`, color: SURFACE.textMuted }}>
                      <th style={{ padding: '10px 8px', fontWeight: 600 }}>VIỆC</th>
                      <th style={{ padding: '10px 8px', fontWeight: 600, width: '20%', textAlign: 'center' }}>GHI CÔNG</th>
                      <th style={{ padding: '10px 8px', fontWeight: 600, width: '32%' }}>GHI CHÚ</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.workItems?.length === 0 ? (
                      <tr>
                        <td colSpan={3} style={{ padding: 24, textAlign: 'center', color: SURFACE.textMuted }}>
                          Chưa có công việc nào trong chu kỳ này.
                        </td>
                      </tr>
                    ) : (
                      data.workItems?.map((item, idx) => {
                        const pts = Number(item.points);
                        const isPositive = pts > 0;
                        const isNegative = pts < 0;
                        const ptsColor = isPositive ? '#16A34A' : isNegative ? '#DC2626' : '#64748B';

                        let noteColor = SURFACE.textMuted;
                        if (item.note?.includes('Đúng hạn')) {
                          noteColor = '#16A34A';
                        } else if (item.note?.includes('Trễ')) {
                          noteColor = '#D97706';
                        } else if (item.note?.includes('mở lại')) {
                          noteColor = '#DC2626';
                        }

                        return (
                          <tr key={idx} style={{ borderBottom: `1px solid ${SURFACE.border}` }}>
                            <td style={{ padding: '12px 8px', color: SURFACE.text, fontWeight: 500 }}>
                              {item.code ? <span style={{ color: SURFACE.textMuted, marginRight: 6 }}>[{item.code}]</span> : ''}
                              {item.title}
                            </td>
                            <td style={{ padding: '12px 8px', textAlign: 'center', fontWeight: 700, color: ptsColor }}>
                              {isPositive ? `+${pts}` : pts}
                            </td>
                            <td style={{ padding: '12px 8px', color: noteColor, fontWeight: 500 }}>
                              {item.note || '—'}
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
