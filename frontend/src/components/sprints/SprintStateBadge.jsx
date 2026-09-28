import { SPRINT_STATE_CONFIG } from '../../constants/workConstants.js';

/**
 * Huy hieu trang thai Sprint. Du an khong dung Tailwind nen mau lay tu
 * SPRINT_STATE_CONFIG va ap bang inline style.
 */
export default function SprintStateBadge({ state, size = 'sm' }) {
  const cfg = SPRINT_STATE_CONFIG[state] || SPRINT_STATE_CONFIG.FUTURE;
  const isActive = state === 'ACTIVE';

  return (
    <>
      {isActive && (
        <style>{'@keyframes sprintPulse{0%,100%{opacity:1}50%{opacity:.55}}'}</style>
      )}
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 5,
          padding: size === 'sm' ? '2px 8px' : '4px 10px',
          borderRadius: 999,
          fontSize: size === 'sm' ? 11 : 12,
          fontWeight: 600,
          letterSpacing: 0.2,
          color: cfg.color,
          backgroundColor: cfg.bgColor,
          border: `1px solid ${cfg.borderColor}`,
          whiteSpace: 'nowrap',
        }}
      >
        {isActive && (
          <span
            style={{
              width: 6,
              height: 6,
              borderRadius: '50%',
              backgroundColor: cfg.color,
              animation: 'sprintPulse 2s ease-in-out infinite',
            }}
          />
        )}
        {cfg.label}
      </span>
    </>
  );
}
