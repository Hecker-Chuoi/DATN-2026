import React, { useState, useEffect, useRef } from 'react';
import toast from 'react-hot-toast';
import { X, Sparkles, FolderKanban, Copy, Shield, Calendar, RefreshCw, Key, AlertCircle } from 'lucide-react';
import { projectApi } from '../../api.js';

/**
 * Tự động tạo Key từ các chữ cái đầu tiên của từng từ trong tên dự án (tối đa 4 từ đầu).
 * Ví dụ: "Dự Án Tốt Nghiệp 2026" -> "DATN"
 *        "Hệ thống Quản lý Bán hàng" -> "HTQL"
 *        "E-Commerce Payment" -> "ECP"
 */
export const generateProjectKey = (projectName) => {
  if (!projectName || !projectName.trim()) return '';

  // 1. Chuẩn hóa tiếng Việt (bỏ dấu)
  const normalized = projectName
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D');

  // 2. Tách từ theo khoảng trắng hoặc ký tự đặc biệt
  const words = normalized
    .trim()
    .split(/[\s\-_.,;:/\\]+/)
    .filter(Boolean);

  if (words.length === 0) return '';

  // 3. Lấy chữ cái đầu tiên của tối đa 4 từ đầu
  const key = words
    .slice(0, 4)
    .map((w) => w.replace(/[^a-zA-Z0-9]/g, '').charAt(0))
    .filter(Boolean)
    .join('')
    .toUpperCase();

  return key;
};

