import { Check, X, Loader2, AlertCircle } from 'lucide-react';
import AiApprovalCard from './AiApprovalCard.jsx';
import AiChoiceChips from './AiChoiceChips.jsx';

// Mã công việc dạng PROJECT-số (AUTH-12) → link mở WorkItemDetailModal
const INLINE_RE = /(\*\*[^*\n]+\*\*|`[^`\n]+`|\b[A-Z][A-Z0-9]{1,9}-\d{1,6}\b)/g;

function renderInline(text, onOpenCode) {
  const parts = [];
  let last = 0;
  let k = 0;
  let m;
  INLINE_RE.lastIndex = 0;
  while ((m = INLINE_RE.exec(text))) {
    if (m.index > last) parts.push(text.slice(last, m.index));
    const tok = m[0];
    if (tok.startsWith('**')) {
      parts.push(<strong key={k++}>{tok.slice(2, -2)}</strong>);
    } else if (tok.startsWith('`')) {
      parts.push(
        <code key={k++} style={{ background: '#E2E8F0', borderRadius: 4, padding: '0 4px', fontSize: 12 }}>
          {tok.slice(1, -1)}
        </code>
      );
    } else {
      parts.push(
        <button
          key={k++}
          type="button"
          onClick={() => onOpenCode && onOpenCode(tok)}
          style={{ background: 'none', border: 'none', padding: 0, color: '#2563EB', fontWeight: 600, cursor: 'pointer', fontSize: 'inherit' }}
        >
          {tok}
        </button>
      );
    }
    last = m.index + tok.length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

/** Markdown tối giản: tiêu đề #, gạch đầu dòng, đánh số, **đậm**, `code`. */
function renderMarkdown(text, onOpenCode) {
  const blocks = [];
  let list = null;
  text.split('\n').forEach((line, i) => {
    const item = /^\s*(?:[-*•]|\d+[.)])\s+(.*)$/.exec(line);
    if (item) {
      if (!list) {
        list = [];
        blocks.push(list);
      }
      list.push(item[1]);
      return;
    }
    list = null;
    const heading = /^#{1,4}\s+(.*)$/.exec(line);
    if (heading) blocks.push(<div key={i} style={{ fontWeight: 700, marginTop: 6 }}>{renderInline(heading[1], onOpenCode)}</div>);
    else if (!line.trim()) blocks.push(<div key={i} style={{ height: 6 }} />);
    else blocks.push(<div key={i}>{renderInline(line, onOpenCode)}</div>);
  });
  return blocks.map((b, i) =>
    Array.isArray(b) ? (
      <ul key={'l' + i} style={{ margin: '4px 0', paddingLeft: 18 }}>
        {b.map((li, j) => (
          <li key={j}>{renderInline(li, onOpenCode)}</li>
        ))}
      </ul>
    ) : (
      b
    )
  );
}

function ToolChip({ tool }) {
  const icon =
    tool.ok === null || tool.ok === undefined ? (
      <Loader2 size={12} className="ai-spin" />
    ) : tool.ok ? (
      <Check size={12} color="#059669" />
    ) : (
      <X size={12} color="#DC2626" />
    );
  return (
    <span
      title={tool.name}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        fontSize: 11,
        padding: '2px 8px',
        borderRadius: 999,
        background: '#fff',
        border: '1px solid #E2E8F0',
        color: '#334155',
      }}
    >
      {icon}
      {tool.summary || tool.name}
    </span>
  );
}

export default function AiMessage({ message, streaming, isLast, onOpenCode, onAnswer }) {
  const isUser = message.role === 'user';
  const busy = Boolean(streaming && isLast);
  const empty = !message.text && !message.tools?.length && !message.interactions?.length && !message.error;

  return (
    <div style={{ display: 'flex', justifyContent: isUser ? 'flex-end' : 'flex-start', marginBottom: 10 }}>
      <div
        style={{
          maxWidth: '90%',
          background: isUser ? '#2563EB' : '#F1F5F9',
          color: isUser ? '#FFFFFF' : '#0F172A',
          borderRadius: 12,
          padding: '8px 12px',
          fontSize: 13,
          lineHeight: 1.5,
          wordBreak: 'break-word',
        }}
      >
        {!isUser && message.tools?.length > 0 && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: message.text ? 6 : 0 }}>
            {message.tools.map((t) => (
              <ToolChip key={t.id} tool={t} />
            ))}
          </div>
        )}

        {isUser ? (
          <div style={{ whiteSpace: 'pre-wrap' }}>{message.text}</div>
        ) : message.text ? (
          <div>{renderMarkdown(message.text, onOpenCode)}</div>
        ) : null}

        {!isUser && empty && busy && (
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: '#64748B', fontSize: 12 }}>
            <Loader2 size={13} className="ai-spin" /> Đang suy nghĩ…
          </span>
        )}

        {message.notices?.map((n, i) => (
          <div key={i} style={{ fontStyle: 'italic', color: '#475569', fontSize: 12, marginTop: 4 }}>
            {n}
          </div>
        ))}

        {message.interactions?.map((it) =>
          it.type === 'ASK_USER' ? (
            <AiChoiceChips
              key={it.interactionId}
              interaction={it}
              disabled={streaming}
              onSubmit={(body) => onAnswer(it.interactionId, body, 'ANSWERED')}
            />
          ) : (
            <AiApprovalCard
              key={it.interactionId}
              interaction={it}
              disabled={streaming}
              onDecide={(approved, editedInput) =>
                onAnswer(it.interactionId, { approved, editedInput }, approved ? 'APPROVED' : 'REJECTED')
              }
            />
          )
        )}

        {message.error && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#B91C1C', fontSize: 12, marginTop: 4 }}>
            <AlertCircle size={12} /> {message.error}
          </div>
        )}
      </div>
    </div>
  );
}
