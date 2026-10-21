package com.datn.workservice.service;

import com.datn.workservice.dto.request.CloneProjectRequest;
import com.datn.workservice.dto.request.CreateProjectFromTemplateRequest;
import com.datn.workservice.dto.request.SaveAsTemplateRequest;
import com.datn.workservice.dto.response.TemplateInstantiationResponse;
import com.datn.workservice.dto.response.TemplateIntegrityReport;
import com.datn.workservice.dto.response.TemplatePreviewResponse;
import com.datn.workservice.dto.response.TemplateSummaryResponse;
import com.datn.workservice.enums.TemplateCategory;

import java.util.List;
import java.util.UUID;

/**
 * Dự án mẫu (Project Template): thư viện mẫu, xem trước, tạo dự án từ mẫu,
 * lưu dự án thành mẫu và nhân bản dự án.
 */
public interface ProjectTemplateService {

    /**
     * Thư viện mẫu trong phạm vi workspace.
     *
     * @param workspaceIds null = không giới hạn (Global ADMIN), rỗng = trang trống
     */
    List<TemplateSummaryResponse> listTemplates(List<UUID> workspaceIds, TemplateCategory category, String search);

    TemplatePreviewResponse getPreview(UUID templateId, UUID currentUserId);

    /** Kiểm tra toàn vẹn một dự án bất kỳ (dùng trước khi đánh dấu nó là mẫu). */
    TemplateIntegrityReport checkIntegrity(UUID projectId);

    TemplateInstantiationResponse createFromTemplate(UUID templateId, CreateProjectFromTemplateRequest request,
                                                     UUID currentUserId);

    TemplateInstantiationResponse saveAsTemplate(UUID projectId, SaveAsTemplateRequest request, UUID currentUserId);

    TemplateInstantiationResponse duplicateProject(UUID projectId, CloneProjectRequest request, UUID currentUserId);
}
