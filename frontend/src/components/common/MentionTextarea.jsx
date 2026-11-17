import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { decodeMentions, encodeMentions, foldText, memberDisplayName } from '../../utils/mentions.jsx';

const MAX_OPTIONS = 6;
const MENU_HEIGHT = 230;

/**
 * Ô nhập bình luận có gợi ý nhắc tên: gõ "@" rồi vài chữ để chọn thành viên dự án.
 *
 * `value` / `onChange` dùng chuỗi đã mã hoá ("@[Tên](id)") để gửi thẳng lên server;
 * trong ô chỉ thấy "@Tên". Các thuộc tính còn lại (rows, placeholder, style, autoFocus…) chuyển cho textarea.
 */
export default function MentionTextarea({ value, onChange, members = [], excludeUserId, onKeyDown, onBlur, ...rest }) {
  const ref = useRef(null);
  const picked = useRef(new Map()); // tên → id đã chọn trong lần soạn này
  const { text, mentions } = useMemo(() => decodeMentions(value), [value]);
  const [query, setQuery] = useState(null); // { start, q, rect }
  const [active, setActive] = useState(0);

  const options = useMemo(() => {
    if (!query) return [];
    const q = foldText(query.q.trim());
    return members
      .filter((m) => m?.userId && (!excludeUserId || String(m.userId) !== String(excludeUserId)))
      .filter((m) => !q
        || foldText(memberDisplayName(m)).includes(q)
        || foldText(m.username).includes(q)
        || foldText(m.email).startsWith(q))
      .slice(0, MAX_OPTIONS);
  }, [query, members, excludeUserId]);

  const open = Boolean(query) && options.length > 0;

  // Danh sách gợi ý đặt cố định theo ô nhập; cuộn trang thì đóng cho khỏi lệch chỗ
  useEffect(() => {
    if (!open) return undefined;
    const close = (e) => {
      if (e.target instanceof Node && e.target.closest?.('[data-mention-menu]')) return;
      setQuery(null);
    };
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [open]);

  const known = () => {
    const map = new Map(picked.current);
    mentions.forEach((m) => { if (!map.has(m.name)) map.set(m.name, m.id); });
    return map;
  };

  const detect = (t, caret) => {
    const before = t.slice(0, caret);
    const at = before.lastIndexOf('@');
    if (at < 0 || (at > 0 && !/\s/.test(before[at - 1]))) return null;
    const q = before.slice(at + 1);
    if (q.length > 40 || q.includes('\n') || q.startsWith(' ') || /\s{2,}/.test(q)) return null;
    if (picked.current.has(q.trim()) || mentions.some((m) => m.name === q.trim())) return null;
    return { start: at, q };
  };

  const refreshQuery = (t, caret) => {
    const found = detect(t, caret);
    if (!found) {
      setQuery(null);
      return;
    }
    const r = ref.current?.getBoundingClientRect();
    const rect = r ? { left: r.left, top: r.top, bottom: r.bottom, width: r.width } : null;
    if (!query || query.q !== found.q || query.start !== found.start) setActive(0);
    setQuery({ ...found, rect });
  };

  const select = (member) => {
    if (!query) return;
    const name = memberDisplayName(member);
    picked.current.set(name, member.userId);
    const caret = ref.current?.selectionStart ?? text.length;
    const before = text.slice(0, query.start);
    const after = text.slice(caret);
    const insert = `@${name} `;
    const next = before + insert + (after.startsWith(' ') ? after.slice(1) : after);
    onChange?.(encodeMentions(next, known()));
    setQuery(null);
    const pos = before.length + insert.length;
    requestAnimationFrame(() => {
      ref.current?.focus();
      ref.current?.setSelectionRange(pos, pos);
    });
  };

  const handleKeyDown = (e) => {
    if (open) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        const step = e.key === 'ArrowDown' ? 1 : -1;
        setActive((i) => (i + step + options.length) % options.length);
        return;
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        select(options[Math.min(active, options.length - 1)]);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        setQuery(null);
        return;
      }
    }
    onKeyDown?.(e);
  };

  let menuStyle = null;
  if (open && query.rect) {
    const below = window.innerHeight - query.rect.bottom > MENU_HEIGHT;
    menuStyle = {
      position: 'fixed',
      left: query.rect.left,
      width: Math.min(Math.max(query.rect.width, 240), 360),
      ...(below ? { top: query.rect.bottom + 4 } : { bottom: window.innerHeight - query.rect.top + 4 }),
    };
  }

  return (
    <>
      <textarea
        {...rest}
        ref={ref}
        value={text}
        onChange={(e) => {
          onChange?.(encodeMentions(e.target.value, known()));
          refreshQuery(e.target.value, e.target.selectionStart);
        }}
        onSelect={(e) => refreshQuery(e.currentTarget.value, e.currentTarget.selectionStart)}
        onKeyDown={handleKeyDown}
        onBlur={(e) => {
          setQuery(null);
          onBlur?.(e);
        }}
        aria-autocomplete="list"
        aria-expanded={open}
      />
      {open && menuStyle && createPortal(
        <div
          data-mention-menu
          role="listbox"
          aria-label="Chọn người để nhắc tên"
          style={{
            ...menuStyle,
            zIndex: 10050,
            background: '#FFFFFF',
            border: '1px solid #E2E8F0',
            borderRadius: 8,
            boxShadow: '0 10px 28px rgba(15, 23, 42, 0.16)',
            padding: 4,
            maxHeight: MENU_HEIGHT,
            overflowY: 'auto',
            fontFamily: 'inherit',
          }}
        >
          {options.map((m, i) => {
            const name = memberDisplayName(m);
            const selected = i === active;
            return (
              <div
                key={m.userId}
                role="option"
                aria-selected={selected}
                onMouseDown={(e) => {
                  e.preventDefault(); // giữ con trỏ trong ô nhập
                  select(m);
                }}
                onMouseEnter={() => setActive(i)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 8, padding: '6px 8px', borderRadius: 6, cursor: 'pointer',
                  background: selected ? '#EFF6FF' : 'transparent',
                }}
              >
                <span style={{
                  width: 24, height: 24, borderRadius: '50%', background: '#DBEAFE', color: '#1D4ED8', fontSize: 11, fontWeight: 700,
                  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
                }}
                >
                  {name.charAt(0).toUpperCase()}
                </span>
                <span style={{ minWidth: 0, flex: 1 }}>
                  <span style={{ display: 'block', fontSize: 12.5, fontWeight: 600, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{name}</span>
                  {(m.email || m.username) && (
                    <span style={{ display: 'block', fontSize: 11, color: '#64748B', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{m.email || m.username}</span>
                  )}
                </span>
              </div>
            );
          })}
        </div>,
        document.body,
      )}
    </>
  );
}
