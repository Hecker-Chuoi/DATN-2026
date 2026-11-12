import { useEffect, useState } from 'react';
import { useProjectContext } from '../context/ProjectContext.jsx';
import { sprintApi } from '../api.js';
import ContributionReportTab from '../components/reports/ContributionReportTab.jsx';

export default function ProjectContributionsPage() {
  const { projectId, isAdmin } = useProjectContext();
  const [sprints, setSprints] = useState([]);
  const [sprintId, setSprintId] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await sprintApi.listByProject(projectId);
        const list = res?.data || [];
        if (cancelled) return;
        setSprints(list);
        const active = list.find((s) => s.state === 'ACTIVE');
        const closed = list.filter((s) => s.state === 'CLOSED');
        setSprintId(active?.id || closed[0]?.id || list[0]?.id || '');
      } catch (err) {
        console.warn('Không tải được danh sách chu kỳ:', err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [projectId]);

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '16px 24px 40px 24px', backgroundColor: '#F8FAFC' }}>
      <ContributionReportTab
        projectId={projectId}
        sprintId={sprintId}
        sprints={sprints}
        isLeader={isAdmin}
      />
    </div>
  );
}
