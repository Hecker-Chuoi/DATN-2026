import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  FolderKanban,
  Kanban,
  ListTodo,
  Layers,
  Sparkles,
  Users,
  ShieldCheck,
  Settings,
  GitBranch
} from 'lucide-react';
import { projectApi } from '../api.js';
import { useProjectPermission } from '../hooks/useProjectPermission.js';
import ProjectMembersTab from '../components/project/ProjectMembersTab.jsx';
import ProjectWorkflowTab from '../components/project/ProjectWorkflowTab.jsx';

export default function ProjectDetailPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('board'); // 'board' | 'backlog' | 'roadmap' | 'members'

  const { isAdmin, canCreateIssue, canManageSprint, permissions } = useProjectPermission(projectId);

  const fetchProject = async () => {
    try {
      setLoading(true);
      const res = await projectApi.getById(projectId);
      if (res && res.data) {
        setProject(res.data);
      }
    } catch (err) {
      console.warn('Could not load project detail:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (projectId) {
      fetchProject();
    }
  }, [projectId]);

  return (
    <div
      style={{
        backgroundColor: '#FFFFFF',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        overflow: 'hidden',
      }}
    >
      {/* ─── Header ─── */}
      <div
        style={{
          height: 48,
          minHeight: 48,
          padding: '0 24px',
          borderBottom: '1px solid #E2E8F0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#FFFFFF',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14, fontWeight: 600, color: '#0F172A' }}>
          <FolderKanban size={16} color="#2563EB" />
          <span>{project?.name || 'Project Details'}</span>
          {project?.key && (
            <span
              style={{
                fontSize: 11,
                fontWeight: 700,
                color: '#2563EB',
                backgroundColor: '#EFF6FF',
                padding: '2px 6px',
                borderRadius: 4,
                border: '1px solid #BFDBFE',
              }}
            >
              {project.key}
            </span>
          )}
          {isAdmin && (
            <span
              style={{
                fontSize: 10,
                fontWeight: 700,
                color: '#7E22CE',
                backgroundColor: '#FAF5FF',
                padding: '2px 6px',
                borderRadius: 10,
                border: '1px solid #E9D5FF',
                display: 'flex',
                alignItems: 'center',
                gap: 3,
              }}
            >
              <ShieldCheck size={11} />
              ADMIN
            </span>
          )}
        </div>

        {/* Sub-tabs switch */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <button
            onClick={() => setActiveTab('board')}
            style={{
              padding: '6px 12px',
              borderRadius: 6,
              border: 'none',
              backgroundColor: activeTab === 'board' ? '#EFF6FF' : 'transparent',
              color: activeTab === 'board' ? '#2563EB' : '#64748B',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <Kanban size={14} />
            <span>Active Board</span>
          </button>
          <button
            onClick={() => setActiveTab('backlog')}
            style={{
              padding: '6px 12px',
              borderRadius: 6,
              border: 'none',
              backgroundColor: activeTab === 'backlog' ? '#EFF6FF' : 'transparent',
              color: activeTab === 'backlog' ? '#2563EB' : '#64748B',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <ListTodo size={14} />
            <span>Backlog</span>
          </button>
          <button
            onClick={() => setActiveTab('roadmap')}
            style={{
              padding: '6px 12px',
              borderRadius: 6,
              border: 'none',
              backgroundColor: activeTab === 'roadmap' ? '#EFF6FF' : 'transparent',
              color: activeTab === 'roadmap' ? '#2563EB' : '#64748B',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <Layers size={14} />
            <span>Roadmap</span>
          </button>
          <button
            onClick={() => setActiveTab('workflow')}
            style={{
              padding: '6px 12px',
              borderRadius: 6,
              border: 'none',
              backgroundColor: activeTab === 'workflow' ? '#EFF6FF' : 'transparent',
              color: activeTab === 'workflow' ? '#2563EB' : '#64748B',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <GitBranch size={14} />
            <span>Workflow & Statuses</span>
          </button>
          <button
            onClick={() => setActiveTab('members')}
            style={{
              padding: '6px 12px',
              borderRadius: 6,
              border: 'none',
              backgroundColor: activeTab === 'members' ? '#EFF6FF' : 'transparent',
              color: activeTab === 'members' ? '#2563EB' : '#64748B',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
            }}
          >
            <Users size={14} />
            <span>Members & Roles</span>
          </button>
        </div>
      </div>

      {/* ─── Body Content ─── */}
      <div style={{ flex: 1, overflowY: 'auto', backgroundColor: '#F8FAFC' }}>
        {activeTab === 'members' ? (
          <ProjectMembersTab
            projectId={projectId}
            project={project}
            isAdmin={isAdmin}
            onMembersUpdated={fetchProject}
          />
        ) : activeTab === 'workflow' ? (
          <ProjectWorkflowTab
            projectId={projectId}
            project={project}
            isAdmin={isAdmin}
          />
        ) : (
          <div
            style={{
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 40,
              textAlign: 'center',
            }}
          >
            <div style={{ maxWidth: 460 }}>
              <Sparkles size={36} color="#2563EB" style={{ marginBottom: 12 }} />
              <h3 style={{ fontSize: 16, fontWeight: 700, color: '#0F172A', marginBottom: 8 }}>
                Jira-Grade Work View ({activeTab.toUpperCase()})
              </h3>
              <p style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>
                Khu vực hiển thị Scrum/Kanban Board, Backlog và Sprints sẽ được triển khai kết nối theo các bước trong Kế hoạch Triển khai (Giai đoạn 4 & 6).
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
