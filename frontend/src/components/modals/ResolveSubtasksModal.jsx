import React from 'react';
import { AlertTriangle, CheckCircle2, GitCommitHorizontal, X } from 'lucide-react';

export default function ResolveSubtasksModal({
  isOpen,
  parentCode,
  parentSummary,
  parentHierarchyLevel,
  subtasks = [],
  onConfirm,
  onCancel,
  loading = false,
}) {
  if (!isOpen) return null;

  const incompleteSubtasks = subtasks.filter(
    (st) => st.statusCategory !== 'DONE'
  );

  const isEpic = parentHierarchyLevel === 1;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.65)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10001,
        padding: 20,
      }}
      onClick={onCancel}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 560,
          backgroundColor: '#FFFFFF',
          borderRadius: 16,
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          border: '1px solid #E2E8F0',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          maxHeight: '85vh',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '18px 24px 14px 24px',
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            borderBottom: '1px solid #F1F5F9',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                backgroundColor: '#FFFBEB',
                color: '#D97706',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <AlertTriangle size={22} />
            </div>
            <div>
              <h2 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                {isEpic ? 'Công việc trực thuộc Epic chưa hoàn thành' : 'Nhiệm vụ con chưa hoàn thành'}
              </h2>
              <div style={{ fontSize: 13, color: '#64748B', marginTop: 2 }}>
                Công việc <strong style={{ color: '#0284C7' }}>[{parentCode}]</strong> còn{' '}
                <strong style={{ color: '#D97706' }}>{incompleteSubtasks.length}</strong> {isEpic ? 'công việc (Story & Sub-task)' : 'nhiệm vụ con'} chưa xong.
              </div>
            </div>
          </div>

          <button
            onClick={onCancel}
            style={{
              background: 'none',
              border: 'none',
              color: '#94A3B8',
              cursor: 'pointer',
              padding: 4,
              borderRadius: 6,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Body content */}
        <div
          style={{
            padding: '16px 24px',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
          }}
        >
          <div
            style={{
              padding: '10px 14px',
              borderRadius: 8,
              backgroundColor: '#FEF3C7',
              border: '1px solid #FDE68A',
              fontSize: 13,
              color: '#92400E',
              lineHeight: 1.5,
            }}
          >
            {isEpic
              ? 'Theo quy tắc phân tầng dự án, Epic chỉ có thể hoàn thành khi tất cả các User Story và Sub-task trực thuộc đã xong. Bạn có thể chọn đóng đồng loạt toàn bộ các công việc trực thuộc bên dưới:'
              : 'Theo quy tắc phân tầng dự án, công việc cha chỉ có thể hoàn thành khi tất cả các nhiệm vụ con đã xong. Bạn có thể chọn đóng đồng loạt toàn bộ nhiệm vụ con bên dưới:'}
          </div>

          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
              maxHeight: 260,
              overflowY: 'auto',
              paddingRight: 4,
            }}
          >
            {incompleteSubtasks.map((st) => {
              const isSub = st.isSubtask || st.hierarchyLevel === -1;
              const typeLabel = st.typeName || (isSub ? 'Sub-task' : 'Story');
              const typeColor = st.typeColor || (isSub ? '#0284C7' : '#059669');

              return (
                <div
                  key={st.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 12px',
                    borderRadius: 8,
                    backgroundColor: '#F8FAFC',
                    border: '1px solid #E2E8F0',
                    fontSize: 13,
                    gap: 10,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden', flex: 1 }}>
                    <GitCommitHorizontal size={14} color={typeColor} style={{ flexShrink: 0 }} />
                    <span
                      style={{
                        padding: '1px 5px',
                        borderRadius: 4,
                        fontSize: 10,
                        fontWeight: 700,
                        backgroundColor: `${typeColor}15`,
                        color: typeColor,
                        border: `1px solid ${typeColor}30`,
                        flexShrink: 0,
                      }}
                    >
                      {typeLabel}
                    </span>
                    <span style={{ fontWeight: 600, color: '#1E293B', flexShrink: 0 }}>
                      {st.code}
                    </span>
                    {st.parentCode && st.parentCode !== parentCode && (
                      <span style={{ fontSize: 11, color: '#64748B', flexShrink: 0 }}>
                        (thuộc {st.parentCode})
                      </span>
                    )}
                    <span
                      style={{
                        color: '#475569',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {st.summary}
                    </span>
                  </div>
                  <span
                    style={{
                      padding: '2px 8px',
                      borderRadius: 12,
                      fontSize: 11,
                      fontWeight: 600,
                      backgroundColor: st.statusColorHex ? `${st.statusColorHex}20` : '#E2E8F0',
                      color: st.statusColorHex || '#475569',
                      flexShrink: 0,
                    }}
                  >
                    {st.statusName || 'Đang làm'}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Actions */}
        <div
          style={{
            padding: '14px 24px',
            backgroundColor: '#F8FAFC',
            borderTop: '1px solid #F1F5F9',
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 10,
          }}
        >
          <button
            type="button"
            onClick={onCancel}
            disabled={loading}
            style={{
              padding: '8px 16px',
              borderRadius: 8,
              border: '1px solid #CBD5E1',
              backgroundColor: '#FFFFFF',
              color: '#475569',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Hủy bỏ
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={loading}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              padding: '8px 18px',
              borderRadius: 8,
              border: 'none',
              backgroundColor: '#16A34A',
              color: '#FFFFFF',
              fontSize: 13,
              fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
            }}
          >
            <CheckCircle2 size={15} />
            <span>
              {loading
                ? 'Đang cập nhật...'
                : isEpic
                ? 'Hoàn thành tất cả Story & Sub-task trực thuộc'
                : 'Hoàn thành tất cả nhiệm vụ con'}
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}