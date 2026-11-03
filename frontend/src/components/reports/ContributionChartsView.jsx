import React from 'react';
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Tooltip,
  Legend,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  ReferenceLine,
} from 'recharts';
import { SURFACE } from '../../constants/jiraTokens.js';
import { AXIS, GRID, TOOLTIP_BOX, LEGEND_STYLE } from '../../constants/chartTheme.js';

const COLORS = [
  '#2563EB', // Blue
  '#10B981', // Green
  '#F59E0B', // Amber
  '#8B5CF6', // Purple
  '#EC4899', // Pink
  '#06B6D4', // Cyan
  '#F97316', // Orange
  '#6366F1', // Indigo
  '#14B8A6', // Teal
  '#64748B', // Slate
];

function CustomPieTooltip({ active, payload }) {
  if (active && payload && payload.length) {
    const item = payload[0];
    const data = item.payload;
    return (
      <div style={TOOLTIP_BOX}>
        <div style={{ fontWeight: 700, color: SURFACE.text, marginBottom: 4 }}>{data.name}</div>
        <div style={{ color: '#2563EB', fontWeight: 600 }}>Điểm: {data.value}</div>
        <div style={{ color: SURFACE.textMuted, fontSize: 11 }}>Tỉ trọng: {data.percentage}%</div>
      </div>
    );
  }
  return null;
}

function CustomBarTooltip({ active, payload, label }) {
  if (active && payload && payload.length) {
    return (
      <div style={TOOLTIP_BOX}>
        <div style={{ fontWeight: 700, color: SURFACE.text, marginBottom: 6 }}>{label}</div>
        {payload.map((entry, idx) => (
          <div
            key={`bar-tooltip-${idx}`}
            style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, marginTop: 2 }}
          >
            <span
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                backgroundColor: entry.color,
                display: 'inline-block',
              }}
            />
            <span style={{ color: SURFACE.textMuted }}>{entry.name}:</span>
            <span style={{ fontWeight: 600, color: SURFACE.text }}>{entry.value}</span>
          </div>
        ))}
      </div>
    );
  }
  return null;
}

