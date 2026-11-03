import React from 'react';
import CustomSelect from './CustomSelect.jsx';

export const FIBONACCI_STEPS = [
  { value: 1, label: '1 điểm', tag: 'Rất nhỏ', color: '#0284C7', bg: '#E0F2FE' },
  { value: 2, label: '2 điểm', tag: 'Nhỏ', color: '#2563EB', bg: '#EFF6FF' },
  { value: 3, label: '3 điểm', tag: 'Vừa', color: '#4F46E5', bg: '#EEF2FF' },
  { value: 5, label: '5 điểm', tag: 'Khá lớn', color: '#7C3AED', bg: '#F5F3FF' },
  { value: 8, label: '8 điểm', tag: 'Lớn', color: '#DB2777', bg: '#FDF2F8' },
  { value: 13, label: '13 điểm', tag: 'Rất lớn', color: '#D97706', bg: '#FEF3C7' },
];

/**
 * StoryPointsPicker - Dropdown chọn điểm ước lượng đơn giản, hiện đại
 * Sử dụng CustomSelect đồng bộ với toàn bộ hệ thống.
 */
export default function StoryPointsPicker({
  value,
  onChange,
  disabled = false,
  placeholder = 'Chưa ước lượng điểm',
  width = '100%',
  align = 'left',
}) {
  const strVal = value != null && value !== '' ? String(value) : '';

  // Danh sách options dạng dropdown đơn giản
  const options = [
    { value: '', label: 'Chưa ước lượng điểm' },
    ...FIBONACCI_STEPS.map((step) => ({
      value: String(step.value),
      label: step.label,
      badge: (
        <span
          style={{
            fontSize: 11,
            fontWeight: 600,
            padding: '1px 7px',
            borderRadius: 999,
            backgroundColor: step.bg,
            color: step.color,
          }}
        >
          {step.tag}
        </span>
      ),
    })),
  ];

  // Nếu có điểm số tùy chỉnh không nằm trong thang Fibonacci chuẩn (vd: 0, 4, 20...)
  if (strVal && !options.some((opt) => opt.value === strVal)) {
    options.push({
      value: strVal,
      label: `${strVal} điểm`,
    });
  }

  return (
    <CustomSelect
      value={strVal}
      onChange={(newVal) => onChange?.(newVal)}
      options={options}
      placeholder={placeholder}
      disabled={disabled}
      searchable={false}
      width={width}
    />
  );
}
