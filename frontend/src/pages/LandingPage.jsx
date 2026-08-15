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
          Portal Admin
        </Link>
      </header>

      {/* Main Hero Section */}
      <main className="landing-hero">
        <div className="hero-badge">
          <Sparkles size={14} style={{ display: 'inline', marginRight: 6 }} />
          Enterprise IAM & Microservices Architecture
        </div>
        <h1 className="hero-title">
          Identity Management & API Gateway Platform
        </h1>
        <p className="hero-subtitle">
          Hệ thống phân quyền truy cập, xác thực tập trung với Keycloak, Spring Cloud Gateway và React Frontend.
        </p>

        <div style={{ display: 'flex', gap: 16 }}>
          <Link to="/admin" className="cta-button" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span>Truy cập Admin Portal</span>
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
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Keycloak Authentication</h3>
            <p style={{ fontSize: 13, color: '#94a3b8' }}>Xác thực chuẩn OAuth2 / OIDC, hỗ trợ đăng nhập mật khẩu và Google Social Login.</p>
          </div>

          <div style={{ background: 'rgba(255,255,255,0.05)', padding: 24, borderRadius: 12, border: '1px solid rgba(255,255,255,0.1)' }}>
            <Server color="#38bdf8" size={24} style={{ marginBottom: 12 }} />
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>Spring Cloud Gateway</h3>
            <p style={{ fontSize: 13, color: '#94a3b8' }}>Cổng vào duy nhất điều phối request, kiểm tra JWT Token và phân quyền RBAC.</p>
          </div>

          <div style={{ background: 'rgba(255,255,255,0.05)', padding: 24, borderRadius: 12, border: '1px solid rgba(255,255,255,0.1)' }}>
            <LayoutDashboard color="#38bdf8" size={24} style={{ marginBottom: 12 }} />
            <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>User Management</h3>
            <p style={{ fontSize: 13, color: '#94a3b8' }}>Giao diện quản lý người dùng, tìm kiếm và phân quyền Role động linh hoạt.</p>
          </div>
        </div>
      </main>
    </div>
  );
}