export default function ContributionChartsView({ members = [], totalPoints = 0, isLeader = true }) {
  const validMembers = members || [];

  // Data for Donut Chart (Distribution of Points)
  let pieData = [];
  if (!isLeader && validMembers.length > 0) {
    const me = validMembers[0];
    const myPoints = Number(me.points) || 0;
    const total = Number(totalPoints) || 0;
    const otherPoints = Math.max(0, total - myPoints);
    const myPct = total > 0 ? ((myPoints / total) * 100).toFixed(1) : '0';
    const otherPct = total > 0 ? ((otherPoints / total) * 100).toFixed(1) : '0';

    pieData = [
      {
        name: `${me.name} (Đóng góp của bạn)`,
        value: myPoints,
        percentage: myPct,
        fill: '#2563EB',
      },
    ];
    if (otherPoints > 0) {
      pieData.push({
        name: 'Thành viên khác trong dự án',
        value: otherPoints,
        percentage: otherPct,
        fill: '#CBD5E1',
      });
    }
  } else {
    pieData = validMembers
      .filter((m) => Number(m.points) > 0)
      .map((m, index) => ({
        name: m.name,
        value: Number(m.points),
        percentage: m.percentage,
        fill: COLORS[index % COLORS.length],
      }));
  }

  // Data for Grouped Bar Chart (Workload & Quality)
  const barData = validMembers.map((m) => ({
    name: m.name?.length > 12 ? m.name.substring(0, 10) + '...' : m.name,
    fullName: m.name,
    points: Number(m.points) || 0,
    completedTasks: m.completedTasks || 0,
    reopenedCount: m.reopenedCount || 0,
  }));

  // Data for On-Time Rate Chart
  const qualityData = validMembers.map((m) => ({
    name: m.name?.length > 12 ? m.name.substring(0, 10) + '...' : m.name,
    fullName: m.name,
    onTimeRate: Number(m.onTimeRatePercent) || 0,
    bugsReported: m.bugsReported || 0,
    discussionCount: m.discussionCount || 0,
  }));

  if (validMembers.length === 0) {
    return (
      <div
        style={{
          padding: 60,
          textAlign: 'center',
          color: SURFACE.textMuted,
          backgroundColor: '#FFFFFF',
          borderRadius: 10,
          border: `1px solid ${SURFACE.border}`,
        }}
      >
        Chưa có dữ liệu thành viên để lập biểu đồ.
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Row 1: Donut Chart & Workload Bar Chart */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: 16 }}>
        {/* Chart 1: Donut Chart - Tỉ trọng đóng góp điểm */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 10,
            border: `1px solid ${SURFACE.border}`,
            padding: '20px 24px',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <div style={{ marginBottom: 16 }}>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>
              {!isLeader ? 'Tỉ trọng Đóng góp của Bạn' : 'Tỉ trọng Đóng góp Điểm'}
            </h3>
            <p style={{ margin: '4px 0 0 0', fontSize: 12, color: SURFACE.textMuted }}>
              {!isLeader
                ? 'Đóng góp Story Points của bạn so với phần còn lại của dự án'
                : 'Phần trăm Story Points hoàn thành của từng thành viên'}
            </p>
          </div>

          {pieData.length > 0 ? (
            <div style={{ width: '100%', height: 280, position: 'relative' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Tooltip content={<CustomPieTooltip />} />
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={65}
                    outerRadius={100}
                    paddingAngle={3}
                    dataKey="value"
                  >
                    {pieData.map((entry, index) => (
                      <Cell
                        key={`cell-${index}`}
                        fill={entry.fill || COLORS[index % COLORS.length]}
                      />
                    ))}
                  </Pie>
                  <Legend wrapperStyle={LEGEND_STYLE} />
                </PieChart>
              </ResponsiveContainer>
              {/* Center Stat */}
              <div
                style={{
                  position: 'absolute',
                  top: '44%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                  textAlign: 'center',
                  pointerEvents: 'none',
                }}
              >
                <div style={{ fontSize: 20, fontWeight: 800, color: '#0F172A' }}>{totalPoints}</div>
                <div style={{ fontSize: 11, color: SURFACE.textMuted }}>Tổng điểm</div>
              </div>
            </div>
          ) : (
            <div
              style={{
                height: 280,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: SURFACE.textMuted,
                fontSize: 13,
                fontStyle: 'italic',
              }}
            >
              Chưa có dữ liệu đóng góp điểm trong chu kỳ này.
            </div>
          )}
        </div>

        {/* Chart 2: Grouped Bar Chart - Năng suất & Chất lượng */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 10,
            border: `1px solid ${SURFACE.border}`,
            padding: '20px 24px',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <div style={{ marginBottom: 16 }}>
            <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>
              {!isLeader ? 'Năng suất & Chất lượng của Bạn' : 'Năng suất & Chất lượng Bàn giao'}
            </h3>
            <p style={{ margin: '4px 0 0 0', fontSize: 12, color: SURFACE.textMuted }}>
              {!isLeader
                ? 'Tương quan Điểm hoàn thành, Việc hoàn thành và Số lần bị mở lại của bạn'
                : 'Tương quan Điểm hoàn thành, Việc hoàn thành và Số lần bị mở lại'}
            </p>
          </div>

          <div style={{ width: '100%', height: 280 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={barData} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                <CartesianGrid {...GRID} />
                <XAxis dataKey="name" {...AXIS} />
                <YAxis {...AXIS} />
                <Tooltip content={<CustomBarTooltip />} />
                <Legend wrapperStyle={LEGEND_STYLE} />
                <Bar dataKey="points" name="Điểm xong" fill="#2563EB" radius={[4, 4, 0, 0]} />
                <Bar dataKey="completedTasks" name="Việc xong" fill="#10B981" radius={[4, 4, 0, 0]} />
                <Bar dataKey="reopenedCount" name="Bị mở lại" fill="#EF4444" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Row 2: Quality & Engagement Index */}
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 10,
          border: `1px solid ${SURFACE.border}`,
          padding: '20px 24px',
        }}
      >
        <div style={{ marginBottom: 16 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>
            {!isLeader ? 'Tỉ lệ Đúng hạn & Tinh thần Đồng đội của Bạn' : 'Tỉ lệ Đúng hạn & Tinh thần Đồng đội'}
          </h3>
          <p style={{ margin: '4px 0 0 0', fontSize: 12, color: SURFACE.textMuted }}>
            {!isLeader
              ? 'Mức độ hoàn thành đúng cam kết (%) và hoạt động thảo luận / báo lỗi của bạn'
              : 'Mức độ hoàn thành đúng cam kết (%) và hoạt động thảo luận / báo lỗi hỗ trợ nhóm'}
          </p>
        </div>

        <div style={{ width: '100%', height: 260 }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={qualityData} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
              <CartesianGrid {...GRID} />
              <XAxis dataKey="name" {...AXIS} />
              <YAxis {...AXIS} domain={[0, 100]} unit="%" />
              <Tooltip content={<CustomBarTooltip />} />
              <Legend wrapperStyle={LEGEND_STYLE} />
              <ReferenceLine y={80} stroke="#F59E0B" strokeDasharray="3 3" label={{ value: 'Mục tiêu 80%', fill: '#B45309', fontSize: 11 }} />
              <Bar dataKey="onTimeRate" name="Đúng hạn (%)" fill="#059669" radius={[4, 4, 0, 0]} />
              <Bar dataKey="discussionCount" name="Thảo luận (comments)" fill="#6366F1" radius={[4, 4, 0, 0]} />
              <Bar dataKey="bugsReported" name="Báo lỗi (bugs)" fill="#F97316" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
