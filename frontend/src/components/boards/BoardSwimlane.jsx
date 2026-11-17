import { useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { SURFACE } from '../../constants/jiraTokens.js';

/** Mot luong ngang tren Board, gap/mo duoc. */
export default function BoardSwimlane({ lane, children }) {
  const [open, setOpen] = useState(true);

  return (
    <div style={{ marginBottom: 14 }}>
      <div
        onClick={() => setOpen((o) => !o)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '7px 10px',
          backgroundColor: SURFACE.track,
          borderRadius: 6,
          marginBottom: 8,
          cursor: 'pointer',
        }}
      >
        {open ? <ChevronDown size={15} color={SURFACE.textMuted} /> : <ChevronRight size={15} color={SURFACE.textMuted} />}
        <span style={{ fontSize: 13, fontWeight: 700, color: SURFACE.text }}>{lane.name === 'Không thuộc Epic nào' ? 'Không thuộc hạng mục lớn nào' : lane.name === 'Epic' ? 'Hạng mục lớn' : lane.name}</span>
        <span style={{ fontSize: 11, color: SURFACE.textMuted }}>({lane.cardCount ?? 0})</span>
        {lane.unsupported && (
          <span
            title="Chiến lược phân luồng này chưa được hỗ trợ"
            style={{
              marginLeft: 6, fontSize: 10, fontWeight: 700, color: '#B45309',
              backgroundColor: '#FFFBEB', border: '1px solid #FCD34D',
              padding: '1px 6px', borderRadius: 4,
            }}
          >
            Chưa hỗ trợ
          </span>
        )}
      </div>
      {open && children}
    </div>
  );
}
