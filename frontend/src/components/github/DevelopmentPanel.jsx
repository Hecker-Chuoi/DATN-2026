import { useEffect, useRef, useState } from 'react';
import { GitBranch, GitPullRequest, CircleDot, Copy, Check, Loader2, ChevronRight, X } from 'lucide-react';
import toast from 'react-hot-toast';
import { githubIntegrationApi } from '../../api.js';

const C = {
  accent: '#2563EB',
  text: '#0F172A',
  textSoft: '#334155',
  muted: '#64748B',
  border: '#E2E8F0',
  divider: '#F1F5F9',
  ok: '#059669',
  idle: '#94A3B8',
  merged: '#7C3AED',
  warn: '#B45309',
};

const LINK_STATUS = {
  OPEN: { color: C.ok, label: 'Đang mở' },
  MERGED: { color: C.merged, label: 'Đã merge' },
  CLOSED: { color: C.idle, label: 'Đã đóng' },
};

const POPOVER_WIDTH = 380;
const GAP = 8;

const smallButton = {
  height: 30,
  padding: '0 10px',
  borderRadius: 6,
  fontSize: 12,
  fontWeight: 600,
  display: 'inline-flex',
  alignItems: 'center',
  gap: 5,
  boxSizing: 'border-box',
  whiteSpace: 'nowrap',
  fontFamily: 'inherit',
  border: `1px solid ${C.border}`,
  background: '#FFFFFF',
  color: C.text,
};

const mono = { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12 };

/**
 * Mục "Phát triển" ở cột phải chi tiết Task, dành cho người quản lý: một ô tóm tắt ("2 PR · 1 Issue"),
 * bấm vào mở bảng nổi gồm quy ước gửi cho dev (tên nhánh, cách ghi PR hoàn thành), nút Tạo Issue để giao việc
 * trên GitHub (assign luôn cho người được giao nếu họ đã liên kết GitHub) và danh sách PR / Issue kèm người làm;
 * PR do người khác mở (không phải người được giao) có nhãn cảnh báo. Dev tự tạo nhánh / PR trên GitHub; hệ thống nghe webhook.
 * Ẩn hoàn toàn khi dự án chưa liên kết repo. Cha nên truyền key={itemId}.
 */
