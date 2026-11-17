import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Award, TrendingUp, TrendingDown, Layers, BarChart3 } from 'lucide-react';
import { useProjectContext } from '../context/ProjectContext.jsx';
import { sprintApi } from '../api.js';
import { SURFACE } from '../constants/jiraTokens.js';
import { toBurndownRows, toVelocityRows, toCfdRows, scopeChangeOf } from '../utils/reportAdapters.js';
import ReportMetricCard from '../components/reports/ReportMetricCard.jsx';
import BurndownChart from '../components/reports/BurndownChart.jsx';
import VelocityChart from '../components/reports/VelocityChart.jsx';
import CumulativeFlowChart from '../components/reports/CumulativeFlowChart.jsx';
import SprintStateBadge from '../components/sprints/SprintStateBadge.jsx';
import ContributionReportTab from '../components/reports/ContributionReportTab.jsx';
import CustomSelect from '../components/common/CustomSelect.jsx';
import SegmentedTabs from '../components/common/SegmentedTabs.jsx';

/**
 * Man "Bao cao" cua du an, gom 2 tab:
 *  - Quan ly dong gop: ai lam duoc gi (diem, viec xong, dung han...) - truoc day la man "Dong gop" rieng.
 *  - Quan ly tien do: du an chay nhanh/cham the nao (Tien do chu ky, Phan bo luong trang thai, Nang suat).
 * Tab va bieu do dang xem nam tren URL (?tab=...&view=...) de deep-link duoc.
 */
const REPORT_TABS = [
  { id: 'contributions', label: 'Quản lý đóng góp', icon: Award },
  { id: 'progress', label: 'Quản lý tiến độ', icon: TrendingUp },
];

// Hai bieu do dau xem theo MOT chu ky; Nang suat tinh tren moi chu ky da hoan tat nen dat cuoi.
const PROGRESS_VIEWS = [
  { id: 'burndown', label: 'Tiến độ chu kỳ', icon: TrendingDown },
  { id: 'cfd', label: 'Phân bổ luồng trạng thái', icon: Layers },
  { id: 'velocity', label: 'Năng suất làm việc', icon: BarChart3 },
];
const PROGRESS_VIEW_IDS = PROGRESS_VIEWS.map((v) => v.id);

function readTabState(searchParams) {
  const rawTab = searchParams.get('tab');
  // Link cu truoc khi gop man: ?tab=burndown|velocity|cfd -> tab Tien do, mo dung bieu do.
  if (PROGRESS_VIEW_IDS.includes(rawTab)) return { tab: 'progress', view: rawTab };
  const rawView = searchParams.get('view');
  return {
    tab: rawTab === 'progress' ? 'progress' : 'contributions',
    view: PROGRESS_VIEW_IDS.includes(rawView) ? rawView : 'burndown',
  };
}

export default function ProjectReportsPage() {
  const { projectId, isAdmin } = useProjectContext();
  const [searchParams, setSearchParams] = useSearchParams();
  const { tab, view } = readTabState(searchParams);

  const [sprints, setSprints] = useState([]);
  const [defaultSprintId, setDefaultSprintId] = useState(null);
  const [loading, setLoading] = useState(true);
  // ?sprint=<id>: mo thang bao cao cua mot chu ky (vd. nut "Xem báo cáo" o khu giai doan da hoan tat).
  const [requestedSprintId] = useState(() => searchParams.get('sprint'));

  // Danh sach chu ky dung chung cho ca 2 tab (uu tien chu ky duoc yeu cau, roi dang chay, roi vua dong).
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await sprintApi.listByProject(projectId);
        const list = res?.data || [];
        if (cancelled) return;
        setSprints(list);
        const requested = list.find((s) => s.id === requestedSprintId);
        const active = list.find((s) => s.state === 'ACTIVE');
        const closed = list.filter((s) => s.state === 'CLOSED');
        setDefaultSprintId(requested?.id || active?.id || closed[0]?.id || list[0]?.id || null);
      } catch (err) {
        console.warn('Không tải được danh sách chu kỳ:', err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId, requestedSprintId]);

  const goTo =(params) => setSearchParams(params, { replace: true });
  const openTab = (id) => goTo(id === 'progress' ? { tab: id, view } : { tab: id });

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '4px 24px 40px 24px' }}>
      <ReportTabBar value={tab} onChange={openTab} />

      {loading ? (
        <div style={centered}>Đang tải báo cáo...</div>
      ) : tab === 'contributions' ? (
        <ContributionReportTab
          projectId={projectId}
          sprintId={defaultSprintId}
          sprints={sprints}
          isLeader={isAdmin}
        />
      ) : (
        <ProgressReport
          projectId={projectId}
          sprints={sprints}
          defaultSprintId={defaultSprintId}
          view={view}
          onViewChange={(v) => goTo({ tab: 'progress', view: v })}
          onOpenContributions={() => openTab('contributions')}
        />
      )}
    </div>
  );
}

