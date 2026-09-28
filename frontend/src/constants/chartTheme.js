/**
 * chartTheme.js - Style dung chung cho cac bieu do Recharts (Giai doan 6.3).
 *
 * Recharts nhan props/style object chu khong nhan class, va du an khong co Tailwind,
 * nen toan bo theme nam o day de 3 bieu do Burndown / Velocity / CFD nhin nhat quan.
 */
import { SURFACE, FONT_STACK } from './jiraTokens.js';

export const AXIS = {
  tick: { fill: SURFACE.textMuted, fontSize: 11, fontFamily: FONT_STACK },
  axisLine: { stroke: SURFACE.border },
  tickLine: false,
};

export const GRID = {
  stroke: SURFACE.track,
  strokeDasharray: '3 3',
  vertical: false,
};

export const TOOLTIP_BOX = {
  background: '#FFFFFF',
  border: `1px solid ${SURFACE.border}`,
  borderRadius: 8,
  boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
  fontSize: 12,
  fontFamily: FONT_STACK,
  padding: '8px 12px',
  color: SURFACE.text,
};

export const LEGEND_STYLE = {
  fontSize: 12,
  color: SURFACE.textMuted,
  fontFamily: FONT_STACK,
};

/** Mau tung chuoi du lieu, bam theo mo ta trong 5_WORK_FRONTEND_UI_JIRA_SPEC. */
export const SERIES = {
  ideal: '#DE350B',      // duong ly thuyet - do, net dut
  actual: '#0052CC',     // duong thuc te - xanh, bac thang
  scope: '#B3BAC5',      // vung thay doi pham vi - xam mo

  committed: '#DFE1E6',  // Velocity: cam ket
  completed: '#0052CC',  // Velocity: hoan thanh
  average: '#6B778C',

  // CFD - to tu duoi len: DONE -> IN_PROGRESS -> TODO
  done: '#ABF5D1',
  inProgress: '#B3D4FF',
  todo: '#DFE1E6',
};

export const CHART_HEIGHT = 340;
