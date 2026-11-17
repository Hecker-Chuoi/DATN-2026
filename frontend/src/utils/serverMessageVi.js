/**
 * serverMessageVi.js - Việt hóa câu thông báo lỗi do BE trả về (chỉ đổi phần HIỂN THỊ).
 *
 * BE (work-service) vẫn giữ nguyên câu chữ gốc; một số câu còn tiếng Anh hoặc dùng thang
 * cấp bậc nội bộ 1 / 0 / -1 (Epic / Standard / Sub-task). FE hiển thị theo thang
 * Cấp 3 / Cấp 2 / Cấp 1 (Hạng mục lớn / Tiêu chuẩn / Công việc con) và chỉ dùng tiếng Việt.
 *
 * Các quy tắc thay theo TỪNG ĐOẠN (không neo cả câu) vì lỗi validate của BE nối nhiều
 * câu bằng ", ". Câu không khớp quy tắc nào được giữ nguyên.
 * Nguồn câu gốc: StrictHierarchyValidator, WorkItemServiceImpl, WorkItemTypeServiceImpl,
 * WorkItemLinkServiceImpl, WorkflowEngineServiceImpl, Create/UpdateWorkItemTypeRequest,
 * CreateSubtaskRequest.
 */
import { toDisplayHierarchyLevel } from '../constants/workConstants.js';

const LEVEL_NAME_VI = { 1: 'Hạng mục lớn', 0: 'Tiêu chuẩn', '-1': 'Công việc con' };

/** "-1" → "Cấp 1 (Công việc con)" */
const levelVi = (beLevel) => {
  const n = Number(beLevel);
  if (![-1, 0, 1].includes(n)) return `cấp ${beLevel}`;
  return `Cấp ${toDisplayHierarchyLevel(n)} (${LEVEL_NAME_VI[n]})`;
};

