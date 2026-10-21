import { useEffect, useState } from 'react';
import { X, BookmarkPlus, AlertTriangle, AlertCircle } from 'lucide-react';
import toast from 'react-hot-toast';
import { templateApi } from '../../api.js';
import { generateProjectKey, sanitizeProjectKey, validateProjectForm } from '../../utils/projectValidation.js';
import { TEMPLATE_CATEGORIES } from '../../constants/templateConstants.js';
import { useModalBackdrop } from '../../hooks/useModalBackdrop.js';

/**
 * Lưu dự án hiện tại thành một dự án mẫu mới (dự án nguồn giữ nguyên).
 * Mẫu giữ nguyên mốc thời gian của nguồn; sprint về FUTURE, công việc về trạng thái khởi tạo.
 */
export default function SaveAsTemplateModal({ isOpen, onClose, project, onSaved }) {
  const backdropProps = useModalBackdrop(onClose);
  const [form, setForm] = useState({ name: '', key: '', description: '', templateCategory: 'SOFTWARE' });
  const [options, setOptions] = useState({ includeSprints: true, includeWorkItems: true, includeVersions: true });
  const [warnings, setWarnings] = useState([]);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!isOpen || !project) return;
    const name = `Mẫu ${project.name}`;
    setForm({
      name,
      key: sanitizeProjectKey(`T${generateProjectKey(project.name)}`),
      description: project.description || '',
      templateCategory: 'SOFTWARE',
    });
    setOptions({ includeSprints: true, includeWorkItems: true, includeVersions: true });
    setErrors({});
    setWarnings([]);
    templateApi
      .checkIntegrity(project.id)
      .then((res) => setWarnings([...(res?.data?.errors || []), ...(res?.data?.warnings || [])]))
      .catch(() => setWarnings([]));
  }, [isOpen, project]);

  if (!isOpen || !project) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    const validation = validateProjectForm({ name: form.name, key: form.key });
    if (!validation.isValid) {
      setErrors(validation.errors);
      return;
    }
    try {
      setSubmitting(true);
      const res = await templateApi.saveAsTemplate(project.id, {
        name: form.name.trim(),
        key: form.key.trim().toUpperCase(),
        description: form.description?.trim() || undefined,
        templateCategory: form.templateCategory,
        ...options,
      });
      toast.success(`Đã lưu mẫu ${res?.data?.project?.name || form.name}`);
      onClose();
      if (onSaved) onSaved(res?.data?.project);
    } catch (err) {
      console.error('Failed to save as template:', err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: 16,
      }}
      {...backdropProps}
    >
      <form
        onSubmit={handleSubmit}
        onClick={(e) => e.stopPropagation()}
        style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 12,
          width: '100%',
          maxWidth: 520,
          maxHeight: '90vh',
          overflowY: 'auto',
          boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)',
        }}
      >
        <div style={{ padding: '18px 22px', borderBottom: '1px solid #E2E8F0', display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            style={{
              width: 34,
              height: 34,
              borderRadius: 8,
              backgroundColor: '#FFFBEB',
              color: '#D97706',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <BookmarkPlus size={18} />
          </div>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>Lưu thành dự án mẫu</h2>
            <p style={{ margin: '2px 0 0', fontSize: 12, color: '#64748B' }}>Từ dự án {project.name} ({project.key})</p>
          </div>
          <button type="button" onClick={onClose} style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer' }}>
            <X size={18} />
          </button>
        </div>

        <div style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="Tên mẫu" error={errors.name}>
            <input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              style={input(errors.name)}
            />
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Mã mẫu" error={errors.key}>
              <input
                value={form.key}
                maxLength={10}
                onChange={(e) => setForm({ ...form, key: sanitizeProjectKey(e.target.value) })}
                style={{ ...input(errors.key), fontWeight: 700, color: '#2563EB' }}
              />
            </Field>
            <Field label="Phân loại">
              <select
                value={form.templateCategory}
                onChange={(e) => setForm({ ...form, templateCategory: e.target.value })}
                style={input()}
              >
                {Object.entries(TEMPLATE_CATEGORIES).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v.label}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <Field label="Mô tả">
            <textarea
              rows={3}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              style={{ ...input(), resize: 'vertical', fontFamily: 'inherit' }}
            />
          </Field>

          <div style={{ padding: '10px 12px', backgroundColor: '#F8FAFC', borderRadius: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div style={{ fontSize: 12, color: '#64748B' }}>
              Cấu hình (trạng thái, quy trình làm việc, bảng làm việc, loại công việc, phân quyền) luôn được sao chép. Thành viên, bình
              luận, tệp đính kèm không được sao chép.
            </div>
            {[
              ['includeSprints', 'Chu kỳ làm việc (đặt lại thành Dự kiến)'],
              ['includeWorkItems', 'Công việc (về trạng thái khởi tạo, bỏ người được giao)'],
              ['includeVersions', 'Phiên bản'],
            ].map(([k, label]) => (
              <label key={k} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#334155', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={options[k]}
                  onChange={(e) => setOptions({ ...options, [k]: e.target.checked })}
                  style={{ accentColor: '#2563EB' }}
                />
                {label}
              </label>
            ))}
          </div>

          {warnings.length > 0 && (
            <div
              style={{
                padding: '10px 12px',
                borderRadius: 8,
                backgroundColor: '#FFFBEB',
                border: '1px solid #FDE68A',
                color: '#92400E',
                fontSize: 12,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600 }}>
                <AlertTriangle size={14} /> Lưu ý về cấu hình hiện tại
              </div>
              <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
                {warnings.slice(0, 6).map((w, i) => (
                  <li key={i}>{w.message}</li>
                ))}
                {warnings.length > 6 && <li>… và {warnings.length - 6} lưu ý khác</li>}
              </ul>
            </div>
          )}
        </div>

        <div style={{ padding: '14px 22px', borderTop: '1px solid #E2E8F0', display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            style={{
              padding: '8px 16px',
              borderRadius: 8,
              border: '1px solid #CBD5E1',
              backgroundColor: '#FFFFFF',
              color: '#475569',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Hủy
          </button>
          <button
            type="submit"
            disabled={submitting}
            style={{
              padding: '8px 18px',
              borderRadius: 8,
              border: 'none',
              backgroundColor: submitting ? '#93C5FD' : '#2563EB',
              color: '#FFFFFF',
              fontSize: 13,
              fontWeight: 600,
              cursor: submitting ? 'not-allowed' : 'pointer',
            }}
          >
            {submitting ? 'Đang lưu mẫu...' : 'Lưu thành mẫu'}
          </button>
        </div>
      </form>
    </div>
  );
}

function Field({ label, error, children }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#334155', marginBottom: 6 }}>{label}</label>
      {children}
      {error && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, color: '#EF4444', fontSize: 12, marginTop: 4 }}>
          <AlertCircle size={13} /> {error}
        </div>
      )}
    </div>
  );
}

function input(error) {
  return {
    width: '100%',
    padding: '8px 12px',
    borderRadius: 8,
    border: `1px solid ${error ? '#EF4444' : '#CBD5E1'}`,
    fontSize: 13,
    color: '#0F172A',
    outline: 'none',
    boxSizing: 'border-box',
    backgroundColor: '#FFFFFF',
  };
}
