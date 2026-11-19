import { useState, useEffect } from 'react';
import { X, Search, Lock, Loader2, GitBranch, ArrowLeft, Check } from 'lucide-react';
import toast from 'react-hot-toast';
import { githubIntegrationApi } from '../../api.js';

const C = {
  accent: '#2563EB',
  text: '#1B1B1F',
  muted: '#6B6F76',
  border: '#E6E6EA',
  divider: '#ECECEF',
  danger: '#B42318',
};

// Tương thích cả snake_case (GitHub API gốc) và camelCase
const getRepoFullName = (r) => r?.full_name || r?.fullName || r?.name || '';
const getRepoUrl = (r) => r?.html_url || r?.htmlUrl || '';
const isRepoPrivate = (r) => r?.private ?? r?.isPrivate ?? false;
const getRepoBranch = (r) => r?.default_branch || r?.defaultBranch || 'main';
const sameName = (a, b) => !!a && !!b && a.toLowerCase() === b.toLowerCase();

/**
 * Hộp chọn repository 2 bước: chọn repo → chọn nhánh đích → Kết nối.
 * branchOnly: mở thẳng bước chọn nhánh cho repo đang liên kết (đổi nhánh), nút "Lưu".
 */
export default function ConnectRepoModal({
  isOpen,
  onClose,
  projectId,
  onConnected,
  onReauth,
  currentRepoFullName,
  currentBranch,
  config,
  syncIssues = true,
  branchOnly = false,
}) {
  const [pickedRepo, setPickedRepo] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Mở lại hộp chọn luôn bắt đầu từ bước 1
  const close = () => {
    setPickedRepo(null);
    onClose();
  };

  useEffect(() => {
    if (!isOpen) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape' && !submitting) close();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, submitting]);

  if (!isOpen) return null;

  // Đổi nhánh: repo cố định là repo đang liên kết
  const repo = branchOnly
    ? { fullName: currentRepoFullName, defaultBranch: currentBranch }
    : pickedRepo;

  const handleSubmit = async (branch) => {
    const fullName = getRepoFullName(repo);
    try {
      setSubmitting(true);
      let res;
      if (branchOnly) {
        res = await githubIntegrationApi.updateConfig(projectId, { ...config, defaultBranch: branch }, { skipErrorToast: true });
        toast.success(`Đã đổi nhánh sang ${branch}`);
      } else {
        res = await githubIntegrationApi.connect(
          projectId,
          { repoFullName: fullName, repoUrl: getRepoUrl(repo), defaultBranch: branch, syncIssues },
          { skipErrorToast: true }
        );
        toast.success(`Đã kết nối ${fullName}`);
      }
      onConnected?.(res?.data);
      close();
    } catch (err) {
      if (!branchOnly) {
        // Request có thể đã thành công dù client báo lỗi: kiểm tra lại trạng thái
        try {
          const checkRes = await githubIntegrationApi.getStatus(projectId, { skipErrorToast: true });
          const d = checkRes?.data;
          if (d?.projectLinked && sameName(d.repoFullName, fullName) && d.defaultBranch === branch) {
            toast.success(`Đã kết nối ${fullName}`);
            onConnected?.(d);
            close();
            return;
          }
        } catch {
          /* báo lỗi bên dưới */
        }
      }
      console.warn('GitHub connect notice:', err);
      toast.error(err?.response?.data?.message || err?.message || 'Không lưu được thay đổi');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      onClick={() => !submitting && close()}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1000,
        background: 'rgba(20, 20, 26, 0.32)',
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'center',
        padding: '12vh 16px 16px',
        boxSizing: 'border-box',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        color: C.text,
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="gh-pick-title"
        onClick={(e) => e.stopPropagation()}
        style={{ width: '100%', maxWidth: 480, background: '#FFFFFF', border: `1px solid ${C.border}`, borderRadius: 10, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
      >
        {repo ? (
          <BranchStep
            key={getRepoFullName(repo)}
            projectId={projectId}
            repo={repo}
            branchOnly={branchOnly}
            currentBranch={branchOnly || sameName(getRepoFullName(repo), currentRepoFullName) ? currentBranch : null}
            submitting={submitting}
            onBack={branchOnly ? null : () => setPickedRepo(null)}
            onClose={close}
            onReauth={onReauth}
            onSubmit={handleSubmit}
          />
        ) : (
          <RepoStep
            projectId={projectId}
            currentRepoFullName={currentRepoFullName}
            onClose={close}
            onReauth={onReauth}
            onPick={setPickedRepo}
          />
        )}
      </div>
    </div>
  );
}

/* ─── Bước 1: chọn repository ─── */
function RepoStep({ projectId, currentRepoFullName, onClose, onReauth, onPick }) {
  const [repos, setRepos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [query, setQuery] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    githubIntegrationApi
      .getRepositories(projectId, { skipErrorToast: true })
      .then((res) => {
        if (ignore) return;
        setRepos(Array.isArray(res?.data) ? res.data : []);
        setError(null);
      })
      .catch((err) => {
        if (!ignore) setError(err?.response?.data?.message || err?.message || 'Không tải được danh sách repository');
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [projectId, reloadKey]);

  const q = query.toLowerCase().trim();
  const filtered = repos.filter(
    (r) => !q || getRepoFullName(r).toLowerCase().includes(q) || (r?.description || '').toLowerCase().includes(q)
  );

  return (
    <>
      <Header title="Chọn repository" onClose={onClose} />
      <SearchBox value={query} onChange={setQuery} label="Tìm repository" placeholder="Tìm theo tên…" />
      <ListBox>
        {loading ? (
          <Loading />
        ) : error ? (
          <ErrorBox
            message={error}
            onReauth={onReauth}
            onRetry={() => {
              setLoading(true);
              setReloadKey((k) => k + 1);
            }}
          />
        ) : filtered.length === 0 ? (
          <Empty>Không tìm thấy repository</Empty>
        ) : (
          filtered.map((repo) => {
            const fullName = getRepoFullName(repo);
            const isCurrent = sameName(fullName, currentRepoFullName);
            return (
              <RowButton key={repo.id ?? fullName} selected={isCurrent} onClick={() => onPick(repo)}>
                <span style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                  <span style={{ fontSize: 13, fontWeight: 500, display: 'inline-flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
                    <Ellipsis>{fullName}</Ellipsis>
                    {isRepoPrivate(repo) && <Lock size={12} color={C.muted} aria-label="Riêng tư" style={{ flexShrink: 0 }} />}
                  </span>
                  {repo.description && (
                    <span style={{ fontSize: 12, color: C.muted, minWidth: 0, display: 'flex' }}>
                      <Ellipsis>{repo.description}</Ellipsis>
                    </span>
                  )}
                </span>
                {isCurrent && <span style={{ fontSize: 12, color: C.muted, flexShrink: 0 }}>Đang dùng</span>}
              </RowButton>
            );
          })
        )}
      </ListBox>
      <Footer>Cần quyền admin trên repo để GitHub gửi sự kiện về.</Footer>
    </>
  );
}

/* ─── Bước 2: chọn nhánh đích ─── */
function BranchStep({ projectId, repo, branchOnly, currentBranch, submitting, onBack, onClose, onReauth, onSubmit }) {
  const fullName = getRepoFullName(repo);
  const githubDefault = branchOnly ? null : getRepoBranch(repo);
  const [branches, setBranches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(currentBranch || githubDefault || null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    githubIntegrationApi
      .getBranches(projectId, fullName, { skipErrorToast: true })
      .then((res) => {
        if (ignore) return;
        const list = Array.isArray(res?.data) ? res.data : [];
        setBranches(list);
        setError(null);
        // Backend trả nhánh mặc định của GitHub đứng đầu: dùng làm lựa chọn sẵn nếu chưa có
        setSelected((cur) => (cur && list.some((b) => b.name === cur) ? cur : list[0]?.name ?? null));
      })
      .catch((err) => {
        if (!ignore) setError(err?.response?.data?.message || err?.message || 'Không tải được danh sách nhánh');
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [projectId, fullName, reloadKey]);

  const defaultName = branches[0]?.name;
  const q = query.toLowerCase().trim();
  const filtered = branches.filter((b) => !q || (b.name || '').toLowerCase().includes(q));
  const unchanged = branchOnly && selected === currentBranch;

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '12px 12px 4px' }}>
        {onBack && (
          <IconButton label="Quay lại chọn repository" onClick={onBack} disabled={submitting}>
            <ArrowLeft size={16} />
          </IconButton>
        )}
        <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', paddingLeft: onBack ? 0 : 4 }}>
          <h2 id="gh-pick-title" style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>
            {branchOnly ? 'Đổi nhánh' : 'Chọn nhánh'}
          </h2>
          <span style={{ fontSize: 12, color: C.muted, display: 'flex', minWidth: 0 }}>
            <Ellipsis>{fullName}</Ellipsis>
          </span>
        </span>
        <IconButton label="Đóng" onClick={onClose} disabled={submitting}>
          <X size={16} />
        </IconButton>
      </div>
      <p style={{ margin: '0 16px 8px', fontSize: 12, color: C.muted }}>
        Chỉ PR mở hoặc merge vào nhánh này mới tự chuyển trạng thái Task.
      </p>
      {branches.length > 8 && <SearchBox value={query} onChange={setQuery} label="Tìm nhánh" placeholder="Tìm nhánh…" />}
      <ListBox>
        {loading ? (
          <Loading />
        ) : error ? (
          <ErrorBox
            message={error}
            onReauth={onReauth}
            onRetry={() => {
              setLoading(true);
              setReloadKey((k) => k + 1);
            }}
          />
        ) : filtered.length === 0 ? (
          <Empty>Không tìm thấy nhánh</Empty>
        ) : (
          <div role="radiogroup" aria-label="Nhánh đích" style={{ display: 'flex', flexDirection: 'column' }}>
            {filtered.map((b) => {
              const isSelected = b.name === selected;
              return (
                <RowButton
                  key={b.name}
                  role="radio"
                  aria-checked={isSelected}
                  selected={isSelected}
                  disabled={submitting}
                  onClick={() => setSelected(b.name)}
                >
                  <GitBranch size={14} color={C.muted} style={{ flexShrink: 0 }} />
                  <span style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, fontSize: 13 }}>
                    <Ellipsis>{b.name}</Ellipsis>
                    {b.name === defaultName && <Tag>Mặc định</Tag>}
                    {branchOnly && b.name === currentBranch && <Tag>Đang dùng</Tag>}
                    {(b.isProtected ?? b.protected) && <Lock size={12} color={C.muted} aria-label="Được bảo vệ" style={{ flexShrink: 0 }} />}
                  </span>
                  {isSelected && <Check size={14} color={C.accent} style={{ flexShrink: 0 }} />}
                </RowButton>
              );
            })}
          </div>
        )}
      </ListBox>
      <div style={{ borderTop: `1px solid ${C.divider}`, padding: '10px 12px', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        <button type="button" onClick={onClose} disabled={submitting} style={smallButton(false)}>
          Huỷ
        </button>
        <button
          type="button"
          onClick={() => onSubmit(selected)}
          disabled={!selected || loading || !!error || submitting || unchanged}
          style={{ ...smallButton(true), opacity: !selected || loading || error || unchanged ? 0.5 : 1, display: 'inline-flex', alignItems: 'center', gap: 6 }}
        >
          {submitting && <Loader2 size={14} className="animate-spin" />}
          {branchOnly ? 'Lưu' : 'Kết nối'}
        </button>
      </div>
    </>
  );
}

/* ─── Thành phần dùng chung ─── */

function Header({ title, onClose }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 12px 12px 16px' }}>
      <h2 id="gh-pick-title" style={{ margin: 0, flex: 1, fontSize: 14, fontWeight: 600 }}>
        {title}
      </h2>
      <IconButton label="Đóng" onClick={onClose}>
        <X size={16} />
      </IconButton>
    </div>
  );
}

function IconButton({ label, onClick, disabled, children }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      disabled={disabled}
      style={{ width: 32, height: 32, flexShrink: 0, borderRadius: 6, border: 0, background: 'transparent', color: C.muted, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
    >
      {children}
    </button>
  );
}

function SearchBox({ value, onChange, label, placeholder }) {
  return (
    <label style={{ margin: '0 12px 8px', display: 'flex', alignItems: 'center', gap: 8, height: 36, padding: '0 10px', border: '1px solid #DCDCE1', borderRadius: 6, color: C.muted }}>
      <Search size={14} />
      <input
        type="text"
        autoFocus
        aria-label={label}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        style={{ flex: 1, border: 0, outline: 'none', background: 'transparent', fontSize: 13, fontFamily: 'inherit', color: C.text }}
      />
    </label>
  );
}

function ListBox({ children }) {
  return (
    <div style={{ borderTop: `1px solid ${C.divider}`, maxHeight: 340, overflowY: 'auto', padding: 4, display: 'flex', flexDirection: 'column' }}>
      {children}
    </div>
  );
}

function RowButton({ selected, disabled, onClick, children, ...rest }) {
  const base = selected ? '#F4F5FB' : 'transparent';
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      onMouseEnter={(e) => (e.currentTarget.style.background = selected ? base : '#F4F4F7')}
      onMouseLeave={(e) => (e.currentTarget.style.background = base)}
      style={{
        textAlign: 'left',
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        minHeight: 44,
        padding: '6px 10px',
        border: 0,
        borderRadius: 6,
        background: base,
        color: C.text,
        fontFamily: 'inherit',
        cursor: disabled ? 'wait' : 'pointer',
      }}
      {...rest}
    >
      {children}
    </button>
  );
}

function Ellipsis({ children }) {
  return <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0 }}>{children}</span>;
}

function Tag({ children }) {
  return (
    <span style={{ flexShrink: 0, fontSize: 11, color: C.muted, border: `1px solid ${C.border}`, borderRadius: 4, padding: '0 5px', lineHeight: '16px' }}>
      {children}
    </span>
  );
}

function Loading() {
  return (
    <div style={{ padding: '28px 10px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, fontSize: 13, color: C.muted }}>
      <Loader2 size={14} className="animate-spin" />
      Đang tải…
    </div>
  );
}

function Empty({ children }) {
  return <div style={{ padding: '24px 10px', textAlign: 'center', fontSize: 13, color: C.muted }}>{children}</div>;
}

function ErrorBox({ message, onReauth, onRetry }) {
  return (
    <div style={{ padding: '20px 12px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, textAlign: 'center' }}>
      <span style={{ fontSize: 13, color: C.danger }}>{message}</span>
      <div style={{ display: 'flex', gap: 8 }}>
        {onReauth && (
          <button type="button" onClick={onReauth} style={smallButton(true)}>
            Cấp quyền lại
          </button>
        )}
        <button type="button" onClick={onRetry} style={smallButton(false)}>
          Thử lại
        </button>
      </div>
    </div>
  );
}

function Footer({ children }) {
  return <div style={{ borderTop: `1px solid ${C.divider}`, padding: '10px 16px', fontSize: 12, color: C.muted }}>{children}</div>;
}

const smallButton = (primary) => ({
  height: 32,
  padding: '0 12px',
  borderRadius: 6,
  fontSize: 13,
  fontWeight: 500,
  fontFamily: 'inherit',
  cursor: 'pointer',
  border: `1px solid ${primary ? C.accent : '#DCDCE1'}`,
  background: primary ? C.accent : '#FFFFFF',
  color: primary ? '#FFFFFF' : C.text,
});
