import { useState, useEffect } from 'react';
import { X, Search, Lock, Globe, Star, Check, Loader2, GitBranch } from 'lucide-react';
import toast from 'react-hot-toast';
import { githubIntegrationApi } from '../../api.js';

export default function ConnectRepoModal({ isOpen, onClose, projectId, onConnected }) {
  const [repos, setRepos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedRepo, setSelectedRepo] = useState(null);
  const [syncIssues, setSyncIssues] = useState(true);

  // Safe accessor helpers để tương thích cả snake_case (GitHub API gốc) và camelCase
  const getRepoFullName = (r) => r?.full_name || r?.fullName || r?.name || '';
  const getRepoUrl = (r) => r?.html_url || r?.htmlUrl || '';
  const isRepoPrivate = (r) => r?.private ?? r?.isPrivate ?? false;
  const getRepoBranch = (r) => r?.default_branch || r?.defaultBranch || 'main';
  const getRepoStars = (r) => r?.stargazers_count ?? r?.stargazersCount ?? 0;

  useEffect(() => {
    let ignore = false;
    if (isOpen && projectId) {
      githubIntegrationApi.getRepositories(projectId)
        .then((res) => {
          if (!ignore) {
            const data = res?.data;
            setRepos(Array.isArray(data) ? data : []);
          }
        })
        .catch((err) => {
          if (!ignore) {
            toast.error(err?.message || 'Không thể tải danh sách kho mã nguồn từ GitHub');
          }
        })
        .finally(() => {
          if (!ignore) setLoading(false);
        });
    }
    return () => {
      ignore = true;
    };
  }, [isOpen, projectId]);

  const handleConnect = async () => {
    if (!selectedRepo) {
      toast.error('Vui lòng chọn một repository để liên kết');
      return;
    }

    try {
      setSubmitting(true);
      const payload = {
        repoFullName: getRepoFullName(selectedRepo),
        repoUrl: getRepoUrl(selectedRepo),
        defaultBranch: getRepoBranch(selectedRepo),
        syncIssues: syncIssues,
      };

      const res = await githubIntegrationApi.connect(projectId, payload);
      toast.success('Liên kết kho mã nguồn thành công!');
      if (onConnected) {
        onConnected(res?.data);
      }
      onClose();
    } catch (err) {
      toast.error(err?.message || 'Liên kết repository thất bại');
    } finally {
      setSubmitting(false);
    }
  };

  if (!isOpen) return null;

  const repoList = Array.isArray(repos) ? repos : [];
  const query = (searchQuery || '').toLowerCase().trim();

  const filteredRepos = repoList.filter((r) => {
    const fullName = getRepoFullName(r).toLowerCase();
    const desc = (r?.description || '').toLowerCase();
    return fullName.includes(query) || desc.includes(query);
  });

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
        zIndex: 1000,
        padding: 16,
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 16,
          width: '100%',
          maxWidth: 600,
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          border: '1px solid #E2E8F0',
          overflow: 'hidden',
          animation: 'fadeIn 0.15s ease',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '18px 24px',
            borderBottom: '1px solid #F1F5F9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div>
            <h2 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: 0 }}>
              Liên kết kho mã nguồn GitHub
            </h2>
            <p style={{ fontSize: 13, color: '#64748B', margin: '4px 0 0' }}>
              Chọn repository bạn muốn kết nối với dự án này
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              color: '#94A3B8',
              padding: 6,
              borderRadius: 6,
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Search Bar */}
        <div style={{ padding: '16px 24px 8px 24px' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              backgroundColor: '#F8FAFC',
              border: '1px solid #E2E8F0',
              borderRadius: 8,
              padding: '8px 12px',
            }}
          >
            <Search size={16} color="#94A3B8" />
            <input
              type="text"
              placeholder="Tìm kiếm repository..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{
                border: 'none',
                background: 'transparent',
                outline: 'none',
                width: '100%',
                fontSize: 13,
                color: '#0F172A',
              }}
            />
          </div>
        </div>

        {/* Repository List */}
        <div
          style={{
            padding: '8px 24px',
            overflowY: 'auto',
            flex: 1,
            maxHeight: 360,
          }}
        >
          {loading ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#64748B' }}>
              <Loader2 size={28} className="animate-spin" style={{ margin: '0 auto 12px', color: '#2563EB' }} />
              <p style={{ fontSize: 13, margin: 0 }}>Đang tải danh sách kho từ GitHub...</p>
            </div>
          ) : filteredRepos.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '40px 0', color: '#94A3B8', fontSize: 13 }}>
              Không tìm thấy repository nào phù hợp
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {filteredRepos.map((repo) => {
                const fullName = getRepoFullName(repo);
                const isSelected = selectedRepo?.id === repo.id;
                const isPrivate = isRepoPrivate(repo);
                const branch = getRepoBranch(repo);
                const stars = getRepoStars(repo);

                return (
                  <div
                    key={repo.id}
                    onClick={() => setSelectedRepo(repo)}
                    style={{
                      padding: '12px 16px',
                      borderRadius: 10,
                      border: `1.5px solid ${isSelected ? '#2563EB' : '#E2E8F0'}`,
                      backgroundColor: isSelected ? '#EFF6FF' : '#FFFFFF',
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                      display: 'flex',
                      alignItems: 'flex-start',
                      justifyContent: 'space-between',
                      gap: 12,
                    }}
                  >
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                        <span style={{ fontWeight: 600, fontSize: 14, color: isSelected ? '#1D4ED8' : '#0F172A' }}>
                          {fullName}
                        </span>
                        {isPrivate ? (
                          <span
                            style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 3,
                              fontSize: 11,
                              padding: '1px 6px',
                              borderRadius: 4,
                              backgroundColor: '#F1F5F9',
                              color: '#64748B',
                              fontWeight: 500,
                            }}
                          >
                            <Lock size={10} /> Riêng tư
                          </span>
                        ) : (
                          <span
                            style={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 3,
                              fontSize: 11,
                              padding: '1px 6px',
                              borderRadius: 4,
                              backgroundColor: '#ECFDF5',
                              color: '#059669',
                              fontWeight: 500,
                            }}
                          >
                            <Globe size={10} /> Công khai
                          </span>
                        )}
                      </div>

                      {repo.description && (
                        <p
                          style={{
                            fontSize: 12,
                            color: '#64748B',
                            margin: '0 0 6px',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {repo.description}
                        </p>
                      )}

                      <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 11, color: '#94A3B8' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          <GitBranch size={12} /> {branch}
                        </span>
                        {stars > 0 && (
                          <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                            <Star size={11} fill="#EAB308" color="#EAB308" /> {stars}
                          </span>
                        )}
                      </div>
                    </div>

                    <div
                      style={{
                        width: 20,
                        height: 20,
                        borderRadius: '50%',
                        border: `2px solid ${isSelected ? '#2563EB' : '#CBD5E1'}`,
                        backgroundColor: isSelected ? '#2563EB' : '#FFFFFF',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: '#FFFFFF',
                        flexShrink: 0,
                        marginTop: 2,
                      }}
                    >
                      {isSelected && <Check size={12} strokeWidth={3} />}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Options & Footer */}
        <div
          style={{
            padding: '16px 24px',
            borderTop: '1px solid #F1F5F9',
            backgroundColor: '#F8FAFC',
          }}
        >
          <label
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              fontSize: 13,
              color: '#334155',
              cursor: 'pointer',
              marginBottom: 16,
            }}
          >
            <input
              type="checkbox"
              checked={syncIssues}
              onChange={(e) => setSyncIssues(e.target.checked)}
              style={{ width: 16, height: 16, accentColor: '#2563EB', cursor: 'pointer' }}
            />
            <span>Tự động kéo toàn bộ GitHub Issues đang mở về làm Task trên Board</span>
          </label>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              style={{
                padding: '8px 16px',
                borderRadius: 8,
                border: '1px solid #CBD5E1',
                backgroundColor: '#FFFFFF',
                color: '#334155',
                fontSize: 13,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Hủy
            </button>
            <button
              type="button"
              onClick={handleConnect}
              disabled={!selectedRepo || submitting}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                padding: '8px 20px',
                borderRadius: 8,
                border: 'none',
                backgroundColor: selectedRepo && !submitting ? '#2563EB' : '#94A3B8',
                color: '#FFFFFF',
                fontSize: 13,
                fontWeight: 600,
                cursor: selectedRepo && !submitting ? 'pointer' : 'not-allowed',
                boxShadow: selectedRepo ? '0 1px 2px rgba(37,99,235,0.2)' : 'none',
              }}
            >
              {submitting && <Loader2 size={15} className="animate-spin" />}
              <span>Liên kết ngay</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
