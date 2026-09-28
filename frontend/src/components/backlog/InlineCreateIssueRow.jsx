import { useEffect, useRef, useState } from 'react';
import { SURFACE } from '../../constants/jiraTokens.js';

/**
 * Tao nhanh cong viec ngay trong danh sach: chon loai, go tieu de, Enter la xong.
 * Esc huy. Theo dung mo ta "+ Create issue" trong UI spec.
 */
export default function InlineCreateIssueRow({ types = [], defaultTypeId, onSubmit, onCancel }) {
  const usable = types.filter((t) => !t.isSubtask);
  const [typeId, setTypeId] = useState(
    defaultTypeId || usable.find((t) => t.code === 'STORY')?.id || usable[0]?.id || ''
  );
  const [summary, setSummary] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const inputRef = useRef(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const submit = async () => {
    const text = summary.trim();
    if (!text || !typeId || submitting) return;
    try {
      setSubmitting(true);
      await onSubmit?.({ typeId, summary: text });
      setSummary('');
      inputRef.current?.focus();
    } catch (err) {
      console.warn('Không tạo được công việc:', err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '8px 12px',
        backgroundColor: '#F8FAFC',
        borderTop: `1px solid ${SURFACE.border}`,
      }}
    >
      <select
        value={typeId}
        onChange={(e) => setTypeId(e.target.value)}
        style={{
          padding: '5px 8px',
          borderRadius: 6,
          border: `1px solid ${SURFACE.border}`,
          fontSize: 12,
          backgroundColor: '#FFFFFF',
          color: SURFACE.text,
        }}
      >
        {usable.map((t) => (
          <option key={t.id} value={t.id}>{t.name}</option>
        ))}
      </select>

      <input
        ref={inputRef}
        value={summary}
        onChange={(e) => setSummary(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') { e.preventDefault(); submit(); }
          if (e.key === 'Escape') { e.preventDefault(); onCancel?.(); }
        }}
        placeholder="Nhập tiêu đề công việc rồi nhấn Enter..."
        disabled={submitting}
        style={{
          flex: 1,
          padding: '6px 10px',
          borderRadius: 6,
          border: `1px solid ${SURFACE.border}`,
          fontSize: 13,
          outline: 'none',
          color: SURFACE.text,
        }}
      />

      <button
        onClick={submit}
        disabled={submitting || !summary.trim()}
        style={{
          padding: '5px 12px',
          borderRadius: 6,
          border: 'none',
          fontSize: 12,
          fontWeight: 600,
          cursor: submitting || !summary.trim() ? 'not-allowed' : 'pointer',
          backgroundColor: submitting || !summary.trim() ? '#E2E8F0' : '#2563EB',
          color: submitting || !summary.trim() ? '#94A3B8' : '#FFFFFF',
        }}
      >
        Tạo
      </button>
      <button
        onClick={onCancel}
        style={{
          padding: '5px 10px',
          borderRadius: 6,
          border: `1px solid ${SURFACE.border}`,
          fontSize: 12,
          cursor: 'pointer',
          backgroundColor: '#FFFFFF',
          color: SURFACE.textMuted,
        }}
      >
        Huỷ
      </button>
    </div>
  );
}
