import { SURFACE } from '../../constants/jiraTokens.js';

/**
 * Bo chuyen che do xem dang "segmented control": nen xam, muc dang chon noi len mau trang.
 * Dung cho cap dieu huong thu 2 ben trong mot tab (vd. Danh sach / Bieu do, hoac chon loai bieu do),
 * de phan biet voi thanh tab chinh cua trang.
 *
 * items: [{ id, label, icon? }]
 */
export default function SegmentedTabs({ items, value, onChange }) {
  return (
    <div
      role="tablist"
      style={{
        display: 'flex',
        gap: 4,
        backgroundColor: SURFACE.border,
        padding: 3,
        borderRadius: 8,
        width: 'fit-content',
        flexWrap: 'wrap',
      }}
    >
      {items.map(({ id, label, icon: Icon }) => {
        const active = value === id;
        return (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(id)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '6px 14px',
              borderRadius: 6,
              fontSize: 13,
              fontWeight: active ? 700 : 500,
              color: active ? SURFACE.text : SURFACE.textMuted,
              backgroundColor: active ? '#FFFFFF' : 'transparent',
              border: 'none',
              boxShadow: active ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
          >
            {Icon && <Icon size={15} />}
            <span>{label}</span>
          </button>
        );
      })}
    </div>
  );
}
