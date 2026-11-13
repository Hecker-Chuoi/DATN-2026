import { Hourglass, ShieldCheck, Undo2, X } from 'lucide-react';
import { getStatusDisplayName } from '../../constants/workConstants.js';

const formatDateTime = (value) => {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' });
};

/**
 * Khung "Tạm hoàn thành": công việc đã được báo hoàn thành nhưng CHƯA đổi trạng thái,
 * chờ Admin dự án xác nhận. Admin thấy nút Xác nhận / Từ chối; người gửi thấy nút Rút lại.
 * Backend vẫn kiểm tra lại quyền (WorkItemCompletionController).
 */
export default function CompletionPendingPanel({
  request,
  canConfirm,
  isRequester,
  busy,
  showRejectForm,
  rejectReason,
  onRejectReasonChange,
  onToggleRejectForm,
  onApprove,
  onReject,
  onCancel,
}) {
  return (
    <div
      style={{
        borderRadius: 8,
        border: '1px solid #FCD34D',
        backgroundColor: '#FFFBEB',
        padding: '12px',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#B45309', fontWeight: 700, fontSize: 13 }}>
        <Hourglass size={15} />
        <span>Tạm hoàn thành – chờ Admin xác nhận</span>
      </div>

      <div style={{ fontSize: 12, color: '#78350F', lineHeight: 1.5 }}>
        <strong>{request.requestedByName || 'Thành viên'}</strong> báo hoàn thành
        {request.requestedAt ? ` lúc ${formatDateTime(request.requestedAt)}` : ''}
        {request.targetStatusName ? (
          <>
            {' '}→ <strong>{getStatusDisplayName(request.targetStatusName)}</strong>
          </>
        ) : null}
        .
        {request.resolution === 'DUPLICATE' && <> Kết quả đề xuất: <strong>Trùng lặp</strong>.</>}
        {request.resolveAllSubtasks && <> Hoàn thành luôn các công việc con khi được xác nhận.</>}
      </div>

      {request.requestNote && (
        <div
          style={{
            fontSize: 12,
            color: '#57534E',
            backgroundColor: '#FFFFFF',
            border: '1px solid #FDE68A',
            borderRadius: 6,
            padding: '6px 8px',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
          }}
        >
          “{request.requestNote}”
        </div>
      )}

      {canConfirm ? (
        showRejectForm ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <textarea
              value={rejectReason}
              onChange={(e) => onRejectReasonChange(e.target.value)}
              placeholder="Lý do từ chối (người làm sẽ thấy trong phần Bình luận)…"
              rows={3}
              maxLength={1000}
              style={{
                width: '100%',
                boxSizing: 'border-box',
                padding: '6px 8px',
                fontSize: 12,
                borderRadius: 6,
                border: '1px solid #FCA5A5',
                resize: 'vertical',
                fontFamily: 'inherit',
                outline: 'none',
              }}
            />
            <div style={{ display: 'flex', gap: 6 }}>
              <button type="button" disabled={busy} onClick={onReject} style={btn('#DC2626', '#FFFFFF', '#DC2626', busy)}>
                Gửi từ chối
              </button>
              <button type="button" disabled={busy} onClick={() => onToggleRejectForm(false)} style={btn('#FFFFFF', '#475569', '#CBD5E1', busy)}>
                Hủy
              </button>
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', gap: 6 }}>
            <button type="button" disabled={busy} onClick={onApprove} style={btn('#16A34A', '#FFFFFF', '#16A34A', busy)}>
              <ShieldCheck size={14} /> Xác nhận hoàn thành
            </button>
            <button type="button" disabled={busy} onClick={() => onToggleRejectForm(true)} style={btn('#FFFFFF', '#DC2626', '#FCA5A5', busy)}>
              <X size={14} /> Từ chối
            </button>
          </div>
        )
      ) : (
        <>
          <div style={{ fontSize: 11, color: '#92400E' }}>
            Chỉ Admin dự án mới xác nhận được. Điểm đóng góp chỉ được ghi khi công việc được xác nhận.
          </div>
          {isRequester && (
            <button type="button" disabled={busy} onClick={onCancel} style={btn('#FFFFFF', '#92400E', '#FCD34D', busy)}>
              <Undo2 size={14} /> Rút lại yêu cầu
            </button>
          )}
        </>
      )}
    </div>
  );
}

const btn = (bg, color, border, disabled) => ({
  flex: 1,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  gap: 5,
  padding: '7px 10px',
  borderRadius: 6,
  border: `1px solid ${border}`,
  backgroundColor: bg,
  color,
  fontSize: 12,
  fontWeight: 700,
  cursor: disabled ? 'not-allowed' : 'pointer',
  opacity: disabled ? 0.6 : 1,
});
