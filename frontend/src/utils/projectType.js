/**
 * projectType.js - Gom mot cho khac biet giua enum backlog va cac chuoi cu tren FE.
 *
 * Backend dung enum ProjectType = SOFTWARE_SCRUM | SOFTWARE_KANBAN | BUSINESS.
 * (workConstants.PROJECT_TYPES tung khai bao SCRUM/KANBAN - sai va khong ai doc.)
 */
export const PROJECT_TYPE = {
  SOFTWARE_SCRUM: 'SOFTWARE_SCRUM',
  SOFTWARE_KANBAN: 'SOFTWARE_KANBAN',
  BUSINESS: 'BUSINESS',
};

export const BOARD_TYPE = {
  SCRUM: 'SCRUM',
  KANBAN: 'KANBAN',
};

/** Du an chay Scrum thi moi co Sprint va man hinh Backlog. */
export function isScrumProject(project) {
  const t = project?.projectType;
  return t === PROJECT_TYPE.SOFTWARE_SCRUM || t === 'SCRUM';
}

export function isKanbanProject(project) {
  const t = project?.projectType;
  return t === PROJECT_TYPE.SOFTWARE_KANBAN || t === 'KANBAN';
}

/** Kieu bang uu tien lay tu chinh Board; neu thieu thi suy ra tu loai du an. */
export function boardTypeOf(project, board) {
  if (board?.type) return board.type;
  return isKanbanProject(project) ? BOARD_TYPE.KANBAN : BOARD_TYPE.SCRUM;
}
