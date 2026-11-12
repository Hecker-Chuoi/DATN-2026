package com.datn.workservice.service;

import com.datn.workservice.entity.Sprint;
import com.datn.workservice.enums.SprintState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SprintSequenceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 12, 9, 0);

    private static Sprint sprint(String name, SprintState state, LocalDateTime start, LocalDateTime createdAt) {
        return Sprint.builder()
                .id(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .name(name)
                .state(state)
                .startDate(start)
                .createdAt(createdAt)
                .build();
    }

    @Test
    @DisplayName("PLAN_ORDER: theo ngày bắt đầu tăng dần, Sprint chưa có ngày xếp cuối")
    void planOrder_sortsByStartDateNullsLast() {
        Sprint undated = sprint("Chưa có ngày", SprintState.FUTURE, null, BASE.minusDays(30));
        Sprint phase3 = sprint("Giai đoạn 3", SprintState.FUTURE, BASE.plusDays(42), BASE);
        Sprint phase1 = sprint("Giai đoạn 1", SprintState.FUTURE, BASE, BASE.plusDays(1));
        Sprint phase2 = sprint("Giai đoạn 2", SprintState.FUTURE, BASE.plusDays(20), BASE.plusDays(2));

        List<String> names = List.of(undated, phase3, phase1, phase2).stream()
                .sorted(SprintSequence.PLAN_ORDER)
                .map(Sprint::getName)
                .toList();

        assertThat(names).containsExactly("Giai đoạn 1", "Giai đoạn 2", "Giai đoạn 3", "Chưa có ngày");
    }

    @Test
    @DisplayName("PLAN_ORDER: trùng ngày bắt đầu thì Sprint tạo trước đứng trước")
    void planOrder_tieBreaksByCreatedAt() {
        Sprint createdLater = sprint("Tạo sau", SprintState.FUTURE, BASE, BASE.plusHours(2));
        Sprint createdEarlier = sprint("Tạo trước", SprintState.FUTURE, BASE, BASE.plusHours(1));

        assertThat(List.of(createdLater, createdEarlier).stream().sorted(SprintSequence.PLAN_ORDER).toList())
                .containsExactly(createdEarlier, createdLater);
    }

    @Test
    @DisplayName("DISPLAY_ORDER: ACTIVE trước, rồi FUTURE theo kế hoạch, CLOSED cuối")
    void displayOrder_groupsByStateThenPlan() {
        Sprint closed = sprint("Đã đóng", SprintState.CLOSED, BASE.minusDays(40), BASE);
        Sprint future2 = sprint("Dự kiến 2", SprintState.FUTURE, BASE.plusDays(20), BASE);
        Sprint active = sprint("Đang chạy", SprintState.ACTIVE, BASE.plusDays(1), BASE);
        Sprint future1 = sprint("Dự kiến 1", SprintState.FUTURE, BASE.plusDays(10), BASE);

        assertThat(List.of(closed, future2, active, future1).stream()
                .sorted(SprintSequence.DISPLAY_ORDER).map(Sprint::getName).toList())
                .containsExactly("Đang chạy", "Dự kiến 1", "Dự kiến 2", "Đã đóng");
    }

    @Test
    @DisplayName("startableSprintId: chỉ Sprint FUTURE đứng đầu kế hoạch, bỏ qua Sprint đã đóng")
    void startableSprintId_returnsFirstFutureSprint() {
        Sprint closed = sprint("Giai đoạn 1", SprintState.CLOSED, BASE.minusDays(20), BASE);
        Sprint phase3 = sprint("Giai đoạn 3", SprintState.FUTURE, BASE.plusDays(40), BASE);
        Sprint phase2 = sprint("Giai đoạn 2", SprintState.FUTURE, BASE.plusDays(20), BASE);

        assertThat(SprintSequence.startableSprintId(List.of(closed, phase3, phase2))).isEqualTo(phase2.getId());
    }

    @Test
    @DisplayName("startableSprintId: null khi dự án đang có Sprint ACTIVE (BR-S11)")
    void startableSprintId_nullWhenActiveExists() {
        Sprint active = sprint("Giai đoạn 1", SprintState.ACTIVE, BASE, BASE);
        Sprint phase2 = sprint("Giai đoạn 2", SprintState.FUTURE, BASE.plusDays(20), BASE);

        assertThat(SprintSequence.startableSprintId(List.of(active, phase2))).isNull();
    }

    @Test
    @DisplayName("startableSprintId: null khi không còn Sprint FUTURE")
    void startableSprintId_nullWhenNoFuture() {
        assertThat(SprintSequence.startableSprintId(List.of())).isNull();
    }
}
