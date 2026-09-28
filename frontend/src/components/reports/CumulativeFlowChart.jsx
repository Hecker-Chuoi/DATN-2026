import {
  Area, AreaChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { AXIS, GRID, LEGEND_STYLE, CHART_HEIGHT } from '../../constants/chartTheme.js';
import ChartTooltip from './ChartTooltip.jsx';
import { EmptyChart } from './BurndownChart.jsx';

/**
 * CFD: to chong tu duoi len theo thu tu DONE -> IN_PROGRESS -> TODO
 * (chuan cua bieu do Cumulative Flow).
 */
export default function CumulativeFlowChart({ rows, buckets }) {
  if (!rows?.length) {
    return <EmptyChart message="Chưa có dữ liệu Cumulative Flow." />;
  }
  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <AreaChart data={rows} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
        <CartesianGrid {...GRID} />
        <XAxis dataKey="label" {...AXIS} />
        <YAxis {...AXIS} allowDecimals={false} />
        <Tooltip content={<ChartTooltip />} />
        <Legend wrapperStyle={LEGEND_STYLE} />
        {buckets.map((b) => (
          <Area
            key={b.key}
            type="monotone"
            dataKey={b.key}
            name={b.name}
            stackId="1"
            fill={b.colorHex}
            stroke={b.colorHex}
            fillOpacity={1}
          />
        ))}
      </AreaChart>
    </ResponsiveContainer>
  );
}
