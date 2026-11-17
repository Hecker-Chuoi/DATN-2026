import React, { useEffect, useMemo, useState } from 'react';
import { Search, LayoutTemplate, Layers, Timer, ListChecks, Users2, ArrowRight, Eye } from 'lucide-react';
import { templateApi } from '../../api.js';
import { STATUS_TOKENS } from '../../constants/jiraTokens.js';
import { TEMPLATE_CATEGORIES, categoryToken, PROJECT_TYPE_LABELS } from '../../constants/templateConstants.js';

/**
 * Thư viện dự án mẫu trong workspace hiện tại. Bấm vào một thẻ → onPreview(template) để mở modal xem trước.
 */
export default function TemplateGallery({ workspaceId, selectedId, onPreview }) {
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [category, setCategory] = useState('ALL');
  const [search, setSearch] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    templateApi
      .list({ workspaceId: workspaceId || undefined })
      .then((res) => {
        if (!cancelled) setTemplates(Array.isArray(res?.data) ? res.data : []);
      })
      .catch(() => {
        if (!cancelled) setTemplates([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [workspaceId]);

  const presentCategories = useMemo(
    () => Object.keys(TEMPLATE_CATEGORIES).filter((c) => templates.some((t) => (t.templateCategory || 'OTHER') === c)),
    [templates]
  );

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return templates.filter((t) => {
      if (category !== 'ALL' && (t.templateCategory || 'OTHER') !== category) return false;
      if (!q) return true;
      return [t.name, t.key, t.description].some((v) => v && v.toLowerCase().includes(q));
    });
  }, [templates, category, search]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {/* Bộ lọc */}
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
        <div style={{ position: 'relative', flex: '1 1 220px' }}>
          <Search size={14} style={{ position: 'absolute', left: 10, top: 10, color: '#94A3B8' }} />
          <input
            type="text"
            placeholder="Tìm mẫu theo tên, mã, mô tả..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{
              width: '100%',
              padding: '7px 10px 7px 30px',
              borderRadius: 8,
              border: '1px solid #CBD5E1',
              fontSize: 13,
              outline: 'none',
              boxSizing: 'border-box',
            }}
          />
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          <CategoryChip active={category === 'ALL'} label="Tất cả" onClick={() => setCategory('ALL')} />
          {presentCategories.map((c) => (
            <CategoryChip
              key={c}
              active={category === c}
              label={categoryToken(c).label}
              color={categoryToken(c).color}
              onClick={() => setCategory(c)}
            />
          ))}
        </div>
      </div>

      {loading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))', gap: 12 }}>
          {[0, 1, 2, 3].map((i) => (
            <div key={i} style={{ height: 170, borderRadius: 10, backgroundColor: '#F1F5F9' }} />
          ))}
        </div>
      ) : visible.length === 0 ? (
        <EmptyState hasTemplates={templates.length > 0} />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(250px, 1fr))', gap: 12 }}>
          {visible.map((t) => (
            <TemplateCard key={t.id} template={t} selected={t.id === selectedId} onClick={() => onPreview(t)} />
          ))}
        </div>
      )}
    </div>
  );
}

