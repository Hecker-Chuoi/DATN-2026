package com.datn.workservice.enums;

/**
 * Quyền hạt mịn trong phạm vi một dự án (Jira-style permission scheme).
 * Mỗi vai trò dự án (project_roles) được gán một tập các key này trong permission_scheme_entries.
 */
public enum PermissionKey {
    // ─── Dự án ───
    PROJECT_ADMIN,
    PROJECT_VIEW,

    // ─── Công việc (Issue) ───
    ISSUE_CREATE,
    ISSUE_EDIT,
    ISSUE_DELETE,
    ISSUE_TRANSITION,
    ISSUE_ASSIGN,

    // ─── Sprint & Release ───
    SPRINT_MANAGE,
    RELEASE_MANAGE,

    // ─── Bình luận ───
    COMMENT_CREATE,
    COMMENT_EDIT_OWN,
    COMMENT_DELETE_OWN,
    COMMENT_DELETE_ALL,

    // ─── Tệp đính kèm ───
    ATTACHMENT_DELETE_OWN,
    ATTACHMENT_DELETE_ALL
}
