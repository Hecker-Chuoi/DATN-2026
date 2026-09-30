import { useEffect, useState } from 'react';
import {
  X,
  LayoutTemplate,
  AlertTriangle,
  XCircle,
  CheckCircle2,
  ChevronRight,
  ChevronDown,
  ArrowRight,
  Users2,
  CalendarRange,
  Check,
} from 'lucide-react';
import { templateApi } from '../../api.js';
import { STATUS_TOKENS, PRIORITY_TOKENS } from '../../constants/jiraTokens.js';
import { categoryToken, PROJECT_TYPE_LABELS, formatOffset } from '../../constants/templateConstants.js';

const TABS = [
  { id: 'OVERVIEW', label: 'Tổng quan' },
  { id: 'WORKFLOW', label: 'Trạng thái & Quy trình' },
  { id: 'BOARD', label: 'Board' },
  { id: 'PLAN', label: 'Sprint & Công việc' },
  { id: 'ROLES', label: 'Phân quyền' },
];

/**
 * Modal xem trước toàn bộ bản thiết kế của một dự án mẫu. Nút "Dùng mẫu này" trả về dữ liệu preview
 * cho form tạo dự án (bị khóa khi mẫu có lỗi toàn vẹn).
 */
export default function TemplatePreviewModal({ templateId, isOpen, onClose, onUse }) {
  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState('OVERVIEW');

  useEffect(() => {
    if (!isOpen || !templateId) return;
    let cancelled = false;
    setLoading(true);
    setPreview(null);
    setTab('OVERVIEW');
    templateApi
      .preview(templateId)
      .then((res) => {
        if (!cancelled) setPreview(res?.data || null);
      })
      .catch(() => {
        if (!cancelled) onClose();
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, templateId]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!isOpen) return null;

  const errors = preview?.integrity?.errors || [];
  const warnings = preview?.integrity?.warnings || [];
  const cat = categoryToken(preview?.templateCategory);

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.55)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 10000,
        padding: 16,
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          boxShadow: '0 25px 50px -12px rgba(0,0,0,0.3)',
          width: '100%',
          maxWidth: 920,
          height: '86vh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div style={{ padding: '18px 24px 0', borderBottom: '1px solid #E2E8F0' }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 10,
                backgroundColor: cat.bg,
                color: cat.color,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <LayoutTemplate size={20} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <h2 style={{ fontSize: 17, fontWeight: 700, color: '#0F172A', margin: 0 }}>
                  {preview?.name || (loading ? 'Đang tải mẫu...' : 'Dự án mẫu')}
                </h2>
                {preview && (
                  <>
                    <span style={badge(cat.color, cat.bg)}>{cat.label}</span>
                    <span style={badge('#475569', '#F1F5F9')}>
                      {PROJECT_TYPE_LABELS[preview.projectType] || preview.projectType}
                    </span>
                    <span style={{ fontSize: 12, color: '#64748B', display: 'flex', alignItems: 'center', gap: 4 }}>
                      <Users2 size={13} /> {preview.usageCount} dự án đã dùng
                    </span>
                  </>
                )}
              </div>
              <p style={{ fontSize: 12.5, color: '#64748B', margin: '4px 0 0' }}>
                {preview?.description || 'Xem trước cấu hình và kế hoạch sẽ được sao chép vào dự án mới.'}
              </p>
            </div>
            <button type="button" onClick={onClose} style={iconBtn}>
              <X size={18} />
            </button>
          </div>

          <div style={{ display: 'flex', gap: 2, marginTop: 14, overflowX: 'auto' }}>
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                style={{
                  padding: '9px 14px',
                  border: 'none',
                  borderBottom: `2px solid ${tab === t.id ? '#2563EB' : 'transparent'}`,
                  background: 'none',
                  color: tab === t.id ? '#2563EB' : '#475569',
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                  fontFamily: 'inherit',
                }}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: 24, backgroundColor: '#FAFBFC' }}>
          {loading || !preview ? (
            <div style={{ color: '#64748B', fontSize: 13 }}>Đang tải bản thiết kế của mẫu...</div>
          ) : (
            <>
              <IntegrityBanner errors={errors} warnings={warnings} />
              {tab === 'OVERVIEW' && <OverviewTab p={preview} />}
              {tab === 'WORKFLOW' && <WorkflowTab p={preview} />}
              {tab === 'BOARD' && <BoardTab p={preview} />}
              {tab === 'PLAN' && <PlanTab p={preview} />}
              {tab === 'ROLES' && <RolesTab p={preview} />}
            </>
          )}
        </div>

        {/* Footer */}
        <div
          style={{
            padding: '14px 24px',
            borderTop: '1px solid #E2E8F0',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
          }}
        >
          <span style={{ fontSize: 12, color: '#64748B', flex: 1 }}>
            Thành viên, bình luận, tệp đính kèm và lịch sử của mẫu không được sao chép. Sprint được đặt lại thành
            “Dự kiến”, công việc về trạng thái khởi tạo.
          </span>
          <button type="button" onClick={onClose} style={secondaryBtn}>
            Đóng
          </button>
          <button
            type="button"
            disabled={!preview || errors.length > 0}
            onClick={() => onUse(preview)}
            style={{
              ...primaryBtn,
              backgroundColor: !preview || errors.length > 0 ? '#93C5FD' : '#2563EB',
              cursor: !preview || errors.length > 0 ? 'not-allowed' : 'pointer',
            }}
            title={errors.length > 0 ? 'Mẫu đang có lỗi toàn vẹn, không thể sử dụng' : undefined}
          >
            <Check size={15} /> Dùng mẫu này
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Kiểm tra toàn vẹn ──────────────────────────────────────────────────────

