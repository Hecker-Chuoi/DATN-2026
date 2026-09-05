/**
 * projectValidation.js - Validation Rules & Helpers for Projects, Teams, and WorkItemTypes
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
    errors.name = 'Project name is required';
  } else if (data.name.trim().length > 255) {
    errors.name = 'Project name cannot exceed 255 characters';
  }

  if (!data.key || !data.key.trim()) {
    errors.key = 'Project key is required';
  } else {
    const trimmedKey = data.key.trim().toUpperCase();
    if (trimmedKey.length < 2 || trimmedKey.length > 10) {
      errors.key = 'Project key must be between 2 and 10 characters';
    } else if (!PROJECT_KEY_REGEX.test(trimmedKey)) {
      errors.key =
        'Project key must start with an uppercase letter and contain only uppercase letters, numbers, and underscores';
    }
  }

  if (data.startDate && data.endDate) {
    const start = new Date(data.startDate);
    const end = new Date(data.endDate);
    if (!isNaN(start) && !isNaN(end) && start > end) {
      errors.endDate = 'End date cannot be earlier than start date';
    }
  }

  if (data.iconUrl && data.iconUrl.length > 500) {
    errors.iconUrl = 'Icon URL cannot exceed 500 characters';
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
    errors.name = 'New project name is required';
  } else if (data.name.trim().length > 255) {
    errors.name = 'Project name cannot exceed 255 characters';
  }

  if (!data.key || !data.key.trim()) {
    errors.key = 'New project key is required';
  } else {
    const trimmedKey = data.key.trim().toUpperCase();
    if (trimmedKey.length < 2 || trimmedKey.length > 10) {
      errors.key = 'Project key must be between 2 and 10 characters';
    } else if (!PROJECT_KEY_REGEX.test(trimmedKey)) {
      errors.key =
        'Project key must start with an uppercase letter and contain only uppercase letters, numbers, and underscores';
    }
  }

  if (!data.startDate) {
    errors.startDate = 'Start date is required for timeline shifting';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}

/**
 * Validates the Team Form data.
 * @param {Object} data
 * @returns {{ isValid: boolean, errors: Record<string, string> }}
 */
export function validateTeamForm(data = {}) {
  const errors = {};

  if (!data.name || !data.name.trim()) {
    errors.name = 'Team name is required';
  } else if (data.name.trim().length > 255) {
    errors.name = 'Team name cannot exceed 255 characters';
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
    errors.name = 'Work item type name is required';
  } else if (data.name.trim().length > 100) {
    errors.name = 'Work item type name cannot exceed 100 characters';
  }

  if (!data.code || !data.code.trim()) {
    errors.code = 'Work item type code is required';
  } else if (data.code.trim().length > 50) {
    errors.code = 'Work item type code cannot exceed 50 characters';
  }

  if (data.hierarchyLevel === undefined || data.hierarchyLevel === null) {
    errors.hierarchyLevel = 'Hierarchy level is required';
  } else {
    const level = Number(data.hierarchyLevel);
    if (![-1, 0, 1].includes(level)) {
      errors.hierarchyLevel = 'Hierarchy level must be -1 (Subtask), 0 (Standard), or 1 (Epic)';
    }
  }

  if (data.colorHex && !HEX_COLOR_REGEX.test(data.colorHex.trim())) {
    errors.colorHex = 'Color must be a valid HEX color code (e.g. #3B82F6 or #FFF)';
  }

  return {
    isValid: Object.keys(errors).length === 0,
    errors,
  };
}
