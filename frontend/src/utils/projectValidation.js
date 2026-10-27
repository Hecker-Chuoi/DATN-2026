/**
 * projectValidation.js - Validation Rules & Helpers for Projects and WorkItemTypes
 */

export const PROJECT_KEY_REGEX = /^[A-Z][A-Z0-9_]{1,9}$/;
export const HEX_COLOR_REGEX = /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/;

/**
 * Automatically generates a recommended Project Key from the project name.
 * e.g., "Web Platform 2.0" -> "WP", "Authentication Service" -> "AUTH", "DATN 2026" -> "DATN"
 */
export function generateProjectKey(name) {
  if (!name || typeof name !== 'string') return '';
  
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 1) {
    const cleaned = words[0].replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
    return cleaned.slice(0, 5) || 'PRJ';
  }

  const initials = words
    .map((w) => w.charAt(0))
    .join('')
    .replace(/[^a-zA-Z0-9]/g, '')
    .toUpperCase();

  return initials.slice(0, 10) || 'PRJ';
}

/**
 * Sanitizes input string to adhere to Project Key format constraints.
 * Converts to uppercase and removes non-alphanumeric/underscore characters.
 */
export function sanitizeProjectKey(input) {
  if (!input || typeof input !== 'string') return '';
  return input
    .toUpperCase()
    .replace(/[^A-Z0-9_]/g, '')
    .slice(0, 10);
}

/**
 * Validates the Create/Update Project Form data.
 * @param {Object} data
 * @returns {{ isValid: boolean, errors: Record<string, string> }}
 */
export function validateProjectForm(data = {}) {
  const errors = {};

  if (!data.name || !data.name.trim()) {
    errors.name = 'Tên dự án là bắt buộc';
  } else if (data.name.trim().length > 255) {
    errors.name = 'Tên dự án không được vượt quá 255 ký tự';
  }

  if (!data.key || !data.key.trim()) {
    errors.key = 'Mã dự án là bắt buộc';
  } else {
    const trimmedKey = data.key.trim().toUpperCase();
    if (trimmedKey.length < 2 || trimmedKey.length > 10) {
      errors.key = 'Mã dự án phải từ 2 đến 10 ký tự';
    } else if (!PROJECT_KEY_REGEX.test(trimmedKey)) {
      errors.key =
        'Mã dự án phải bắt đầu bằng chữ cái viết hoa và chỉ chứa chữ cái viết hoa, chữ số và dấu gạch dưới';
    }
  }

  if (data.startDate && data.endDate) {
    const start = new Date(data.startDate);
    const end = new Date(data.endDate);
    if (!isNaN(start) && !isNaN(end) && start > end) {
      errors.endDate = 'Ngày kết thúc không thể trước ngày bắt đầu';
    }
  }

  if (data.iconUrl && data.iconUrl.length > 500) {
    errors.iconUrl = 'Đường dẫn biểu tượng không được vượt quá 500 ký tự';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Validates the Clone Project Form data.
 * @param {Object} data
 * @returns {{ isValid: boolean, errors: Record<string, string> }}
 */
export function validateCloneProjectForm(data = {}) {
  const errors = {};

  if (!data.name || !data.name.trim()) {
    errors.name = 'Tên dự án mới là bắt buộc';
  } else if (data.name.trim().length > 255) {
    errors.name = 'Tên dự án mới không được vượt quá 255 ký tự';
  }

  if (!data.key || !data.key.trim()) {
    errors.key = 'Mã dự án mới là bắt buộc';
  } else {
    const trimmedKey = data.key.trim().toUpperCase();
    if (trimmedKey.length < 2 || trimmedKey.length > 10) {
      errors.key = 'Mã dự án phải từ 2 đến 10 ký tự';
    } else if (!PROJECT_KEY_REGEX.test(trimmedKey)) {
      errors.key =
        'Mã dự án phải bắt đầu bằng chữ cái viết hoa và chỉ chứa chữ cái viết hoa, chữ số và dấu gạch dưới';
    }
  }

  if (!data.startDate) {
    errors.startDate = 'Ngày bắt đầu là bắt buộc để tịnh tiến mốc thời gian';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}


/**
 * Validates the Work Item Type Form data.
 * @param {Object} data
 * @returns {{ isValid: boolean, errors: Record<string, string> }}
 */
export function validateWorkItemTypeForm(data = {}) {
  const errors = {};

  if (!data.name || !data.name.trim()) {
    errors.name = 'Tên loại công việc là bắt buộc';
  } else if (data.name.trim().length > 100) {
    errors.name = 'Tên loại công việc không được vượt quá 100 ký tự';
  }

  if (!data.code || !data.code.trim()) {
    errors.code = 'Mã loại công việc là bắt buộc';
  } else if (data.code.trim().length > 50) {
    errors.code = 'Mã loại công việc không được vượt quá 50 ký tự';
  }

  if (data.hierarchyLevel === undefined || data.hierarchyLevel === null) {
    errors.hierarchyLevel = 'Cấp bậc phân cấp là bắt buộc';
  } else {
    const level = Number(data.hierarchyLevel);
    if (![-1, 0, 1].includes(level)) {
      errors.hierarchyLevel = 'Cấp bậc phân cấp phải là -1 (Công việc con), 0 (Tiêu chuẩn), hoặc 1 (Hạng mục lớn)';
    }
  }

  if (data.colorHex && !HEX_COLOR_REGEX.test(data.colorHex.trim())) {
    errors.colorHex = 'Mã màu phải đúng định dạng HEX (ví dụ: #3B82F6 hoặc #FFF)';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}
