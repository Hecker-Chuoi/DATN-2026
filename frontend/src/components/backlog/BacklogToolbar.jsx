import { Search, X } from 'lucide-react';
import { SURFACE } from '../../constants/jiraTokens.js';
import { getAvatarColor, getInitials } from '../../utils/avatarColor.js';

/**
 * Thanh cong cu Backlog: tim kiem, loc theo Epic, loc theo nguoi thuc hien (bam avatar),
 * va cac bo loc nhanh. Toan bo loc chay o phia client tren du lieu da nap.
 */
export default function BacklogToolbar({
  filters,
  onChange,
  epics = [],
  members = [],
  currentUserId,
}) {
  const set = (patch) => onChange({ ...filters, ...patch });
  const hasAny =
    filters.q || filters.epicId || filters.assigneeIds?.length || filters.onlyMine || filters.recentlyUpdated;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 0 14px 0',
        flexWrap: 'wrap',
      }}
    >
      {/* Tim kiem */}
      <div style={{ position: 'relative', width: 230 }}>
        <Search
          size={14}
          style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: SURFACE.textLight }}
        />
        <input
          value={filters.q || ''}
          onChange={(e) => set({ q: e.target.value })}
          placeholder="Tìm theo mã hoặc tiêu đề..."
          style={{
            width: '100%',
            padding: '6px 10px 6px 28px',
            borderRadius: 6,
            border: `1px solid ${SURFACE.border}`,
            fontSize: 13,
            outline: 'none',
            color: SURFACE.text,
          }}
        />
      </div>

      {/* Loc theo Epic */}
      <select
        value={filters.epicId || ''}
        onChange={(e) => set({ epicId: e.target.value || null })}
        style={{
          padding: '6px 10px',
          borderRadius: 6,
          border: `1px solid ${SURFACE.border}`,
          fontSize: 13,
          backgroundColor: '#FFFFFF',
          color: SURFACE.text,
          maxWidth: 200,
        }}
      >
        <option value="">Tất cả hạng mục lớn</option>
        {epics.map((e) => (
          <option key={e.id} value={e.id}>{e.code} — {e.summary}</option>
        ))}
      </select>

      {/* Nhom avatar thanh vien */}
      {members.length > 0 && (
        <div style={{ display: 'flex', alignItems: 'center' }}>
          {members.slice(0, 6).map((m, i) => {
            const uid = m.userId || m.id;
            const name = m.fullName || m.username || m.name || '';
            const selected = filters.assigneeIds?.includes(uid);
            return (
              <button
                key={uid}
                title={name}
                onClick={() => {
                  const cur = filters.assigneeIds || [];
                  set({ assigneeIds: selected ? cur.filter((x) => x !== uid) : [...cur, uid] });
                }}
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  marginLeft: i === 0 ? 0 : -6,
                  zIndex: 10 - i,
                  backgroundColor: getAvatarColor(name || uid),
                  color: '#FFFFFF',
                  fontSize: 11,
                  fontWeight: 700,
                  cursor: 'pointer',
                  border: 'none',
                  boxShadow: selected ? '0 0 0 2px #2563EB' : '0 0 0 2px #FFFFFF',
                }}
              >
                {getInitials(name || '?')}
              </button>
            );
          })}
        </div>
      )}

      {/* Bo loc nhanh */}
      <QuickFilter
        active={filters.onlyMine}
        onClick={() => set({ onlyMine: !filters.onlyMine })}
        disabled={!currentUserId}
      >
        Chỉ việc của tôi
      </QuickFilter>
      <QuickFilter
        active={filters.recentlyUpdated}
        onClick={() => set({ recentlyUpdated: !filters.recentlyUpdated })}
      >
        Mới cập nhật
      </QuickFilter>

      {hasAny && (
        <button
          onClick={() =>
            onChange({ q: '', epicId: null, assigneeIds: [], onlyMine: false, recentlyUpdated: false })
          }
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 4,
            padding: '5px 10px',
            borderRadius: 6,
            border: `1px solid ${SURFACE.border}`,
            backgroundColor: '#FFFFFF',
            color: SURFACE.textMuted,
            fontSize: 12,
            cursor: 'pointer',
          }}
        >
          <X size={12} /> Xoá bộ lọc
        </button>
      )}
    </div>
  );
}

function QuickFilter({ active, onClick, disabled, children }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        padding: '5px 12px',
        borderRadius: 999,
        fontSize: 12,
        fontWeight: active ? 600 : 500,
        cursor: disabled ? 'not-allowed' : 'pointer',
        border: `1px solid ${active ? '#93C5FD' : SURFACE.border}`,
        backgroundColor: active ? '#EFF6FF' : '#FFFFFF',
        color: disabled ? SURFACE.textLight : active ? '#1D4ED8' : SURFACE.textMuted,
      }}
    >
      {children}
    </button>
  );
}