export default function CreateProjectModal({ teams, initialTeamId, sourceProject, onClose, onSuccess }) {
  const initialName = sourceProject ? `${sourceProject.name} (Bản sao)` : '';
  const [name, setName] = useState(initialName);
  const [key, setKey] = useState(initialName ? generateProjectKey(initialName) : '');
  const [isKeyTouched, setIsKeyTouched] = useState(false);
  const [description, setDescription] = useState(sourceProject ? sourceProject.description || '' : '');
  const [teamId, setTeamId] = useState(initialTeamId || sourceProject?.teamId || teams[0]?.id || '');
  const [isTemplate, setIsTemplate] = useState(false);
  const [isPublic, setIsPublic] = useState(false);
  const [startDate, setStartDate] = useState(() => new Date().toISOString().substring(0, 10));
  const [endDate, setEndDate] = useState('');

  const [templateList, setTemplateList] = useState([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState(sourceProject?.id || '');
  const [mode, setMode] = useState(sourceProject ? 'clone' : 'blank'); // 'blank' | 'clone'

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const keyInputRef = useRef(null);

  // Load available templates if in clone mode
  useEffect(() => {
    async function loadTemplates() {
      try {
        const res = await projectApi.listTemplates();
        if (res && res.data) {
          setTemplateList(res.data);
        }
      } catch (err) {
        console.error('Failed to load templates:', err);
      }
    }
    loadTemplates();
  }, []);

  const handleNameChange = (e) => {
    const newName = e.target.value;
    setName(newName);
    // Nếu người dùng chưa can thiệp sửa key bằng tay, tự động gen key theo tên gõ vào
    if (!isKeyTouched) {
      setKey(generateProjectKey(newName));
    }
    if (errorMsg) setErrorMsg('');
  };

  const handleKeyChange = (e) => {
    // Cho phép chữ cái, số và dấu gạch dưới, tự động in hoa
    const val = e.target.value.toUpperCase().replace(/[^A-Z0-9_-]/g, '');
    setKey(val);
    setIsKeyTouched(true);
    if (errorMsg) setErrorMsg('');
  };

  const handleTemplateChange = (e) => {
    const tId = e.target.value;
    setSelectedTemplateId(tId);
    const found = templateList.find((t) => t.id === tId);
    if (found) {
      const clonedName = `${found.name} (Bản sao)`;
      setName(clonedName);
      setKey(generateProjectKey(clonedName));
      setIsKeyTouched(false);
      setDescription(found.description || '');
    }
  };

  const handleGenerateKey = () => {
    const newKey = generateProjectKey(name);
    setKey(newKey);
    setIsKeyTouched(false);
    if (errorMsg) setErrorMsg('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim() || !key.trim()) return;

    try {
      setLoading(true);
      setErrorMsg('');

      const cleanKey = key.trim().toUpperCase();

      if (mode === 'clone' && (sourceProject?.id || selectedTemplateId)) {
        const srcId = sourceProject?.id || selectedTemplateId;
        await projectApi.clone(srcId, {
          sourceProjectId: srcId,
          teamId: teamId || null,
          newKey: cleanKey,
          newName: name.trim(),
          newDescription: description.trim(),
          startDate: startDate || null,
          isPublic,
        });
        toast.success('Nhân bản dự án từ mẫu thành công!');
      } else {
        await projectApi.create({
          teamId: teamId || null,
          key: cleanKey,
          name: name.trim(),
          description: description.trim(),
          startDate: startDate || null,
          endDate: endDate || null,
          isPublic,
          isTemplate,
        });
        toast.success('Tạo dự án mới thành công!');
      }

      onSuccess();
    } catch (err) {
      const msg = err.message || err.body?.message || 'Có lỗi xảy ra khi tạo dự án. Vui lòng kiểm tra lại.';
      setErrorMsg(msg);
      // Tự động focus vào ô Key để người dùng dễ dàng đổi mã khác khi trùng
      setTimeout(() => {
        keyInputRef.current?.focus();
        keyInputRef.current?.select();
      }, 50);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.45)',
        backdropFilter: 'blur(3px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 9999,
        padding: 20,
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: '100%',
          maxWidth: 520,
          backgroundColor: '#FFFFFF',
          borderRadius: 14,
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
          border: '1px solid #E2E8F0',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div
          style={{
            padding: '16px 20px',
            borderBottom: '1px solid #F1F5F9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: 6,
                backgroundColor: mode === 'clone' ? '#F5F3FF' : '#EFF6FF',
                color: mode === 'clone' ? '#7C3AED' : '#2563EB',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {mode === 'clone' ? <Copy size={15} /> : <FolderKanban size={15} />}
            </div>
            <h2 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', margin: 0 }}>
              {sourceProject ? 'Nhân bản Dự án từ Mẫu' : 'Tạo Dự án Mới'}
            </h2>
          </div>

          <button
            onClick={onClose}
            style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer' }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Mode Selector (Blank vs Template Clone) */}
        {!sourceProject && templateList.length > 0 && (
          <div style={{ display: 'flex', borderBottom: '1px solid #F1F5F9', backgroundColor: '#F8FAFC' }}>
            <button
              type="button"
              onClick={() => setMode('blank')}
              style={{
                flex: 1,
                padding: '10px',
                border: 'none',
                backgroundColor: mode === 'blank' ? '#FFFFFF' : 'transparent',
                borderBottom: mode === 'blank' ? '2px solid #2563EB' : 'none',
                color: mode === 'blank' ? '#2563EB' : '#64748B',
                fontWeight: 600,
                fontSize: 13,
                cursor: 'pointer',
              }}
            >
              Dự án mới trống
            </button>
            <button
              type="button"
              onClick={() => setMode('clone')}
              style={{
                flex: 1,
                padding: '10px',
                border: 'none',
                backgroundColor: mode === 'clone' ? '#FFFFFF' : 'transparent',
                borderBottom: mode === 'clone' ? '2px solid #8B5CF6' : 'none',
                color: mode === 'clone' ? '#8B5CF6' : '#64748B',
                fontWeight: 600,
                fontSize: 13,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 4,
              }}
            >
              <Sparkles size={13} />
              <span>Clone từ Template ({templateList.length})</span>
            </button>
          </div>
        )}

        {/* Modal Form */}
        <form onSubmit={handleSubmit} style={{ padding: '20px' }}>
          {errorMsg && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '10px 14px',
                borderRadius: 8,
                backgroundColor: '#FEF2F2',
                border: '1px solid #FCA5A5',
                color: '#DC2626',
                fontSize: 13,
                marginBottom: 14,
              }}
            >
              <AlertCircle size={16} style={{ flexShrink: 0 }} />
              <span>{errorMsg}</span>
            </div>
          )}

          {/* If Clone mode: Template selector dropdown */}
          {mode === 'clone' && !sourceProject && (
            <div style={{ marginBottom: 14 }}>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                Chọn Template mẫu muốn sao chép:
              </label>
              <select
                value={selectedTemplateId}
                onChange={handleTemplateChange}
                required
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  backgroundColor: '#FFFFFF',
                  outline: 'none',
                }}
              >
                <option value="">-- Chọn một template mẫu --</option>
                {templateList.map((t, idx) => (
                  <option key={t.id || `template-${idx}`} value={t.id}>
                    {t.name} ({t.key}) - {t.taskCount || 0} tasks mẫu
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Project Name (Full Width) */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
              Tên dự án *
            </label>
            <input
              type="text"
              placeholder="VD: Cổng thanh toán E-Commerce"
              value={name}
              onChange={handleNameChange}
              required
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: 6,
                border: '1px solid #CBD5E1',
                fontSize: 13,
                outline: 'none',
              }}
            />
          </div>

          {/* Project Key (Full Width Row with Generate Button) */}
          <div style={{ marginBottom: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
              <label style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>
                Mã dự án (Key) *
              </label>
              <span style={{ fontSize: 11, color: '#64748B' }}>
                Tự động tạo: Mặc định lấy từ 4 chữ cái đầu của tên dự án
              </span>
            </div>

            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input
                ref={keyInputRef}
                type="text"
                placeholder="VD: CTTE, DATN, CRM..."
                value={key}
                onChange={handleKeyChange}
                required
                maxLength={20}
                style={{
                  flex: 1,
                  padding: '8px 10px',
                  borderRadius: 6,
                  border: errorMsg ? '1px solid #EF4444' : '1px solid #CBD5E1',
                  fontSize: 13,
                  fontFamily: 'monospace',
                  fontWeight: 700,
                  color: '#1E293B',
                  backgroundColor: '#F8FAFC',
                  outline: 'none',
                  letterSpacing: '0.05em',
                }}
              />
              <button
                type="button"
                onClick={handleGenerateKey}
                title="Tạo lại mã Key tự động từ tên dự án"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  padding: '8px 14px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  backgroundColor: '#FFFFFF',
                  color: '#2563EB',
                  fontSize: 12,
                  fontWeight: 600,
                  cursor: 'pointer',
                  whiteSpace: 'nowrap',
                  boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.backgroundColor = '#EFF6FF';
                  e.currentTarget.style.borderColor = '#BFDBFE';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = '#FFFFFF';
                  e.currentTarget.style.borderColor = '#CBD5E1';
                }}
              >
                <span>Tạo lại mã</span>
              </button>
            </div>
          </div>

          {/* Team Selector */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
              Nhóm phụ trách (Team)
            </label>
            <select
              value={teamId}
              onChange={(e) => setTeamId(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: 6,
                border: '1px solid #CBD5E1',
                fontSize: 13,
                backgroundColor: '#FFFFFF',
                outline: 'none',
              }}
            >
              <option value="">-- Dự án chung (Không gán nhóm) --</option>
              {teams && teams.map((t, idx) => (
                <option key={t.id || `team-opt-${idx}`} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>

          {/* Description */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
              Mô tả dự án
            </label>
            <textarea
              rows={3}
              placeholder="Mô tả mục tiêu, lộ trình và yêu cầu của dự án..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: 6,
                border: '1px solid #CBD5E1',
                fontSize: 13,
                outline: 'none',
                resize: 'vertical',
              }}
            />
          </div>

          {/* Date Picker Section */}
          {mode === 'clone' ? (
            <div
              style={{
                marginBottom: 16,
                padding: '12px 14px',
                borderRadius: 8,
                backgroundColor: '#F8FAFC',
                border: '1px solid #E2E8F0',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 700, color: '#1E293B' }}>
                  <Calendar size={14} color="#7C3AED" />
                  <span>Ngày bắt đầu dự án mới *</span>
                </label>
                <span style={{ fontSize: 11, color: '#7C3AED', fontWeight: 600 }}>Tự động tịnh tiến timeline</span>
              </div>

              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                required
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  backgroundColor: '#FFFFFF',
                  outline: 'none',
                  marginBottom: 8,
                }}
              />

              <div style={{ fontSize: 11, color: '#64748B', lineHeight: 1.4 }}>
                💡 <strong>Cơ chế dịch chuyển ngày thông minh:</strong> Toàn bộ Cột mốc (Milestones), Công việc chính (Tasks) và Công việc con (Subtasks) của mẫu sẽ tự động cộng thêm số ngày tương ứng từ mốc bắt đầu này, bảo toàn tuyệt đối thời lượng thực hiện.
              </div>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 14 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                  Ngày bắt đầu
                </label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 10px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                  }}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                  Ngày dự kiến kết thúc
                </label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 10px',
                    borderRadius: 6,
                    border: '1px solid #CBD5E1',
                    fontSize: 13,
                    outline: 'none',
                  }}
                />
              </div>
            </div>
          )}

          {/* Options Toggles */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 20 }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#334155', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={isPublic}
                onChange={(e) => setIsPublic(e.target.checked)}
              />
              <span>Công khai dự án cho toàn bộ thành viên trong Workspace</span>
            </label>
          </div>

          {/* Action Buttons */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, borderTop: '1px solid #F1F5F9', paddingTop: 14 }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '8px 16px',
                borderRadius: 6,
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
              disabled={loading}
              style={{
                padding: '8px 18px',
                borderRadius: 6,
                border: 'none',
                backgroundColor: mode === 'clone' ? '#7C3AED' : '#2563EB',
                color: '#FFFFFF',
                fontSize: 13,
                fontWeight: 600,
                cursor: loading ? 'not-allowed' : 'pointer',
                boxShadow: '0 2px 4px rgba(37,99,235,0.2)',
              }}
            >
              {loading ? 'Đang xử lý...' : mode === 'clone' ? 'Nhân bản ngay' : 'Tạo dự án'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
