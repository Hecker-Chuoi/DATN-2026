import { useEffect, useRef, useState } from 'react';
import { GitBranch, GitPullRequest, CircleDot, Copy, Check, ExternalLink, Loader2, ChevronRight, X } from 'lucide-react';
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
  textDecoration: 'none',
  boxSizing: 'border-box',
  whiteSpace: 'nowrap',
  fontFamily: 'inherit',
};

const secondaryStyle = { ...smallButton, border: `1px solid ${C.border}`, background: '#FFFFFF', color: C.text, cursor: 'pointer' };

const mono = { fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12 };

/**
 * Mục "Phát triển" ở cột phải chi tiết Task: chỉ là một ô tóm tắt (VD "1 nhánh · 2 PR · 1 Issue"),
 * bấm vào mới mở bảng nổi bên trái với nhánh, nút tạo nhánh / PR và danh sách liên kết.
 * Ẩn hoàn toàn khi dự án chưa liên kết repo. Cha nên truyền key={itemId}.
 */
export default function DevelopmentPanel({ itemId, githubLinks = [], canEdit, labelStyle }) {
  const [info, setInfo] = useState(null);
  const [creating, setCreating] = useState(false);
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

  if (!info?.linked) return null;

  // Ưu tiên danh sách mới nhất từ server (vừa tạo Issue xong là có), dự phòng bằng dữ liệu chi tiết Task
  const allLinks = Array.isArray(info.links) ? info.links : githubLinks;
  const pullRequests = allLinks.filter((l) => l.linkType === 'PULL_REQUEST');
  const issues = allLinks.filter((l) => l.linkType === 'ISSUE');
  const linked = [...pullRequests, ...issues];

  const summaryParts = [];
  if (info.branchCreated) summaryParts.push('1 nhánh');
  if (pullRequests.length) summaryParts.push(`${pullRequests.length} PR`);
  if (issues.length) summaryParts.push(`${issues.length} Issue`);
  const summary = summaryParts.length ? summaryParts.join(' · ') : 'Tạo nhánh, tạo PR…';

  const togglePopover = () => {
    if (popover) {
      setPopover(null);
      return;
    }
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    // Bảng nổi nằm bên trái cột phải để không bị khung cuộn của cột cắt mất
    const left = Math.max(GAP, rect.left - POPOVER_WIDTH - GAP);
    const top = Math.max(GAP, Math.min(rect.top, window.innerHeight - 420));
    setPopover({ top, left });
  };

  const handleCreateBranch = async () => {
    setCreating(true);
    try {
      const res = await githubIntegrationApi.createBranch(itemId, { skipErrorToast: true });
      if (res?.data) setInfo(res.data);
      toast.success('Đã tạo nhánh trên GitHub');
    } catch (err) {
      toast.error(err?.message || 'Không tạo được nhánh');
    } finally {
      setCreating(false);
    }
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

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(info.checkoutCommand);
      setCopied(true);
      toast.success('Đã sao chép lệnh', { id: 'dev-copy' });
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
            <span style={{ fontSize: 12, color: C.muted, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 200 }}>
              {info.repoFullName}
            </span>
            <button
              type="button"
              onClick={() => setPopover(null)}
              aria-label="Đóng bảng Phát triển"
              style={{ width: 32, height: 32, border: 0, borderRadius: 6, background: 'transparent', color: C.muted, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            >
              <X size={15} />
            </button>
          </div>

          {/* Nhánh */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 6px 8px 14px', minHeight: 48, boxSizing: 'border-box' }}>
            <GitBranch size={14} color={C.muted} aria-hidden="true" style={{ flexShrink: 0 }} />
            <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
              {info.branchCreated ? (
                <a href={info.branchUrl} target="_blank" rel="noreferrer" title={`Mở nhánh ${info.branchName} trên GitHub`}
                  style={{ ...mono, color: C.text, textDecoration: 'none', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {info.branchName}
                </a>
              ) : (
                <span title={info.branchName} style={{ ...mono, color: C.textSoft, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {info.branchName}
                </span>
              )}
              <span style={{ fontSize: 12, color: C.muted }}>
                {info.branchCreated ? `Tách từ ${info.baseBranch}` : `Chưa tạo · sẽ tách từ ${info.baseBranch}`}
              </span>
            </span>
            <button
              type="button"
              onClick={handleCopy}
              aria-label="Sao chép lệnh git checkout"
              title={info.checkoutCommand}
              style={{ width: 32, height: 32, flexShrink: 0, border: 0, borderRadius: 6, background: 'transparent', color: C.muted, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            >
              {copied ? <Check size={14} color={C.ok} /> : <Copy size={14} />}
            </button>
          </div>

          {/* Thao tác */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, padding: '0 14px 12px' }}>
            {!info.branchCreated && (
              <button
                type="button"
                onClick={handleCreateBranch}
                disabled={!canEdit || creating}
                title={canEdit ? `Tạo nhánh ${info.branchName} trên GitHub` : 'Cần quyền sửa công việc để tạo nhánh'}
                style={{
                  ...smallButton,
                  border: `1px solid ${C.accent}`,
                  background: C.accent,
                  color: '#FFFFFF',
                  cursor: !canEdit || creating ? 'not-allowed' : 'pointer',
                  opacity: !canEdit ? 0.45 : creating ? 0.7 : 1,
                }}
              >
                {creating ? <Loader2 size={12} style={{ animation: 'spin 0.8s linear infinite' }} /> : <GitBranch size={12} />}
                Tạo nhánh
              </button>
            )}
            <a href={info.compareUrl} target="_blank" rel="noreferrer" style={secondaryStyle}
              title={`Mở PR với tiêu đề "${info.prTitle}". Merge chỉ gắn liên kết, không hoàn thành Task`}>
              Tạo PR
              <ExternalLink size={11} color={C.muted} />
            </a>
            <a href={info.closeCompareUrl} target="_blank" rel="noreferrer" style={secondaryStyle}
              title={`Mở PR với tiêu đề "${info.closePrTitle}". Merge vào ${info.baseBranch} thì Task hoàn thành`}>
              PR hoàn thành
              <ExternalLink size={11} color={C.muted} />
            </a>
            {!info.issueLinked && (
              <button
                type="button"
                onClick={handleCreateIssue}
                disabled={!canEdit || creatingIssue}
                title={canEdit
                  ? 'Tạo Issue trên GitHub từ tiêu đề và mô tả Task, gắn hai chiều (đóng Issue ↔ Task hoàn thành)'
                  : 'Cần quyền sửa công việc để tạo Issue'}
                style={{
                  ...secondaryStyle,
                  cursor: !canEdit || creatingIssue ? 'not-allowed' : 'pointer',
                  opacity: !canEdit ? 0.45 : creatingIssue ? 0.7 : 1,
                }}
              >
                {creatingIssue ? <Loader2 size={12} style={{ animation: 'spin 0.8s linear infinite' }} /> : <CircleDot size={12} color={C.muted} />}
                Tạo Issue
              </button>
            )}
          </div>

          {/* Đã liên kết */}
          {linked.length > 0 && (
            <>
              <div style={{ padding: '8px 14px 4px', fontSize: 12, color: C.muted, borderTop: `1px solid ${C.divider}` }}>Đã liên kết</div>
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
                    style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 14px', minHeight: 38, color: C.text, textDecoration: 'none' }}
                  >
                    <Icon size={14} color={C.muted} aria-hidden="true" style={{ flexShrink: 0 }} />
                    <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12 }}>
                      <span style={{ color: C.muted }}>{isPr ? 'PR' : 'Issue'} #{l.githubNumber}</span> {l.title}
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12, color: C.muted, flexShrink: 0 }}>
                      <span style={{ width: 6, height: 6, borderRadius: '50%', background: st.color }} />
                      {st.label}
                    </span>
                  </a>
                );
              })}
              <div style={{ height: 6 }} />
            </>
          )}
        </div>
      )}
    </div>
  );
}
