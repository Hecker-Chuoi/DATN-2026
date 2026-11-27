import { useCallback, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { aiApi } from '../api.js';

/**
 * State của một phiên chat với trợ lý AI: danh sách hội thoại, tin nhắn, gửi/nhận SSE, duyệt thẻ, huỷ.
 * Tin nhắn dạng hiển thị: { id, role: 'user'|'model', text, tools: [{id,name,summary,ok}],
 *   interactions: [{interactionId,type,toolName,summary,payload,status}], notices: [string], error }
 */
export function useAiChat({ workspaceId, projectId, workItemId }) {
  const [conversations, setConversations] = useState([]);
  const [conversationId, setConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [streaming, setStreaming] = useState(false);
  const abortRef = useRef(null);

  const context = useCallback(
    () => ({ workspaceId, projectId, workItemId, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone }),
    [workspaceId, projectId, workItemId]
  );

  const loadConversations = useCallback(async () => {
    try {
      const res = await aiApi.listConversations(projectId);
      setConversations(Array.isArray(res?.data) ? res.data : []);
    } catch {
      setConversations([]);
    }
  }, [projectId]);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  const openConversation = useCallback(async (id) => {
    setConversationId(id);
    try {
      const res = await aiApi.getMessages(id);
      const list = Array.isArray(res?.data) ? res.data : [];
      setMessages(list.map((m) => ({ ...m, tools: m.tools || [], interactions: m.interactions || [], notices: [] })));
    } catch {
      setMessages([]);
    }
  }, []);

  const newConversation = useCallback(() => {
    setConversationId(null);
    setMessages([]);
  }, []);

  /** Cập nhật tin MODEL cuối cùng (đang stream). */
  const patchLast = (fn) =>
    setMessages((prev) => {
      if (!prev.length) return prev;
      const next = prev.slice();
      next[next.length - 1] = fn(next[next.length - 1]);
      return next;
    });

  const handlers = () => ({
    text_delta: (d) => patchLast((m) => ({ ...m, text: (m.text || '') + (d.text || '') })),
    tool_started: (d) => patchLast((m) => ({ ...m, tools: [...m.tools, { id: d.id, name: d.name, summary: d.summary, ok: null }] })),
    tool_finished: (d) =>
      patchLast((m) => ({ ...m, tools: m.tools.map((t) => (t.id === d.id ? { ...t, ok: d.ok } : t)) })),
    interaction_required: (d) =>
      patchLast((m) => ({ ...m, interactions: [...m.interactions, { ...d, status: 'PENDING' }] })),
    notice: (d) => patchLast((m) => ({ ...m, notices: [...m.notices, d.message] })),
    done: (d) => patchLast((m) => ({ ...m, doneStatus: d.status })),
    error: (d) => {
      patchLast((m) => ({ ...m, error: d.message }));
      toast.error(d.message || 'Trợ lý AI gặp lỗi');
    },
  });

  const runStream = async (starter) => {
    const controller = new AbortController();
    abortRef.current = controller;
    setStreaming(true);
    setMessages((prev) => [...prev, { id: 'model-' + Date.now(), role: 'model', text: '', tools: [], interactions: [], notices: [] }]);
    try {
      await starter(handlers(), controller.signal);
    } catch (err) {
      if (err?.name === 'AbortError') {
        patchLast((m) => ({ ...m, doneStatus: 'CANCELLED' }));
      } else {
        const msg = err?.status === 429 ? 'Trợ lý AI đang quá tải, vui lòng thử lại sau.' : err?.message || 'Không kết nối được trợ lý AI';
        patchLast((m) => ({ ...m, error: msg }));
        toast.error(msg);
      }
    } finally {
      setStreaming(false);
      abortRef.current = null;
      loadConversations();
    }
  };

  const send = useCallback(
    async (text) => {
      const trimmed = (text || '').trim();
      if (!trimmed || streaming) return;
      let id = conversationId;
      if (!id) {
        try {
          const res = await aiApi.createConversation({ workspaceId, projectId });
          id = res?.data?.id;
          setConversationId(id);
        } catch {
          return;
        }
      }
      // Thẻ đang chờ bị bỏ qua khi gửi tin mới (backend đánh dấu EXPIRED)
      setMessages((prev) => [
        ...prev.map((m) => ({ ...m, interactions: m.interactions.map((i) => (i.status === 'PENDING' ? { ...i, status: 'EXPIRED' } : i)) })),
        { id: 'user-' + Date.now(), role: 'user', text: trimmed, tools: [], interactions: [], notices: [] },
      ]);
      await runStream((h, signal) => aiApi.streamMessage(id, { text: trimmed, context: context() }, h, signal));
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [conversationId, streaming, workspaceId, projectId, context]
  );

  const answer = useCallback(
    async (interactionId, body, localStatus) => {
      if (!conversationId || streaming) return;
      setMessages((prev) =>
        prev.map((m) => ({
          ...m,
          interactions: m.interactions.map((i) => (i.interactionId === interactionId ? { ...i, status: localStatus } : i)),
        }))
      );
      await runStream((h, signal) => aiApi.answerInteraction(conversationId, interactionId, { ...body, context: context() }, h, signal));
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [conversationId, streaming, context]
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return { conversations, conversationId, messages, streaming, send, answer, stop, openConversation, newConversation, loadConversations };
}
