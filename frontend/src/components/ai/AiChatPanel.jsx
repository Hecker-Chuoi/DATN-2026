import { useEffect, useRef, useState } from 'react';
import { Bot, X, Plus, History, Brain, Send, Square, MessageSquare, Sparkles } from 'lucide-react';
import { useAiPanel } from '../../context/AiChatContext.jsx';
import { useAiChat } from '../../hooks/useAiChat.js';
import AiMessage from './AiMessage.jsx';
import AiMemoryPanel from './AiMemoryPanel.jsx';

const SUGGESTIONS = [
  'Sprint hiện tại còn bao nhiêu point?',
  'Hôm nay tôi có những việc gì?',
  'Ai đang làm nhiều việc nhất trong dự án?',
  'Tạo 3 subtask cho công việc đang mở',
];

const iconBtn = {
  background: 'transparent',
  border: 'none',
  width: 28,
  height: 28,
  borderRadius: 6,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
  color: '#64748B',
};

/** Nút nổi mở/đóng khung chat (đặt trong WorkspaceLayout). */
export function AiLaunchButton() {
  const panel = useAiPanel();
  if (!panel || panel.open) return null;
  return (
    <button
      type="button"
      onClick={panel.toggle}
      title="Trợ lý AI"
      style={{
        position: 'fixed',
        right: 20,
        bottom: 20,
        zIndex: 900,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        background: '#2563EB',
        color: '#fff',
        border: 'none',
        borderRadius: 999,
        padding: '10px 16px',
        fontSize: 13,
        fontWeight: 600,
        cursor: 'pointer',
        boxShadow: '0 10px 15px -3px rgba(37,99,235,.35)',
      }}
    >
      <Sparkles size={16} /> Trợ lý AI
    </button>
  );
}

/**
 * Khung chat bên phải. Luôn mount (ẩn bằng display:none) để giữ hội thoại khi đóng/mở.
 * Ngữ cảnh gửi kèm mỗi tin: workspaceId, projectId (từ route) và workItemId (modal đang mở).
 */
export default function AiChatPanel({ workspaceId, projectId, projectName }) {
  const panel = useAiPanel();
  const chat = useAiChat({ workspaceId, projectId, workItemId: panel?.workItemId || null });
  const [view, setView] = useState('chat'); // chat | history | memory
  const [input, setInput] = useState('');
  const listRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [chat.messages, view]);

  useEffect(() => {
    if (panel?.open && view === 'chat') inputRef.current?.focus();
  }, [panel?.open, view]);

  if (!panel) return null;

  const submit = () => {
    const text = input;
    setInput('');
    chat.send(text);
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!chat.streaming) submit();
    }
  };

  return (
    <aside
      style={{
        display: panel.open ? 'flex' : 'none',
        flexDirection: 'column',
        width: 400,
        minWidth: 400,
        height: '100%',
        borderLeft: '1px solid #E2E8F0',
        background: '#FFFFFF',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      <style>{'@keyframes ai-spin{to{transform:rotate(360deg)}} .ai-spin{animation:ai-spin 1s linear infinite}'}</style>

      {/* ─── Header ─── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px', borderBottom: '1px solid #E2E8F0' }}>
        <Bot size={18} color="#2563EB" />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>Trợ lý AI</div>
          <div style={{ fontSize: 11, color: '#64748B', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {projectId ? `Dự án: ${projectName || '…'}` : 'Chưa mở dự án – mở một dự án để dùng công cụ dự án'}
            {panel.workItemId ? ' · đang xem 1 công việc' : ''}
          </div>
        </div>
        <button type="button" style={iconBtn} title="Hội thoại mới" onClick={() => { chat.newConversation(); setView('chat'); }}>
          <Plus size={16} />
        </button>
        <button type="button" style={{ ...iconBtn, color: view === 'history' ? '#2563EB' : iconBtn.color }} title="Lịch sử hội thoại" onClick={() => setView(view === 'history' ? 'chat' : 'history')}>
          <History size={16} />
        </button>
        <button type="button" style={{ ...iconBtn, color: view === 'memory' ? '#2563EB' : iconBtn.color }} title="Bộ nhớ AI" onClick={() => setView(view === 'memory' ? 'chat' : 'memory')}>
          <Brain size={16} />
        </button>
        <button type="button" style={iconBtn} title="Đóng" onClick={() => panel.setOpen(false)}>
          <X size={16} />
        </button>
      </div>

      {/* ─── Body ─── */}
      {view === 'memory' && <AiMemoryPanel />}

      {view === 'history' && (
        <div style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
          {chat.conversations.length === 0 && (
            <div style={{ color: '#94A3B8', fontSize: 13, fontStyle: 'italic', padding: 8 }}>Chưa có hội thoại nào.</div>
          )}
          {chat.conversations.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => { chat.openConversation(c.id); setView('chat'); }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                width: '100%',
                textAlign: 'left',
                background: c.id === chat.conversationId ? '#EFF6FF' : 'transparent',
                border: 'none',
                borderRadius: 8,
                padding: '8px 10px',
                cursor: 'pointer',
                fontSize: 13,
                color: '#0F172A',
              }}
            >
              <MessageSquare size={14} color="#64748B" />
              <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.title || 'Hội thoại mới'}</span>
              {c.status === 'WAITING_INPUT' && <span style={{ fontSize: 10, color: '#B45309' }}>đang chờ</span>}
            </button>
          ))}
        </div>
      )}

      {view === 'chat' && (
        <>
          <div ref={listRef} style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
            {chat.messages.length === 0 && (
              <div style={{ color: '#64748B', fontSize: 13 }}>
                <p style={{ marginTop: 0 }}>Hỏi về công việc, sprint, báo cáo hoặc nhờ tôi tạo/sửa công việc (mọi thay đổi đều chờ bạn duyệt).</p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {SUGGESTIONS.map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => chat.send(s)}
                      style={{ textAlign: 'left', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, padding: '8px 10px', fontSize: 12, cursor: 'pointer', color: '#0F172A' }}
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}
            {chat.messages.map((m, i) => (
              <AiMessage
                key={m.id}
                message={m}
                streaming={chat.streaming}
                isLast={i === chat.messages.length - 1}
                onOpenCode={panel.openItemByCode}
                onAnswer={chat.answer}
              />
            ))}
          </div>

          <div style={{ borderTop: '1px solid #E2E8F0', padding: 10, display: 'flex', gap: 8, alignItems: 'flex-end' }}>
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKeyDown}
              rows={2}
              maxLength={4000}
              placeholder="Nhập câu hỏi… (Enter gửi, Shift+Enter xuống dòng)"
              style={{ flex: 1, resize: 'none', fontSize: 13, padding: '8px 10px', borderRadius: 8, border: '1px solid #CBD5E1', fontFamily: 'inherit' }}
            />
            {chat.streaming ? (
              <button type="button" onClick={chat.stop} title="Dừng" style={{ ...iconBtn, width: 36, height: 36, background: '#FEE2E2', color: '#B91C1C' }}>
                <Square size={16} />
              </button>
            ) : (
              <button type="button" onClick={submit} disabled={!input.trim()} title="Gửi" style={{ ...iconBtn, width: 36, height: 36, background: '#2563EB', color: '#fff', opacity: input.trim() ? 1 : 0.5 }}>
                <Send size={16} />
              </button>
            )}
          </div>
        </>
      )}
    </aside>
  );
}
