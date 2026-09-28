import { useEffect, useMemo, useState } from 'react';
import { useProjectContext } from '../context/ProjectContext.jsx';
import { sprintApi } from '../api.js';
import { SURFACE } from '../constants/jiraTokens.js';
import { toBurndownRows, toVelocityRows, toCfdRows, scopeChangeOf } from '../utils/reportAdapters.js';
import ReportMetricCard from '../components/reports/ReportMetricCard.jsx';
import BurndownChart from '../components/reports/BurndownChart.jsx';
import VelocityChart from '../components/reports/VelocityChart.jsx';
import CumulativeFlowChart from '../components/reports/CumulativeFlowChart.jsx';
import SprintStateBadge from '../components/sprints/SprintStateBadge.jsx';

const TABS = [
  { id: 'burndown', label: 'Burndown' },
  { id: 'velocity', label: 'Velocity' },
  { id: 'cfd', label: 'Cumulative Flow' },
];

export default function ProjectReportsPage() {
  const { projectId } = useProjectContext();

  const [tab, setTab] = useState('burndown');
  const [sprints, setSprints] = useState([]);
  const [sprintId, setSprintId] = useState(null);
  const [burndown, setBurndown] = useState(null);
  const [velocity, setVelocity] = useState(null);
  const [cfd, setCfd] = useState(null);
  const [loading, setLoading] = useState(true);

  // Danh sach Sprint cho o chon (uu tien Sprint dang chay).
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await sprintApi.listByProject(projectId);
        const list = res?.data || [];
        if (cancelled) return;
        setSprints(list);
        const active = list.find((s) => s.state === 'ACTIVE');
        const closed = list.filter((s) => s.state === 'CLOSED');
        setSprintId(active?.id || closed[0]?.id || list[0]?.id || null);
      } catch (err) {
        console.warn('Không tải được danh sách Sprint:', err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId]);

  // Velocity khong phu thuoc Sprint dang chon.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await sprintApi.getVelocity(projectId);
        if (!cancelled) setVelocity(res?.data ?? null);
      } catch (err) {
        console.warn('Không tải được Velocity:', err);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId]);

  useEffect(() => {
    if (!sprintId) return;
    let cancelled = false;
    (async () => {
      try {
        const [b, c] = await Promise.all([
          sprintApi.getBurndown(sprintId).catch(() => null),
          sprintApi.getCfd(projectId, { sprintId }).catch(() => null),
        ]);
        if (cancelled) return;
        setBurndown(b?.data ?? null);
        setCfd(c?.data ?? null);
      } catch (err) {
        console.warn('Không tải được báo cáo:', err);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId, sprintId]);

  const burndownRows = useMemo(() => toBurndownRows(burndown), [burndown]);
  const velocityRows = useMemo(() => toVelocityRows(velocity), [velocity]);
  const cfdRows = useMemo(() => toCfdRows(cfd), [cfd]);

  if (loading) return <div style={centered}>Đang tải báo cáo...</div>;
  if (!sprints.length) {
    return <div style={centered}>Dự án chưa có Sprint nào để lập báo cáo.</div>;
  }

  const selected = sprints.find((s) => s.id === sprintId);
  const scopeChange = scopeChangeOf(burndownRows);

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '16px 24px 40px 24px' }}>
      {/* Chon Sprint + tab bieu do */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 16 }}>
        <select
          value={sprintId || ''}
          onChange={(e) => setSprintId(e.target.value)}
          style={{
            padding: '6px 10px', borderRadius: 6, border: `1px solid ${SURFACE.border}`,
            fontSize: 13, backgroundColor: '#FFFFFF', color: SURFACE.text, maxWidth: 260,
          }}
        >
          {sprints.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
        {selected && <SprintStateBadge state={selected.state} />}

        <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
          {TABS.map((t) => {
            const active = tab === t.id;
            return (
              <button
                key={t.id}
                onClick={() => setTab(t.id)}
                style={{
                  padding: '6px 14px', borderRadius: 999, fontSize: 12,
                  fontWeight: active ? 600 : 500, cursor: 'pointer',
                  border: `1px solid ${active ? '#93C5FD' : SURFACE.border}`,
                  backgroundColor: active ? '#EFF6FF' : '#FFFFFF',
                  color: active ? '#1D4ED8' : SURFACE.textMuted,
                }}
              >
                {t.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* The so lieu */}
      {tab === 'burndown' && burndown && (
        <div style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap' }}>
          <ReportMetricCard
            label="Story Points hoàn thành"
            value={`${num(burndown.completedStoryPoints)} / ${num(burndown.committedStoryPoints)}`}
            tone="positive"
          />
          <ReportMetricCard
            label="Thay đổi phạm vi"
            value={`${scopeChange > 0 ? '+' : ''}${scopeChange} SP`}
            tone={scopeChange > 0 ? 'warning' : 'neutral'}
          />
          <ReportMetricCard
            label="Công việc hoàn thành"
            value={`${num(burndown.completedItems)} / ${num(burndown.totalItems)}`}
            tone="info"
          />
          <ReportMetricCard
            label="Còn lại"
            value={`${num(burndown.remainingStoryPoints)} SP`}
          />
        </div>
      )}

      {tab === 'velocity' && velocity && (
        <div style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap' }}>
          <ReportMetricCard label="Velocity trung bình" value={`${num(velocity.averageVelocity)} SP`} tone="positive" />
          <ReportMetricCard label="Cam kết trung bình" value={`${num(velocity.averageCommitment)} SP`} />
          <ReportMetricCard
            label="Độ tin cậy cam kết"
            value={`${num(velocity.commitmentReliabilityPercent)}%`}
            tone="info"
            sub="Hoàn thành / Cam kết"
          />
          <ReportMetricCard label="Số Sprint đã đóng" value={num(velocity.sprintCount)} />
        </div>
      )}

      {/* Bieu do */}
      <div style={chartBox}>
        {tab === 'burndown' && (
          <>
            <ChartTitle
              title={`Burndown — ${selected?.name || ''}`}
              note={sourceNote(burndown?.source)}
            />
            <BurndownChart rows={burndownRows} />
          </>
        )}
        {tab === 'velocity' && (
          <>
            <ChartTitle title="Velocity qua các Sprint đã đóng" />
            <VelocityChart rows={velocityRows} average={num(velocity?.averageVelocity)} />
          </>
        )}
        {tab === 'cfd' && (
          <>
            <ChartTitle title={`Cumulative Flow — ${selected?.name || ''}`} />
            <CumulativeFlowChart rows={cfdRows} buckets={cfd?.buckets || []} />
          </>
        )}
      </div>
    </div>
  );
}

function ChartTitle({ title, note }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, flexWrap: 'wrap' }}>
      <h3 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: SURFACE.text }}>{title}</h3>
      {note && (
        <span
          style={{
            fontSize: 11, color: SURFACE.textMuted, backgroundColor: SURFACE.track,
            padding: '2px 8px', borderRadius: 999,
          }}
        >
          {note}
        </span>
      )}
    </div>
  );
}

/** Noi ro so lieu den tu dau de nguoi doc biet muc do tin cay. */
function sourceNote(source) {
  switch (source) {
    case 'SNAPSHOT': return 'Số liệu đã chốt khi đóng Sprint';
    case 'RECONSTRUCTED': return 'Dựng lại từ nhật ký thay đổi (Sprint đang chạy)';
    case 'RECONSTRUCTED_LEGACY': return 'Dựng lại từ nhật ký — Sprint đóng trước khi có báo cáo';
    default: return null;
  }
}

const num = (v) => (v == null ? 0 : Number(v));
const centered = { padding: 48, textAlign: 'center', color: SURFACE.textMuted, fontSize: 13 };
const chartBox = {
  border: `1px solid ${SURFACE.border}`,
  borderRadius: 10,
  padding: '14px 16px',
  backgroundColor: '#FFFFFF',
};
