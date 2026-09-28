import { TOOLTIP_BOX } from '../../constants/chartTheme.js';

/**
 * Tooltip tuy bien - tooltip mac dinh cua Recharts khong the style sang phong cach Jira
 * neu khong co CSS, ma du an nay khong dung Tailwind.
 */
export default function ChartTooltip({ active, payload, label, unit = '' }) {
  if (!active || !payload?.length) return null;
  return (
    <div style={TOOLTIP_BOX}>
      <div style={{ fontWeight: 700, marginBottom: 4 }}>{label}</div>
      {payload.map((p) => (
        <div key={p.dataKey} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ width: 8, height: 8, borderRadius: 2, backgroundColor: p.color, display: 'inline-block' }} />
          <span style={{ flex: 1 }}>{p.name}</span>
          <b>{formatValue(p.value)}{unit}</b>
        </div>
      ))}
    </div>
  );
}

function formatValue(v) {
  const n = Number(v);
  if (Number.isNaN(n)) return v;
  return Number.isInteger(n) ? n : n.toFixed(1);
}
