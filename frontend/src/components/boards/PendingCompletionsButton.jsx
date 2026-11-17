import { useEffect, useRef, useState } from 'react';
import { Hourglass, ChevronDown } from 'lucide-react';
import { workItemApi } from '../../api.js';
import { getStatusDisplayName } from '../../constants/workConstants.js';

const formatDateTime = (value) => {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' });
};

/**
 * Nút "Chờ xác nhận (n)": các công việc đang "Tạm hoàn thành" của dự án (cả trong Sprint lẫn Backlog).
 * Bấm một dòng để mở chi tiết — Admin dự án xác nhận / từ chối ngay trong modal.
 * Ẩn khi không có yêu cầu nào.
 */
export default function PendingCompletionsButton({ projectId, refreshKey, isAdmin, onOpenItem }) {
  const [requests, setRequests] = useState([]);
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  useEffect(() => {
    if (!projectId) return undefined;
    let cancelled = false;
    workItemApi
      .listCompletionRequests(projectId)
      .then((res) => {
        if (!cancelled) setRequests(Array.isArray(res?.data) ? res.data : []);
      })
      .catch((err) => console.debug('Không tải được yêu cầu xác nhận hoàn thành:', err));
    return () => {
      cancelled = true;
    };
  }, [projectId, refreshKey]);

  useEffect(() => {
    if (!open) return undefined;
    const onDocClick = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  if (requests.length === 0) return null;

  return (
    <div ref={wrapRef} style={{ position: 'relative' }}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        title={isAdmin ? 'Các công việc đang chờ bạn xác nhận hoàn thành' : 'Các công việc đang chờ Admin dự án xác nhận hoàn thành'}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 5,
          padding: '4px 10px',
          borderRadius: 6,
          border: '1px solid #FCD34D',
          backgroundColor: '#FFFBEB',
          color: '#B45309',
          fontSize: 12,
          fontWeight: 700,
          cursor: 'pointer',
        }}
      >
        <Hourglass size={13} />
        Chờ xác nhận HT ({requests.length})
        <ChevronDown size={12} />
      </button>

      {open && (
        <div
          style={{
            position: 'absolute',
            right: 0,
            top: 'calc(100% + 6px)',
            width: 340,
            maxWidth: 'calc(100vw - 32px)',
            maxHeight: 360,
            overflowY: 'auto',
            backgroundColor: '#FFFFFF',
            border: '1px solid #E2E8F0',
            borderRadius: 8,
            boxShadow: '0 10px 25px rgba(15,23,42,0.12)',
            zIndex: 30,
          }}
        >
          <div style={{ padding: '8px 12px', fontSize: 11, color: '#64748B', borderBottom: '1px solid #F1F5F9' }}>
            {isAdmin
              ? 'Mở công việc để Xác nhận hoặc Từ chối.'
              : 'Chỉ Admin dự án mới xác nhận được các công việc này.'}
          </div>
          {requests.map((r) => (
            <button
              key={r.id}
              type="button"
              onClick={() => {
                setOpen(false);
                onOpenItem?.(r.workItemId);
              }}
              style={{
                display: 'block',
                width: '100%',
                textAlign: 'left',
                padding: '8px 12px',
                border: 'none',
                borderBottom: '1px solid #F1F5F9',
                backgroundColor: 'transparent',
                cursor: 'pointer',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
            >
              <div style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                <span style={{ color: '#64748B', marginRight: 6 }}>{r.workItemCode}</span>
                {r.workItemSummary}
              </div>
              <div style={{ fontSize: 11, color: '#64748B', marginTop: 2 }}>
                {r.requestedByName || 'Thành viên'} · {formatDateTime(r.requestedAt)}
                {r.targetStatusName ? ` → ${getStatusDisplayName(r.targetStatusName)}` : ''}
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