const RULES = [
  // ── Phân cấp cha – con (StrictHierarchyValidator) ──
  [/Child work item hierarchy level must not be null\.?/g, 'Loại công việc chưa được xác định cấp bậc.'],
  [/Epic \(Hierarchy Level 1\) cannot have a parent item\.?/g, 'Hạng mục lớn (Cấp 3) không được có công việc cha.'],
  [
    /Standard item \(Story\/Task\/Bug\) can only have an Epic \(Level 1\) as its parent\. Found parent level: (-?\d+)/g,
    (_, lv) => `Công việc tiêu chuẩn (Cấp 2) chỉ được trực thuộc Hạng mục lớn (Cấp 3). Công việc cha đang chọn thuộc ${levelVi(lv)}.`,
  ],
  [
    /Sub-task \(-1\) must have a parent \(Story\/Task\/Bug\)\. It cannot stand alone without a parent\.?/g,
    'Công việc con (Cấp 1) bắt buộc phải có công việc cha thuộc Cấp 2, không thể đứng độc lập.',
  ],
  [
    /Sub-task \(-1\) cannot be directly attached to an Epic \(1\)\. It must belong to a Story, Task, or Bug \(0\)\.?/g,
    'Công việc con (Cấp 1) không thể trực thuộc trực tiếp Hạng mục lớn (Cấp 3). Hãy chọn công việc cha thuộc Cấp 2.',
  ],
  [
    /Sub-task \(-1\) cannot have another Sub-task as parent \(Multi-level subtask nesting is not allowed\)\.?/g,
    'Công việc con (Cấp 1) không thể có cha là một công việc con khác.',
  ],
  [
    /Sub-task \(-1\) must belong to a Level 0 item \(Story\/Task\/Bug\)\. Found parent level: (-?\d+)/g,
    (_, lv) => `Công việc con (Cấp 1) phải trực thuộc một công việc Cấp 2. Công việc cha đang chọn thuộc ${levelVi(lv)}.`,
  ],
  [/An item cannot be its own parent\.?/g, 'Công việc không thể là cha của chính nó.'],
  [/Parent item \(([^)]+)\) belongs to a different project\.?/g, 'Công việc cha ($1) thuộc dự án khác.'],
  [/Cannot link to a deleted parent item: (\S+)/g, 'Không thể gắn vào công việc cha đã bị xóa: $1'],
  [/Parent WorkItemType not found with ID: \S+/g, 'Không tìm thấy loại công việc của công việc cha.'],
  [/Parent WorkItem not found with ID: \S+/g, 'Không tìm thấy công việc cha.'],
  [/WorkItemType not found with ID: \S+/g, 'Không tìm thấy loại công việc.'],

  // ── Tạo công việc con (WorkItemServiceImpl) ──
  [/Cannot create subtask for deleted work item: (\S+)/g, 'Không thể tạo công việc con cho công việc đã bị xóa: $1'],
  [/không phải là Sub-task \(Cấp bậc Level -1\)/g, 'không phải là Công việc con (Cấp 1)'],
  [/thuộc cấp bậc Sub-task \(Level -1\)/g, 'thuộc Cấp 1 (Công việc con)'],

  // ── Loại công việc (WorkItemTypeServiceImpl) ──
  [
    /Cấp bậc \(Hierarchy Level\) phải là 1 \(Epic\), 0 \(Standard\) hoặc -1 \(Subtask\)/g,
    'Cấp bậc phải là Cấp 3 (Hạng mục lớn), Cấp 2 (Tiêu chuẩn) hoặc Cấp 1 (Công việc con)',
  ],
  [/thay đổi cấp bậc \(Level\) vì/g, 'thay đổi cấp bậc vì'],
  [/(1 loại công việc cho) Cấp bậc (-?\d+)/g, (_, head, lv) => `${head} ${levelVi(lv)}`],
  [/(1 loại công việc cho) Level (-?\d+) \((?:Epic|Standard|Subtask)\)/g, (_, head, lv) => `${head} ${levelVi(lv)}`],
  // Mã loại (EPIC, TASK...) không hiển thị trên FE
  [/Mã loại công việc '[^']*' đã tồn tại trong dự án/g, 'Loại công việc này đã tồn tại trong dự án'],

  // ── Validate DTO loại công việc (Create/UpdateWorkItemTypeRequest) ──
  [/Work item type name is required/g, 'Tên loại công việc là bắt buộc'],
  [/Work item type name cannot exceed 100 characters/g, 'Tên loại công việc không được vượt quá 100 ký tự'],
  [/Work item type code is required/g, 'Thiếu mã loại công việc'],
  [/Work item type code cannot exceed 50 characters/g, 'Mã loại công việc không được vượt quá 50 ký tự'],
  [/Icon name cannot exceed 100 characters/g, 'Tên biểu tượng không được vượt quá 100 ký tự'],
  [/Color must be a valid hex color code \(e\.g\. #FF5733 or #FFF\)/g, 'Mã màu phải đúng định dạng HEX (ví dụ: #FF5733 hoặc #FFF)'],
  [/Hierarchy level is required/g, 'Cấp bậc là bắt buộc'],
  [
    /Hierarchy level must be between -1 \(Subtask\) and 1 \(Epic\)/g,
    'Cấp bậc phải từ Cấp 1 (Công việc con) đến Cấp 3 (Hạng mục lớn)',
  ],

  // ── Công việc con (CreateSubtaskRequest) ──
  [/Tiêu đề Sub-task/g, 'Tiêu đề công việc con'],

  // ── Chuyển trạng thái (WorkflowEngineServiceImpl) ──
  [/hoàn thành Story\/Task này/g, 'hoàn thành công việc này'],
  [/nhiệm vụ con \(Sub-task\)/g, 'công việc con'],

  // ── Liên kết (WorkItemLinkServiceImpl) ──
  [/\(đã được liên kết qua phân cấp Subtask\)/g, '(đã có quan hệ cha – con)'],
  [
    /Không thể tạo liên kết BLOCKS trực tiếp giữa Epic và Subtask do chênh lệch cấp độ \(Scale Mismatch\)\. Vui lòng liên kết ở cấp độ Story\/Task\./g,
    'Không thể tạo liên kết chặn tiến độ trực tiếp giữa Hạng mục lớn (Cấp 3) và Công việc con (Cấp 1) do chênh lệch cấp độ. Vui lòng liên kết ở Cấp 2.',
  ],
];

/** Việt hóa một câu thông báo từ BE; trả nguyên văn nếu không khớp quy tắc nào. */
export function translateServerMessage(message) {
  if (!message || typeof message !== 'string') return message;
  return RULES.reduce((text, [pattern, replacement]) => text.replace(pattern, replacement), message);
}
