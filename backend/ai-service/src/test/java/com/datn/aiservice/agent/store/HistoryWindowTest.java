package com.datn.aiservice.agent.store;

import com.datn.aiservice.agent.llm.MessageJson;
import com.datn.aiservice.config.AgentProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryWindowTest {

    private static HistoryWindow window(int charBudget) {
        AgentProperties props = new AgentProperties(null, null, null, null, 0, 0, 0, charBudget, 0, 0, false, null, null);
        return new HistoryWindow(props);
    }

    private static ObjectNode userText(String text) {
        return MessageJson.user(List.of(MessageJson.text(MessageJson.CONTEXT_OPEN + "\n</context>"), MessageJson.text(text)));
    }

    private static ObjectNode toolCall(String id) {
        ObjectNode tu = MessageJson.mapper().createObjectNode();
        tu.put("type", "tool_use").put("id", id).put("name", "search_work_items");
        tu.putObject("input");
        return MessageJson.assistant(List.of(tu));
    }

    private static ObjectNode toolResults(String id, String content) {
        return MessageJson.user(List.of(MessageJson.toolResult(id, content, false)));
    }

    private static ObjectNode answer(String text) {
        return MessageJson.assistant(List.of(MessageJson.text(text)));
    }

    /** Một lượt: câu hỏi → gọi tool → kết quả → trả lời. */
    private static List<ObjectNode> turn(int i, String payload) {
        return List.of(userText("câu " + i), toolCall("t" + i), toolResults("t" + i, payload), answer("trả lời " + i));
    }

    @Test
    void noCutWhenUnderBudget() {
        List<ObjectNode> h = new ArrayList<>(turn(1, "{}"));
        HistoryWindow.Result r = window(1_000_000).apply(h);
        assertThat(r.cutIndex()).isZero();
        assertThat(r.window()).hasSize(4);
    }

    @Test
    void cutStartsAtUserTextNeverAtOrphanToolResult() {
        List<ObjectNode> h = new ArrayList<>();
        for (int i = 1; i <= 5; i++) h.addAll(turn(i, "x".repeat(400)));
        // Ngân sách chỉ đủ khoảng 1,5 lượt cuối → điểm cắt rơi giữa một lượt.
        int budget = h.subList(h.size() - 6, h.size()).stream().mapToInt(n -> n.toString().length()).sum();

        HistoryWindow.Result r = window(budget).apply(h);

        assertThat(r.cutIndex()).isPositive();
        assertThat(MessageJson.isUserText(r.window().get(0))).isTrue();
        assertThat(MessageJson.visibleText(r.window().get(0))).startsWith("câu ");
    }

    @Test
    void toolResultsOlderThanThreeTurnsAreTrimmedWithoutTouchingStoredNodes() {
        List<ObjectNode> h = new ArrayList<>();
        for (int i = 1; i <= 4; i++) h.addAll(turn(i, "{\"result\":\"dữ liệu " + i + "\"}"));
        String originalTurn1 = h.get(2).toString();

        HistoryWindow.Result r = window(1_000_000).apply(h);

        String trimmed = MessageJson.blocksOfType(r.window().get(2), "tool_result").get(0).path("content").asText();
        String kept = MessageJson.blocksOfType(r.window().get(6), "tool_result").get(0).path("content").asText();
        assertThat(trimmed).isEqualTo(HistoryWindow.TRIMMED);
        assertThat(kept).contains("dữ liệu 2");
        assertThat(h.get(2).toString()).isEqualTo(originalTurn1);
    }
}
