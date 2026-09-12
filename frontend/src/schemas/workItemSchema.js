/**
 * workItemSchema.js - Client-side validation schema and helpers for Jira-grade Work Items
 */
import { HIERARCHY_LEVELS } from '../constants/workConstants.js';

export function validateWorkItemForm(formData, parentItem = null) {
  const errors = {};

  // 1. Summary validation
  if (!formData.summary || !formData.summary.trim()) {
    errors.summary = 'Tiêu đề công việc không được để trống';
  } else if (formData.summary.trim().length > 500) {
    errors.summary = 'Tiêu đề công việc tối đa 500 ký tự';
  }

  // 2. Type validation
  if (!formData.typeId) {
    errors.typeId = 'Vui lòng chọn loại công việc (Epic, Story, Task, Bug, Sub-task)';
  }

  // 3. Story points validation
  if (formData.storyPoints !== undefined && formData.storyPoints !== null && formData.storyPoints !== '') {
    const pts = Number(formData.storyPoints);
    if (isNaN(pts) || pts < 0) {
      errors.storyPoints = 'Điểm Story Points phải là số dương hoặc 0';
    } else if (pts > 999.99) {
      errors.storyPoints = 'Điểm Story Points tối đa 999.99';
    }
  }

  // 4. Estimate hours validation
  if (formData.estimateHours !== undefined && formData.estimateHours !== null && formData.estimateHours !== '') {
    const hrs = Number(formData.estimateHours);
    if (isNaN(hrs) || hrs < 0) {
      errors.estimateHours = 'Thời gian ước lượng phải là số dương';
    }
  }

  // 5. Date range validation
  if (formData.startDate && formData.dueDate) {
    const start = new Date(formData.startDate);
    const due = new Date(formData.dueDate);
    if (due < start) {
      errors.dueDate = 'Hạn chót (Due Date) không thể diễn ra trước ngày bắt đầu (Start Date)';
    }
  }

  // 6. Hierarchy rules
  if (formData.typeCode === 'SUB_TASK' && !formData.parentId) {
    errors.parentId = 'Nhiệm vụ con (Sub-task) bắt buộc phải có công việc cha (Story / Task / Bug)';
  }

  if (formData.typeCode === 'EPIC' && formData.parentId) {
    errors.parentId = 'Hạng mục lớn (Epic) là cấp bậc cao nhất và không được có công việc cha';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Converts hours to seconds for backend storage (e.g. 8h -> 28800s).
 */
export function hoursToSeconds(hours) {
  if (hours === undefined || hours === null || hours === '') return null;
  const h = Number(hours);
  return isNaN(h) ? null : Math.round(h * 3600);
}

/**
 * Converts seconds to display hours string (e.g. 28800s -> "8h", 5400s -> "1.5h").
 */
export function secondsToHoursString(seconds) {
  if (!seconds || seconds <= 0) return '0h';
  const hours = seconds / 3600;
  return hours % 1 === 0 ? `${hours}h` : `${hours.toFixed(1)}h`;
}
