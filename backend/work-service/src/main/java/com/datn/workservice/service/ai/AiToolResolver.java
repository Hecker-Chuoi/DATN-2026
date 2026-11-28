package com.datn.workservice.service.ai;

import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import com.datn.workservice.dto.response.RoleAssignmentResponse;
import com.datn.workservice.dto.response.SprintResponse;
import com.datn.workservice.dto.response.StatusResponse;
import com.datn.workservice.dto.response.WorkItemTypeResponse;
import com.datn.workservice.entity.WorkItem;
import com.datn.workservice.enums.SprintState;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.repository.WorkItemRepository;
import com.datn.workservice.service.ProjectMemberService;
import com.datn.workservice.service.SprintService;
import com.datn.workservice.service.WorkItemTypeService;
import com.datn.workservice.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Đổi những gì AI/người dùng nói (mã AUTH-12, tên loại, tên trạng thái, tên người, tên sprint) thành id.
 * Khi mơ hồ (nhiều người trùng tên) ném BadRequest liệt kê lựa chọn để AI chuyển sang ask_user.
 */
@Component
@RequiredArgsConstructor
public class AiToolResolver {

    private final WorkItemRepository workItemRepository;
    private final WorkItemTypeService workItemTypeService;
    private final WorkflowService workflowService;
    private final SprintService sprintService;
    private final ProjectMemberService projectMemberService;

