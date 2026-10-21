package com.datn.workservice.service.template;

import com.datn.workservice.dto.response.TemplateIntegrityReport;
import com.datn.workservice.entity.Status;
import com.datn.workservice.entity.WorkflowTransition;
import com.datn.workservice.enums.SprintState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateIntegrityValidatorTest {

    private static TemplateIntegrityReport validate(BlueprintFixtures f) {
        return TemplateIntegrityValidator.validate(f.blueprint());
    }

    private static boolean hasError(TemplateIntegrityReport r, String code) {
        return r.getErrors().stream().anyMatch(i -> i.getCode().equals(code));
    }

    private static boolean hasWarning(TemplateIntegrityReport r, String code) {
        return r.getWarnings().stream().anyMatch(i -> i.getCode().equals(code));
    }

    @Test
    @DisplayName("Mẫu đầy đủ là hợp lệ; sprint CLOSED chỉ bị cảnh báo sẽ reset")
    void validTemplate() {
        TemplateIntegrityReport r = validate(new BlueprintFixtures());
        assertThat(r.getErrors()).isEmpty();
        assertThat(r.isValid()).isTrue();
        assertThat(hasWarning(r, "SPRINT_NOT_FUTURE")).isTrue();
    }

    @Test
    @DisplayName("Thiếu trạng thái nhóm DONE → lỗi (Burndown/Velocity cần DONE)")
    void missingDoneCategory() {
        BlueprintFixtures f = new BlueprintFixtures();
        f.done.setCategory(com.datn.workservice.enums.StatusCategory.IN_PROGRESS);
        assertThat(hasError(validate(f), "NO_DONE_STATUS")).isTrue();
    }

    @Test
    @DisplayName("Transition trỏ tới trạng thái của dự án khác → lỗi")
    void foreignStatusInTransition() {
        BlueprintFixtures f = new BlueprintFixtures();
        f.transitions.add(WorkflowTransition.builder().id(UUID.randomUUID()).workflowId(f.workflow.getId())
                .fromStatusId(f.done.getId()).toStatusId(UUID.randomUUID()).name("Lạc").build());
        assertThat(hasError(validate(f), "TRANSITION_FOREIGN_STATUS")).isTrue();
    }

    @Test
    @DisplayName("Subtask gắn trực tiếp vào Epic → lỗi phân cấp")
    void hierarchyViolation() {
        BlueprintFixtures f = new BlueprintFixtures();
        f.subtask.setParentId(f.epic.getId());
        assertThat(hasError(validate(f), "ITEM_HIERARCHY")).isTrue();
    }

    @Test
    @DisplayName("Sprint kết thúc sau hạn chót dự án → lỗi thời gian")
    void sprintAfterProjectEnd() {
        BlueprintFixtures f = new BlueprintFixtures();
        f.project.setEndDate(BlueprintFixtures.ANCHOR.plusDays(20));
        assertThat(hasError(validate(f), "SPRINT_AFTER_PROJECT")).isTrue();
    }

    @Test
    @DisplayName("Sprint dài hơn 8 tuần → lỗi (không thể bắt đầu)")
    void sprintTooLong() {
        BlueprintFixtures f = new BlueprintFixtures();
        f.project.setEndDate(null);
        f.sprint2.setEndDate(f.sprint2.getStartDate().plusDays(60));
        assertThat(hasError(validate(f), "SPRINT_TOO_LONG")).isTrue();
    }

    @Test
    @DisplayName("Hạn chót công việc sớm hơn ngày bắt đầu → lỗi")
    void itemDateRange() {
        BlueprintFixtures f = new BlueprintFixtures();
        f.task.setDueDate(f.task.getStartDate().minusDays(1));
        assertThat(hasError(validate(f), "ITEM_DATE_RANGE")).isTrue();
    }

    @Test
    @DisplayName("Thiếu vai trò VIEWER → lỗi (không đồng bộ được thành viên nhóm)")
    void missingRole() {
        BlueprintFixtures f = new BlueprintFixtures();
        f.roles.removeIf(r -> r.getName().equals("VIEWER"));
        assertThat(hasError(validate(f), "MISSING_ROLE")).isTrue();
    }

    @Test
    @DisplayName("Trạng thái không nằm trên cột board nào → cảnh báo")
    void statusNotOnBoard() {
        BlueprintFixtures f = new BlueprintFixtures();
        Status extra = Status.builder().id(UUID.randomUUID()).projectId(f.project.getId()).name("Blocked")
                .category(com.datn.workservice.enums.StatusCategory.IN_PROGRESS).build();
        ProjectBlueprint bp = f.blueprint();
        bp.statuses().add(extra);
        TemplateIntegrityReport r = TemplateIntegrityValidator.validate(bp);
        assertThat(hasWarning(r, "STATUS_NOT_ON_BOARD")).isTrue();
        assertThat(hasWarning(r, "STATUS_UNREACHABLE")).isTrue();
        assertThat(r.isValid()).isTrue();
    }

    @Test
    @DisplayName("Hai sprint chồng lấn → cảnh báo")
    void overlappingSprints() {
        BlueprintFixtures f = new BlueprintFixtures();
        f.sprint2.setStartDate(f.sprint1.getStartDate().plusDays(7));
        f.sprint2.setState(SprintState.FUTURE);
        assertThat(hasWarning(validate(f), "SPRINT_OVERLAP")).isTrue();
    }

    @Test
    @DisplayName("Timeline: mốc gốc = ngày bắt đầu dự án, độ dài = tới mốc muộn nhất")
    void timelineSpan() {
        BlueprintFixtures f = new BlueprintFixtures();
        ProjectBlueprint bp = f.blueprint();
        assertThat(bp.anchorDate()).isEqualTo(BlueprintFixtures.ANCHOR);
        assertThat(bp.spanDays()).isEqualTo(60L);

        f.project.setStartDate(null);
        f.project.setEndDate(null);
        // Không có ngày dự án: mốc gốc = sprint sớm nhất, mốc muộn nhất = kết thúc Sprint 2 (offset 28)
        assertThat(f.blueprint().anchorDate()).isEqualTo(BlueprintFixtures.ANCHOR);
        assertThat(f.blueprint().spanDays()).isEqualTo(28L);
    }
}
