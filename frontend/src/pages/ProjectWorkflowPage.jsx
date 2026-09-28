import { useProjectContext } from '../context/ProjectContext.jsx';
import ProjectWorkflowTab from '../components/project/ProjectWorkflowTab.jsx';

/**
 * Vo route cho tab "Workflow & Statuses".
 * Giu nguyen ProjectWorkflowTab (1146 dong) khong sua.
 */
export default function ProjectWorkflowPage() {
  const { projectId, isAdmin, reloadProject } = useProjectContext();
  return (
    <div style={{ padding: 24, overflowY: 'auto', flex: 1 }}>
      {/* Sua loai cong viec -> nap lai du an de bo loc tren Board cap nhat theo */}
      <ProjectWorkflowTab
        projectId={projectId}
        isAdmin={isAdmin}
        onTypesUpdated={() => reloadProject?.(true)}
      />
    </div>
  );
}
