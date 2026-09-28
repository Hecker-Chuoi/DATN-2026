/**
 * reportAdapters.js - Chuan hoa payload bao cao tu backend sang dang Recharts doc duoc.
 *
 * Viet phong thu (chap nhan thieu truong, kieu null) de mot thay doi nho o backend
 * khong lam vo ca man hinh bao cao.
 */

/** Nhan truc hoanh: "Ngày N" + ngay/thang, de doc hon ISO date. */
function dayLabel(dateStr, index) {
  if (!dateStr) return `Ngày ${index + 1}`;
  const d = new Date(dateStr);
  if (Number.isNaN(d.getTime())) return `Ngày ${index + 1}`;
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
}

const num = (v) => (v == null ? 0 : Number(v));

export function toBurndownRows(dto) {
  const points = dto?.points || [];
  return points.map((p, i) => ({
    label: dayLabel(p.date, i),
    date: p.date,
    actual: num(p.remainingPoints),
    ideal: num(p.idealRemaining),
    scope: num(p.scopePoints),
    completed: num(p.completedPoints),
    isToday: !!p.isToday,
  }));
}

export function toVelocityRows(dto) {
  const sprints = dto?.sprints || [];
  return sprints.map((s) => ({
    label: s.sprintName || 'Sprint',
    committed: num(s.committedStoryPoints),
    completed: num(s.completedStoryPoints),
    estimated: !!s.estimated,
  }));
}

export function toCfdRows(dto) {
  const points = dto?.points || [];
  const buckets = dto?.buckets || [];
  return points.map((p, i) => {
    const row = { label: dayLabel(p.date, i), date: p.date, total: num(p.total) };
    for (const b of buckets) {
      row[b.key] = num(p.counts?.[b.key]);
    }
    return row;
  });
}

/**
 * Thay doi pham vi = pham vi hien tai tru pham vi luc bat dau.
 * Duong (+) nghia la Sprint bi them viec giua chung.
 */
export function scopeChangeOf(rows) {
  if (!rows?.length) return 0;
  return Math.round((rows[rows.length - 1].scope - rows[0].scope) * 10) / 10;
}