    /** Theo mã nếu có, không thì theo workItemId đang mở trên màn hình. */
    public WorkItem item(UUID workItemId, String code) {
        WorkItem item;
        if (code != null && !code.isBlank()) {
            String c = code.trim().toUpperCase(Locale.ROOT);
            item = workItemRepository.findByCode(c)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc có mã " + c));
        } else if (workItemId != null) {
            item = workItemRepository.findById(workItemId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công việc đang mở"));
        } else {
            throw new BadRequestException("Thiếu mã công việc (ví dụ AUTH-12)");
        }
        if (item.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Công việc " + item.getCode() + " đã bị xóa");
        }
        return item;
    }

    public UUID typeId(UUID projectId, String name) {
        if (name == null || name.isBlank()) return null;
        List<WorkItemTypeResponse> types = workItemTypeService.getProjectWorkItemTypes(projectId);
        String n = name.trim();
        return types.stream()
                .filter(t -> n.equalsIgnoreCase(t.getName()) || n.equalsIgnoreCase(t.getCode()))
                .findFirst()
                .or(() -> types.stream().filter(t -> containsIgnoreCase(t.getName(), n)).findFirst())
                .map(WorkItemTypeResponse::getId)
                .orElseThrow(() -> new BadRequestException("Không có loại công việc '" + n + "'. Các loại hiện có: "
                        + types.stream().map(WorkItemTypeResponse::getName).collect(Collectors.joining(", "))));
    }

    public UUID statusId(UUID projectId, String name) {
        if (name == null || name.isBlank()) return null;
        List<StatusResponse> statuses = workflowService.getProjectStatuses(projectId);
        String n = name.trim();
        return statuses.stream().filter(s -> n.equalsIgnoreCase(s.getName())).findFirst()
                .or(() -> statuses.stream().filter(s -> containsIgnoreCase(s.getName(), n)).findFirst())
                .map(StatusResponse::getId)
                .orElseThrow(() -> new BadRequestException("Không có trạng thái '" + n + "'. Các trạng thái hiện có: "
                        + statuses.stream().map(StatusResponse::getName).collect(Collectors.joining(", "))));
    }

    /** Trống hoặc "active" → sprint đang chạy. */
    public SprintResponse sprint(UUID projectId, String name) {
        if (name == null || name.isBlank() || "active".equalsIgnoreCase(name.trim())) {
            return sprintService.listByProject(projectId, Set.of(SprintState.ACTIVE)).stream()
                    .filter(s -> s.getState() == SprintState.ACTIVE).findFirst()
                    .orElseThrow(() -> new BadRequestException("Dự án không có sprint nào đang chạy"));
        }
        List<SprintResponse> sprints = sprintService.listByProject(projectId, null);
        String n = name.trim();
        List<SprintResponse> exact = sprints.stream().filter(s -> n.equalsIgnoreCase(s.getName())).toList();
        if (exact.size() == 1) return exact.get(0);
        List<SprintResponse> partial = sprints.stream().filter(s -> containsIgnoreCase(s.getName(), n)).toList();
        if (partial.size() == 1) return partial.get(0);
        if (partial.isEmpty()) {
            throw new ResourceNotFoundException("Không có sprint '" + n + "'. Sprint hiện có: "
                    + sprints.stream().map(SprintResponse::getName).collect(Collectors.joining(", ")));
        }
        throw new BadRequestException("Có nhiều sprint khớp '" + n + "': "
                + partial.stream().map(SprintResponse::getName).collect(Collectors.joining(", ")) + ". Hãy hỏi lại người dùng.");
    }

    /** "me"/"tôi" → chính người dùng; tên/username/email → sub của thành viên dự án; trùng nhiều → BadRequest. */
    public UUID memberId(UUID projectId, String name, UUID currentUserId) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        if (Set.of("me", "tôi", "toi", "mình", "minh").contains(n.toLowerCase(Locale.ROOT))) return currentUserId;

        List<RoleAssignmentResponse> members = projectMemberService.getProjectMembers(projectId);
        List<RoleAssignmentResponse> exact = members.stream()
                .filter(m -> n.equalsIgnoreCase(m.getFullName()) || n.equalsIgnoreCase(m.getUsername()) || n.equalsIgnoreCase(m.getEmail()))
                .toList();
        if (exact.size() == 1) return exact.get(0).getUserId();
        List<RoleAssignmentResponse> partial = members.stream()
                .filter(m -> containsIgnoreCase(m.getFullName(), n) || containsIgnoreCase(m.getUsername(), n) || containsIgnoreCase(m.getEmail(), n))
                .toList();
        if (partial.size() == 1) return partial.get(0).getUserId();
        if (partial.isEmpty()) {
            throw new ResourceNotFoundException("Không có thành viên nào tên '" + n + "' trong dự án. Thành viên: "
                    + members.stream().map(AiToolResolver::memberLabel).collect(Collectors.joining(", ")));
        }
        throw new BadRequestException("Có nhiều thành viên khớp '" + n + "': "
                + partial.stream().map(AiToolResolver::memberLabel).collect(Collectors.joining(", "))
                + ". Hãy hỏi lại người dùng chọn đúng người.");
    }

    public static String memberLabel(RoleAssignmentResponse m) {
        String name = m.getFullName() != null && !m.getFullName().isBlank() ? m.getFullName() : m.getUsername();
        return name + (m.getEmail() == null ? "" : " (" + m.getEmail() + ")");
    }

    public WorkItemPriority priority(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        try {
            return WorkItemPriority.valueOf(s);
        } catch (IllegalArgumentException e) {
            return switch (s) {
                case "KHẨN", "KHAN", "RẤT CAO", "URGENT", "CRITICAL" -> WorkItemPriority.HIGHEST;
                case "CAO" -> WorkItemPriority.HIGH;
                case "TRUNG BÌNH", "TRUNG BINH", "NORMAL" -> WorkItemPriority.MEDIUM;
                case "THẤP", "THAP" -> WorkItemPriority.LOW;
                case "RẤT THẤP", "RAT THAP", "TRIVIAL" -> WorkItemPriority.LOWEST;
                default -> throw new BadRequestException("Độ ưu tiên không hợp lệ: " + v + " (LOWEST, LOW, MEDIUM, HIGH, HIGHEST)");
            };
        }
    }

    public LocalDate date(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return LocalDate.parse(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new BadRequestException("Ngày không hợp lệ: " + v + " (định dạng yyyy-MM-dd)");
        }
    }

    static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
