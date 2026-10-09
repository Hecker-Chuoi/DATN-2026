import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  User,
  Mail,
  Shield,
  Key,
  Calendar,
  CheckCircle2,
  Copy,
  Check,
  RefreshCw,
  ExternalLink,
  Users,
  Layers,
  Sparkles,
  Lock,
} from 'lucide-react';
import toast from 'react-hot-toast';
import { userApi } from '../api.js';
import GithubConnectCard from '../components/profile/GithubConnectCard.jsx';

export default function ProfilePage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [copiedField, setCopiedField] = useState(null);

  const fetchProfile = async (showToast = false) => {
    try {
      if (showToast) setRefreshing(true);
      else setLoading(true);

      const res = await userApi.me();
      if (res && res.data) {
        setProfile(res.data);
        if (showToast) toast.success('Đã làm mới thông tin tài khoản!');
      }
    } catch (err) {
      console.error('Failed to load user profile:', err);
      toast.error('Không thể tải thông tin tài khoản');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  const handleCopy = (text, fieldName) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    toast.success(`Đã sao chép ${fieldName}!`);
    setTimeout(() => setCopiedField(null), 2000);
  };

  const getInitials = (name) => {
    if (!name) return 'U';
    const parts = name.trim().split(' ');
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

  const getAvatarBg = (str) => {
    const colors = ['#2563EB', '#7C3AED', '#DB2777', '#059669', '#D97706', '#0284C7', '#4F46E5'];
    let hash = 0;
    for (let i = 0; i < (str || '').length; i++) {
      hash = str.charCodeAt(i) + ((hash << 5) - hash);
    }
    return colors[Math.abs(hash) % colors.length];
  };

  if (loading) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100%',
          backgroundColor: '#FFFFFF',
          gap: 12,
        }}
      >
        <div
          style={{
            width: 36,
            height: 36,
            border: '3px solid #E2E8F0',
            borderTopColor: '#2563EB',
            borderRadius: '50%',
            animation: 'spin 0.8s linear infinite',
          }}
        />
        <span style={{ fontSize: 13, color: '#64748B' }}>Đang tải thông tin tài khoản...</span>
        <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
      </div>
    );
  }

  const displayName = profile?.fullName || profile?.username || 'Người dùng';
  const initials = getInitials(displayName);
  const avatarBg = getAvatarBg(profile?.email || displayName);
  const isGoogleAccount = profile?.email && profile?.email.includes('@');

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: '#F8FAFC',
        overflowY: 'auto',
        fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      {/* ─── Top Bar / Header ─── */}
      <div
        style={{
          padding: '14px 28px',
          backgroundColor: '#FFFFFF',
          borderBottom: '1px solid #E2E8F0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#64748B' }}>
          <span
            onClick={() => navigate('/projects')}
            style={{ cursor: 'pointer', color: '#64748B' }}
            onMouseEnter={(e) => (e.currentTarget.style.color = '#2563EB')}
            onMouseLeave={(e) => (e.currentTarget.style.color = '#64748B')}
          >
            Không gian làm việc
          </span>
          <span>/</span>
          <span style={{ fontWeight: 600, color: '#0F172A' }}>Tài khoản cá nhân</span>
        </div>

        <button
          onClick={() => fetchProfile(true)}
          disabled={refreshing}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            padding: '6px 12px',
            borderRadius: 6,
            border: '1px solid #E2E8F0',
            backgroundColor: '#FFFFFF',
            fontSize: 12,
            fontWeight: 500,
            color: '#334155',
            cursor: refreshing ? 'not-allowed' : 'pointer',
            transition: 'all 0.15s ease',
          }}
          onMouseEnter={(e) => {
            if (!refreshing) e.currentTarget.style.backgroundColor = '#F1F5F9';
          }}
          onMouseLeave={(e) => {
            if (!refreshing) e.currentTarget.style.backgroundColor = '#FFFFFF';
          }}
        >
          <RefreshCw size={13} style={{ animation: refreshing ? 'spin 1s linear infinite' : 'none' }} />
          <span>{refreshing ? 'Đang cập nhật...' : 'Làm mới'}</span>
        </button>
      </div>

      {/* ─── Main Content Container ─── */}
      <div
        style={{
          maxWidth: 920,
          width: '100%',
          margin: '0 auto',
          padding: '28px 24px 48px',
          display: 'flex',
          flexDirection: 'column',
          gap: 24,
        }}
      >
        {/* ─── Banner Card: Avatar & Summary ─── */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 12,
            border: '1px solid #E2E8F0',
            overflow: 'hidden',
            boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
          }}
        >
          {/* Header Banner Background */}
          <div
            style={{
              height: 100,
              background: 'linear-gradient(135deg, #1E40AF 0%, #3B82F6 50%, #60A5FA 100%)',
              position: 'relative',
            }}
          />

          <div style={{ padding: '0 28px 24px', position: 'relative' }}>
            {/* Avatar */}
            <div
              style={{
                width: 80,
                height: 80,
                borderRadius: '50%',
                backgroundColor: avatarBg,
                color: '#FFFFFF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 28,
                fontWeight: 700,
                border: '4px solid #FFFFFF',
                boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
                marginTop: -40,
                marginBottom: 16,
              }}
            >
              {initials}
            </div>

            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <h1 style={{ fontSize: 22, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                    {displayName}
                  </h1>
                  {profile?.status === 'ACTIVE' && (
                    <span
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 4,
                        fontSize: 12,
                        fontWeight: 600,
                        color: '#059669',
                        backgroundColor: '#ECFDF5',
                        padding: '2px 8px',
                        borderRadius: 999,
                        border: '1px solid #A7F3D0',
                      }}
                    >
                      <CheckCircle2 size={12} />
                      Hoạt động
                    </span>
                  )}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginTop: 6, color: '#64748B', fontSize: 13, flexWrap: 'wrap' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Mail size={14} color="#94A3B8" />
                    {profile?.email || 'Chưa cập nhật email'}
                  </span>
                  {profile?.username && (
                    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <User size={14} color="#94A3B8" />
                      @{profile.username}
                    </span>
                  )}
                </div>
              </div>

              {/* Badges / Quick stats */}
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {(profile?.roles || ['USER']).map((role) => (
                  <span
                    key={role}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      fontSize: 12,
                      fontWeight: 600,
                      color: role === 'ADMIN' ? '#7C3AED' : '#2563EB',
                      backgroundColor: role === 'ADMIN' ? '#F5F3FF' : '#EFF6FF',
                      border: `1px solid ${role === 'ADMIN' ? '#DDD6FE' : '#BFDBFE'}`,
                      padding: '4px 10px',
                      borderRadius: 6,
                    }}
                  >
                    <Shield size={12} />
                    {role === 'ADMIN' ? 'Quản trị viên' : 'Thành viên'}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* ─── Grid: 2 Columns ─── */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: 24 }}>
          {/* Card 1: Personal Information */}
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              border: '1px solid #E2E8F0',
              padding: 24,
              boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
              <div
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: 8,
                  backgroundColor: '#EFF6FF',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#2563EB',
                }}
              >
                <User size={16} />
              </div>
              <div>
                <h3 style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', margin: 0 }}>
                  Thông tin cá nhân
                </h3>
                <p style={{ fontSize: 12, color: '#64748B', margin: 0 }}>
                  Dữ liệu tài khoản trong hệ thống
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <InfoRow label="Họ và tên" value={profile?.fullName || '—'} />
              <InfoRow label="Tên đăng nhập" value={profile?.username || '—'} />
              <InfoRow label="Họ & tên đệm" value={profile?.firstName || '—'} />
              <InfoRow label="Tên" value={profile?.lastName || '—'} />
              <InfoRow label="Địa chỉ thư điện tử" value={profile?.email || '—'} />
              <InfoRow
                label="Ngày tạo tài khoản"
                value={
                  profile?.createdAt
                    ? new Date(profile.createdAt).toLocaleString('vi-VN', {
                        day: '2-digit',
                        month: '2-digit',
                        year: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })
                    : '—'
                }
              />
            </div>
          </div>

          {/* Card 2: Identifiers & Security */}
          <div
            style={{
              backgroundColor: '#FFFFFF',
              borderRadius: 12,
              border: '1px solid #E2E8F0',
              padding: 24,
              boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
              <div
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: 8,
                  backgroundColor: '#F5F3FF',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#7C3AED',
                }}
              >
                <Key size={16} />
              </div>
              <div>
                <h3 style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', margin: 0 }}>
                  Định danh & Bảo mật
                </h3>
                <p style={{ fontSize: 12, color: '#64748B', margin: 0 }}>
                  Mã định danh nghiệp vụ và xác thực tài khoản
                </p>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* Database User ID */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: '#64748B' }}>
                    Mã người dùng
                  </span>
                  <button
                    onClick={() => handleCopy(profile?.id, 'Mã người dùng')}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      background: 'none',
                      border: 'none',
                      color: '#2563EB',
                      cursor: 'pointer',
                      fontSize: 11,
                      fontWeight: 600,
                      padding: '2px 6px',
                      borderRadius: 4,
                    }}
                  >
                    {copiedField === 'Mã người dùng' ? <Check size={12} color="#059669" /> : <Copy size={12} />}
                    {copiedField === 'Mã người dùng' ? 'Đã chép' : 'Sao chép'}
                  </button>
                </div>
                <div
                  style={{
                    backgroundColor: '#F8FAFC',
                    border: '1px solid #E2E8F0',
                    borderRadius: 6,
                    padding: '8px 10px',
                    fontSize: 12,
                    fontFamily: 'monospace',
                    color: '#0F172A',
                    wordBreak: 'break-all',
                  }}
                >
                  {profile?.id || '—'}
                </div>
              </div>

              {/* Keycloak Auth ID */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 500, color: '#64748B' }}>
                    Mã xác thực tài khoản
                  </span>
                  <button
                    onClick={() => handleCopy(profile?.keycloakId, 'Mã xác thực')}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      background: 'none',
                      border: 'none',
                      color: '#2563EB',
                      cursor: 'pointer',
                      fontSize: 11,
                      fontWeight: 600,
                      padding: '2px 6px',
                      borderRadius: 4,
                    }}
                  >
                    {copiedField === 'Mã xác thực' ? <Check size={12} color="#059669" /> : <Copy size={12} />}
                    {copiedField === 'Mã xác thực' ? 'Đã chép' : 'Sao chép'}
                  </button>
                </div>
                <div
                  style={{
                    backgroundColor: '#F8FAFC',
                    border: '1px solid #E2E8F0',
                    borderRadius: 6,
                    padding: '8px 10px',
                    fontSize: 12,
                    fontFamily: 'monospace',
                    color: '#0F172A',
                    wordBreak: 'break-all',
                  }}
                >
                  {profile?.keycloakId || '—'}
                </div>
              </div>

              {/* Authentication Type */}
              <InfoRow
                label="Phương thức đăng nhập"
                value={
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: 600 }}>
                    <Sparkles size={14} color="#D97706" />
                    {isGoogleAccount ? 'Tài khoản Google' : 'Tài khoản hệ thống'}
                  </span>
                }
              />

              {/* Status */}
              <InfoRow
                label="Trạng thái tài khoản"
                value={
                  <span
                    style={{
                      display: 'inline-block',
                      fontSize: 12,
                      fontWeight: 600,
                      color: profile?.status === 'ACTIVE' ? '#059669' : '#DC2626',
                      backgroundColor: profile?.status === 'ACTIVE' ? '#ECFDF5' : '#FEF2F2',
                      padding: '2px 8px',
                      borderRadius: 4,
                    }}
                  >
                    {profile?.status === 'ACTIVE' ? 'Đang hoạt động' : profile?.status || '—'}
                  </span>
                }
              />
            </div>
          </div>
        </div>

        {/* ─── Card 2b: Tài khoản liên kết (GitHub) ─── */}
        <GithubConnectCard />

        {/* ─── Card 3: Workspace Quick Links / Context ─── */}
        <div
          style={{
            backgroundColor: '#FFFFFF',
            borderRadius: 12,
            border: '1px solid #E2E8F0',
            padding: 24,
            boxShadow: '0 1px 3px 0 rgba(0, 0, 0, 0.05)',
          }}
        >
          <h3 style={{ fontSize: 15, fontWeight: 600, color: '#0F172A', marginBottom: 16 }}>
            Truy cập nhanh trong Không gian làm việc
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 14 }}>
            <QuickLinkCard
              icon={<Layers size={18} color="#2563EB" />}
              title="Danh sách dự án"
              desc="Xem và quản lý các dự án bạn đang tham gia"
              onClick={() => navigate('/projects')}
            />
            <QuickLinkCard
              icon={<Users size={18} color="#7C3AED" />}
              title="Nhóm làm việc"
              desc="Xem danh sách nhóm và các thành viên cộng tác"
              onClick={() => navigate('/projects')}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function InfoRow({ label, value }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingBottom: 10,
        borderBottom: '1px solid #F8FAFC',
        fontSize: 13,
      }}
    >
      <span style={{ color: '#64748B', fontWeight: 500 }}>{label}</span>
      <span style={{ color: '#0F172A', fontWeight: 600, textAlign: 'right' }}>{value}</span>
    </div>
  );
}

function QuickLinkCard({ icon, title, desc, onClick }) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 12,
        padding: 14,
        borderRadius: 8,
        border: '1px solid #E2E8F0',
        backgroundColor: '#FFFFFF',
        cursor: 'pointer',
        transition: 'all 0.15s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.backgroundColor = '#F8FAFC';
        e.currentTarget.style.borderColor = '#CBD5E1';
        e.currentTarget.style.transform = 'translateY(-1px)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.backgroundColor = '#FFFFFF';
        e.currentTarget.style.borderColor = '#E2E8F0';
        e.currentTarget.style.transform = 'none';
      }}
    >
      <div style={{ marginTop: 2 }}>{icon}</div>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', marginBottom: 2 }}>
          {title}
        </div>
        <div style={{ fontSize: 11, color: '#64748B' }}>{desc}</div>
      </div>
      <ExternalLink size={14} color="#94A3B8" style={{ marginTop: 2 }} />
    </div>
  );
}
