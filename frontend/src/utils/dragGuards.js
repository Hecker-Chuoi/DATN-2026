/**
 * dragGuards.js - Frontend Drag-and-Drop Validation Guards for 3-Level Jira Hierarchy
 * Level 1: Epic -> Level 0: Standard (Story/Task/Bug) -> Level -1: Subtask.
 */
import { HIERARCHY_LEVELS, isHierarchyValid } from '../constants/workConstants';

/**
 * Validates whether draggedItem can be dropped onto targetParent as its child.
 *
 * @param {Object} draggedItem - The item being dragged
 * @param {Object|null} targetParent - The target parent item (null if top-level/standalone)
 * @returns {{ allowed: boolean, reason?: string, icon?: string }}
 */
export function validateHierarchyDrop(draggedItem, targetParent) {
  if (!draggedItem) {
    return { allowed: false, reason: 'Không tìm thấy thẻ công việc', icon: 'Ban' };
  }

  const childLevel = draggedItem.hierarchyLevel ?? (draggedItem.isSubtask ? HIERARCHY_LEVELS.SUBTASK : HIERARCHY_LEVELS.STANDARD);
  const parentLevel = targetParent
    ? (targetParent.hierarchyLevel ?? (targetParent.isSubtask ? HIERARCHY_LEVELS.SUBTASK : HIERARCHY_LEVELS.STANDARD))
    : null;

  // Cannot be parent of oneself
  if (targetParent && (draggedItem.id === targetParent.id || draggedItem.code === targetParent.code)) {
    return {
      allowed: false,
      reason: 'Một công việc không thể tự làm cha chính mình!',
      icon: 'Ban',
    };
  }

  // Epic (Level 1)
  if (childLevel === HIERARCHY_LEVELS.EPIC) {
    if (parentLevel !== null) {
      return {
        allowed: false,
        reason: 'Hạng mục lớn là cấp cao nhất, không thể nằm trong công việc khác!',
        icon: 'Ban',
      };
    }
    return { allowed: true, icon: 'Check' };
  }

  // Standard (Level 0: Story/Task/Bug)
  if (childLevel === HIERARCHY_LEVELS.STANDARD) {
    if (parentLevel === null || parentLevel === HIERARCHY_LEVELS.EPIC) {
      return { allowed: true, icon: 'Check' };
    }
    return {
      allowed: false,
      reason: 'Công việc tiêu chuẩn chỉ có thể thuộc về Hạng mục lớn hoặc đứng độc lập!',
      icon: 'Ban',
    };
  }

  // Sub-task (Level -1)
  if (childLevel === HIERARCHY_LEVELS.SUBTASK) {
    if (!targetParent) {
      return {
        allowed: false,
        reason: 'Nhiệm vụ con bắt buộc phải trực thuộc một Công việc tiêu chuẩn (Câu chuyện, Nhiệm vụ hoặc Lỗi phần mềm)!',
        icon: 'Ban',
      };
    }
    if (parentLevel === HIERARCHY_LEVELS.EPIC) {
      return {
        allowed: false,
        reason: 'Nhiệm vụ con không thể gắn trực tiếp vào Hạng mục lớn (Phải gắn qua Công việc tiêu chuẩn)!',
        icon: 'Ban',
      };
    }
    if (parentLevel === HIERARCHY_LEVELS.SUBTASK) {
      return {
        allowed: false,
        reason: 'Không cho phép lồng nhiệm vụ con đa tầng bên trong nhiệm vụ con khác!',
        icon: 'Ban',
      };
    }
    if (parentLevel === HIERARCHY_LEVELS.STANDARD) {
      return { allowed: true, icon: 'Check' };
    }
  }

  return { allowed: false, reason: 'Cấp độ phân cấp không hợp lệ', icon: 'Ban' };
}

/**
 * Quick boolean check whether drop is allowed
 */
export function isDropAllowed(draggedItem, targetParent) {
  return validateHierarchyDrop(draggedItem, targetParent).allowed;
}
