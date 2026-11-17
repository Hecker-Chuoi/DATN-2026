import React, { useState, useRef, useEffect, useLayoutEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { ChevronDown, Check, Search, X } from 'lucide-react';

const MENU_GAP = 4;
// Cao hơn overlay của các modal (9999-10001) vì menu giờ được portal ra <body>,
// không còn nằm trong stacking context của modal; thấp hơn tooltip (99999).
const MENU_Z_INDEX = 10050;

/**
 * CustomSelect - Dropdown tùy chỉnh hiện đại thay thế thẻ <select> nguyên bản
 * 
 * Props:
 * - options: Array<{ value: any, label: string, sublabel?: string, badge?: React.ReactNode, icon?: React.ReactNode, disabled?: boolean }>
 * - value: any
 * - onChange: (value: any) => void
 * - placeholder?: string
 * - disabled?: boolean
 * - searchable?: boolean (tự động bật nếu options.length > 6 hoặc set thủ công)
 * - width?: string | number
 * - style?: React.CSSProperties
 * - menuStyle?: React.CSSProperties
 */
export default function CustomSelect({
  options = [],
  value,
  onChange,
  placeholder = 'Chọn một mục...',
  disabled = false,
  searchable = undefined,
  width = '100%',
  style = {},
  menuStyle = {},
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const containerRef = useRef(null);
  const menuRef = useRef(null);

  const selectedOption = options.find((opt) => String(opt.value) === String(value));

  // Tự động đóng menu khi click ra ngoài (menu nằm trong portal nên phải xét cả menuRef)
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (containerRef.current?.contains(e.target)) return;
      if (menuRef.current?.contains(e.target)) return;
      setIsOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const shouldShowSearch = searchable === true || (searchable !== false && options.length > 8);

  const filteredOptions = options.filter((opt) => {
    if (!searchTerm.trim()) return true;
    const term = searchTerm.toLowerCase();
    const labelMatch = opt.label?.toLowerCase().includes(term);
    const subMatch = opt.sublabel?.toLowerCase().includes(term);
    return labelMatch || subMatch;
  });

  /*
   * Menu được render qua portal với position: fixed để không bị cắt bởi
   * container cha có overflow: hidden/auto (bảng thành viên, thân modal...).
   * Vị trí tính theo nút bấm; thiếu chỗ phía dưới thì lật lên trên.
   * Ghi thẳng vào style của DOM (không qua state) để cuộn trang không gây re-render.
   */
  const updatePosition = useCallback(() => {
    const trigger = containerRef.current;
    const menu = menuRef.current;
    if (!trigger || !menu) return;
    const rect = trigger.getBoundingClientRect();
    const spaceBelow = window.innerHeight - rect.bottom - MENU_GAP;
    const spaceAbove = rect.top - MENU_GAP;
    const openUp = menu.offsetHeight > spaceBelow && spaceAbove > spaceBelow;
    menu.style.left = `${rect.left}px`;
    menu.style.width = `${rect.width}px`;
    menu.style.top = openUp ? 'auto' : `${rect.bottom + MENU_GAP}px`;
    menu.style.bottom = openUp ? `${window.innerHeight - rect.top + MENU_GAP}px` : 'auto';
    menu.style.visibility = 'visible';
  }, []);

  // Đo trước khi trình duyệt vẽ để menu không bị nháy ở vị trí sai;
  // đo lại khi số option đổi (gõ tìm kiếm) vì chiều cao menu thay đổi.
  useLayoutEffect(() => {
    if (isOpen) updatePosition();
  }, [isOpen, filteredOptions.length, updatePosition]);

  // Bám theo nút bấm khi cuộn (bất kỳ container nào) hoặc đổi kích thước cửa sổ.
  useEffect(() => {
    if (!isOpen) return undefined;
    window.addEventListener('scroll', updatePosition, true);
    window.addEventListener('resize', updatePosition);
    return () => {
      window.removeEventListener('scroll', updatePosition, true);
      window.removeEventListener('resize', updatePosition);
    };
  }, [isOpen, updatePosition]);

  return (
    <div
      ref={containerRef}
      style={{
        position: 'relative',
        width,
        userSelect: 'none',
        ...style,
      }}
    >
      {/* Trigger Button */}
      <button
        type="button"
        disabled={disabled}
        onClick={() => !disabled && setIsOpen(!isOpen)}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '7px 12px',
          borderRadius: 8,
          border: `1px solid ${isOpen ? '#2563EB' : '#CBD5E1'}`,
          backgroundColor: disabled ? '#F8FAFC' : '#FFFFFF',
          cursor: disabled ? 'not-allowed' : 'pointer',
          outline: 'none',
          boxSizing: 'border-box',
          transition: 'all 0.15s ease',
          boxShadow: isOpen ? '0 0 0 3px rgba(37, 99, 235, 0.12)' : 'none',
          gap: 8,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden', flex: 1, textAlign: 'left' }}>
          {selectedOption?.icon && <span>{selectedOption.icon}</span>}
          {selectedOption ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, overflow: 'hidden' }}>
              <span
                style={{
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#0F172A',
                  whiteSpace: 'nowrap',
                  textOverflow: 'ellipsis',
                  overflow: 'hidden',
                }}
              >
                {selectedOption.label}
              </span>
              {selectedOption.badge}
            </div>
          ) : (
            <span style={{ fontSize: 13, color: '#94A3B8' }}>{placeholder}</span>
          )}
        </div>

        <ChevronDown
          size={16}
          color="#64748B"
          style={{
            transform: isOpen ? 'rotate(180deg)' : 'none',
            transition: 'transform 0.15s ease',
            flexShrink: 0,
          }}
        />
      </button>

      {/* Dropdown Menu (portal ra <body>) */}
      {isOpen && createPortal(
        <div
          ref={menuRef}
          style={{
            position: 'fixed',
            // top/left/width/visibility do updatePosition() đặt sau khi đo;
            // trước đó menu nằm trong DOM để đo chiều cao nhưng ẩn đi.
            visibility: 'hidden',
            backgroundColor: '#FFFFFF',
            borderRadius: 8,
            border: '1px solid #E2E8F0',
            boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
            zIndex: MENU_Z_INDEX,
            overflow: 'hidden',
            boxSizing: 'border-box',
            ...menuStyle,
          }}
        >
          {/* Ô tìm kiếm nếu danh sách dài */}
          {shouldShowSearch && (
            <div
              style={{
                padding: '8px 10px',
                borderBottom: '1px solid #F1F5F9',
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                backgroundColor: '#FAFAFA',
              }}
            >
              <Search size={13} color="#94A3B8" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Tìm kiếm..."
                autoFocus
                style={{
                  border: 'none',
                  outline: 'none',
                  fontSize: 12,
                  width: '100%',
                  color: '#0F172A',
                  backgroundColor: 'transparent',
                }}
              />
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => setSearchTerm('')}
                  style={{ border: 'none', background: 'transparent', cursor: 'pointer', padding: 0, color: '#94A3B8' }}
                >
                  <X size={12} />
                </button>
              )}
            </div>
          )}

          {/* Danh sách options */}
          <div style={{ maxHeight: 240, overflowY: 'auto', padding: 4 }}>
            {filteredOptions.length === 0 ? (
              <div style={{ padding: '12px 14px', fontSize: 13, color: '#94A3B8', textAlign: 'center' }}>
                Không tìm thấy kết quả
              </div>
            ) : (
              filteredOptions.map((opt) => {
                const isSelected = String(opt.value) === String(value);

                return (
                  <div
                    key={String(opt.value)}
                    onClick={() => {
                      if (opt.disabled) return;
                      onChange(opt.value);
                      setIsOpen(false);
                      setSearchTerm('');
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '8px 10px',
                      borderRadius: 6,
                      cursor: opt.disabled ? 'not-allowed' : 'pointer',
                      backgroundColor: isSelected ? '#EFF6FF' : 'transparent',
                      color: isSelected ? '#1D4ED8' : opt.disabled ? '#94A3B8' : '#1E293B',
                      fontSize: 13,
                      transition: 'background-color 0.1s ease',
                      gap: 8,
                    }}
                    onMouseEnter={(e) => {
                      if (!isSelected && !opt.disabled) {
                        e.currentTarget.style.backgroundColor = '#F8FAFC';
                      }
                    }}
                    onMouseLeave={(e) => {
                      if (!isSelected) {
                        e.currentTarget.style.backgroundColor = 'transparent';
                      }
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden', flex: 1 }}>
                      {opt.icon && <span>{opt.icon}</span>}
                      <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <span style={{ fontWeight: isSelected ? 700 : 500, whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                            {opt.label}
                          </span>
                          {opt.badge}
                        </div>
                        {opt.sublabel && (
                          <span style={{ fontSize: 11, color: '#64748B', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                            {opt.sublabel}
                          </span>
                        )}
                      </div>
                    </div>

                    {isSelected && (
                      <Check size={14} color="#2563EB" style={{ flexShrink: 0, strokeWidth: 2.5 }} />
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}
