import { Fragment, useState, useEffect, useRef } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import {
  GitBranch,
  ExternalLink,
  RefreshCw,
  Loader2,
  MoreHorizontal,
  ArrowRight,
  Lock,
  Folder,
  User,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { githubIntegrationApi } from '../api.js';
import ConnectRepoModal from '../components/github/ConnectRepoModal.jsx';
import { useProjectPermission } from '../hooks/useProjectPermission.js';
import { useProjectContext } from '../context/ProjectContext.jsx';

/* ─── Giao diện tối giản: 1 màu nhấn, viền 1px, không đổ bóng ─── */
const C = {
  accent: '#2563EB',
  text: '#1B1B1F',
  textSoft: '#3C3F45',
  muted: '#6B6F76',
  border: '#E6E6EA',
  divider: '#ECECEF',
  danger: '#B42318',
  dangerBg: '#FDF6F5',
  ok: '#2E9E5B',
  idle: '#A0A3AA',
};

const DEFAULT_CONFIG = {
  syncIssuesEnabled: true,
  autoMoveInReviewOnPr: true,
  autoCloseTaskOnPrMerge: true,
  autoCloseTaskOnIssueClose: true,
  autoCloseGithubIssueOnDone: true,
  prCloseKeywords: '',
};

const DEFAULT_CLOSE_KEYWORDS = 'Closes, Fixes, Resolves, Hoàn thành';
const firstKeyword = (keywords) => (keywords || 'Closes').split(',')[0].trim() || 'Closes';

const RULES = [
  { key: 'syncIssuesEnabled', from: 'Issue mới', to: 'Tạo Task', hint: 'Nhận sự kiện Issue mở / đóng / sửa từ GitHub' },
  {
    key: 'autoMoveInReviewOnPr',
    from: 'Mở PR',
    to: 'Task sang Review',
    hint: (branch) => `PR vào ${branch}, có mã Task trong tên nhánh hoặc tiêu đề (VD: DATN-15)`,
  },
  {
    key: 'autoCloseTaskOnPrMerge',
    from: 'Merge PR',
    to: 'Task hoàn thành',
    title: 'Bắt buộc ghi từ khoá hoàn thành ngay trước mã Task trong tiêu đề hoặc mô tả PR. PR chỉ nhắc mã (tên nhánh) khi merge chỉ gắn liên kết, để Task cần nhiều PR không bị hoàn thành sớm. Mỗi mã cần từ khoá riêng.',
    hint: (branch, key, cfg) => `PR ghi "${firstKeyword(cfg.prCloseKeywords)} ${key}-12" trong tiêu đề hoặc mô tả, merge vào ${branch}`,
  },
  {
    key: 'autoCloseTaskOnIssueClose',
    from: 'Đóng Issue',
    to: 'Task hoàn thành',
    title: 'Người đóng Issue phải liên kết tài khoản GitHub. Thành viên không phải Admin sẽ tạo yêu cầu "Tạm hoàn thành" chờ duyệt.',
  },
  { key: 'autoCloseGithubIssueOnDone', from: 'Task hoàn thành', to: 'Đóng Issue' },
];

const configFrom = (data) =>
  Object.fromEntries(Object.keys(DEFAULT_CONFIG).map((k) => [k, data?.[k] ?? DEFAULT_CONFIG[k]]));

const timeAgo = (value) => {
  if (!value) return null;
  const diff = Math.max(0, Date.now() - new Date(value).getTime());
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'vừa xong';
  if (m < 60) return `${m} phút trước`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} giờ trước`;
  const d = Math.floor(h / 24);
  if (d < 30) return `${d} ngày trước`;
  return new Date(value).toLocaleDateString('vi-VN');
};

export default function ProjectGitHubPage() {
  const { projectId } = useParams();
  const { isAdmin, loading: permissionLoading } = useProjectPermission(projectId);
  const { project } = useProjectContext();
  const projectKey = project?.key || 'KEY';
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [config, setConfig] = useState(DEFAULT_CONFIG);
  const [isConnectModalOpen, setIsConnectModalOpen] = useState(false);
  const [branchOnly, setBranchOnly] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [syncingIssues, setSyncingIssues] = useState(false);
  const [authVersion, setAuthVersion] = useState(0);
  const [openMenu, setOpenMenu] = useState(null); // 'account' | 'repo' | null
  const [confirming, setConfirming] = useState(null); // 'account' | 'repo' | null
  const [busy, setBusy] = useState(false);
  const processedCodesRef = useRef(new Set());
  const isHandlingAuthRef = useRef(false);

  const applyStatus = (data) => {
    setStatus(data);
    setConfig(configFrom(data));
  };

  const reloadStatus = () =>
    githubIntegrationApi
      .getStatus(projectId, { skipErrorToast: true })
      .then((res) => res?.data && applyStatus(res.data))
      .catch((err) => console.warn('Could not load github status:', err));

  useEffect(() => {
    let ignore = false;
    if (projectId) {
      githubIntegrationApi
        .getStatus(projectId, { skipErrorToast: true })
        .then((res) => {
          if (!ignore && res?.data) applyStatus(res.data);
        })
        .catch((err) => console.warn('Could not load github status:', err))
        .finally(() => {
          if (!ignore) setLoading(false);
        });
    }
    return () => {
      ignore = true;
    };
  }, [projectId]);

  // Đóng menu khi bấm ra ngoài hoặc nhấn Esc
  useEffect(() => {
    if (!openMenu) return undefined;
    const close = (e) => {
      if (e.type === 'keydown' && e.key !== 'Escape') return;
      if (e.type === 'mousedown' && e.target.closest?.('[data-gh-menu]')) return;
      setOpenMenu(null);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, [openMenu]);

  // Lắng nghe popup OAuth trả code về
  useEffect(() => {
    const handleOAuthMessage = async (event) => {
      if (event.origin !== window.location.origin) return;

      if (event.data?.type === 'GITHUB_OAUTH_CODE') {
        const code = event.data.code;
        if (!code || processedCodesRef.current.has(code) || isHandlingAuthRef.current) return;
        processedCodesRef.current.add(code);
        isHandlingAuthRef.current = true;

        try {
          setIsAuthenticating(true);
          const res = await githubIntegrationApi.handleCallback(projectId, code, event.data.state, { skipErrorToast: true });
          toast.success('Đã kết nối GitHub');
          if (res?.data) {
            applyStatus(res.data);
            setAuthVersion((v) => v + 1);
          }
        } catch (err) {
          // Popup có thể gửi trùng code: kiểm tra lại trạng thái thực tế trước khi báo lỗi
          try {
            const checkRes = await githubIntegrationApi.getStatus(projectId, { skipErrorToast: true });
            if (checkRes?.data?.userConnected) {
              applyStatus(checkRes.data);
              toast.success('Đã kết nối GitHub');
              return;
            }
          } catch {
            /* bỏ qua, báo lỗi bên dưới */
          }
          console.warn('OAuth callback notice:', err);
          toast.error(err?.message || 'Không kết nối được GitHub');
        } finally {
          setIsAuthenticating(false);
          isHandlingAuthRef.current = false;
        }
      } else if (event.data?.type === 'GITHUB_OAUTH_ERROR') {
        console.warn('GitHub OAuth status notice:', event.data.error);
        setIsAuthenticating(false);
      }
    };

    window.addEventListener('message', handleOAuthMessage);
    return () => window.removeEventListener('message', handleOAuthMessage);
  }, [projectId]);

  const handleStartOAuth = async () => {
    setOpenMenu(null);
    try {
      setIsAuthenticating(true);
      const res = await githubIntegrationApi.getAuthUrl(projectId);
      const authUrl = res?.data?.authUrl;
      if (!authUrl) {
        toast.error('Không lấy được URL ủy quyền từ máy chủ');
        setIsAuthenticating(false);
        return;
      }
      const width = 600;
      const height = 720;
      const left = window.screen.width / 2 - width / 2;
      const top = window.screen.height / 2 - height / 2;
      window.open(
        authUrl,
        'github_oauth_popup',
        `width=${width},height=${height},top=${top},left=${left},status=no,toolbar=no,menubar=no`
      );
    } catch (err) {
      toast.error(err?.message || 'Không thể bắt đầu ủy quyền GitHub');
      setIsAuthenticating(false);
    }
  };

  const handleDisconnect = async () => {
    try {
      setBusy(true);
      await githubIntegrationApi.disconnect(projectId);
      toast.success('Đã ngắt kết nối');
      setConfirming(null);
      await reloadStatus();
    } catch (err) {
      toast.error(err?.message || 'Không thể ngắt kết nối');
    } finally {
      setBusy(false);
    }
  };

  const handleUnlinkAccount = async () => {
    try {
      setBusy(true);
      await githubIntegrationApi.unlinkAccount(projectId);
      toast.success('Đã huỷ liên kết tài khoản');
      setConfirming(null);
      await reloadStatus();
    } catch (err) {
      toast.error(err?.message || 'Không thể huỷ liên kết tài khoản');
    } finally {
      setBusy(false);
    }
  };

  // Bật/tắt là lưu luôn; lỗi thì trả công tắc về như cũ
  const handleToggle = async (key) => {
    const previous = config;
    const next = { ...config, [key]: !config[key] };
    setConfig(next);
    try {
      const res = await githubIntegrationApi.updateConfig(projectId, next, { skipErrorToast: true });
      if (res?.data) setStatus(res.data);
      toast.success('Đã lưu', { id: 'gh-config-saved' });
    } catch (err) {
      setConfig(previous);
      toast.error(err?.message || 'Không lưu được thay đổi');
    }
  };

  const handleSaveKeywords = async (value) => {
    try {
      const res = await githubIntegrationApi.updateConfig(projectId, { ...config, prCloseKeywords: value }, { skipErrorToast: true });
      if (res?.data) {
        setStatus(res.data);
        setConfig(configFrom(res.data));
      }
      toast.success('Đã lưu từ khoá', { id: 'gh-config-saved' });
      return true;
    } catch (err) {
      toast.error(err?.message || 'Không lưu được từ khoá');
      return false;
    }
  };

  const handleSyncIssues = async () => {
    try {
      setSyncingIssues(true);
      const res = await githubIntegrationApi.syncIssues(projectId);
      const data = res?.data;
      toast.success(
        data
          ? `Đã đồng bộ ${data.totalFound || 0} Issue (${data.importedCount || 0} mới, ${data.updatedCount || 0} cập nhật)`
          : 'Đã đồng bộ'
      );
      reloadStatus();
    } catch (err) {
      const errMsg = err?.response?.data?.message || err?.message || 'Đồng bộ thất bại';
      toast.error(errMsg);
    } finally {
      setSyncingIssues(false);
    }
  };

  // Thành viên thường không vào được màn này (kể cả gõ thẳng URL): đưa về Bảng tiến độ
  if (!permissionLoading && !isAdmin) {
    return <Navigate to="../board" replace />;
  }

  if (loading || permissionLoading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 8, color: C.muted, fontSize: 13 }}>
        <Loader2 size={16} className="animate-spin" />
        <span>Đang tải…</span>
      </div>
    );
  }

  const isUserConnected = !!(status?.userConnected ?? status?.user_connected);
  const isProjectLinked = !!(status?.projectLinked ?? status?.project_linked);
  const githubAvatarUrl = status?.githubAvatarUrl || status?.github_avatar_url;
  const githubUsername = status?.githubUsername || status?.github_username;
  const repoUrl = status?.repoUrl || status?.repo_url;
  const repoFullName = status?.repoFullName || status?.repo_full_name;
  const defaultBranch = status?.defaultBranch || status?.default_branch || 'main';
  const webhookActive = !!(status?.webhookActive ?? status?.webhook_active);
  const lastSyncedAt = status?.lastSyncedAt || status?.last_synced_at;
  const canEdit = isProjectLinked && isAdmin;

  return (
    <div
      style={{
        height: '100%',
        overflowY: 'auto',
        backgroundColor: '#FFFFFF',
        color: C.text,
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        fontSize: 14,
        lineHeight: 1.45,
      }}
    >
      <div style={{ maxWidth: 640, margin: '0 auto', padding: '40px 16px 96px', display: 'flex', flexDirection: 'column', gap: 32 }}>
        <header style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <div style={{ fontSize: 12, color: C.muted }}>Tích hợp</div>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 600, letterSpacing: '-0.01em' }}>GitHub</h1>
          <p style={{ margin: 0, color: C.muted }}>Issue và Pull Request tự cập nhật Task trong dự án.</p>
        </header>

        {/* ─── Tài khoản ─── */}
        <Section title="Tài khoản">
          <Card>
            <Row>
              {!isUserConnected ? (
                <>
                  <span style={{ width: 28, height: 28, borderRadius: '50%', border: '1px dashed #C9CAD1', display: 'flex', alignItems: 'center', justifyContent: 'center', color: C.muted, flexShrink: 0 }}>
                    <User size={14} />
                  </span>
                  <span style={{ flex: 1, color: C.textSoft }}>Chưa kết nối tài khoản GitHub</span>
                  <PrimaryButton onClick={handleStartOAuth} disabled={isAuthenticating}>
                    {isAuthenticating && <Loader2 size={14} className="animate-spin" />}
                    {isAuthenticating ? 'Đang mở…' : 'Kết nối GitHub'}
                  </PrimaryButton>
                </>
              ) : (
                <>
                  {githubAvatarUrl ? (
                    <img src={githubAvatarUrl} alt="" style={{ width: 28, height: 28, borderRadius: '50%', flexShrink: 0 }} />
                  ) : (
                    <span style={{ width: 28, height: 28, borderRadius: '50%', background: '#EEF2FF', color: '#3E48A8', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      <User size={14} />
                    </span>
                  )}
                  <span style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 10, minWidth: 0, flexWrap: 'wrap' }}>
                    <span style={{ fontWeight: 500 }}>@{githubUsername}</span>
                    <StatusDot color={C.ok}>Đã kết nối</StatusDot>
                  </span>
                  <Menu
                    label="Tuỳ chọn tài khoản"
                    open={openMenu === 'account'}
                    onToggle={() => {
                      setConfirming(null);
                      setOpenMenu(openMenu === 'account' ? null : 'account');
                    }}
                    items={[
                      { label: 'Cấp quyền lại', onClick: handleStartOAuth },
                      {
                        label: 'Huỷ liên kết tài khoản',
                        danger: true,
                        onClick: () => {
                          setOpenMenu(null);
                          setConfirming('account');
                        },
                      },
                    ]}
                  />
                </>
              )}
            </Row>
            {confirming === 'account' && (
              <ConfirmBar
                text="Huỷ liên kết tài khoản GitHub của bạn?"
                actionLabel="Huỷ liên kết"
                busy={busy}
                onCancel={() => setConfirming(null)}
                onConfirm={handleUnlinkAccount}
              />
            )}
          </Card>
        </Section>

        {/* ─── Repository ─── */}
        <Section title="Repository">
          <Card>
            {!isUserConnected && !isProjectLinked && (
              <Row>
                <span style={{ color: C.muted, fontSize: 13 }}>Kết nối tài khoản để chọn repository</span>
              </Row>
            )}

            {isUserConnected && !isProjectLinked && (
              <Row>
                <span style={{ flex: 1, color: C.textSoft }}>Chưa chọn repository</span>
                {isAdmin ? (
                  <PrimaryButton
                    onClick={() => {
                      setBranchOnly(false);
                      setIsConnectModalOpen(true);
                    }}
                  >
                    Chọn repository
                  </PrimaryButton>
                ) : (
                  <span style={{ fontSize: 12, color: C.muted }}>Admin dự án sẽ chọn</span>
                )}
              </Row>
            )}

            {isProjectLinked && (
              <>
                <Row>
                  <Folder size={16} color={C.muted} style={{ flexShrink: 0 }} />
                  <span style={{ flex: 1, display: 'flex', flexWrap: 'wrap', alignItems: 'center', columnGap: 12, rowGap: 2, minWidth: 0 }}>
                    <a
                      href={repoUrl}
                      target="_blank"
                      rel="noreferrer"
                      style={{ fontWeight: 500, color: C.text, textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 4, minWidth: 0 }}
                    >
                      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{repoFullName}</span>
                      <ExternalLink size={12} color={C.muted} />
                    </a>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: C.muted }}>
                      <GitBranch size={12} />
                      {defaultBranch}
                    </span>
                    {webhookActive ? (
                      <StatusDot color={C.ok} title="GitHub gửi sự kiện Issue/PR về hệ thống qua webhook">
                        Đang nhận sự kiện
                      </StatusDot>
                    ) : (
                      <StatusDot
                        color={C.idle}
                        title="Chưa tạo được webhook: cần quyền admin trên repo và cấu hình GITHUB_WEBHOOK_URL. Thử kết nối lại repo."
                      >
                        Chưa nhận sự kiện
                      </StatusDot>
                    )}
                  </span>
                  {isAdmin && (
                    <Menu
                      label="Tuỳ chọn repository"
                      open={openMenu === 'repo'}
                      onToggle={() => {
                        setConfirming(null);
                        setOpenMenu(openMenu === 'repo' ? null : 'repo');
                      }}
                      items={[
                        {
                          label: 'Đổi nhánh',
                          onClick: () => {
                            setOpenMenu(null);
                            setBranchOnly(true);
                            setIsConnectModalOpen(true);
                          },
                        },
                        {
                          label: 'Đổi repository',
                          onClick: () => {
                            setOpenMenu(null);
                            setBranchOnly(false);
                            setIsConnectModalOpen(true);
                          },
                        },
                        {
                          label: 'Ngắt kết nối',
                          danger: true,
                          onClick: () => {
                            setOpenMenu(null);
                            setConfirming('repo');
                          },
                        },
                      ]}
                    />
                  )}
                </Row>
                {confirming === 'repo' && (
                  <ConfirmBar
                    text="Ngắt kết nối repo này? Task đã liên kết vẫn giữ nguyên."
                    actionLabel="Ngắt kết nối"
                    busy={busy}
                    onCancel={() => setConfirming(null)}
                    onConfirm={handleDisconnect}
                  />
                )}
              </>
            )}
          </Card>
        </Section>

        {/* ─── Đồng bộ ─── */}
        <Section title="Đồng bộ" dimmed={!isProjectLinked}>
          <Card>
            <Row>
              <span style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
                <span>Issue trên GitHub</span>
                <span style={{ fontSize: 12, color: C.muted }}>
                  {!isProjectLinked
                    ? 'Chọn repository để đồng bộ'
                    : lastSyncedAt
                      ? `Lần cuối ${timeAgo(lastSyncedAt)}`
                      : 'Chưa đồng bộ lần nào'}
                </span>
              </span>
              <SecondaryButton onClick={handleSyncIssues} disabled={!canEdit || syncingIssues}>
                <RefreshCw size={14} className={syncingIssues ? 'animate-spin' : ''} />
                {syncingIssues ? 'Đang đồng bộ…' : 'Đồng bộ'}
              </SecondaryButton>
            </Row>
          </Card>
        </Section>

        {/* ─── Tự động ─── */}
        <Section
          title="Tự động"
          dimmed={!isProjectLinked}
          aside={
            isProjectLinked && !isAdmin ? (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: C.muted }}>
                <Lock size={12} />
                Chỉ Admin dự án thay đổi được
              </span>
            ) : null
          }
        >
          <Card>
            {RULES.map((rule, i) => (
              <Fragment key={rule.key}>
              <div
                title={rule.title}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '10px 14px',
                  minHeight: 44,
                  boxSizing: 'border-box',
                  borderTop: i === 0 ? 'none' : `1px solid ${C.divider}`,
                }}
              >
                <span style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    {rule.from}
                    <ArrowRight size={14} color="#9A9DA4" aria-hidden="true" />
                    {rule.to}
                  </span>
                  {rule.hint && (
                    <span style={{ fontSize: 12, color: C.muted }}>
                      {typeof rule.hint === 'function' ? rule.hint(defaultBranch, projectKey, config) : rule.hint}
                    </span>
                  )}
                </span>
                <Switch
                  checked={!!config[rule.key]}
                  disabled={!canEdit}
                  label={`${rule.from} thì ${rule.to}`}
                  onChange={() => handleToggle(rule.key)}
                />
              </div>
              {rule.key === 'autoCloseTaskOnPrMerge' && config.autoCloseTaskOnPrMerge && (
                <CloseKeywordsRow
                  key={config.prCloseKeywords}
                  value={config.prCloseKeywords}
                  disabled={!canEdit}
                  onSave={handleSaveKeywords}
                />
              )}
              </Fragment>
            ))}
          </Card>
        </Section>
      </div>

      <ConnectRepoModal
        key={authVersion}
        isOpen={isConnectModalOpen}
        onClose={() => setIsConnectModalOpen(false)}
        projectId={projectId}
        currentRepoFullName={isProjectLinked ? repoFullName : null}
        currentBranch={isProjectLinked ? defaultBranch : null}
        branchOnly={branchOnly && isProjectLinked}
        config={config}
        syncIssues={config.syncIssuesEnabled}
        onReauth={handleStartOAuth}
        onConnected={(newStatus) => {
          if (newStatus) applyStatus(newStatus);
          reloadStatus();
        }}
      />
    </div>
  );
}

/* ─── Thành phần nhỏ dùng trong trang ─── */

const normalizeKeywords = (raw) =>
  raw
    .split(/[,;\n]/)
    .map((s) => s.trim().replace(/\s+/g, ' '))
    .filter(Boolean)
    .join(', ');

// Ô nhập từ khoá hoàn thành, lưu khi rời ô hoặc Enter; Esc trả về giá trị đã lưu
function CloseKeywordsRow({ value, disabled, onSave }) {
  const saved = value || '';
  const [draft, setDraft] = useState(saved);
  const [saving, setSaving] = useState(false);

  const commit = async () => {
    const next = normalizeKeywords(draft);
    if (next === saved) {
      setDraft(saved);
      return;
    }
    setSaving(true);
    const ok = await onSave(next);
    setSaving(false);
    if (!ok) setDraft(saved);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: '0 14px 12px 14px' }}>
      <label htmlFor="gh-close-keywords" style={{ fontSize: 12, color: C.muted }}>
        Từ khoá hoàn thành
      </label>
      <input
        id="gh-close-keywords"
        type="text"
        value={draft}
        disabled={disabled || saving}
        placeholder={DEFAULT_CLOSE_KEYWORDS}
        maxLength={500}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') e.currentTarget.blur();
          if (e.key === 'Escape') {
            setDraft(saved);
            e.currentTarget.blur();
          }
        }}
        style={{
          height: 32,
          padding: '0 10px',
          border: `1px solid ${C.border}`,
          borderRadius: 6,
          fontSize: 13,
          color: C.text,
          background: disabled ? '#F7F7F8' : '#fff',
          outline: 'none',
          boxSizing: 'border-box',
          width: '100%',
        }}
        onFocus={(e) => { e.currentTarget.style.borderColor = C.accent; }}
        onBlurCapture={(e) => { e.currentTarget.style.borderColor = C.border; }}
      />
    </div>
  );
}

function Section({ title, aside, dimmed, children }) {
  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 8, opacity: dimmed ? 0.45 : 1 }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <h2 style={{ margin: 0, flex: 1, fontSize: 12, fontWeight: 500, color: C.muted }}>{title}</h2>
        {aside}
      </div>
      {children}
    </section>
  );
}

function Card({ children }) {
  return (
    <div style={{ position: 'relative', border: `1px solid ${C.border}`, borderRadius: 8, display: 'flex', flexDirection: 'column' }}>
      {children}
    </div>
  );
}

function Row({ children }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', minHeight: 44, boxSizing: 'border-box' }}>
      {children}
    </div>
  );
}

function StatusDot({ color, title, children }) {
  return (
    <span title={title} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: C.muted }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: color }} />
      {children}
    </span>
  );
}

const buttonBase = {
  height: 32,
  padding: '0 14px',
  borderRadius: 6,
  fontSize: 13,
  fontWeight: 500,
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  flexShrink: 0,
  fontFamily: 'inherit',
};

function PrimaryButton({ disabled, children, ...rest }) {
  return (
    <button
      type="button"
      disabled={disabled}
      style={{ ...buttonBase, border: `1px solid ${C.accent}`, background: C.accent, color: '#FFFFFF', cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.7 : 1 }}
      {...rest}
    >
      {children}
    </button>
  );
}

function SecondaryButton({ disabled, children, ...rest }) {
  return (
    <button
      type="button"
      disabled={disabled}
      style={{ ...buttonBase, padding: '0 12px', border: '1px solid #DCDCE1', background: '#FFFFFF', color: C.text, cursor: disabled ? 'not-allowed' : 'pointer' }}
      {...rest}
    >
      {children}
    </button>
  );
}

function Switch({ checked, disabled, label, onChange }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={onChange}
      style={{ flexShrink: 0, width: 44, height: 32, padding: 0, border: 0, background: 'transparent', cursor: disabled ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
    >
      <span style={{ position: 'relative', width: 30, height: 18, borderRadius: 9, background: checked ? C.accent : '#D5D6DC', transition: 'background 0.15s ease', display: 'block' }}>
        <span style={{ position: 'absolute', top: 2, left: checked ? 14 : 2, width: 14, height: 14, borderRadius: '50%', background: '#FFFFFF', transition: 'left 0.15s ease', display: 'block' }} />
      </span>
    </button>
  );
}

function Menu({ label, open, onToggle, items }) {
  return (
    <div data-gh-menu style={{ position: 'relative', flexShrink: 0 }}>
      <button
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={onToggle}
        style={{ width: 32, height: 32, borderRadius: 6, border: 0, background: open ? '#F1F1F4' : 'transparent', color: C.muted, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
      >
        <MoreHorizontal size={16} />
      </button>
      {open && (
        <div
          role="menu"
          style={{ position: 'absolute', right: 0, top: 36, zIndex: 20, width: 200, background: '#FFFFFF', border: `1px solid ${C.border}`, borderRadius: 8, padding: 4, display: 'flex', flexDirection: 'column' }}
        >
          {items.map((item) => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              onClick={item.onClick}
              onMouseEnter={(e) => (e.currentTarget.style.background = '#F4F4F7')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
              style={{ textAlign: 'left', height: 32, padding: '0 10px', border: 0, borderRadius: 6, background: 'transparent', fontSize: 13, fontFamily: 'inherit', color: item.danger ? C.danger : C.text, cursor: 'pointer' }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function ConfirmBar({ text, actionLabel, busy, onCancel, onConfirm }) {
  return (
    <div style={{ borderTop: `1px solid ${C.border}`, padding: '10px 14px', display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8, background: C.dangerBg, borderRadius: '0 0 8px 8px' }}>
      <span style={{ flex: '1 1 260px', fontSize: 13, color: C.textSoft }}>{text}</span>
      <button
        type="button"
        onClick={onCancel}
        disabled={busy}
        style={{ ...buttonBase, padding: '0 12px', border: `1px solid ${C.border}`, background: '#FFFFFF', color: C.text, cursor: 'pointer' }}
      >
        Huỷ
      </button>
      <button
        type="button"
        onClick={onConfirm}
        disabled={busy}
        style={{ ...buttonBase, padding: '0 12px', border: `1px solid ${C.danger}`, background: C.danger, color: '#FFFFFF', cursor: busy ? 'not-allowed' : 'pointer' }}
      >
        {busy && <Loader2 size={14} className="animate-spin" />}
        {actionLabel}
      </button>
    </div>
  );
}