function IntegrityBanner({ errors, warnings }) {
  const [open, setOpen] = useState(false);
  if (errors.length === 0 && warnings.length === 0) {
    return (
      <div style={{ ...banner('#ECFDF5', '#A7F3D0', '#047857'), marginBottom: 16 }}>
        <CheckCircle2 size={15} /> Mẫu hợp lệ: trạng thái, workflow, board, sprint và thời gian đều nhất quán.
      </div>
    );
  }
  return (
    <div style={{ marginBottom: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
      {errors.length > 0 && (
        <div style={{ ...banner('#FEF2F2', '#FECACA', '#B91C1C'), alignItems: 'flex-start' }}>
          <XCircle size={15} style={{ marginTop: 1, flexShrink: 0 }} />
          <div>
            <strong>Mẫu chưa thể sử dụng ({errors.length} lỗi):</strong>
            <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
              {errors.map((e, i) => (
                <li key={i}>{e.message}</li>
              ))}
            </ul>
          </div>
        </div>
      )}
      {warnings.length > 0 && (
        <div style={{ ...banner('#FFFBEB', '#FDE68A', '#92400E'), flexDirection: 'column', alignItems: 'stretch' }}>
          <button
            type="button"
            onClick={() => setOpen(!open)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              background: 'none',
              border: 'none',
              color: 'inherit',
              padding: 0,
              cursor: 'pointer',
              fontSize: 12.5,
              fontFamily: 'inherit',
              fontWeight: 600,
            }}
          >
            <AlertTriangle size={15} /> {warnings.length} lưu ý khi dùng mẫu
            {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          </button>
          {open && (
            <ul style={{ margin: '6px 0 0', paddingLeft: 26 }}>
              {warnings.map((w, i) => (
                <li key={i}>{w.message}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

// ─── Tab: Tổng quan ─────────────────────────────────────────────────────────

function OverviewTab({ p }) {
  const s = p.stats || {};
  const cards = [
    ['Trạng thái', s.statusCount],
    ['Loại công việc', s.workItemTypeCount],
    ['Workflow', s.workflowCount],
    ['Transition', s.transitionCount],
    ['Sprint', s.sprintCount],
    ['Công việc', s.workItemCount],
    ['Epic', s.epicCount],
    ['Story points', s.totalStoryPoints ?? 0],
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 10 }}>
        {cards.map(([label, value]) => (
          <div key={label} style={card}>
            <div style={{ fontSize: 11, color: '#64748B', fontWeight: 600, textTransform: 'uppercase' }}>{label}</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: '#0F172A', marginTop: 2 }}>{value ?? 0}</div>
          </div>
        ))}
      </div>

      <Section title="Dòng thời gian">
        <TimelineChart p={p} />
      </Section>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
        <Section title={`Nhãn (${p.labels?.length || 0})`}>
          <ChipList items={(p.labels || []).map((l) => ({ text: l.name, color: l.colorHex }))} empty="Không có nhãn" />
        </Section>
        <Section title={`Component (${p.components?.length || 0})`}>
          <ChipList items={(p.components || []).map((c) => ({ text: c.name }))} empty="Không có component" />
        </Section>
        <Section title={`Version (${p.versions?.length || 0})`}>
          <ChipList
            items={(p.versions || []).map((v) => ({
              text: v.releaseOffsetDays != null ? `${v.name} · ${formatOffset(v.releaseOffsetDays)}` : v.name,
            }))}
            empty="Không có version"
          />
        </Section>
        <Section title={`Trường tùy chỉnh (${p.customFields?.length || 0})`}>
          <ChipList
            items={(p.customFields || []).map((f) => ({ text: `${f.name} (${f.fieldType})` }))}
            empty="Không có trường tùy chỉnh"
          />
        </Section>
      </div>
    </div>
  );
}

/** Gantt thu nhỏ: mỗi sprint là một thanh theo offset ngày so với mốc gốc của mẫu. */
function TimelineChart({ p }) {
  const span = p.timeline?.spanDays;
  const sprints = (p.sprints || []).filter((s) => s.startOffsetDays != null && s.durationDays != null);
  if (!span) {
    return <div style={{ fontSize: 12.5, color: '#64748B' }}>Mẫu không có mốc thời gian - dự án mới sẽ dùng ngày bạn nhập.</div>;
  }
  return (
    <div>
      <div style={{ fontSize: 12.5, color: '#475569', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
        <CalendarRange size={14} /> Tổng thời lượng <strong>{span} ngày</strong>. Khi tạo dự án, mọi mốc được dời theo
        ngày bắt đầu bạn chọn và giữ nguyên khoảng cách tương đối.
      </div>
      {sprints.length === 0 ? (
        <div style={{ fontSize: 12.5, color: '#94A3B8' }}>Mẫu không có sprint có ngày.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {sprints.map((s) => (
            <div key={s.id} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <div style={{ width: 110, fontSize: 12, color: '#334155', fontWeight: 600, flexShrink: 0 }}>{s.name}</div>
              <div style={{ position: 'relative', flex: 1, height: 18, backgroundColor: '#F1F5F9', borderRadius: 4 }}>
                <div
                  style={{
                    position: 'absolute',
                    left: `${(s.startOffsetDays / span) * 100}%`,
                    width: `${Math.max((s.durationDays / span) * 100, 2)}%`,
                    top: 0,
                    bottom: 0,
                    backgroundColor: '#60A5FA',
                    borderRadius: 4,
                  }}
                  title={`${formatOffset(s.startOffsetDays)} → ${formatOffset(s.startOffsetDays + s.durationDays)}`}
                />
              </div>
              <div style={{ width: 90, fontSize: 11, color: '#64748B', flexShrink: 0 }}>
                {formatOffset(s.startOffsetDays)} · {s.durationDays}d
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Tab: Trạng thái & Quy trình ────────────────────────────────────────────

function WorkflowTab({ p }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Section title={`Trạng thái (${p.statuses?.length || 0})`}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {(p.statuses || []).map((s) => (
            <StatusPill key={s.id} name={s.name} category={s.category} />
          ))}
        </div>
      </Section>

      <Section title={`Loại công việc (${p.workItemTypes?.length || 0})`}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {(p.workItemTypes || []).map((t) => (
            <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
              <span style={{ width: 10, height: 10, borderRadius: 3, backgroundColor: t.colorHex || '#94A3B8' }} />
              <span style={{ fontWeight: 600, color: '#0F172A', paddingLeft: t.hierarchyLevel === 0 ? 14 : t.hierarchyLevel < 0 ? 28 : 0 }}>
                {t.name}
              </span>
              <span style={{ fontSize: 11, color: '#94A3B8' }}>
                {t.code} · cấp {t.hierarchyLevel}
              </span>
            </div>
          ))}
        </div>
      </Section>

      {(p.workflows || []).map((w) => (
        <Section
          key={w.id}
          title={`Workflow: ${w.name}${w.isDefault ? ' (mặc định)' : ''}`}
          subtitle={w.appliesToTypes?.length ? `Áp dụng cho: ${w.appliesToTypes.join(', ')}` : 'Chưa gắn loại công việc nào'}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {(w.transitions || []).map((t, i) => {
              const from = (p.statuses || []).find((s) => s.name === t.fromStatus);
              const to = (p.statuses || []).find((s) => s.name === t.toStatus);
              return (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12.5 }}>
                  {t.fromStatus ? (
                    <StatusPill name={t.fromStatus} category={from?.category} />
                  ) : (
                    <span style={{ fontSize: 11, color: '#64748B', fontStyle: 'italic' }}>(Tạo mới)</span>
                  )}
                  <ArrowRight size={13} color="#94A3B8" />
                  <StatusPill name={t.toStatus} category={to?.category} />
                  <span style={{ color: '#64748B' }}>“{t.name}”</span>
                </div>
              );
            })}
          </div>
        </Section>
      ))}
    </div>
  );
}

// ─── Tab: Board ─────────────────────────────────────────────────────────────

function BoardTab({ p }) {
  if (!p.boards?.length) return <div style={{ fontSize: 13, color: '#64748B' }}>Mẫu không có board.</div>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {p.boards.map((b, bi) => (
        <Section key={bi} title={`${b.name} · ${b.type}`}>
          <div style={{ display: 'flex', gap: 10, overflowX: 'auto', paddingBottom: 4 }}>
            {(b.columns || []).map((c, ci) => (
              <div
                key={ci}
                style={{
                  minWidth: 160,
                  flex: '0 0 160px',
                  backgroundColor: '#F1F5F9',
                  borderRadius: 8,
                  padding: 10,
                }}
              >
                <div style={{ fontSize: 11, fontWeight: 700, color: '#475569', letterSpacing: '0.04em' }}>{c.name}</div>
                {(c.minWipLimit != null || c.maxWipLimit != null) && (
                  <div style={{ fontSize: 10.5, color: '#B45309', marginTop: 2 }}>
                    WIP {c.minWipLimit ?? 0} – {c.maxWipLimit ?? '∞'}
                  </div>
                )}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 8 }}>
                  {(c.statuses || []).map((sName) => {
                    const st = (p.statuses || []).find((s) => s.name === sName);
                    return <StatusPill key={sName} name={sName} category={st?.category} />;
                  })}
                </div>
              </div>
            ))}
          </div>
          {b.swimlanes?.length > 0 && (
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 8 }}>
              Swimlane: {b.swimlanes.map((s) => `${s.name} (${s.strategy})`).join(', ')}
            </div>
          )}
        </Section>
      ))}
    </div>
  );
}

// ─── Tab: Sprint & Công việc ────────────────────────────────────────────────

function PlanTab({ p }) {
  const s = p.stats || {};
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Section title={`Sprint (${p.sprints?.length || 0})`} subtitle={`Backlog: ${s.backlogItemCount || 0} công việc chưa xếp sprint`}>
        {p.sprints?.length ? (
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12.5 }}>
            <thead>
              <tr style={{ color: '#64748B', textAlign: 'left' }}>
                <th style={th}>Sprint</th>
                <th style={th}>Mục tiêu</th>
                <th style={th}>Bắt đầu</th>
                <th style={th}>Độ dài</th>
                <th style={th}>Công việc</th>
                <th style={th}>Points</th>
              </tr>
            </thead>
            <tbody>
              {p.sprints.map((sp) => (
                <tr key={sp.id} style={{ borderTop: '1px solid #E2E8F0' }}>
                  <td style={{ ...td, fontWeight: 600 }}>{sp.name}</td>
                  <td style={{ ...td, color: '#64748B' }}>{sp.goal || '—'}</td>
                  <td style={td}>{formatOffset(sp.startOffsetDays)}</td>
                  <td style={td}>{sp.durationDays != null ? `${sp.durationDays} ngày` : '—'}</td>
                  <td style={td}>{sp.itemCount}</td>
                  <td style={td}>{sp.storyPoints ?? 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div style={{ fontSize: 12.5, color: '#94A3B8' }}>Mẫu không có sprint.</div>
        )}
      </Section>

      <Section
        title={`Cây công việc (${s.workItemCount || 0})`}
        subtitle={p.workItemsTruncated ? 'Chỉ hiển thị 500 công việc đầu tiên' : 'Epic → Story/Task/Bug → Subtask'}
      >
        {p.workItems?.length ? (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {p.workItems.map((n) => (
              <TreeRow key={n.id} node={n} depth={0} />
            ))}
          </div>
        ) : (
          <div style={{ fontSize: 12.5, color: '#94A3B8' }}>Mẫu không có công việc.</div>
        )}
      </Section>
    </div>
  );
}

function TreeRow({ node, depth }) {
  const [open, setOpen] = useState(depth === 0);
  const hasChildren = node.children?.length > 0;
  const pr = PRIORITY_TOKENS[node.priority];
  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '6px 4px',
          paddingLeft: 4 + depth * 22,
          borderBottom: '1px solid #F1F5F9',
          fontSize: 12.5,
        }}
      >
        <button
          type="button"
          onClick={() => setOpen(!open)}
          style={{ ...iconBtn, padding: 0, visibility: hasChildren ? 'visible' : 'hidden' }}
        >
          {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        </button>
        <span style={{ width: 9, height: 9, borderRadius: 2, backgroundColor: node.typeColor || '#94A3B8', flexShrink: 0 }} />
        <span style={{ fontSize: 11, color: '#64748B', width: 70, flexShrink: 0 }}>{node.typeName}</span>
        <span style={{ color: '#0F172A', flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {node.summary}
        </span>
        {node.sprintName && <span style={badge('#1D4ED8', '#EFF6FF')}>{node.sprintName}</span>}
        {pr && <span style={{ fontSize: 11, color: pr.color, fontWeight: 600 }}>{pr.label}</span>}
        {node.storyPoints != null && (
          <span style={{ fontSize: 11, backgroundColor: '#F1F5F9', borderRadius: 999, padding: '0 7px' }}>{node.storyPoints}</span>
        )}
      </div>
      {open && hasChildren && node.children.map((c) => <TreeRow key={c.id} node={c} depth={depth + 1} />)}
    </>
  );
}

// ─── Tab: Phân quyền ────────────────────────────────────────────────────────

function RolesTab({ p }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ fontSize: 12.5, color: '#64748B' }}>
        Ma trận quyền được sao chép nguyên vẹn. Người tạo dự án và Project Lead trở thành ADMINISTRATOR; thành viên
        nhóm được đồng bộ theo vai trò trong nhóm.
      </div>
      {(p.roles || []).map((r) => (
        <Section key={r.name} title={r.name} subtitle={r.description}>
          <ChipList items={(r.permissions || []).map((k) => ({ text: k }))} empty="Không có quyền" />
        </Section>
      ))}
    </div>
  );
}

// ─── Primitive ──────────────────────────────────────────────────────────────

function Section({ title, subtitle, children }) {
  return (
    <div style={card}>
      <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>{title}</div>
      {subtitle && <div style={{ fontSize: 12, color: '#64748B', marginTop: 2 }}>{subtitle}</div>}
      <div style={{ marginTop: 10 }}>{children}</div>
    </div>
  );
}

function StatusPill({ name, category }) {
  const tk = STATUS_TOKENS[category] || STATUS_TOKENS.TODO;
  return (
    <span
      style={{
        fontSize: 11,
        fontWeight: 700,
        padding: '2px 8px',
        borderRadius: 4,
        color: tk.color,
        backgroundColor: tk.bg,
        textTransform: 'uppercase',
        whiteSpace: 'nowrap',
      }}
    >
      {name}
    </span>
  );
}

function ChipList({ items, empty }) {
  if (!items.length) return <div style={{ fontSize: 12.5, color: '#94A3B8' }}>{empty}</div>;
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
      {items.map((it, i) => (
        <span
          key={i}
          style={{
            fontSize: 11.5,
            padding: '2px 8px',
            borderRadius: 4,
            backgroundColor: '#F1F5F9',
            color: '#334155',
            borderLeft: it.color ? `3px solid ${it.color}` : undefined,
          }}
        >
          {it.text}
        </span>
      ))}
    </div>
  );
}

const card = {
  backgroundColor: '#FFFFFF',
  border: '1px solid #E2E8F0',
  borderRadius: 10,
  padding: 14,
};

const th = { padding: '6px 8px', fontWeight: 600, fontSize: 11.5 };
const td = { padding: '7px 8px', color: '#0F172A' };

const iconBtn = {
  background: 'none',
  border: 'none',
  color: '#94A3B8',
  cursor: 'pointer',
  padding: 6,
  borderRadius: 6,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

const secondaryBtn = {
  padding: '8px 16px',
  borderRadius: 8,
  border: '1px solid #CBD5E1',
  backgroundColor: '#FFFFFF',
  color: '#475569',
  fontSize: 13,
  fontWeight: 600,
  cursor: 'pointer',
  fontFamily: 'inherit',
};

const primaryBtn = {
  padding: '8px 18px',
  borderRadius: 8,
  border: 'none',
  color: '#FFFFFF',
  fontSize: 13,
  fontWeight: 600,
  display: 'flex',
  alignItems: 'center',
  gap: 6,
  fontFamily: 'inherit',
};

function badge(color, bg) {
  return {
    fontSize: 10.5,
    fontWeight: 700,
    padding: '2px 7px',
    borderRadius: 4,
    color,
    backgroundColor: bg,
    whiteSpace: 'nowrap',
  };
}

function banner(bg, border, color) {
  return {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '10px 12px',
    borderRadius: 8,
    backgroundColor: bg,
    border: `1px solid ${border}`,
    color,
    fontSize: 12.5,
  };
}
