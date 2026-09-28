package com.datn.workservice.service;

import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.dto.response.BoardSwimlaneResponse;
import com.datn.workservice.entity.BoardSwimlane;
import com.datn.workservice.enums.SwimlaneStrategy;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.service.board.SwimlaneGrouper;
import com.datn.workservice.service.mapper.CardMappingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SwimlaneGrouperTest {

    private SwimlaneGrouper grouper;
    private CardMappingContext ctx;

    @BeforeEach
    void setUp() {
        grouper = new SwimlaneGrouper();
        ctx = CardMappingContext.builder().build();
    }

    private BoardSwimlane cfg(SwimlaneStrategy strategy) {
        return BoardSwimlane.builder()
                .id(UUID.randomUUID()).boardId(UUID.randomUUID())
                .name("Phân luồng").strategy(strategy).swimlaneOrder(0).build();
    }

    private BoardCardResponse card() {
        return BoardCardResponse.builder().id(UUID.randomUUID()).code("TEST-1").build();
    }

    @Test
    @DisplayName("Không cấu hình luồng → một luồng duy nhất 'Tất cả'")
    void noConfigProducesSingleLane() {
        var lanes = grouper.group(List.of(), List.of(card(), card()), ctx);

        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).getName()).isEqualTo("Tất cả");
        assertThat(lanes.get(0).getCardCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("BY_EPIC: gán khoá luồng lên thẻ, luồng 'không thuộc Epic' xuống cuối")
    void byEpicPutsUngroupedLast() {
        UUID epicId = UUID.randomUUID();
        var withEpic = card();
        withEpic.setEpicId(epicId);
        withEpic.setEpicCode("TEST-9");
        withEpic.setEpicSummary("Thanh toán");
        var without = card();

        var lanes = grouper.group(List.of(cfg(SwimlaneStrategy.BY_EPIC)), List.of(without, withEpic), ctx);

        assertThat(lanes).hasSize(2);
        assertThat(lanes.get(lanes.size() - 1).getName()).isEqualTo("Không thuộc Epic nào");
        assertThat(withEpic.getSwimlaneKey()).isEqualTo(epicId.toString());
        assertThat(withEpic.getSwimlaneLabel()).isEqualTo("TEST-9 Thanh toán");
    }

    @Test
    @DisplayName("BY_ASSIGNEE: thẻ chưa phân công gom vào luồng cuối")
    void byAssigneeHandlesUnassigned() {
        UUID userId = UUID.randomUUID();
        var assigned = card();
        assigned.setAssigneeId(userId);
        assigned.setAssigneeName("Nguyễn Văn A");
        var unassigned = card();

        var lanes = grouper.group(List.of(cfg(SwimlaneStrategy.BY_ASSIGNEE)), List.of(assigned, unassigned), ctx);

        assertThat(lanes).hasSize(2);
        assertThat(lanes.get(lanes.size() - 1).getName()).isEqualTo("Chưa phân công");
        assertThat(assigned.getSwimlaneLabel()).isEqualTo("Nguyễn Văn A");
    }

    @Test
    @DisplayName("BY_PRIORITY: phát đủ mọi mức ưu tiên kể cả luồng rỗng")
    void byPriorityEmitsAllLevels() {
        var high = card();
        high.setPriority(WorkItemPriority.HIGH);

        var lanes = grouper.group(List.of(cfg(SwimlaneStrategy.BY_PRIORITY)), List.of(high), ctx);

        assertThat(lanes).hasSize(WorkItemPriority.values().length);
        assertThat(lanes).extracting(BoardSwimlaneResponse::getCardCount).contains(0);
        assertThat(lanes).filteredOn(l -> "HIGH".equals(l.getSwimlaneKey()))
                .singleElement().extracting(BoardSwimlaneResponse::getCardCount).isEqualTo(1);
    }

    @Test
    @DisplayName("CUSTOM_JQL chưa hỗ trợ → hạ cấp về một luồng, KHÔNG ném lỗi")
    void customJqlDegradesGracefully() {
        var lanes = grouper.group(List.of(cfg(SwimlaneStrategy.CUSTOM_JQL)), List.of(card()), ctx);

        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).getUnsupported()).isTrue();
        assertThat(lanes.get(0).getStrategy()).isEqualTo(SwimlaneStrategy.CUSTOM_JQL);
    }
}
