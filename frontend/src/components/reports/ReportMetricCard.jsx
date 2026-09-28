import { SURFACE } from '../../constants/jiraTokens.js';

const TONES = {
  neutral: { bg: '#F8FAFC', border: '#E2E8F0', value: '#0F172A' },
  positive: { bg: '#E3FCEF', border: '#ABF5D1', value: '#00875A' },
  warning: { bg: '#FFFBEB', border: '#FCD34D', value: '#B45309' },
  info: { bg: '#DEEBFF', border: '#B3D4FF', value: '#0052CC' },
};

/** The so lieu tom tat phia tren bieu do (Story Points, Scope change, Issues Done...). */
export default function ReportMetricCard({ label, value, sub, tone = 'neutral' }) {
  const t = TONES[tone] || TONES.neutral;
  return (
    <div
      style={{
        flex: 1,
        minWidth: 150,
        padding: '12px 14px',
        borderRadius: 8,
        backgroundColor: t.bg,
        border: `1px solid ${t.border}`,
      }}
    >
      <div style={{ fontSize: 11, fontWeight: 600, color: SURFACE.textMuted, textTransform: 'uppercase', letterSpacing: 0.3 }}>
        {label}
      </div>
      <div style={{ fontSize: 22, fontWeight: 700, color: t.value, marginTop: 4 }}>{value}</div>
      {sub && <div style={{ fontSize: 11, color: SURFACE.textMuted, marginTop: 2 }}>{sub}</div>}
    </div>
  );
}
