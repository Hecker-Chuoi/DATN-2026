import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Trash2, Pencil, Check, X } from 'lucide-react';
import { aiApi } from '../../api.js';

const CATEGORY_LABEL = { PREFERENCE: 'Sở thích', CONTEXT: 'Bối cảnh', HABIT: 'Thói quen' };

/** Tab "Bộ nhớ AI": bật/tắt bộ nhớ dài hạn, xem/sửa/xóa từng mục. */
export default function AiMemoryPanel() {
  const [enabled, setEnabled] = useState(true);
  const [items, setItems] = useState([]);
  const [editId, setEditId] = useState(null);
  const [draft, setDraft] = useState('');

  const load = async () => {
    try {
      const [s, m] = await Promise.all([aiApi.getSettings(), aiApi.listMemories()]);
      setEnabled(s?.data?.memoryEnabled !== false);
      setItems(Array.isArray(m?.data) ? m.data : []);
    } catch {
      // interceptor đã toast
    }
  };

  useEffect(() => {
    load();
  }, []);

  const toggle = async () => {
    try {
      const res = await aiApi.updateSettings({ memoryEnabled: !enabled });
      setEnabled(res?.data?.memoryEnabled !== false);
      toast.success(!enabled ? 'Đã bật bộ nhớ dài hạn' : 'Đã tắt bộ nhớ dài hạn');
    } catch {
      // đã toast
    }
  };

  const save = async (id) => {
    if (!draft.trim()) return;
    try {
      const res = await aiApi.updateMemory(id, { content: draft.trim() });
      setItems((prev) => prev.map((m) => (m.id === id ? res.data : m)));
      setEditId(null);
    } catch {
      // đã toast
    }
  };

  const remove = async (id) => {
    if (!window.confirm('Xóa mục ghi nhớ này?')) return;
    try {
      await aiApi.deleteMemory(id);
      setItems((prev) => prev.filter((m) => m.id !== id));
    } catch {
      // đã toast
    }
  };

  return (
    <div style={{ padding: 12, fontSize: 13, overflowY: 'auto', flex: 1 }}>
      <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', marginBottom: 12 }}>
        <input type="checkbox" checked={enabled} onChange={toggle} />
        <span style={{ fontWeight: 600 }}>Cho phép trợ lý ghi nhớ sở thích của tôi</span>
      </label>
      <p style={{ color: '#64748B', fontSize: 12, marginTop: 0 }}>
        Trợ lý chỉ lưu khi bạn yêu cầu rõ (ví dụ “nhớ là tôi phụ trách backend”). Các mục này được đưa vào ngữ cảnh mỗi lần chat.
      </p>

      {items.length === 0 && <div style={{ color: '#94A3B8', fontStyle: 'italic' }}>Chưa có mục ghi nhớ nào.</div>}

      {items.map((m) => (
        <div key={m.id} style={{ border: '1px solid #E2E8F0', borderRadius: 8, padding: 8, marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: '#64748B', marginBottom: 4 }}>
            {CATEGORY_LABEL[m.category] || m.category} · {m.scope === 'PROJECT' ? 'Theo dự án' : 'Chung'}
          </div>
          {editId === m.id ? (
            <div style={{ display: 'flex', gap: 6 }}>
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && save(m.id)}
                style={{ flex: 1, fontSize: 13, padding: '4px 6px', borderRadius: 6, border: '1px solid #CBD5E1' }}
                autoFocus
              />
              <button type="button" onClick={() => save(m.id)} title="Lưu" style={iconBtn}>
                <Check size={14} color="#059669" />
              </button>
              <button type="button" onClick={() => setEditId(null)} title="Huỷ" style={iconBtn}>
                <X size={14} />
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
              <div style={{ flex: 1 }}>{m.content}</div>
              <button
                type="button"
                onClick={() => {
                  setEditId(m.id);
                  setDraft(m.content);
                }}
                title="Sửa"
                style={iconBtn}
              >
                <Pencil size={14} />
              </button>
              <button type="button" onClick={() => remove(m.id)} title="Xóa" style={iconBtn}>
                <Trash2 size={14} color="#DC2626" />
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

const iconBtn = { background: 'none', border: 'none', cursor: 'pointer', padding: 2, color: '#64748B' };
