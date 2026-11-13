import { useState } from 'react';
import toast from 'react-hot-toast';
import { ShieldCheck } from 'lucide-react';

const STATUS_LABEL = {
  APPROVED: '✓ Đã duyệt và thực hiện',
  REJECTED: 'Đã bỏ qua',
  EXPIRED: 'Đã bỏ qua (bạn gửi tin khác)',
};

const btn = (bg, color, border) => ({
  background: bg,
  color,
  border: border || 'none',
  borderRadius: 6,
  padding: '5px 12px',
  fontSize: 12,
  fontWeight: 600,
  cursor: 'pointer',
});

/** Thẻ duyệt một hành động ghi (WRITE): xem tham số, sửa JSON cơ bản, Duyệt / Bỏ qua. */
export default function AiApprovalCard({ interaction, disabled, onDecide }) {
  const args = interaction.payload?.args || {};
  const [editing, setEditing] = useState(false);
  const [json, setJson] = useState(() => JSON.stringify(args, null, 2));
  const pending = interaction.status === 'PENDING';

  const decide = (approved) => {
    let edited;
    if (approved && editing) {
      try {
        edited = JSON.parse(json);
      } catch {
        toast.error('JSON tham số không hợp lệ');
        return;
      }
    }
    onDecide(approved, edited);
  };

  return (
    <div style={{ marginTop: 8, background: '#FFFFFF', border: '1px solid #FDE68A', borderRadius: 10, padding: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, fontSize: 13, color: '#92400E' }}>
        <ShieldCheck size={14} /> Cần bạn duyệt
      </div>
      <div style={{ fontSize: 13, marginTop: 4 }}>{interaction.summary}</div>
      <div style={{ fontSize: 11, color: '#64748B' }}>Công cụ: {interaction.toolName}</div>

      {editing ? (
        <textarea
          value={json}
          onChange={(e) => setJson(e.target.value)}
          rows={6}
          style={{ width: '100%', marginTop: 6, fontFamily: 'monospace', fontSize: 12, borderRadius: 6, border: '1px solid #CBD5E1', padding: 6 }}
        />
      ) : (
        Object.keys(args).length > 0 && (
          <div style={{ marginTop: 6, fontSize: 12 }}>
            {Object.entries(args).map(([k, v]) => (
              <div key={k}>
                <span style={{ color: '#64748B' }}>{k}:</span>{' '}
                <span style={{ whiteSpace: 'pre-wrap' }}>{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span>
              </div>
            ))}
          </div>
        )
      )}

      {pending ? (
        <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
          <button type="button" onClick={() => decide(true)} disabled={disabled} style={btn('#2563EB', '#fff')}>
            Duyệt
          </button>
          <button type="button" onClick={() => decide(false)} disabled={disabled} style={btn('#F1F5F9', '#0F172A')}>
            Bỏ qua
          </button>
          <button type="button" onClick={() => setEditing((v) => !v)} disabled={disabled} style={btn('transparent', '#2563EB', '1px solid #BFDBFE')}>
            {editing ? 'Xem lại' : 'Sửa tham số'}
          </button>
        </div>
      ) : (
        <div style={{ marginTop: 8, fontSize: 12, color: interaction.status === 'APPROVED' ? '#059669' : '#64748B' }}>
          {STATUS_LABEL[interaction.status] || interaction.status}
        </div>
      )}
    </div>
  );
}
