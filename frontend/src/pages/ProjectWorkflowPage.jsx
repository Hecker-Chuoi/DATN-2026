import { useProjectContext } from '../context/ProjectContext.jsx';
import ProjectWorkflowTab from '../components/project/ProjectWorkflowTab.jsx';

/**
 * Vo route cho tab "Workflow & Statuses".
 * Giu nguyen ProjectWorkflowTab (1146 dong) khong sua.
 */
export default function ProjectWorkflowPage() {
  const { projectId, isAdmin } = useProjectContext();
  return (
    <div style={{ padding: 24, overflowY: 'auto', flex: 1 }}>
      <ProjectWorkflowTab projectId={projectId} isAdmin={isAdmin} />
    </div>
  );
}
