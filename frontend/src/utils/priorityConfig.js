import { Flame, ArrowUp, Equal, ArrowDown, ChevronsDown } from 'lucide-react';

export const PRIORITY_CONFIG = {
  HIGHEST: {
    key: 'HIGHEST',
    label: 'Khẩn cấp',
    subLabel: 'Chặn tiến độ',
    color: '#DC2626',      // Red-600
    bgColor: '#FEF2F2',    // Red-50
    borderColor: '#FECACA',// Red-200
    icon: Flame,
  },
  HIGH: {
    key: 'HIGH',
    label: 'Cao',
    subLabel: 'Ưu tiên cao',
    color: '#EA580C',      // Orange-600
    bgColor: '#FFF7ED',    // Orange-50
    borderColor: '#FED7AA',// Orange-200
    icon: ArrowUp,
  },
  MEDIUM: {
    key: 'MEDIUM',
    label: 'Trung bình',
    subLabel: 'Bình thường',
    color: '#D97706',      // Amber-600
    bgColor: '#FEFCE8',    // Amber-50
    borderColor: '#FEF08A',// Amber-200
    icon: Equal,
  },
  LOW: {
    key: 'LOW',
    label: 'Thấp',
    subLabel: 'Ưu tiên thấp',
    color: '#16A34A',      // Green-600
    bgColor: '#F0FDF4',    // Green-50
    borderColor: '#BBF7D0',// Green-200
    icon: ArrowDown,
  },
  LOWEST: {
    key: 'LOWEST',
    label: 'Rất thấp',
    subLabel: 'Không khẩn',
    color: '#64748B',      // Slate-500
    bgColor: '#F8FAFC',    // Slate-50
    borderColor: '#E2E8F0',// Slate-200
    icon: ChevronsDown,
  },
};

export const getPriorityConfig = (priority) => {
  if (!priority) return PRIORITY_CONFIG.MEDIUM;
  const key = String(priority).toUpperCase();
  return PRIORITY_CONFIG[key] || PRIORITY_CONFIG.MEDIUM;
};
