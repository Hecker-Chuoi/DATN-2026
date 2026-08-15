import React, { useState, useEffect } from 'react';
import { X, Sparkles, FolderKanban, Copy, Shield, Calendar } from 'lucide-react';
import { projectApi } from '../../api.js';

export default function CreateProjectModal({ teams, initialTeamId, sourceProject, onClose, onSuccess }) {
  const [name, setName] = useState(sourceProject ? `${sourceProject.name} (Bản sao)` : '');
  const [key, setKey] = useState(sourceProject ? `${sourceProject.key}2` : '');
  const [description, setDescription] = useState(sourceProject ? sourceProject.description || '' : '');
  const [teamId, setTeamId] = useState(initialTeamId || sourceProject?.teamId || teams[0]?.id || '');
  const [isTemplate, setIsTemplate] = useState(false);
  const [isPublic, setIsPublic] = useState(false);
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const [templateList, setTemplateList] = useState([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState(sourceProject?.id || '');
  const [mode, setMode] = useState(sourceProject ? 'clone' : 'blank'); // 'blank' | 'clone'

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

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

  const handleTemplateChange = (e) => {
    const tId = e.target.value;
    setSelectedTemplateId(tId);
    const found = templateList.find((t) => t.id === tId);
    if (found) {
      setName(`${found.name} (Bản sao)`);
      setKey(`${found.key}2`);
      setDescription(found.description || '');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim() || !key.trim()) return;

    try {
      setLoading(true);
      setErrorMsg('');

      if (mode === 'clone' && (sourceProject?.id || selectedTemplateId)) {
        const srcId = sourceProject?.id || selectedTemplateId;
        await projectApi.clone(srcId, {
          sourceProjectId: srcId,
          teamId: teamId || null,
          newKey: key.trim().toUpperCase(),
          newName: name.trim(),
          newDescription: description.trim(),
          isPublic,
        });
      } else {
        await projectApi.create({
          teamId: teamId || null,
          key: key.trim().toUpperCase(),
          name: name.trim(),
          description: description.trim(),
          startDate: startDate || null,
          endDate: endDate || null,
          isPublic,
          isTemplate,
        });
      }

      onSuccess();
    } catch (err) {
      setErrorMsg(err.message || 'Có lỗi xảy ra khi tạo dự án. Vui lòng thử lại.');
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
                padding: '8px 12px',
                borderRadius: 6,
                backgroundColor: '#FEF2F2',
                color: '#DC2626',
                fontSize: 12,
                marginBottom: 14,
              }}
            >
              {errorMsg}
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
                {templateList.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} ({t.key}) - {t.taskCount || 0} tasks mẫu
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Project Name & Key */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 120px', gap: 12, marginBottom: 14 }}>
            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                Tên dự án *
              </label>
              <input
                type="text"
                placeholder="VD: Cổng thanh toán E-Commerce"
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  if (!key && e.target.value.length >= 2) {
                    setKey(e.target.value.substring(0, 4).toUpperCase());
                  }
                }}
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

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#334155', marginBottom: 4 }}>
                Mã Key *
              </label>
              <input
                type="text"
                placeholder="PTMS"
                value={key}
                onChange={(e) => setKey(e.target.value.toUpperCase())}
                maxLength={10}
                required
                style={{
                  width: '100%',
                  padding: '8px 10px',
                  borderRadius: 6,
                  border: '1px solid #CBD5E1',
                  fontSize: 13,
                  fontWeight: 700,
                  outline: 'none',
                  textTransform: 'uppercase',
                }}
              />
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
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} ({t.key || 'No Key'})
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

          {/* Options Toggles */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 20 }}>
            {mode !== 'clone' && (
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#334155', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={isTemplate}
                  onChange={(e) => setIsTemplate(e.target.checked)}
                />
                <span>Đánh dấu dự án này là <b>Template mẫu</b> (để nhân bản sau này)</span>
              </label>
            )}

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
