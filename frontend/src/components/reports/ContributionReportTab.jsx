import { useEffect, useState } from 'react';
import { BookOpen, SlidersHorizontal, Download, Users, BarChart3 } from 'lucide-react';
import { SURFACE } from '../../constants/jiraTokens.js';
import { contributionApi } from '../../api.js';
import CustomSelect from '../common/CustomSelect.jsx';
import ContributionLedgerModal from './ContributionLedgerModal.jsx';
import MemberContributionDrawer from './MemberContributionDrawer.jsx';
import ManualAdjustmentModal from './ManualAdjustmentModal.jsx';
import ContributionChartsView from './ContributionChartsView.jsx';
import SegmentedTabs from '../common/SegmentedTabs.jsx';

const SUB_TABS = [
  { id: 'members', label: 'Danh sách thành viên', icon: Users },
  { id: 'charts', label: 'Biểu đồ trực quan', icon: BarChart3 },
];

export default function ContributionReportTab({ projectId, sprintId, sprints, isLeader }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [selectedSprintId, setSelectedSprintId] = useState(sprintId ?? '');
  const [selectedMemberId, setSelectedMemberId] = useState(null);
  const [showLedger, setShowLedger] = useState(false);
  const [showAdjust, setShowAdjust] = useState(false);
  const [sortBy, setSortBy] = useState('points'); // 'points' or 'name'
  const [activeSubTab, setActiveSubTab] = useState('members'); // 'members' | 'charts'
  const [hasInitializedSprint, setHasInitializedSprint] = useState(false);

  const loadReport = async () => {
    setLoading(true);
    try {
      const res = await contributionApi.getOverview(projectId, {
        sprintId: selectedSprintId || undefined,
      });
      setData(res?.data || null);
    } catch (err) {
      console.warn('Không tải được báo cáo đóng góp:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!hasInitializedSprint && sprints && sprints.length > 0) {
      const active = sprints.find((s) => s.state === 'ACTIVE');
      const defaultSprint = sprintId || active?.id || sprints[0]?.id || '';
      setSelectedSprintId(defaultSprint);
      setHasInitializedSprint(true);
    }
  }, [sprintId, sprints, hasInitializedSprint]);

  useEffect(() => {
    loadReport();
  }, [projectId, selectedSprintId]);

  const handleExport = () => {
    const url = contributionApi.getExportUrl(projectId, selectedSprintId);
    window.open(url, '_blank');
  };

  const sprintOptions = [
    { value: '', label: 'Toàn bộ quá trình (Tất cả chu kỳ)' },
    ...(sprints || []).map((s) => ({
      value: s.id,
      label: s.name,
      badge: (
        <span
          style={{
            fontSize: 10,
            fontWeight: 600,
            padding: '1px 6px',
            borderRadius: 999,
            backgroundColor: s.state === 'ACTIVE' ? '#DCFCE7' : s.state === 'CLOSED' ? '#F1F5F9' : '#FEF3C7',
            color: s.state === 'ACTIVE' ? '#15803D' : s.state === 'CLOSED' ? '#64748B' : '#B45309',
          }}
        >
          {s.state === 'ACTIVE' ? 'Đang chạy' : s.state === 'CLOSED' ? 'Đã đóng' : 'Kế hoạch'}
        </span>
      ),
    })),
  ];

  const members = [...(data?.members || [])].sort((a, b) => {
    if (sortBy === 'name') {
      return (a.name || '').localeCompare(b.name || '');
    }
    return Number(b.points) - Number(a.points);
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* 1. Header & Controls Bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 13, color: SURFACE.textMuted }}>Xem theo:</span>
          <CustomSelect
            value={selectedSprintId}
            onChange={(val) => setSelectedSprintId(val)}
            options={sprintOptions}
            width={280}
            placeholder="Chọn chu kỳ..."
          />
          {data?.isFrozen && (
            <span
              style={{
                fontSize: 11,
                fontWeight: 600,
                padding: '2px 8px',
                borderRadius: 999,
                backgroundColor: '#EFF6FF',
                color: '#1D4ED8',
                border: '1px solid #BFDBFE',
              }}
            >
              🔒 Số liệu đóng băng
            </span>
          )}
        </div>

        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <button
            onClick={() => setShowLedger(true)}
            title="Sổ ghi công"
            aria-label="Sổ ghi công"
            style={{
              width: 34,
              height: 34,
              borderRadius: 8,
              border: `1px solid ${SURFACE.border}`,
              backgroundColor: '#FFFFFF',
              color: SURFACE.text,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'all 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
          >
            <BookOpen size={16} />
          </button>

          {/* Nút Điều chỉnh điểm thủ công (tạm ẩn theo yêu cầu) */}
          {/* {isLeader && (
            <button
              onClick={() => setShowAdjust(true)}
              title="Điều chỉnh điểm"
              aria-label="Điều chỉnh điểm"
              style={{
                width: 34,
                height: 34,
                borderRadius: 8,
                border: `1px solid #BFDBFE`,
                backgroundColor: '#EFF6FF',
                color: '#1D4ED8',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#DBEAFE')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#EFF6FF')}
            >
              <SlidersHorizontal size={16} />
            </button>
          )} */}

          <button
            onClick={handleExport}
            title="Xuất file Excel"
            aria-label="Xuất file Excel"
            style={{
              width: 34,
              height: 34,
              borderRadius: 8,
              border: `1px solid ${SURFACE.border}`,
              backgroundColor: '#FFFFFF',
              color: SURFACE.text,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              transition: 'all 0.15s ease',
            }}
            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
          >
            <Download size={16} />
          </button>
        </div>
      </div>

      {loading ? (
        <div style={{ padding: 60, textAlign: 'center', color: SURFACE.textMuted }}>Đang tải báo cáo đóng góp...</div>
      ) : !data ? (
        <div style={{ padding: 60, textAlign: 'center', color: SURFACE.textMuted }}>Chưa có dữ liệu đóng góp.</div>
      ) : (
        <>
          {/* 2. 4 KPI Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
            <div style={{ padding: '16px 20px', borderRadius: 10, backgroundColor: '#FFFFFF', border: `1px solid ${SURFACE.border}` }}>
              <div style={{ fontSize: 28, fontWeight: 800, color: '#0F172A' }}>{data.totalPoints}</div>
              <div style={{ fontSize: 13, color: SURFACE.textMuted, marginTop: 4 }}>Điểm đã xong</div>
            </div>

            <div style={{ padding: '16px 20px', borderRadius: 10, backgroundColor: '#FFFFFF', border: `1px solid ${SURFACE.border}` }}>
              <div style={{ fontSize: 28, fontWeight: 800, color: '#0F172A' }}>{data.totalTasksCompleted}</div>
              <div style={{ fontSize: 13, color: SURFACE.textMuted, marginTop: 4 }}>Việc đã xong</div>
            </div>

            <div style={{ padding: '16px 20px', borderRadius: 10, backgroundColor: '#FFFFFF', border: `1px solid ${SURFACE.border}` }}>
              <div style={{ fontSize: 28, fontWeight: 800, color: '#0F172A' }}>{data.averageCycleTimeDays} ngày</div>
              <div style={{ fontSize: 13, color: SURFACE.textMuted, marginTop: 4 }}>Thời gian làm một việc</div>
            </div>

            <div style={{ padding: '16px 20px', borderRadius: 10, backgroundColor: '#FFFFFF', border: `1px solid ${SURFACE.border}` }}>
              <div style={{ fontSize: 28, fontWeight: 800, color: '#0F172A' }}>{data.onTimeRatePercent}%</div>
              <div style={{ fontSize: 13, color: SURFACE.textMuted, marginTop: 4 }}>Xong đúng hạn</div>
            </div>
          </div>

          {/* 3. Canh bao chat luong du lieu - anh huong toan bo bao cao nen hien o ca 2 che do xem */}
          {data.alerts && data.alerts.length > 0 && (
            <div
              style={{
                padding: '12px 18px',
                borderRadius: 8,
                backgroundColor: '#FFFBEB',
                border: '1px solid #FCD34D',
                color: '#92400E',
                fontSize: 13,
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <span>⚠️</span>
              <span>{data.alerts.join(' ')}</span>
            </div>
          )}

          {/* 4. Che do xem: Danh sách thành viên | Biểu đồ trực quan */}
          <SegmentedTabs items={SUB_TABS} value={activeSubTab} onChange={setActiveSubTab} />

          {/* 5. Noi dung */}
          {activeSubTab === 'charts' ? (
            <ContributionChartsView
              members={members}
              totalPoints={data.totalPoints}
              isLeader={isLeader}
            />
          ) : (
            <div
              style={{
                backgroundColor: '#FFFFFF',
                borderRadius: 10,
                border: `1px solid ${SURFACE.border}`,
                overflow: 'hidden',
              }}
            >
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: 13 }}>
                  <thead>
                    <tr style={{ borderBottom: `2px solid ${SURFACE.border}`, backgroundColor: '#F8FAFC', color: SURFACE.textMuted }}>
                      <th
                        onClick={() => setSortBy(sortBy === 'name' ? 'points' : 'name')}
                        style={{ padding: '12px 16px', fontWeight: 700, cursor: 'pointer', userSelect: 'none' }}
                      >
                        THÀNH VIÊN {sortBy === 'name' ? '▲' : '▾'}
                      </th>
                      <th
                        onClick={() => setSortBy('points')}
                        style={{ padding: '12px 16px', fontWeight: 700, cursor: 'pointer', textAlign: 'right', userSelect: 'none' }}
                      >
                        ĐIỂM
                      </th>
                      <th style={{ padding: '12px 16px', fontWeight: 700, textAlign: 'right' }}>TỈ LỆ</th>
                      <th style={{ padding: '12px 16px', fontWeight: 700, textAlign: 'right' }}>VIỆC XONG</th>
                      <th style={{ padding: '12px 16px', fontWeight: 700, textAlign: 'right' }}>THẢO LUẬN</th>
                      <th style={{ padding: '12px 16px', fontWeight: 700, textAlign: 'right' }}>BÁO LỖI</th>
                      <th style={{ padding: '12px 16px', fontWeight: 700, textAlign: 'right' }}>ĐÚNG HẠN</th>
                      <th style={{ padding: '12px 16px', fontWeight: 700, textAlign: 'right' }}>BỊ MỞ LẠI</th>
                      <th style={{ padding: '12px 16px', width: 40 }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {members.map((m) => (
                      <tr
                        key={m.userId}
                        onClick={() => setSelectedMemberId(m.userId)}
                        style={{
                          borderBottom: `1px solid ${SURFACE.border}`,
                          cursor: 'pointer',
                          transition: 'background-color 0.15s ease',
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                        onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                      >
                        <td style={{ padding: '14px 16px', fontWeight: 700, color: SURFACE.text }}>
                          {m.name}
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', fontWeight: 700, color: '#2563EB' }}>
                          {m.points}
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: SURFACE.text }}>
                          {m.percentage}%
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: SURFACE.text }}>
                          {m.completedTasks}
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: SURFACE.text }}>
                          {m.discussionCount}
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: SURFACE.text }}>
                          {m.bugsReported}
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', fontWeight: 600, color: Number(m.onTimeRatePercent) >= 80 ? '#16A34A' : '#D97706' }}>
                          {m.onTimeRatePercent}%
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'right', color: Number(m.reopenedCount) > 0 ? '#DC2626' : SURFACE.textMuted, fontWeight: Number(m.reopenedCount) > 0 ? 700 : 400 }}>
                          {m.reopenedCount}
                        </td>
                        <td style={{ padding: '14px 16px', textAlign: 'center', color: SURFACE.textMuted, fontSize: 16 }}>
                          ›
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {/* Sổ ghi công Modal */}
      {showLedger && (
        <ContributionLedgerModal
          projectId={projectId}
          sprintId={selectedSprintId}
          isLeader={isLeader}
          onOpenAdjust={() => setShowAdjust(true)}
          onClose={() => setShowLedger(false)}
        />
      )}

      {/* Điều chỉnh điểm Modal */}
      {showAdjust && (
        <ManualAdjustmentModal
          projectId={projectId}
          sprintId={selectedSprintId}
          members={members}
          onClose={() => setShowAdjust(false)}
          onSuccess={loadReport}
        />
      )}

      {/* Chi tiết thành viên Drawer */}
      {selectedMemberId && (
        <MemberContributionDrawer
          projectId={projectId}
          userId={selectedMemberId}
          sprintId={selectedSprintId}
          onClose={() => setSelectedMemberId(null)}
        />
      )}
    </div>
  );
}