function TemplateCard({ template, selected, onClick }) {
  const cat = categoryToken(template.templateCategory);
  const statuses = template.statuses || [];
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        textAlign: 'left',
        padding: 14,
        borderRadius: 10,
        border: `2px solid ${selected ? '#2563EB' : '#E2E8F0'}`,
        backgroundColor: selected ? '#F8FBFF' : '#FFFFFF',
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        fontFamily: 'inherit',
        transition: 'border-color 0.15s ease, box-shadow 0.15s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.boxShadow = '0 4px 12px rgba(15,23,42,0.08)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.boxShadow = 'none';
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <span style={chip(cat.color, cat.bg)}>{cat.label}</span>
        <span style={chip('#475569', '#F1F5F9')}>{PROJECT_TYPE_LABELS[template.projectType] || template.projectType}</span>
        <span style={{ marginLeft: 'auto', fontSize: 11, color: '#94A3B8', display: 'flex', alignItems: 'center', gap: 3 }}>
          <Users2 size={12} /> {template.usageCount || 0}
        </span>
      </div>

      <div>
        <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', lineHeight: 1.3 }}>{template.name}</div>
        <div style={{ fontSize: 11, color: '#2563EB', fontWeight: 700, marginTop: 2 }}>{template.key}</div>
      </div>

      <div
        style={{
          fontSize: 12,
          color: '#64748B',
          lineHeight: 1.4,
          minHeight: 34,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {template.description || 'Không có mô tả'}
      </div>

      {/* Luồng trạng thái */}
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 3 }}>
        {statuses.slice(0, 5).map((s, idx) => {
          const tk = STATUS_TOKENS[s.category] || STATUS_TOKENS.TODO;
          return (
            <React.Fragment key={s.name + idx}>
              {idx > 0 && <ArrowRight size={10} color="#CBD5E1" />}
              <span
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  padding: '1px 6px',
                  borderRadius: 4,
                  color: tk.color,
                  backgroundColor: tk.bg,
                }}
              >
                {s.name}
              </span>
            </React.Fragment>
          );
        })}
        {statuses.length > 5 && <span style={{ fontSize: 10, color: '#94A3B8' }}>+{statuses.length - 5}</span>}
      </div>

      <div style={{ display: 'flex', gap: 10, fontSize: 11, color: '#475569', borderTop: '1px solid #F1F5F9', paddingTop: 8 }}>
        <Stat icon={<Layers size={12} />} value={template.workItemTypeCount} label="loại" />
        <Stat icon={<Timer size={12} />} value={template.sprintCount} label="chu kỳ" />
        <Stat icon={<ListChecks size={12} />} value={template.workItemCount} label="việc" />
        {template.timelineSpanDays != null && (
          <span style={{ marginLeft: 'auto', color: '#64748B' }}>{template.timelineSpanDays} ngày</span>
        )}
      </div>

      <div style={{ fontSize: 11, color: '#2563EB', fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4 }}>
        <Eye size={12} /> Xem trước
      </div>
    </button>
  );
}

function Stat({ icon, value, label }) {
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
      {icon}
      <strong>{value ?? 0}</strong> {label}
    </span>
  );
}

function CategoryChip({ active, label, color = '#2563EB', onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        padding: '5px 10px',
        borderRadius: 999,
        border: `1px solid ${active ? color : '#E2E8F0'}`,
        backgroundColor: active ? color : '#FFFFFF',
        color: active ? '#FFFFFF' : '#475569',
        fontSize: 12,
        fontWeight: 600,
        cursor: 'pointer',
        fontFamily: 'inherit',
      }}
    >
      {label}
    </button>
  );
}

function EmptyState({ hasTemplates }) {
  return (
    <div
      style={{
        padding: '36px 16px',
        textAlign: 'center',
        border: '1px dashed #CBD5E1',
        borderRadius: 10,
        color: '#64748B',
        fontSize: 13,
      }}
    >
      <LayoutTemplate size={28} color="#94A3B8" style={{ marginBottom: 8 }} />
      <div style={{ fontWeight: 600, color: '#334155' }}>
        {hasTemplates ? 'Không có mẫu phù hợp bộ lọc' : 'Workspace chưa có dự án mẫu nào'}
      </div>
      {!hasTemplates && (
        <div style={{ marginTop: 4 }}>
          Dùng tùy chọn “Lưu thành mẫu” trong menu của một dự án có sẵn để tạo dự án mẫu.
        </div>
      )}
    </div>
  );
}

function chip(color, bg) {
  return {
    fontSize: 10,
    fontWeight: 700,
    padding: '2px 7px',
    borderRadius: 4,
    color,
    backgroundColor: bg,
    textTransform: 'uppercase',
    letterSpacing: '0.03em',
  };
}
