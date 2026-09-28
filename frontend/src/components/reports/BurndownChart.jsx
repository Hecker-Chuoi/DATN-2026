import {
  Area, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { AXIS, GRID, LEGEND_STYLE, SERIES, CHART_HEIGHT } from '../../constants/chartTheme.js';
import { SURFACE } from '../../constants/jiraTokens.js';
import ChartTooltip from './ChartTooltip.jsx';

/**
 * Burndown: duong ly thuyet (do, net dut) vs duong thuc te (xanh, BAC THANG),
 * kem vung xam the hien thay doi pham vi - dung mo ta trong UI spec.
 */
export default function BurndownChart({ rows }) {
  if (!rows?.length) {
    return <EmptyChart message="Chưa có dữ liệu Burndown cho Sprint này." />;
  }
  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <ComposedChart data={rows} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
        <CartesianGrid {...GRID} />
        <XAxis dataKey="label" {...AXIS} />
        <YAxis
          {...AXIS}
          label={{ value: 'Story Points', angle: -90, position: 'insideLeft', style: { fill: SURFACE.textMuted, fontSize: 11 } }}
        />
        <Tooltip content={<ChartTooltip />} />
        <Legend wrapperStyle={LEGEND_STYLE} />

        <Area
          type="stepAfter" dataKey="scope" name="Phạm vi"
          fill={SERIES.scope} fillOpacity={0.3} stroke="none" isAnimationActive={false}
        />
        <Line
          type="linear" dataKey="ideal" name="Lý thuyết"
          stroke={SERIES.ideal} strokeDasharray="5 4" strokeWidth={1.5} dot={false}
        />
        <Line
          type="stepAfter" dataKey="actual" name="Thực tế"
          stroke={SERIES.actual} strokeWidth={2.5}
          dot={{ r: 3, fill: '#FFFFFF', strokeWidth: 2 }} activeDot={{ r: 5 }}
        />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

export function EmptyChart({ message }) {
  return (
    <div
      style={{
        height: CHART_HEIGHT,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: SURFACE.textLight,
        fontSize: 13,
        fontStyle: 'italic',
        border: `1px dashed ${SURFACE.borderStrong}`,
        borderRadius: 8,
      }}
    >
      {message}
    </div>
  );
}
