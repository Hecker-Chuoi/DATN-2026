import React, { useState, useRef, useEffect } from 'react';
import { HelpCircle, X, Sparkles } from 'lucide-react';

/**
 * StoryPointsHelpTooltip
 * 
 * Reusable helper tooltip explaining Story Points concept,
 * Fibonacci estimation scale, and DATN-2026 hierarchy rules.
 * Supports both hover preview and click-to-pin interaction.
 */
export default function StoryPointsHelpTooltip({
  size = 14,
  align = 'center', // 'center' | 'left' | 'right' | 'sidebar'
  position = 'bottom', // 'bottom' | 'top'
  style = {},
}) {
  const [isHovered, setIsHovered] = useState(false);
  const [isPinned, setIsPinned] = useState(false);
  const containerRef = useRef(null);
  const timeoutRef = useRef(null);

  const isOpen = isHovered || isPinned;

  const handleMouseEnter = () => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
    setIsHovered(true);
  };

  const handleMouseLeave = () => {
    timeoutRef.current = setTimeout(() => {
      setIsHovered(false);
    }, 200);
  };

  const handleToggleClick = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsPinned((prev) => !prev);
  };

  const handleClose = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsPinned(false);
    setIsHovered(false);
  };

  useEffect(() => {
    if (!isPinned) return;
    const handleOutsideClick = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsPinned(false);
        setIsHovered(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [isPinned]);

  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, []);

  // Alignment positioning calculation
  let popupStyle = {
    position: 'absolute',
    zIndex: 99999,
    width: 300,
    maxWidth: 'calc(100vw - 32px)',
    backgroundColor: '#FFFFFF',
    borderRadius: 10,
    border: '1px solid #E2E8F0',
    boxShadow: '0 10px 25px -5px rgba(15, 23, 42, 0.18), 0 8px 10px -6px rgba(15, 23, 42, 0.1)',
    padding: '12px 14px',
    textAlign: 'left',
    cursor: 'default',
    pointerEvents: 'auto',
    animation: 'fadeInScale 0.15s ease-out',
    boxSizing: 'border-box',
  };

  let arrowStyle = {
    position: 'absolute',
    width: 8,
    height: 8,
    backgroundColor: '#FFFFFF',
    transform: 'rotate(45deg)',
    zIndex: 100000,
  };

  if (position === 'bottom') {
    popupStyle.top = 'calc(100% + 8px)';
    arrowStyle.top = -5;
    arrowStyle.borderLeft = '1px solid #E2E8F0';
    arrowStyle.borderTop = '1px solid #E2E8F0';
  } else {
    popupStyle.bottom = 'calc(100% + 8px)';
    arrowStyle.bottom = -5;
    arrowStyle.borderRight = '1px solid #E2E8F0';
    arrowStyle.borderBottom = '1px solid #E2E8F0';
  }

  if (align === 'center') {
    popupStyle.left = '50%';
    popupStyle.transform = 'translateX(-50%)';
    arrowStyle.left = '50%';
    arrowStyle.transform = 'translateX(-50%) rotate(45deg)';
  } else if (align === 'left') {
    popupStyle.left = -6;
    arrowStyle.left = 12;
  } else if (align === 'right') {
    popupStyle.right = -6;
    arrowStyle.right = 12;
  } else if (align === 'sidebar') {
    // Specially tuned for right-hand metadata sidebars (~300px wide)
    popupStyle.left = -80;
    arrowStyle.left = 86;
  }

  return (
    <span
      ref={containerRef}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      style={{
        position: 'relative',
        display: 'inline-flex',
        alignItems: 'center',
        lineHeight: 1,
        ...style,
      }}
    >
      <style>{`
        @keyframes fadeInScale {
          from { opacity: 0; transform: ${align === 'center' ? 'translateX(-50%) scale(0.96)' : 'scale(0.96)'}; }
          to { opacity: 1; transform: ${align === 'center' ? 'translateX(-50%) scale(1)' : 'scale(1)'}; }
        }
      `}</style>

      {/* Trigger Button */}
      <button
        type="button"
        onClick={handleToggleClick}
        aria-label="Điểm ước lượng là gì?"
        title="Bấm hoặc rê chuột để xem giải thích về điểm ước lượng"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: size + 4,
          height: size + 4,
          padding: 0,
          border: 'none',
          backgroundColor: isOpen ? '#EFF6FF' : 'transparent',
          color: isOpen ? '#2563EB' : '#94A3B8',
          borderRadius: '50%',
          cursor: 'pointer',
          transition: 'all 0.15s ease',
          outline: 'none',
        }}
      >
        <HelpCircle size={size} strokeWidth={2.2} />
      </button>

      {/* Popover Card */}
      {isOpen && (
        <div style={popupStyle}>
          {/* Arrow */}
          <div style={arrowStyle} />

          {/* Header */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 8,
              paddingBottom: 6,
              borderBottom: '1px solid #F1F5F9',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <div
                style={{
                  width: 20,
                  height: 20,
                  borderRadius: 6,
                  backgroundColor: '#EFF6FF',
                  color: '#2563EB',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Sparkles size={12} />
              </div>
              <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>
                Điểm ước lượng là gì?
              </span>
            </div>
            <button
              type="button"
              onClick={handleClose}
              style={{
                background: 'none',
                border: 'none',
                color: '#94A3B8',
                cursor: 'pointer',
                padding: 2,
                borderRadius: 4,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <X size={13} />
            </button>
          </div>

          {/* Core Definition */}
          <p
            style={{
              fontSize: 12,
              color: '#334155',
              lineHeight: 1.45,
              margin: '0 0 8px 0',
            }}
          >
            Đơn vị ước lượng tương đối dùng để đo lường{' '}
            <strong>độ phức tạp</strong>, <strong>khối lượng công việc</strong> và{' '}
            <strong>mức độ rủi ro</strong> của nhiệm vụ thay vì số giờ cố định.
          </p>

          {/* Fibonacci Scale Reference */}
          <div
            style={{
              backgroundColor: '#F8FAFC',
              borderRadius: 6,
              padding: '6px 8px',
              marginBottom: 8,
              border: '1px solid #F1F5F9',
            }}
          >
            <div
              style={{
                fontSize: 11,
                fontWeight: 700,
                color: '#475569',
                marginBottom: 4,
                display: 'flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              <span>Thang điểm gợi ý:</span>
              <span style={{ display: 'inline-flex', gap: 3 }}>
                {[1, 2, 3, 5, 8, 13].map((pt) => (
                  <span
                    key={pt}
                    style={{
                      fontSize: 10,
                      fontWeight: 700,
                      padding: '0 4px',
                      borderRadius: 3,
                      backgroundColor: '#FFFFFF',
                      border: '1px solid #CBD5E1',
                      color: '#1E293B',
                    }}
                  >
                    {pt}
                  </span>
                ))}
              </span>
            </div>
            <div style={{ fontSize: 11, color: '#64748B', lineHeight: 1.4 }}>
              <div>• <strong>1 - 2:</strong> Việc nhỏ, đơn giản (vài giờ)</div>
              <div>• <strong>3 - 5:</strong> Trung bình, phổ biến (1 - 2 ngày)</div>
              <div>• <strong>8 - 13:</strong> Lớn, phức tạp (nên phân rã nhỏ)</div>
            </div>
          </div>

          {/* DATN-2026 Hierarchy Rules */}
          <div
            style={{
              fontSize: 11,
              color: '#475569',
              display: 'flex',
              flexDirection: 'column',
              gap: 3,
              marginBottom: 8,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <span
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  backgroundColor: '#10B981',
                  flexShrink: 0,
                }}
              />
              <span><strong>Yêu cầu / Nhiệm vụ:</strong> Nhập điểm trực tiếp</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <span
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  backgroundColor: '#8B5CF6',
                  flexShrink: 0,
                }}
              />
              <span><strong>Hạng mục lớn:</strong> Tự động cộng dồn từ công việc con</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <span
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  backgroundColor: '#94A3B8',
                  flexShrink: 0,
                }}
              />
              <span><strong>Nhiệm vụ con:</strong> Không tính điểm (theo dõi theo tiến độ chi tiết)</span>
            </div>
          </div>

          {/* Velocity Hint */}
          <div
            style={{
              fontSize: 11,
              color: '#1D4ED8',
              backgroundColor: '#EFF6FF',
              padding: '5px 8px',
              borderRadius: 6,
              lineHeight: 1.35,
            }}
          >
            💡 <strong>Mẹo:</strong> Giúp đo lường <strong>tốc độ hoàn thành</strong> của đội ngũ qua từng chu kỳ làm việc.
          </div>
        </div>
      )}
    </span>
  );
}
