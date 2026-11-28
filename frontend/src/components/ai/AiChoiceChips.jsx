import { useState } from 'react';
import { HelpCircle } from 'lucide-react';

/** Câu hỏi ngược của trợ lý: chip lựa chọn (đơn/đa) + ô gõ tự do. */
export default function AiChoiceChips({ interaction, disabled, onSubmit }) {
  const p = interaction.payload || {};
  const options = Array.isArray(p.options) ? p.options : [];
  const multi = Boolean(p.multi_select);
  const allowText = p.allow_free_text !== false;
  const pending = interaction.status === 'PENDING';
  const [selected, setSelected] = useState([]);
  const [text, setText] = useState('');

  const answered = interaction.response?.result;

  const pick = (value) => {
    if (!multi && !allowText) {
      onSubmit({ selectedValues: [value] });
      return;
    }
    setSelected((s) => (multi ? (s.includes(value) ? s.filter((x) => x !== value) : [...s, value]) : [value]));
  };

  const submit = () => {
    if (!selected.length && !text.trim()) return;
    onSubmit({ selectedValues: selected, freeText: text.trim() || undefined });
  };

  return (
    <div style={{ marginTop: 8, background: '#FFFFFF', border: '1px solid #BFDBFE', borderRadius: 10, padding: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, fontSize: 13, color: '#1D4ED8' }}>
        <HelpCircle size={14} /> {p.question || interaction.summary}
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
        {options.map((o) => {
          const active = selected.includes(o.value) || (answered?.selectedValues || []).includes(o.value);
          return (
            <button
              key={o.value}
              type="button"
              title={o.description}
              disabled={!pending || disabled}
              onClick={() => pick(o.value)}
              style={{
                borderRadius: 999,
                padding: '4px 10px',
                fontSize: 12,
                cursor: pending ? 'pointer' : 'default',
                border: active ? '1px solid #2563EB' : '1px solid #CBD5E1',
                background: active ? '#DBEAFE' : '#FFFFFF',
                color: '#0F172A',
              }}
            >
              {o.label || o.value}
            </button>
          );
        })}
      </div>

      {pending && allowText && (
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit()}
          placeholder="Hoặc gõ câu trả lời…"
          disabled={disabled}
          style={{ width: '100%', marginTop: 8, fontSize: 12, padding: '6px 8px', borderRadius: 6, border: '1px solid #CBD5E1' }}
        />
      )}
      {pending && (multi || allowText) && (
        <button
          type="button"
          onClick={submit}
          disabled={disabled || (!selected.length && !text.trim())}
          style={{ marginTop: 8, background: '#2563EB', color: '#fff', border: 'none', borderRadius: 6, padding: '5px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}
        >
          Gửi
        </button>
      )}
      {!pending && (
        <div style={{ marginTop: 6, fontSize: 12, color: '#64748B' }}>
          {interaction.status === 'ANSWERED'
            ? 'Đã trả lời' + (answered?.freeText ? ': ' + answered.freeText : '')
            : 'Đã bỏ qua'}
        </div>
      )}
    </div>
  );
}
