import { createContext, useContext } from 'react';

/**
 * ProjectContext - state dung chung cua mot du an cho toan bo cac tab con.
 *
 * Dung Context thay vi <Outlet context> vi cay component kha sau: the tren Board
 * (InteractiveBoard > BoardSwimlane > BoardColumn > BoardCard) can goi openItemDetail,
 * ma useOutletContext() chi toi duoc component route truc tiep.
 *
 * ProjectDetailPage dong vai tro layout va cung cap gia tri nay.
 */
const ProjectContext = createContext(null);

export function ProjectProvider({ value, children }) {
  return <ProjectContext.Provider value={value}>{children}</ProjectContext.Provider>;
}

export function useProjectContext() {
  const ctx = useContext(ProjectContext);
  if (!ctx) {
    throw new Error('useProjectContext phải được dùng bên trong <ProjectProvider>');
  }
  return ctx;
}

export default ProjectContext;
