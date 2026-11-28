package com.datn.aiservice.dto;

import java.util.UUID;

/** Ngữ cảnh trang mà frontend gửi kèm mỗi tin. */
public record ChatContextRequest(UUID workspaceId, UUID projectId, UUID workItemId, String timezone) {}
