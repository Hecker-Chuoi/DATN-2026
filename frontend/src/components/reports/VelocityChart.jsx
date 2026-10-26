import {
  Bar, BarChart, CartesianGrid, Legend, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { AXIS, GRID, LEGEND_STYLE, SERIES, CHART_HEIGHT } from '../../constants/chartTheme.js';
import ChartTooltip from './ChartTooltip.jsx';
import { EmptyChart } from './BurndownChart.jsx';

/** Velocity: so sanh cam ket vs hoan thanh qua cac Sprint da dong. */
export default function VelocityChart({ rows, average }) {
  if (!rows?.length) {
    return <EmptyChart message="Chưa có chu kỳ nào hoàn tất để tính năng suất làm việc." />;
  }
  return (
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <BarChart data={rows} margin={{ top: 8, right: 16, left: 0, bottom: 8 }}>
        <CartesianGrid {...GRID} />
        <XAxis dataKey="label" {...AXIS} />
        <YAxis {...AXIS} />
        <Tooltip content={<ChartTooltip />} />
        <Legend wrapperStyle={LEGEND_STYLE} />
        <Bar dataKey="committed" name="Cam kết" fill={SERIES.committed} radius={[4, 4, 0, 0]} barSize={22} />
        <Bar dataKey="completed" name="Hoàn thành" fill={SERIES.completed} radius={[4, 4, 0, 0]} barSize={22} />
        {average > 0 && (
          <ReferenceLine
            y={average} stroke={SERIES.average} strokeDasharray="4 4"
            label={{ value: `TB ${average}`, position: 'right', style: { fontSize: 11, fill: SERIES.average } }}
          />
        )}
      </BarChart>
    </ResponsiveContainer>
  );
}
