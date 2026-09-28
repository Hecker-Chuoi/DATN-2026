/**
 * workServiceApi.js - Lop tuong thich (shim).
 *
 * Truoc day file nay khai bao mot bo client rieng cho work-service, nhung cac duong dan
 * cua no LECH voi backend that (`/items` thay vi `/projects/{id}/items`,
 * `/items/{id}/transitions` thay vi `/work-items/{id}/transitions`) va khong mot man hinh
 * nao import no. De tranh bay cho nguoi doc sau, toan bo ten cu duoc tro thang ve
 * `src/api.js` - nguon su that duy nhat.
 */
export {
  projectApi as workProjectApi,
  teamApi as workTeamApi,
  workItemApi,
  workflowApi,
  boardApi as workBoardApi,
  sprintApi as workSprintApi,
  componentApi as workComponentApi,
  versionApi as workVersionApi,
  jqlApi as workJqlApi,
} from '../api.js';
