import {
  AlarmClock,
  Award,
  BadgeCheck,
  ClipboardList,
  Flag,
  FolderKanban,
  Info,
  CircleCheck,
  TriangleAlert,
  OctagonAlert,
  Link2,
  MessageSquare,
  Users,
} from 'lucide-react';

/**
 * Định dạng thông báo dùng chung cho chuông, bảng thông báo và trang thông báo.
 * Quy ước: docs/services/notification/1_DANH_MUC_LOAI_THONG_BAO.md §1.
 */

/** Nhóm thông báo → biểu tượng + tên (bộ lọc). */
export const CATEGORY_META = {
  TASK: { label: 'Công việc', Icon: ClipboardList },
  DEPENDENCY: { label: 'Phụ thuộc', Icon: Link2 },
  APPROVAL: { label: 'Xác nhận hoàn thành', Icon: BadgeCheck },
  COMMENT: { label: 'Bình luận', Icon: MessageSquare },
  SPRINT: { label: 'Chu kỳ / Giai đoạn', Icon: Flag },
  REMINDER: { label: 'Nhắc hạn', Icon: AlarmClock },
  MEMBER: { label: 'Thành viên & quyền', Icon: Users },
  PROJECT: { label: 'Dự án', Icon: FolderKanban },
  CONTRIBUTION: { label: 'Đóng góp', Icon: Award },
};

/** Mức độ → màu (luôn đi kèm nhãn chữ + biểu tượng, không dựa riêng vào màu). */
export const SEVERITY_META = {
  INFO: { label: 'Thông thường', color: '#2563EB', soft: '#EFF6FF', ink: '#1D4ED8', Icon: Info },
  SUCCESS: { label: 'Tích cực', color: '#16A34A', soft: '#F0FDF4', ink: '#15803D', Icon: CircleCheck },
  WARNING: { label: 'Cảnh báo', color: '#D97706', soft: '#FFFBEB', ink: '#B45309', Icon: TriangleAlert },
  URGENT: { label: 'Khẩn cấp', color: '#DC2626', soft: '#FEF2F2', ink: '#B91C1C', Icon: OctagonAlert },
};

export const ACTION_COLOR = { color: '#7C3AED', soft: '#F5F3FF', ink: '#6D28D9' };

export const FILTERS = [
  { id: 'all', label: 'Tất cả' },
  { id: 'unread', label: 'Chưa đọc' },
  { id: 'action', label: 'Cần xử lý' },
];

const ENTITIES = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'" };
const decode = (s) => s.replace(/&(amp|lt|gt|quot|#39);/g, (m) => ENTITIES[m] || m);

/**
 * Tiêu đề do server dựng sẵn: đã escape HTML, chỉ có thẻ <b>. Tách thành phần tử React,
 * KHÔNG dùng dangerouslySetInnerHTML.
 */
export function renderTitle(title) {
  if (!title) return null;
  const parts = [];
  const re = /<b>([\s\S]*?)<\/b>/g;
  let last = 0;
  let m;
  let key = 0;
  while ((m = re.exec(title)) !== null) {
    if (m.index > last) parts.push(decode(title.slice(last, m.index)));
    parts.push(<b key={key++} style={{ fontWeight: 650, color: '#0F172A' }}>{decode(m[1])}</b>);
    last = re.lastIndex;
  }
  if (last < title.length) parts.push(decode(title.slice(last).replace(/<\/?b>/g, '')));
  return parts;
}

/** Văn bản thuần của tiêu đề (dùng cho toast, aria-label). */
export function plainTitle(title) {
  return decode(String(title || '').replace(/<\/?b>/g, ''));
}

/** "vừa xong", "5 phút trước", "Hôm qua", "12/11"… */
export function formatRelative(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const diff = Math.floor((Date.now() - date.getTime()) / 1000);
  if (diff < 45) return 'vừa xong';
  const minutes = Math.floor(diff / 60);
  if (minutes < 60) return `${Math.max(1, minutes)} phút trước`;
  const hours = Math.floor(minutes / 60);
  const today = new Date();
  const isSameDay = date.toDateString() === today.toDateString();
  if (isSameDay) return `${hours} giờ trước`;
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  const hhmm = date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  if (date.toDateString() === yesterday.toDateString()) return `Hôm qua, ${hhmm}`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} ngày trước`;
  return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: date.getFullYear() === today.getFullYear() ? undefined : 'numeric' });
}

/** Thông báo có khớp bộ lọc đang xem hay không (để chèn thông báo mới tới qua WebSocket). */
export function matchesFilter(n, filter, category, projectId) {
  if (!n) return false;
  if (category && n.category !== category) return false;
  if (projectId && n.projectId !== projectId) return false;
  if (filter === 'unread') return !n.read;
  if (filter === 'action') return n.actionRequired && !n.actionResolved;
  return true;
}
