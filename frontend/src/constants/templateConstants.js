/**
 * templateConstants.js - Nhãn & màu cho thư viện Dự án mẫu (khớp enum TemplateCategory của work-service).
 */

export const TEMPLATE_CATEGORIES = {
  SOFTWARE: { label: 'Phần mềm', color: '#2563EB', bg: '#EFF6FF' },
  BUSINESS: { label: 'Kinh doanh', color: '#0F766E', bg: '#F0FDFA' },
  MARKETING: { label: 'Tiếp thị', color: '#DB2777', bg: '#FDF2F8' },
  DESIGN: { label: 'Thiết kế', color: '#7C3AED', bg: '#F5F3FF' },
  OPERATIONS: { label: 'Vận hành', color: '#EA580C', bg: '#FFF7ED' },
  EDUCATION: { label: 'Giáo dục', color: '#16A34A', bg: '#F0FDF4' },
  OTHER: { label: 'Khác', color: '#475569', bg: '#F1F5F9' },
};

export function categoryToken(category) {
  return TEMPLATE_CATEGORIES[category] || TEMPLATE_CATEGORIES.OTHER;
}

export const PROJECT_TYPE_LABELS = {
  SOFTWARE_SCRUM: 'Theo chu kỳ / Theo giai đoạn',
  SOFTWARE_KANBAN: 'Bảng công việc liên tục',
  BUSINESS: 'Nghiệp vụ doanh nghiệp',
};

/** Cộng số ngày vào chuỗi yyyy-MM-dd, trả về yyyy-MM-dd (không lệch múi giờ). */
export function addDays(isoDate, days) {
  if (!isoDate || days == null) return '';
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + Number(days)));
  return date.toISOString().split('T')[0];
}

/** "Ngày 0", "Ngày 14"... - mốc tương đối trong mẫu. */
export function formatOffset(days) {
  if (days == null) return '—';
  return `Ngày ${days}`;
}
