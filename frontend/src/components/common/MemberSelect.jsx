import React, { useState, useRef, useEffect } from 'react';
import { User, Check, ChevronDown, Search, X } from 'lucide-react';

const AVATAR_COLORS = [
  '#2563EB', '#7C3AED', '#DB2777', '#EA580C', '#059669', '#0891B2', '#4F46E5', '#D97706'
];

function getAvatarColor(name = '') {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

export default function MemberSelect({
  members = [],
  value = '',
  onChange,
  placeholder = 'Chưa gán (Unassigned)',
  disabled = false,
  style = {},
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const containerRef = useRef(null);

  const selectedMember = members.find((m) => (m.userId || m.id) === value);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const filteredMembers = members.filter((m) => {
    if (!searchTerm.trim()) return true;
    const term = searchTerm.toLowerCase();
    return (
      m.fullName?.toLowerCase().includes(term) ||
      m.username?.toLowerCase().includes(term) ||
      m.email?.toLowerCase().includes(term)
    );
  });

  return (
    <div ref={containerRef} style={{ position: 'relative', width: '100%', ...style }}>
      {/* Trigger Button */}
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen(!isOpen)}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '7px 10px',
          borderRadius: 8,
          border: `1px solid ${isOpen ? '#2563EB' : '#CBD5E1'}`,
          backgroundColor: '#FFFFFF',
          cursor: disabled ? 'not-allowed' : 'pointer',
          outline: 'none',
          boxSizing: 'border-box',
          transition: 'all 0.15s ease',
          boxShadow: isOpen ? '0 0 0 3px rgba(37,99,235,0.1)' : 'none',
        }}
      >
        {selectedMember ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden' }}>
            <div
              style={{
                width: 24,
                height: 24,
                borderRadius: '50%',
                backgroundColor: getAvatarColor(selectedMember.fullName || selectedMember.username || 'User'),
                color: '#FFFFFF',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 11,
                fontWeight: 700,
                flexShrink: 0,
              }}
            >
              {(selectedMember.fullName || selectedMember.username || 'U').charAt(0).toUpperCase()}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', overflow: 'hidden', textAlign: 'left' }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                {selectedMember.fullName || selectedMember.username || selectedMember.email}
              </span>
              {selectedMember.username && (
                <span style={{ fontSize: 11, color: '#64748B', lineHeight: 1 }}>@{selectedMember.username}</span>
              )}
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#64748B' }}>
            <div
              style={{
                width: 24,
                height: 24,
                borderRadius: '50%',
                backgroundColor: '#F1F5F9',
                border: '1px dashed #94A3B8',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#94A3B8',
              }}
            >
              <User size={12} />
            </div>
            <span style={{ fontSize: 13 }}>{placeholder}</span>
          </div>
        )}

        <ChevronDown size={15} color="#94A3B8" style={{ transform: isOpen ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s ease' }} />
      </button>

      {/* Dropdown Menu */}
      {isOpen && (
        <div
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            marginTop: 4,
            backgroundColor: '#FFFFFF',
            borderRadius: 8,
            border: '1px solid #E2E8F0',
            boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            zIndex: 9999,
            overflow: 'hidden',
            animation: 'modalSlideIn 0.15s ease',
          }}
        >
          {/* Search box inside dropdown */}
          {members.length > 4 && (
            <div style={{ padding: '8px 10px', borderBottom: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', gap: 6 }}>
              <Search size={13} color="#94A3B8" />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Tìm thành viên..."
                autoFocus
                style={{
                  border: 'none',
                  outline: 'none',
                  fontSize: 12,
                  width: '100%',
                  color: '#0F172A',
                }}
              />
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => setSearchTerm('')}
                  style={{ border: 'none', background: 'transparent', cursor: 'pointer', padding: 0, color: '#94A3B8' }}
                >
                  <X size={12} />
                </button>
              )}
            </div>
          )}

          {/* Members list */}
          <div style={{ maxHeight: 220, overflowY: 'auto', padding: 4 }}>
            {/* Unassigned Option */}
            <div
              onClick={() => {
                onChange('');
                setIsOpen(false);
              }}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '7px 10px',
                borderRadius: 6,
                cursor: 'pointer',
                backgroundColor: !value ? '#EFF6FF' : 'transparent',
                transition: 'background-color 0.1s ease',
              }}
              onMouseEnter={(e) => {
                if (value) e.currentTarget.style.backgroundColor = '#F8FAFC';
              }}
              onMouseLeave={(e) => {
                if (value) e.currentTarget.style.backgroundColor = 'transparent';
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    backgroundColor: '#F1F5F9',
                    border: '1px dashed #94A3B8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#94A3B8',
                  }}
                >
                  <User size={12} />
                </div>
                <span style={{ fontSize: 13, color: '#64748B' }}>Chưa gán (Unassigned)</span>
              </div>
              {!value && <Check size={14} color="#2563EB" strokeWidth={2.5} />}
            </div>

            {filteredMembers.map((m) => {
              const isSelected = (m.userId || m.id) === value;
              const avatarColor = getAvatarColor(m.fullName || m.username || 'User');
              const initial = (m.fullName || m.username || 'U').charAt(0).toUpperCase();

              return (
                <div
                  key={m.userId || m.id}
                  onClick={() => {
                    onChange(m.userId || m.id);
                    setIsOpen(false);
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '7px 10px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    backgroundColor: isSelected ? '#EFF6FF' : 'transparent',
                    transition: 'background-color 0.1s ease',
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) e.currentTarget.style.backgroundColor = '#F8FAFC';
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) e.currentTarget.style.backgroundColor = 'transparent';
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, overflow: 'hidden' }}>
                    <div
                      style={{
                        width: 28,
                        height: 28,
                        borderRadius: '50%',
                        backgroundColor: avatarColor,
                        color: '#FFFFFF',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 12,
                        fontWeight: 700,
                        flexShrink: 0,
                      }}
                    >
                      {initial}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                        {m.fullName || m.username || m.email}
                      </span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: '#64748B' }}>
                        {m.username && <span>@{m.username}</span>}
                        {m.username && m.email && <span>•</span>}
                        {m.email && <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{m.email}</span>}
                      </div>
                    </div>
                  </div>
                  {isSelected && <Check size={14} color="#2563EB" strokeWidth={2.5} />}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