export default function DevelopmentPanel({ itemId, githubLinks = [], canEdit, labelStyle }) {
  const [info, setInfo] = useState(null);
  const [creatingIssue, setCreatingIssue] = useState(false);
  const [copied, setCopied] = useState(false);
  const [popover, setPopover] = useState(null); // { top, left } khi đang mở
  const triggerRef = useRef(null);
  const popoverRef = useRef(null);

  useEffect(() => {
    if (!itemId) return undefined;
    let cancelled = false;
    githubIntegrationApi
      .getDevelopment(itemId, { skipErrorToast: true })
      .then((res) => {
        if (!cancelled) setInfo(res?.data || null);
      })
      .catch(() => {
        if (!cancelled) setInfo(null);
      });
    return () => {
      cancelled = true;
    };
  }, [itemId]);

  // Đóng bảng nổi khi bấm ra ngoài, nhấn Esc, cuộn hoặc đổi kích thước cửa sổ
  useEffect(() => {
    if (!popover) return undefined;
    const close = () => setPopover(null);
    const onPointer = (e) => {
      if (popoverRef.current?.contains(e.target) || triggerRef.current?.contains(e.target)) return;
      close();
    };
    const onKey = (e) => {
      if (e.key === 'Escape') {
        close();
        triggerRef.current?.focus();
      }
    };
    const onScroll = (e) => {
      if (popoverRef.current?.contains(e.target)) return;
      close();
    };
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    window.addEventListener('scroll', onScroll, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
      window.removeEventListener('scroll', onScroll, true);
      window.removeEventListener('resize', close);
    };
  }, [popover]);

  // Tải lại mỗi lần mở bảng để thấy người được giao / PR mới nhất
  const reload = () => {
    githubIntegrationApi
      .getDevelopment(itemId, { skipErrorToast: true })
      .then((res) => {
        if (res?.data) setInfo(res.data);
      })
      .catch(() => {});
  };

  if (!info?.linked) return null;

  // Ưu tiên danh sách mới nhất từ server (vừa tạo Issue xong là có), dự phòng bằng dữ liệu chi tiết Task
  const allLinks = Array.isArray(info.links) ? info.links : githubLinks;
  const pullRequests = allLinks.filter((l) => l.linkType === 'PULL_REQUEST');
  const issues = allLinks.filter((l) => l.linkType === 'ISSUE');
  const linked = [...pullRequests, ...issues];

  const summaryParts = [];
  if (pullRequests.length) summaryParts.push(`${pullRequests.length} PR`);
  if (issues.length) summaryParts.push(`${issues.length} Issue`);
  const summary = summaryParts.length ? summaryParts.join(' · ') : 'Chưa có PR hay Issue';

  const assigneeText = !info.assigneeId
    ? 'Chưa giao'
    : `${info.assigneeName || 'Thành viên dự án'}${info.assigneeGithubLogin ? ` (@${info.assigneeGithubLogin})` : ''}`;

  const convention = [
    `Người làm: ${assigneeText}`,
    `Nhánh: ${info.branchName}`,
    `Hoàn thành: ghi "${info.closeExample}" trong tiêu đề PR, merge vào ${info.baseBranch}`,
  ].join('\n');

  const togglePopover = () => {
    if (popover) {
      setPopover(null);
      return;
    }
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    // Bảng nổi nằm bên trái cột phải để không bị khung cuộn của cột cắt mất
    const left = Math.max(GAP, rect.left - POPOVER_WIDTH - GAP);
    const top = Math.max(GAP, Math.min(rect.top, window.innerHeight - 380));
    setPopover({ top, left });
    reload();
  };

  const handleCreateIssue = async () => {
    setCreatingIssue(true);
    try {
      const res = await githubIntegrationApi.createIssue(itemId, { skipErrorToast: true });
      if (res?.data) setInfo(res.data);
      toast.success('Đã tạo Issue trên GitHub');
    } catch (err) {
      toast.error(err?.message || 'Không tạo được Issue');
    } finally {
      setCreatingIssue(false);
    }
  };

  const handleCopyConvention = async () => {
    try {
      await navigator.clipboard.writeText(convention);
      setCopied(true);
      toast.success('Đã sao chép quy ước', { id: 'dev-copy' });
      setTimeout(() => setCopied(false), 1500);
    } catch {
      toast.error('Không sao chép được');
    }
  };

  return (
    <div>
      <span style={labelStyle}>Phát triển</span>
      <button
        ref={triggerRef}
        type="button"
        onClick={togglePopover}
        aria-haspopup="dialog"
        aria-expanded={!!popover}
        style={{
          marginTop: 6,
          width: '100%',
          height: 40,
          padding: '0 12px',
          border: `1px solid ${popover ? C.accent : C.border}`,
          borderRadius: 8,
          background: '#FFFFFF',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          fontSize: 14,
          fontFamily: 'inherit',
          color: summaryParts.length ? C.text : C.muted,
          cursor: 'pointer',
          textAlign: 'left',
          boxSizing: 'border-box',
        }}
      >
        <GitBranch size={15} color={C.muted} aria-hidden="true" />
        <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{summary}</span>
        <ChevronRight size={15} color={C.muted} aria-hidden="true" />
      </button>

      {popover && (
        <div
          ref={popoverRef}
          role="dialog"
          aria-label="Phát triển"
          style={{
            position: 'fixed',
            top: popover.top,
            left: popover.left,
            width: POPOVER_WIDTH,
            maxHeight: `calc(100vh - ${popover.top + GAP}px)`,
            overflowY: 'auto',
            background: '#FFFFFF',
            border: `1px solid ${C.border}`,
            borderRadius: 10,
            boxShadow: '0 12px 32px rgba(15, 23, 42, 0.14)',
            zIndex: 2000,
            fontSize: 13,
            color: C.text,
          }}
        >
          {/* Tiêu đề */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 6px 8px 14px', borderBottom: `1px solid ${C.divider}` }}>
            <span style={{ flex: 1, fontSize: 14, fontWeight: 600 }}>Phát triển</span>
            <a
              href={info.repoUrl}
              target="_blank"
              rel="noreferrer"
              style={{ fontSize: 12, color: C.muted, textDecoration: 'none', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 200 }}
            >
              {info.repoFullName}
            </a>
            <button
              type="button"
              onClick={() => setPopover(null)}
              aria-label="Đóng bảng Phát triển"
              style={{ width: 32, height: 32, border: 0, borderRadius: 6, background: 'transparent', color: C.muted, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            >
              <X size={15} />
            </button>
          </div>

          {/* Quy ước cho dev */}
          <div style={{ padding: '10px 14px 12px', display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 12, color: C.muted }}>Quy ước cho dev</span>
            <div style={{ display: 'grid', gridTemplateColumns: '80px minmax(0, 1fr)', rowGap: 4, columnGap: 8, alignItems: 'baseline' }}>
              <span style={{ fontSize: 12, color: C.muted }}>Người làm</span>
              {!info.assigneeId ? (
                <span style={{ fontSize: 12, color: C.muted }}>Chưa giao</span>
              ) : (
                <span style={{ fontSize: 12, color: C.text, minWidth: 0 }}>
                  {info.assigneeName || 'Thành viên dự án'}{' '}
                  {info.assigneeGithubLogin ? (
                    <span style={{ ...mono, color: C.muted }}>@{info.assigneeGithubLogin}</span>
                  ) : (
                    <span style={{ color: C.warn }} title="Người này chưa bấm Liên kết GitHub nên Issue không assign được và không nhận ra PR của họ">
                      · chưa liên kết GitHub
                    </span>
                  )}
                </span>
              )}
              <span style={{ fontSize: 12, color: C.muted }}>Nhánh</span>
              <span title={info.branchName} style={{ ...mono, color: C.text, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {info.branchName}
              </span>
              <span style={{ fontSize: 12, color: C.muted }}>Hoàn thành</span>
              <span style={{ fontSize: 12, color: C.textSoft }}>
                ghi <span style={{ ...mono, color: C.text }}>{info.closeExample}</span> trong tiêu đề PR, merge vào{' '}
                <span style={mono}>{info.baseBranch}</span>
              </span>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
              <button type="button" onClick={handleCopyConvention} style={{ ...smallButton, cursor: 'pointer' }}
                title="Sao chép người làm, tên nhánh và cách ghi PR để gửi cho dev">
                {copied ? <Check size={12} color={C.ok} /> : <Copy size={12} color={C.muted} />}
                Sao chép quy ước
              </button>
              {!info.issueLinked && (
                <button
                  type="button"
                  onClick={handleCreateIssue}
                  disabled={!canEdit || creatingIssue}
                  title={canEdit
                    ? 'Tạo Issue trên GitHub từ tiêu đề và mô tả Task để giao việc cho dev, gắn hai chiều (đóng Issue ↔ Task hoàn thành)'
                    : 'Cần quyền sửa công việc để tạo Issue'}
                  style={{
                    ...smallButton,
                    cursor: !canEdit || creatingIssue ? 'not-allowed' : 'pointer',
                    opacity: !canEdit ? 0.45 : creatingIssue ? 0.7 : 1,
                  }}
                >
                  {creatingIssue ? <Loader2 size={12} style={{ animation: 'spin 0.8s linear infinite' }} /> : <CircleDot size={12} color={C.muted} />}
                  Tạo Issue
                </button>
              )}
            </div>
          </div>

          {/* Đã liên kết */}
          <div style={{ padding: '8px 14px 4px', fontSize: 12, color: C.muted, borderTop: `1px solid ${C.divider}` }}>
            {linked.length ? 'Đã liên kết' : 'Chưa có PR hay Issue. PR của dev nhắc mã Task sẽ tự hiện ở đây.'}
          </div>
          {linked.map((l) => {
            const isPr = l.linkType === 'PULL_REQUEST';
            const st = LINK_STATUS[l.status] || LINK_STATUS.OPEN;
            const Icon = isPr ? GitPullRequest : CircleDot;
            return (
              <a
                key={l.id || `${l.linkType}-${l.githubNumber}`}
                href={l.url}
                target="_blank"
                rel="noreferrer"
                title={l.title || ''}
                style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 14px', minHeight: 40, color: C.text, textDecoration: 'none' }}
              >
                <Icon size={14} color={C.muted} aria-hidden="true" style={{ flexShrink: 0 }} />
                <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12 }}>
                    <span style={{ color: C.muted }}>{isPr ? 'PR' : 'Issue'} #{l.githubNumber}</span> {l.title}
                  </span>
                  {l.authorName && (
                    <span style={{ fontSize: 11, color: C.muted }}>
                      {l.authorName}
                      {l.authorNotAssignee && (
                        <span style={{ color: C.warn }} title="Người mở PR không phải tài khoản GitHub của người được giao Task">
                          {' '}· không phải người được giao
                        </span>
                      )}
                    </span>
                  )}
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12, color: C.muted, flexShrink: 0 }}>
                  <span style={{ width: 6, height: 6, borderRadius: '50%', background: st.color }} />
                  {st.label}
                </span>
              </a>
            );
          })}
          <div style={{ height: 6 }} />
        </div>
      )}
    </div>
  );
}
