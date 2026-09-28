import { useProjectContext } from '../context/ProjectContext.jsx';
import ProjectMembersTab from '../components/project/ProjectMembersTab.jsx';

/**
 * Vo route cho tab "Members & Roles".
 * Giu nguyen ProjectMembersTab (811 dong) khong sua, chi cap lai props va padding
 * ma truoc day ProjectDetailPage bao quanh.
 */
export default function ProjectMembersPage() {
  const { projectId, isAdmin } = useProjectContext();
  return (
    <div style={{ padding: 24, overflowY: 'auto', flex: 1 }}>
      <ProjectMembersTab projectId={projectId} isAdmin={isAdmin} />
    </div>
  );
}
