import React from 'react';
import { Link } from 'react-router-dom';
import { ShieldCheck, LayoutDashboard, ArrowRight, Server, KeyRound, Sparkles } from 'lucide-react';

export default function LandingPage() {
  return (
    <div className="landing-container">
      {/* Top Header */}
      <header className="landing-header">
        <div className="landing-brand">
          <ShieldCheck size={28} />
          <span>DATN 2026</span>
        </div>
        <Link to="/admin" className="cta-button" style={{ padding: '8px 20px', fontSize: '14px' }}>
          Cổng Quản trị
        </Link>
      </header>

      {/* Main Hero Section */}
      <main className="landing-hero">
        <div className="hero-badge">
          <Sparkles size={14} style={{ display: 'inline', marginRight: 6 }} />
          Nền tảng Quản trị Công việc & Dự án Doanh nghiệp
        </div>
        <h1 className="hero-title">
          Nền tảng Quản lý Công việc & Dự án Toàn diện
        </h1>
        <p className="hero-subtitle">
          Giải pháp toàn diện giúp doanh nghiệp lập kế hoạch, theo dõi tiến độ, phân bổ nguồn lực và tối ưu hiệu suất làm việc đa phòng ban.
        </p>

        <div style={{ display: 'flex', gap: 16 }}>
          <Link to="/admin" className="cta-button" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span>Truy cập Cổng Quản trị</span>
            <ArrowRight size={18} />
          </Link>
        </div>

        {/* Feature Cards Grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: 24,
          maxWidth: 900,
          width: '100%',
          marginTop: 60,
          textAlign: 'left'
        }}>
          <div style={{ background: 'rgba(255,255,255,0.05)', padding: 24, borderRadius: 12, border: '1px solid rgba(255,255,255,0.1)' }}>
            <KeyRound color="#38bdf8" size={24} style={{ marginBottom: 12 }} />
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Bảo mật & Phân quyền tập trung</h3>
            <p style={{ fontSize: 13, color: '#94a3b8' }}>Bảo mật đa tầng, đăng nhập an toàn tiện lợi qua Google và tài khoản doanh nghiệp.</p>
          </div>

          <div style={{ background: 'rgba(255,255,255,0.05)', padding: 24, borderRadius: 12, border: '1px solid rgba(255,255,255,0.1)' }}>
            <Server color="#38bdf8" size={24} style={{ marginBottom: 12 }} />
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Điều phối & Vận hành mượt mà</h3>
            <p style={{ fontSize: 13, color: '#94a3b8' }}>Hạ tầng ổn định, tốc độ xử lý tức thì, đảm bảo dữ liệu luôn xuyên suốt giữa các nhóm công tác.</p>
          </div>

          <div style={{ background: 'rgba(255,255,255,0.05)', padding: 24, borderRadius: 12, border: '1px solid rgba(255,255,255,0.1)' }}>
            <LayoutDashboard color="#38bdf8" size={24} style={{ marginBottom: 12 }} />
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Quản lý Người dùng</h3>
            <p style={{ fontSize: 13, color: '#94a3b8' }}>Giao diện quản lý người dùng, tìm kiếm và phân quyền vai trò linh hoạt.</p>
          </div>
        </div>
      </main>
    </div>
  );
}