/** Thanh tab chinh (gach chan) - khac kieu "pill" cua thanh dieu huong du an phia tren. */
function ReportTabBar({ value, onChange }) {
  return (
    <div
      role="tablist"
      style={{
        display: 'flex',
        gap: 24,
        borderBottom: `1px solid ${SURFACE.border}`,
        marginBottom: 16,
      }}
    >
      {REPORT_TABS.map(({ id, label, icon: Icon }) => {
        const active = value === id;
        return (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(id)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '10px 2px',
              marginBottom: -1,
              background: 'transparent',
              border: 'none',
              borderBottom: `2px solid ${active ? SURFACE.primary : 'transparent'}`,
              color: active ? SURFACE.primary : SURFACE.textMuted,
              fontSize: 14,
              fontWeight: active ? 600 : 500,
              cursor: 'pointer',
              transition: 'color 0.15s ease, border-color 0.15s ease',
            }}
          >
            <Icon size={15} />
            <span>{label}</span>
          </button>
        );
      })}
    </div>
  );
}

/** Tab "Quan ly tien do": chon chu ky + chon bieu do -> the so lieu -> bieu do. */
function ProgressReport({ projectId, sprints, defaultSprintId, view, onViewChange, onOpenContributions }) {
  const [sprintId, setSprintId] = useState(defaultSprintId);
  const [burndown, setBurndown] = useState(null);
  const [velocity, setVelocity] = useState(null);
  const [cfd, setCfd] = useState(null);

  // Nang suat khong phu thuoc chu ky dang chon.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await sprintApi.getVelocity(projectId);
        if (!cancelled) setVelocity(res?.data ?? null);
      } catch (err) {
        console.warn('Không tải được Năng suất:', err);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId]);

  useEffect(() => {
    if (!sprintId) return;
    let cancelled = false;
    (async () => {
      const [b, c] = await Promise.all([
        sprintApi.getBurndown(sprintId).catch(() => null),
        sprintApi.getCfd(projectId, { sprintId }).catch(() => null),
      ]);
      if (cancelled) return;
      setBurndown(b?.data ?? null);
      setCfd(c?.data ?? null);
    })();
    return () => { cancelled = true; };
  }, [projectId, sprintId]);

  const burndownRows = useMemo(() => toBurndownRows(burndown), [burndown]);
  const velocityRows = useMemo(() => toVelocityRows(velocity), [velocity]);
  const cfdRows = useMemo(() => toCfdRows(cfd), [cfd]);

  if (!sprints.length) {
    return (
      <div style={centered}>
        Dự án chưa có chu kỳ (Sprint) nào để lập báo cáo tiến độ.
        <br />
        Bạn vẫn có thể xem thống kê đóng góp của các thành viên ở tab{' '}
        <button type="button" onClick={onOpenContributions} style={linkButton}>
          Quản lý đóng góp
        </button>
        .
      </div>
    );
  }

  const selected = sprints.find((s) => s.id === sprintId);
  const scopeChange = scopeChangeOf(burndownRows);
  const sprintScoped = view !== 'velocity';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 1. Bo loc (trai) + chon bieu do (phai) */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, minHeight: 34 }}>
          {sprintScoped ? (
            <>
              <span style={{ fontSize: 13, color: SURFACE.textMuted }}>Xem theo:</span>
              <CustomSelect
                value={sprintId || ''}
                onChange={(val) => setSprintId(val)}
                options={sprints.map((s) => ({ value: s.id, label: s.name }))}
                width={240}
                placeholder="Chọn chu kỳ..."
              />
              {selected && <SprintStateBadge state={selected.state} />}
            </>
          ) : (
            <span style={{ fontSize: 13, color: SURFACE.textMuted }}>
              Tính trên tất cả chu kỳ đã hoàn tất
            </span>
          )}
        </div>

        <SegmentedTabs items={PROGRESS_VIEWS} value={view} onChange={onViewChange} />
      </div>

      {/* 2. The so lieu cua bieu do dang xem */}
      {view === 'burndown' && burndown && (
        <div style={metricRow}>
          <ReportMetricCard
            label="Điểm ước lượng hoàn thành"
            value={`${num(burndown.completedStoryPoints)} / ${num(burndown.committedStoryPoints)}`}
            tone="positive"
          />
          <ReportMetricCard
            label="Thay đổi phạm vi"
            value={`${scopeChange > 0 ? '+' : ''}${scopeChange} điểm`}
            tone={scopeChange > 0 ? 'warning' : 'neutral'}
          />
          <ReportMetricCard
            label="Công việc hoàn thành"
            value={`${num(burndown.completedItems)} / ${num(burndown.totalItems)}`}
            tone="info"
          />
          <ReportMetricCard label="Còn lại" value={`${num(burndown.remainingStoryPoints)} điểm`} />
        </div>
      )}

      {view === 'velocity' && velocity && (
        <div style={metricRow}>
          <ReportMetricCard label="Năng suất trung bình" value={`${num(velocity.averageVelocity)} điểm`} tone="positive" />
          <ReportMetricCard label="Cam kết trung bình" value={`${num(velocity.averageCommitment)} điểm`} />
          <ReportMetricCard
            label="Độ tin cậy cam kết"
            value={`${num(velocity.commitmentReliabilityPercent)}%`}
            tone="info"
            sub="Hoàn thành / Cam kết"
          />
          <ReportMetricCard label="Số chu kỳ đã hoàn tất" value={num(velocity.sprintCount)} />
        </div>
      )}

      {/* 3. Bieu do */}
      <div style={chartBox}>
        {view === 'burndown' && (
          <>
            <ChartTitle title={`Tiến độ — ${selected?.name || ''}`} note={sourceNote(burndown?.source)} />
            <BurndownChart rows={burndownRows} />
          </>
        )}
        {view === 'cfd' && (
          <>
            <ChartTitle title={`Phân bổ luồng trạng thái — ${selected?.name || ''}`} />
            <CumulativeFlowChart rows={cfdRows} buckets={cfd?.buckets || []} />
          </>
        )}
        {view === 'velocity' && (
          <>
            <ChartTitle title="Năng suất qua các chu kỳ đã hoàn tất" />
            <VelocityChart rows={velocityRows} average={num(velocity?.averageVelocity)} />
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
    case 'SNAPSHOT': return 'Số liệu đã chốt khi đóng chu kỳ';
    case 'RECONSTRUCTED': return 'Dựng lại từ nhật ký thay đổi (Chu kỳ đang chạy)';
    case 'RECONSTRUCTED_LEGACY': return 'Dựng lại từ nhật ký — Chu kỳ đóng trước khi có báo cáo';
    default: return null;
  }
}

const num = (v) => (v == null ? 0 : Number(v));
const centered = { padding: 48, textAlign: 'center', color: SURFACE.textMuted, fontSize: 13, lineHeight: 1.7 };
const metricRow = { display: 'flex', gap: 10, flexWrap: 'wrap' };
const chartBox = {
  border: `1px solid ${SURFACE.border}`,
  borderRadius: 10,
  padding: '14px 16px',
  backgroundColor: '#FFFFFF',
};
const linkButton = {
  background: 'none',
  border: 'none',
  padding: 0,
  color: SURFACE.primary,
  fontWeight: 600,
  fontSize: 'inherit',
  cursor: 'pointer',
  textDecoration: 'underline',
};
