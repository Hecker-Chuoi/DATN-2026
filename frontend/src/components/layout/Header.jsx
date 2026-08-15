import React from 'react';
import { useLocation, Link, useNavigate } from 'react-router-dom';
import {
  Search,
  Plus,
  Bell,
  LogOut,
  ShieldAlert,
  ChevronRight,
  FolderKanban
} from 'lucide-react';

export default function Header({ currentUser, onLogout, onOpenCreateProject }) {
  const location = useLocation();
  const navigate = useNavigate();

  // Tạo breadcrumbs hiển thị linh hoạt
  const getBreadcrumbs = () => {
    if (location.pathname.startsWith('/projects/')) {
      return (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#64748B' }}>
          <Link to="/projects" style={{ color: '#64748B', textDecoration: 'none', fontWeight: 500 }}>
            Projects
          </Link>
          <ChevronRight size={14} />
          <span style={{ color: '#0F172A', fontWeight: 600 }}>Chi tiết Dự án</span>
        </div>
      );
    }
    if (location.pathname === '/projects') {
      return <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Dự án & Quy trình</span>;
    }
    if (location.pathname === '/marketplace') {
      return <span style={{ fontSize: 14, fontWeight: 700, color: '#8B5CF6' }}>Kho Mẫu Dự án (Templates Marketplace)</span>;
    }
    if (location.pathname.startsWith('/teams/')) {
      return <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Chi tiết Nhóm</span>;
    }
    return <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Work Management</span>;
  };

  const isAdmin = currentUser?.roles?.includes('ADMIN') || currentUser?.roles?.includes('ROLE_ADMIN');

  return (
    <header
      style={{
        height: 54,
        minHeight: 54,
        backgroundColor: '#FFFFFF',
        borderBottom: '1px solid #E2E8F0',
        padding: '0 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        userSelect: 'none',
      }}
    >
      {/* ─── Breadcrumb ─── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {getBreadcrumbs()}
      </div>

      {/* ─── Quick Actions & User Menu ─── */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {/* Search trigger */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            backgroundColor: '#F8FAFC',
            border: '1px solid #E2E8F0',
            borderRadius: 8,
            padding: '5px 10px',
            fontSize: 12,
            color: '#94A3B8',
            cursor: 'pointer',
            minWidth: 180,
          }}
        >
          <Search size={14} color="#64748B" />
          <span>Tìm kiếm... (Ctrl + K)</span>
        </div>

        {/* Nút Tạo Dự Án Mới */}
        <button
          onClick={onOpenCreateProject}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            backgroundColor: '#2563EB',
            color: '#FFFFFF',
            border: 'none',
            borderRadius: 7,
            padding: '6px 14px',
            fontSize: 13,
            fontWeight: 600,
            cursor: 'pointer',
            boxShadow: '0 1px 2px rgba(37,99,235,0.2)',
            transition: 'background-color 0.15s ease',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#1D4ED8')}
          onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = '#2563EB')}
        >
          <Plus size={15} />
          <span>Tạo dự án</span>
        </button>

        {/* Link chuyển sang Admin nếu là Admin */}
        {isAdmin && (
          <Link
            to="/admin"
            title="Trang quản trị hệ thống"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              backgroundColor: '#FEF2F2',
              color: '#DC2626',
              border: '1px solid #FECACA',
              borderRadius: 6,
              padding: '5px 9px',
              fontSize: 12,
              fontWeight: 600,
              textDecoration: 'none',
            }}
          >
            <ShieldAlert size={14} />
            <span>Admin</span>
          </Link>
        )}

        {/* Logout Button */}
        <button
          onClick={onLogout}
          title="Đăng xuất"
          style={{
            background: 'none',
            border: 'none',
            color: '#64748B',
            cursor: 'pointer',
            padding: 6,
            borderRadius: 6,
            display: 'flex',
            alignItems: 'center',
            transition: 'all 0.15s ease',
          }}
          onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#FEE2E2'; e.currentTarget.style.color = '#DC2626'; }}
          onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'transparent'; e.currentTarget.style.color = '#64748B'; }}
        >
          <LogOut size={16} />
        </button>
      </div>
    </header>
  );
}
