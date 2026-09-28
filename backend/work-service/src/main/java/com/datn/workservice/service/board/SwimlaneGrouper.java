package com.datn.workservice.service.board;

import com.datn.workservice.dto.response.BoardCardResponse;
import com.datn.workservice.dto.response.BoardSwimlaneResponse;
import com.datn.workservice.entity.BoardSwimlane;
import com.datn.workservice.enums.SwimlaneStrategy;
import com.datn.workservice.enums.WorkItemPriority;
import com.datn.workservice.service.mapper.CardMappingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gom the vao cac luong ngang (swimlane) va GAN swimlaneKey len tung the.
 *
 * Jira chi ap dung MOT chien luoc tai mot thoi diem, nen chi lay cau hinh dau tien.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SwimlaneGrouper {

    private static final String KEY_NONE = "__none__";

    private static final Map<WorkItemPriority, String> PRIORITY_LABELS = Map.of(
            WorkItemPriority.HIGHEST, "Cao nhất",
            WorkItemPriority.HIGH, "Cao",
            WorkItemPriority.MEDIUM, "Trung bình",
            WorkItemPriority.LOW, "Thấp",
            WorkItemPriority.LOWEST, "Thấp nhất"
    );

    /**
     * @param configured cau hinh luong cua bang (da sap theo swimlaneOrder)
     * @param cards      danh sach the - PHUONG THUC NAY GAN swimlaneKey/Label len tung the
     */
    public List<BoardSwimlaneResponse> group(List<BoardSwimlane> configured,
                                             List<BoardCardResponse> cards,
                                             CardMappingContext ctx) {
        if (configured == null || configured.isEmpty()) {
            return List.of(singleLane("Tất cả", null, cards.size(), false));
        }

        BoardSwimlane cfg = configured.get(0);
        SwimlaneStrategy strategy = cfg.getStrategy();

        return switch (strategy) {
            case BY_EPIC -> byEpic(cfg, cards);
            case BY_ASSIGNEE -> byAssignee(cfg, cards, ctx);
            case BY_PRIORITY -> byPriority(cfg, cards);
            // Chua co bo may JQL (thuoc Giai doan 8): ha cap ve mot luong thay vi nem loi,
            // de bang duoc luu cau hinh CUSTOM_JQL van hien thi binh thuong.
            case CUSTOM_JQL -> {
                log.debug("Swimlane CUSTOM_JQL chua duoc ho tro tren board {}", cfg.getBoardId());
                BoardSwimlaneResponse lane = singleLane(cfg.getName(), cfg.getId(), cards.size(), true);
                lane.setStrategy(SwimlaneStrategy.CUSTOM_JQL);
                lane.setCustomJql(cfg.getCustomJql());
                yield List.of(lane);
            }
        };
    }

    private List<BoardSwimlaneResponse> byEpic(BoardSwimlane cfg, List<BoardCardResponse> cards) {
        Map<String, BoardSwimlaneResponse> lanes = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (BoardCardResponse c : cards) {
            String key = c.getEpicId() == null ? KEY_NONE : c.getEpicId().toString();
            String label = c.getEpicId() == null
                    ? "Không thuộc Epic nào"
                    : join(c.getEpicCode(), c.getEpicSummary());
            c.setSwimlaneKey(key);
            c.setSwimlaneLabel(label);
            lanes.computeIfAbsent(key, k -> lane(k, label, cfg));
            counts.merge(key, 1, Integer::sum);
        }
        return finish(lanes, counts, cfg.getStrategy());
    }

    private List<BoardSwimlaneResponse> byAssignee(BoardSwimlane cfg, List<BoardCardResponse> cards,
                                                   CardMappingContext ctx) {
        Map<String, BoardSwimlaneResponse> lanes = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (BoardCardResponse c : cards) {
            UUID assignee = c.getAssigneeId();
            String key = assignee == null ? KEY_NONE : assignee.toString();
            String label = assignee == null
                    ? "Chưa phân công"
                    : (c.getAssigneeName() != null && !c.getAssigneeName().isBlank()
                        ? c.getAssigneeName()
                        : "Người dùng (" + assignee.toString().substring(0, 8) + ")");
            c.setSwimlaneKey(key);
            c.setSwimlaneLabel(label);
            lanes.computeIfAbsent(key, k -> lane(k, label, cfg));
            counts.merge(key, 1, Integer::sum);
        }
        return finish(lanes, counts, cfg.getStrategy());
    }

    private List<BoardSwimlaneResponse> byPriority(BoardSwimlane cfg, List<BoardCardResponse> cards) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BoardCardResponse c : cards) {
            WorkItemPriority p = c.getPriority() == null ? WorkItemPriority.MEDIUM : c.getPriority();
            c.setSwimlaneKey(p.name());
            c.setSwimlaneLabel(PRIORITY_LABELS.getOrDefault(p, p.name()));
            counts.merge(p.name(), 1, Integer::sum);
        }

        // Phat du moi muc uu tien ke ca luong rong - dung hanh vi cua Jira.
        List<BoardSwimlaneResponse> result = new ArrayList<>();
        int order = 0;
        for (WorkItemPriority p : WorkItemPriority.values()) {
            BoardSwimlaneResponse lane = lane(p.name(), PRIORITY_LABELS.getOrDefault(p, p.name()), cfg);
            lane.setSwimlaneOrder(order++);
            lane.setCardCount(counts.getOrDefault(p.name(), 0));
            result.add(lane);
        }
        return result;
    }

    /** Sap luong theo ten, rieng luong "khong thuoc nhom nao" luon xuong cuoi. */
    private List<BoardSwimlaneResponse> finish(Map<String, BoardSwimlaneResponse> lanes,
                                               Map<String, Integer> counts,
                                               SwimlaneStrategy strategy) {
        List<BoardSwimlaneResponse> result = new ArrayList<>(lanes.values());
        result.sort(Comparator
                .<BoardSwimlaneResponse, Integer>comparing(l -> KEY_NONE.equals(l.getSwimlaneKey()) ? 1 : 0)
                .thenComparing(l -> l.getName() == null ? "" : l.getName()));

        int order = 0;
        for (BoardSwimlaneResponse lane : result) {
            lane.setSwimlaneOrder(order++);
            lane.setStrategy(strategy);
            lane.setCardCount(counts.getOrDefault(lane.getSwimlaneKey(), 0));
        }
        return result;
    }

    private BoardSwimlaneResponse lane(String key, String label, BoardSwimlane cfg) {
        return BoardSwimlaneResponse.builder()
                .id(null)
                .swimlaneKey(key)
                .name(label)
                .strategy(cfg.getStrategy())
                .unsupported(false)
                .build();
    }

    private BoardSwimlaneResponse singleLane(String name, UUID id, int count, boolean unsupported) {
        return BoardSwimlaneResponse.builder()
                .id(id)
                .swimlaneKey(null)
                .name(name)
                .strategy(null)
                .swimlaneOrder(0)
                .unsupported(unsupported)
                .cardCount(count)
                .build();
    }

    private String join(String code, String summary) {
        if (code == null && summary == null) return "Epic";
        if (code == null) return summary;
        if (summary == null) return code;
        return code + " " + summary;
    }
}
