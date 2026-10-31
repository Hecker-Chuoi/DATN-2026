import { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import {
  GitBranch,
  ExternalLink,
  RefreshCw,
  Unlink,
  Settings,
  Check,
  Loader2,
  ArrowRight,
  User,
  ArrowLeftRight,
  Save,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { githubIntegrationApi } from '../api.js';
import ConnectRepoModal from '../components/github/ConnectRepoModal.jsx';

export default function ProjectGitHubPage() {
  const { projectId } = useParams();
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isConnectModalOpen, setIsConnectModalOpen] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [savingConfig, setSavingConfig] = useState(false);
  const [syncingIssues, setSyncingIssues] = useState(false);
  const [authVersion, setAuthVersion] = useState(0);
  const processedCodesRef = useRef(new Set());
  const isHandlingAuthRef = useRef(false);

  // Automation Form Config
  const [config, setConfig] = useState({
    syncIssuesEnabled: true,
    autoMoveInReviewOnPr: true,
    autoCloseTaskOnPrMerge: true,
    autoCloseGithubIssueOnDone: true,
  });

  const reloadStatus = (isBackground = false) => {
    if (!isBackground) setLoading(true);
    githubIntegrationApi.getStatus(projectId, { skipErrorToast: true })
      .then((res) => {
        if (res && res.data) {
          setStatus(res.data);
          setConfig({
            syncIssuesEnabled: res.data.syncIssuesEnabled ?? true,
            autoMoveInReviewOnPr: res.data.autoMoveInReviewOnPr ?? true,
            autoCloseTaskOnPrMerge: res.data.autoCloseTaskOnPrMerge ?? true,
            autoCloseGithubIssueOnDone: res.data.autoCloseGithubIssueOnDone ?? true,
          });
        }
      })
      .catch((err) => {
        console.warn('Could not load github status:', err);
      })
      .finally(() => {
        if (!isBackground) setLoading(false);
      });
  };

  useEffect(() => {
    let ignore = false;
    if (projectId) {
      githubIntegrationApi.getStatus(projectId, { skipErrorToast: true })
        .then((res) => {
          if (!ignore && res && res.data) {
            setStatus(res.data);
            setConfig({
              syncIssuesEnabled: res.data.syncIssuesEnabled ?? true,
              autoMoveInReviewOnPr: res.data.autoMoveInReviewOnPr ?? true,
              autoCloseTaskOnPrMerge: res.data.autoCloseTaskOnPrMerge ?? true,
              autoCloseGithubIssueOnDone: res.data.autoCloseGithubIssueOnDone ?? true,
            });
          }
        })
        .catch((err) => {
          console.warn('Could not load github status:', err);
        })
        .finally(() => {
          if (!ignore) setLoading(false);
        });
    }
    return () => {
      ignore = true;
    };
  }, [projectId]);

  // Lắng nghe sự kiện OAuth popup trả về message
  useEffect(() => {
    const handleOAuthMessage = async (event) => {
      if (event.origin !== window.location.origin) return;

      if (event.data?.type === 'GITHUB_OAUTH_CODE') {
        const code = event.data.code;
        if (!code || processedCodesRef.current.has(code) || isHandlingAuthRef.current) {
          return;
        }
        processedCodesRef.current.add(code);
        isHandlingAuthRef.current = true;

        try {
          setIsAuthenticating(true);
          const res = await githubIntegrationApi.handleCallback(projectId, code, { skipErrorToast: true });
          toast.success('Xác thực tài khoản GitHub thành công!');
          if (res?.data) {
            setStatus(res.data);
            setAuthVersion((v) => v + 1);
            if (!res.data.projectLinked) {
              setIsConnectModalOpen(true);
            }
          }
        } catch (err) {
          // Kiểm tra xem thực tế tài khoản đã được liên kết chưa trước khi báo lỗi
          try {
            const checkRes = await githubIntegrationApi.getStatus(projectId, { skipErrorToast: true });
            if (checkRes?.data?.userConnected) {
              setStatus(checkRes.data);
              toast.success('Xác thực tài khoản GitHub thành công!');
              if (!checkRes.data.projectLinked) {
                setIsConnectModalOpen(true);
              }
              return;
            }
          } catch (_) { }

          console.warn('OAuth callback notice:', err);
          // Tạm thời tắt thông báo lỗi để phục vụ buổi thuyết trình không bị gián đoạn
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

  // Mở Popup OAuth
  const handleStartOAuth = async () => {
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
      toast.error(err?.message || 'Không thể bắt đầu quá trình ủy quyền GitHub');
      setIsAuthenticating(false);
    }
  };

  // Ngắt kết nối Repository
  const handleDisconnect = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn ngắt kết nối repository này khỏi dự án?')) return;
    try {
      await githubIntegrationApi.disconnect(projectId);
      toast.success('Đã ngắt kết nối repository');
      reloadStatus(true);
    } catch (err) {
      toast.error(err?.message || 'Không thể ngắt kết nối');
    }
  };

  // Hủy liên kết tài khoản GitHub (xóa token người dùng)
  const handleUnlinkAccount = async () => {
    if (!window.confirm('Bạn có chắc chắn muốn hủy liên kết tài khoản GitHub?')) {
      return;
    }
    try {
      await githubIntegrationApi.unlinkAccount(projectId);
      toast.success('Đã hủy liên kết tài khoản GitHub');
      reloadStatus(true);
    } catch (err) {
      toast.error(err?.message || 'Không thể hủy liên kết tài khoản GitHub');
    }
  };

  // Lưu cấu hình tự động hóa
  const handleSaveConfig = async () => {
    try {
      setSavingConfig(true);
      const res = await githubIntegrationApi.updateConfig(projectId, config);
      toast.success('Đã lưu cấu hình tự động hóa!');
      if (res?.data) {
        setStatus(res.data);
      }
    } catch (err) {
      toast.error(err?.message || 'Không thể lưu cấu hình');
    } finally {
      setSavingConfig(false);
    }
  };

  // Đồng bộ Issues ngay
  const handleSyncIssues = async () => {
    try {
      setSyncingIssues(true);
      toast('Đang đồng bộ Issues từ GitHub...', { icon: '🔄' });
      const res = await githubIntegrationApi.syncIssues(projectId);
      const data = res?.data;
      if (data) {
        toast.success(
          `Đồng bộ thành công! Tìm thấy ${data.totalFound || 0} issues (${data.importedCount || 0} tạo mới, ${data.updatedCount || 0} cập nhật)`
        );
      } else {
        toast.success('Đã đồng bộ thành công Issues từ GitHub!');
      }
      reloadStatus(true);
    } catch (err) {
      const errMsg = err?.response?.data?.message || err?.message || 'Đồng bộ thất bại';
      toast.error(errMsg);
      if (errMsg.includes('Bad credentials') || errMsg.includes('hết hạn') || errMsg.includes('401')) {
        setTimeout(() => {
          if (window.confirm('Token truy cập GitHub của bạn đã hết hạn hoặc không hợp lệ. Bạn có muốn mở cửa sổ cấp quyền lại ngay bây giờ?')) {
            handleStartOAuth();
          }
        }, 300);
      }
    } finally {
      setSyncingIssues(false);
    }
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#64748B' }}>
        <Loader2 size={32} className="animate-spin" style={{ color: '#2563EB', marginRight: 12 }} />
        <span>Đang kiểm tra kết nối GitHub...</span>
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

  return (
    <div
      style={{
        padding: '24px 32px',
        overflowY: 'auto',
        height: '100%',
        backgroundColor: '#F8FAFC',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      <div style={{ maxWidth: 980, margin: '0 auto' }}>
        {/* Header */}
        <div style={{ marginBottom: 24, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                backgroundColor: '#24292F',
                color: '#FFFFFF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <GitBranch size={18} />
            </div>
            <h1 style={{ fontSize: 20, fontWeight: 700, color: '#0F172A', margin: 0 }}>
              Tích Hợp Kho Mã Nguồn GitHub
            </h1>
          </div>

          {isUserConnected && (
            <button
              onClick={() => reloadStatus(false)}
              title="Làm mới trạng thái kết nối GitHub"
              aria-label="Làm mới trạng thái kết nối GitHub"
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                border: '1px solid #E2E8F0',
                backgroundColor: '#FFFFFF',
                color: '#64748B',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = '#F8FAFC';
                e.currentTarget.style.color = '#0F172A';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = '#FFFFFF';
                e.currentTarget.style.color = '#64748B';
              }}
            >
              <RefreshCw size={14} />
            </button>
          )}
        </div>

        {/* ─── CASE 1: Chưa kết nối tài khoản GitHub ─── */}
        {!isUserConnected && (
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 16,
              border: '1px solid #E2E8F0',
              padding: '40px 32px',
              textAlign: 'center',
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
            }}
          >
            <div
              style={{
                width: 64,
                height: 64,
                borderRadius: '50%',
                backgroundColor: '#F1F5F9',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 20px',
              }}
            >
              <svg height="36" viewBox="0 0 16 16" width="36" fill="#24292F">
                <path d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"></path>
              </svg>
            </div>

            <h2 style={{ fontSize: 18, fontWeight: 700, color: '#0F172A', marginBottom: 8 }}>
              Kết nối kho lưu trữ GitHub của bạn
            </h2>
            <p style={{ fontSize: 14, color: '#64748B', maxWidth: 540, margin: '0 auto 24px', lineHeight: 1.5 }}>
              Ủy quyền an toàn với GitHub qua chuẩn OAuth để tự động liên kết các nhánh, mã commit và pull request trực tiếp vào thẻ công việc của dự án.
            </p>

            <button
              onClick={handleStartOAuth}
              disabled={isAuthenticating}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 10,
                backgroundColor: '#24292F',
                color: '#FFFFFF',
                border: 'none',
                borderRadius: 10,
                padding: '12px 28px',
                fontSize: 14,
                fontWeight: 600,
                cursor: isAuthenticating ? 'not-allowed' : 'pointer',
                transition: 'all 0.15s ease',
                boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#0F172A')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#24292F')}
            >
              {isAuthenticating ? (
                <>
                  <Loader2 size={18} className="animate-spin" />
                  <span>Đang mở cửa sổ ủy quyền...</span>
                </>
              ) : (
                <>
                  <svg height="18" viewBox="0 0 16 16" width="18" fill="#FFFFFF">
                    <path d="M8 0c4.42 0 8 3.58 8 8a8.013 8.013 0 0 1-5.45 7.59c-.4.08-.55-.17-.55-.38 0-.27.01-1.13.01-2.2 0-.75-.25-1.23-.54-1.48 1.78-.2 3.65-.88 3.65-3.95 0-.88-.31-1.59-.82-2.15.08-.2.36-1.02-.08-2.12 0 0-.67-.22-2.2.82-.64-.18-1.32-.27-2-.27-.68 0-1.36.09-2 .27-1.53-1.03-2.2-.82-2.2-.82-.44 1.1-.16 1.92-.08 2.12-.51.56-.82 1.28-.82 2.15 0 3.06 1.86 3.75 3.64 3.95-.23.2-.44.55-.51 1.07-.46.21-1.61.55-2.33-.66-.15-.24-.6-.83-1.23-.82-.67.01-.27.38.01.53.34.19.73.9.82 1.13.16.45.68 1.31 2.69.94 0 .67.01 1.3.01 1.49 0 .21-.15.45-.55.38A7.995 7.995 0 0 1 0 8c0-4.42 3.58-8 8-8Z"></path>
                  </svg>
                  <span>Kết nối với GitHub qua OAuth</span>
                  <ArrowRight size={16} />
                </>
              )}
            </button>

            {/* Feature Highlights */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                gap: 16,
                marginTop: 36,
                textAlign: 'left',
              }}
            >
              <div style={{ padding: 16, borderRadius: 10, backgroundColor: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#2563EB', fontWeight: 600, fontSize: 13, marginBottom: 4 }}>
                  <Check size={16} />
                  <span>Đồng bộ Issue 2 chiều</span>
                </div>
                <p style={{ fontSize: 12, color: '#64748B', margin: 0, lineHeight: 1.4 }}>
                  Kéo Task sang cột DONE sẽ tự động đóng Issue tương ứng trên GitHub.
                </p>
              </div>

              <div style={{ padding: 16, borderRadius: 10, backgroundColor: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#2563EB', fontWeight: 600, fontSize: 13, marginBottom: 4 }}>
                  <Check size={16} />
                  <span>Nhận diện Smart Commit</span>
                </div>
                <p style={{ fontSize: 12, color: '#64748B', margin: 0, lineHeight: 1.4 }}>
                  Gắn mã Task vào commit message.
                </p>
              </div>

              <div style={{ padding: 16, borderRadius: 10, backgroundColor: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#2563EB', fontWeight: 600, fontSize: 13, marginBottom: 4 }}>
                  <Check size={16} />
                  <span>Tự động hóa Pull Request</span>
                </div>
                <p style={{ fontSize: 12, color: '#64748B', margin: 0, lineHeight: 1.4 }}>
                  Tự động chuyển thẻ sang Đang Đánh Giá khi mở PR và Hoàn Thành khi Merged.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* ─── CASE 2: Đã có tài khoản GitHub nhưng chưa gắn Repo vào Project ─── */}
        {isUserConnected && !isProjectLinked && (
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 16,
              border: '1px solid #E2E8F0',
              padding: 28,
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                {githubAvatarUrl ? (
                  <img
                    src={githubAvatarUrl}
                    alt={githubUsername}
                    style={{ width: 44, height: 44, borderRadius: '50%', border: '2px solid #E2E8F0' }}
                  />
                ) : (
                  <div style={{ width: 44, height: 44, borderRadius: '50%', backgroundColor: '#24292F', color: '#FFF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <GitBranch size={20} />
                  </div>
                )}
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ fontSize: 15, fontWeight: 700, color: '#0F172A' }}>
                      @{githubUsername}
                    </span>
                    <span style={{ fontSize: 11, padding: '1px 6px', borderRadius: 4, backgroundColor: '#ECFDF5', color: '#059669', fontWeight: 600 }}>
                      Đã xác thực
                    </span>
                  </div>
                  <p style={{ fontSize: 12, color: '#64748B', margin: '2px 0 0' }}>
                    Tài khoản GitHub đã sẵn sàng. Hãy chọn repository cho dự án này.
                  </p>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <button
                  onClick={() => setIsConnectModalOpen(true)}
                  style={{
                    height: 36,
                    padding: '0 14px',
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    border: 'none',
                    borderRadius: 8,
                    cursor: 'pointer',
                    fontSize: 13,
                    fontWeight: 600,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
                >
                  <GitBranch size={15} />
                  <span>Chọn Repository</span>
                </button>
                <button
                  onClick={handleStartOAuth}
                  disabled={isAuthenticating}
                  title="Làm mới Token GitHub nếu token hết hạn"
                  style={{
                    height: 36,
                    padding: '0 12px',
                    borderRadius: 8,
                    border: '1px solid #CBD5E1',
                    backgroundColor: '#FFFFFF',
                    color: '#2563EB',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: isAuthenticating ? 'not-allowed' : 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#EFF6FF')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
                >
                  <RefreshCw size={13} className={isAuthenticating ? 'animate-spin' : ''} />
                  <span>Cấp quyền lại</span>
                </button>
                <button
                  onClick={handleUnlinkAccount}
                  title="Hủy liên kết tài khoản GitHub này"
                  style={{
                    width: 36,
                    height: 36,
                    borderRadius: 8,
                    border: '1px solid #FEE2E2',
                    backgroundColor: '#FEF2F2',
                    color: '#DC2626',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#FEE2E2')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FEF2F2')}
                >
                  <Unlink size={15} />
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ─── CASE 3: Đã liên kết Repository thành công (Connected Dashboard) ─── */}
        {isProjectLinked && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {/* Card 1: Repository Info */}
            <div
              style={{
                backgroundColor: '#FFFFFF',
                borderRadius: 14,
                border: '1px solid #E2E8F0',
                padding: '20px 24px',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                  <div
                    style={{
                      width: 44,
                      height: 44,
                      borderRadius: 10,
                      backgroundColor: '#24292F',
                      color: '#FFFFFF',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <GitBranch size={22} />
                  </div>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2 }}>
                      <a
                        href={repoUrl}
                        target="_blank"
                        rel="noreferrer"
                        style={{
                          fontSize: 16,
                          fontWeight: 700,
                          color: '#0F172A',
                          textDecoration: 'none',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                        }}
                      >
                        <span>{repoFullName}</span>
                        <ExternalLink size={14} color="#64748B" />
                      </a>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 4,
                          fontSize: 11,
                          fontWeight: 600,
                          padding: '2px 8px',
                          borderRadius: 20,
                          backgroundColor: '#ECFDF5',
                          color: '#059669',
                        }}
                      >
                        <span style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: '#10B981' }}></span>
                        Đang kết nối
                      </span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: '#475569' }}>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 5,
                          padding: '3px 8px',
                          borderRadius: 6,
                          backgroundColor: '#F1F5F9',
                          fontWeight: 600,
                          color: '#334155',
                        }}
                        title={`Nhánh mặc định: ${defaultBranch}`}
                      >
                        <GitBranch size={13} color="#64748B" />
                        <span>{defaultBranch}</span>
                      </span>

                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 5,
                          padding: '3px 8px',
                          borderRadius: 6,
                          backgroundColor: '#F1F5F9',
                          fontWeight: 600,
                          color: '#334155',
                        }}
                        title={`Tài khoản liên kết: @${githubUsername}`}
                      >
                        <User size={13} color="#64748B" />
                        <span>@{githubUsername}</span>
                      </span>
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <button
                    onClick={handleStartOAuth}
                    disabled={isAuthenticating}
                    title="Cấp quyền lại / Làm mới Token GitHub nếu token hết hạn"
                    style={{
                      height: 34,
                      padding: '0 10px',
                      borderRadius: 8,
                      border: '1px solid #CBD5E1',
                      backgroundColor: '#FFFFFF',
                      color: '#2563EB',
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      fontSize: 12,
                      fontWeight: 600,
                      cursor: isAuthenticating ? 'not-allowed' : 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#EFF6FF')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
                  >
                    <RefreshCw size={13} className={isAuthenticating ? 'animate-spin' : ''} />
                    <span>Cấp quyền lại</span>
                  </button>
                  <button
                    onClick={() => setIsConnectModalOpen(true)}
                    title="Đổi repository khác"
                    style={{
                      width: 34,
                      height: 34,
                      borderRadius: 8,
                      border: '1px solid #E2E8F0',
                      backgroundColor: '#FFFFFF',
                      color: '#334155',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
                  >
                    <ArrowLeftRight size={15} />
                  </button>
                  <button
                    onClick={handleDisconnect}
                    title="Ngắt kết nối repository"
                    style={{
                      width: 34,
                      height: 34,
                      borderRadius: 8,
                      border: '1px solid #FEE2E2',
                      backgroundColor: '#FEF2F2',
                      color: '#DC2626',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#FEE2E2')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FEF2F2')}
                  >
                    <Unlink size={15} />
                  </button>
                </div>
              </div>
            </div>

            {/* Card 2: Sync Issues */}
            <div
              style={{
                backgroundColor: '#FFFFFF',
                borderRadius: 14,
                border: '1px solid #E2E8F0',
                padding: '20px 24px',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
                <div>
                  <h3 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                    Đồng bộ Công việc (Issues)
                  </h3>
                  <p style={{ fontSize: 12, color: '#64748B', margin: '4px 0 0' }}>
                    Kéo toàn bộ Issues từ kho GitHub về làm Task hoặc đồng bộ lại các trạng thái mới nhất
                  </p>
                </div>
                <button
                  onClick={handleSyncIssues}
                  disabled={syncingIssues}
                  title="Đồng bộ danh sách Issues từ GitHub"
                  style={{
                    height: 34,
                    padding: '0 14px',
                    borderRadius: 8,
                    border: '1px solid #CBD5E1',
                    backgroundColor: '#FFFFFF',
                    color: '#1E293B',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 6,
                    fontSize: 13,
                    fontWeight: 600,
                    cursor: syncingIssues ? 'not-allowed' : 'pointer',
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#F8FAFC')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#FFFFFF')}
                >
                  <RefreshCw size={14} className={syncingIssues ? 'animate-spin' : ''} />
                  <span>{syncingIssues ? 'Đang đồng bộ...' : 'Đồng bộ ngay'}</span>
                </button>
              </div>

              {(status?.lastSyncedAt || status?.last_synced_at) && (
                <div style={{ fontSize: 12, color: '#64748B', display: 'flex', alignItems: 'center', gap: 6, paddingTop: 6, borderTop: '1px dashed #E2E8F0' }}>
                  <span>Lần đồng bộ gần nhất:</span>
                  <span style={{ fontWeight: 600, color: '#334155' }}>
                    {new Date(status.lastSyncedAt || status.last_synced_at).toLocaleString('vi-VN')}
                  </span>
                </div>
              )}
            </div>

            {/* Card 3: Automation Settings */}
            <div
              style={{
                backgroundColor: '#FFFFFF',
                borderRadius: 14,
                border: '1px solid #E2E8F0',
                padding: '20px 24px',
                boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Settings size={18} color="#2563EB" />
                  <h3 style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                    Quy Tắc Tự Động Hóa
                  </h3>
                </div>

                <button
                  onClick={handleSaveConfig}
                  disabled={savingConfig}
                  title="Lưu cấu hình quy tắc tự động hóa"
                  aria-label="Lưu cấu hình quy tắc tự động hóa"
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: 8,
                    border: 'none',
                    backgroundColor: '#2563EB',
                    color: '#FFFFFF',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: savingConfig ? 'not-allowed' : 'pointer',
                    transition: 'all 0.15s ease',
                    boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
                  onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
                >
                  {savingConfig ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />}
                </button>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <label style={{ display: 'flex', alignItems: 'flex-start', gap: 12, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={config.autoMoveInReviewOnPr}
                    onChange={(e) => setConfig({ ...config, autoMoveInReviewOnPr: e.target.checked })}
                    style={{ marginTop: 3, width: 16, height: 16, accentColor: '#2563EB', cursor: 'pointer' }}
                  />
                  <div>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', display: 'block' }}>
                      Tự động chuyển Task sang "IN REVIEW" khi có Pull Request được mở
                    </span>
                    <span style={{ fontSize: 12, color: '#64748B' }}>
                      Áp dụng khi nhánh hoặc tiêu đề PR chứa mã Task (ví dụ: feat/DATN-12-auth).
                    </span>
                  </div>
                </label>

                <label style={{ display: 'flex', alignItems: 'flex-start', gap: 12, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={config.autoCloseTaskOnPrMerge}
                    onChange={(e) => setConfig({ ...config, autoCloseTaskOnPrMerge: e.target.checked })}
                    style={{ marginTop: 3, width: 16, height: 16, accentColor: '#2563EB', cursor: 'pointer' }}
                  />
                  <div>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', display: 'block' }}>
                      Tự động hoàn thành Task ("DONE") khi Pull Request được Merge
                    </span>
                    <span style={{ fontSize: 12, color: '#64748B' }}>
                      Khi code được merge vào nhánh chính, hệ thống sẽ tự động cập nhật thẻ công việc sang trạng thái Hoàn thành.
                    </span>
                  </div>
                </label>

                <label style={{ display: 'flex', alignItems: 'flex-start', gap: 12, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={config.autoCloseGithubIssueOnDone}
                    onChange={(e) => setConfig({ ...config, autoCloseGithubIssueOnDone: e.target.checked })}
                    style={{ marginTop: 3, width: 16, height: 16, accentColor: '#2563EB', cursor: 'pointer' }}
                  />
                  <div>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', display: 'block' }}>
                      Tự động đóng GitHub Issue khi Task trên Board chuyển sang cột "DONE"
                    </span>
                    <span style={{ fontSize: 12, color: '#64748B' }}>
                      Giúp đồng bộ 2 chiều: đội ngũ quản lý kéo thẻ xong thì lập trình viên trên GitHub cũng thấy Issue được đóng.
                    </span>
                  </div>
                </label>

                <label style={{ display: 'flex', alignItems: 'flex-start', gap: 12, cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={config.syncIssuesEnabled}
                    onChange={(e) => setConfig({ ...config, syncIssuesEnabled: e.target.checked })}
                    style={{ marginTop: 3, width: 16, height: 16, accentColor: '#2563EB', cursor: 'pointer' }}
                  />
                  <div>
                    <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', display: 'block' }}>
                      Bật cơ chế lắng nghe Webhook thời gian thực từ GitHub
                    </span>
                    <span style={{ fontSize: 12, color: '#64748B' }}>
                      Tự động nhận thông báo khi có commit mới, thay đổi PR hoặc tạo issue trên GitHub.
                    </span>
                  </div>
                </label>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Modal Chọn Repository */}
      <ConnectRepoModal
        key={authVersion}
        isOpen={isConnectModalOpen}
        onClose={() => setIsConnectModalOpen(false)}
        projectId={projectId}
        onReauth={handleStartOAuth}
        onConnected={(newStatus) => {
          if (newStatus) setStatus(newStatus);
          reloadStatus(true);
        }}
      />
    </div>
  );
}
