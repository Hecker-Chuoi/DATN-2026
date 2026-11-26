package com.datn.aiservice.agent.store;

import com.datn.aiservice.agent.llm.MessageJson;
import com.datn.aiservice.config.AgentProperties;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cắt lịch sử theo ngân sách ký tự: cửa sổ luôn bắt đầu ở một tin USER có text (không tách cặp
 * tool_use ↔ tool_result). tool_result cũ hơn 3 lượt gần nhất được thay bằng "[đã lược bớt]".
 * Cả hai là sửa lịch sử; thinking block bị ảnh hưởng do API tự bỏ ({@code prefix_mismatch_behavior = drop_block}).
 */
@Component
@RequiredArgsConstructor
public class HistoryWindow {

    static final int KEEP_FULL_TURNS = 3;
    static final String TRIMMED = "[đã lược bớt]";

    private final AgentProperties props;

    /** @param window lịch sử gửi cho model; @param cutIndex số tin đầu bị bỏ (0 = không cắt). */
    public record Result(List<ObjectNode> window, int cutIndex) {}

    public Result apply(List<ObjectNode> history) {
        if (history.isEmpty()) return new Result(history, 0);

        int budget = props.historyCharBudget();
        int total = 0;
        int cut = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            total += history.get(i).toString().length();
            if (total > budget) {
                cut = i + 1;
                break;
            }
        }
        // Dời điểm cắt tới tin USER có text gần nhất phía sau; nếu không có thì lùi về tin USER text cuối cùng.
        int start = cut;
        while (start < history.size() && !MessageJson.isUserText(history.get(start))) start++;
        if (start >= history.size()) {
            start = cut;
            while (start > 0 && !MessageJson.isUserText(history.get(start))) start--;
        }

        List<ObjectNode> window = new ArrayList<>(history.subList(start, history.size()));

        // Vị trí bắt đầu của 3 lượt (tin USER text) gần nhất; tool_result trước đó bị rút gọn.
        int turns = 0;
        int keepFrom = 0;
        for (int i = window.size() - 1; i >= 0; i--) {
            if (MessageJson.isUserText(window.get(i)) && ++turns == KEEP_FULL_TURNS) {
                keepFrom = i;
                break;
            }
        }
        for (int i = 0; i < keepFrom; i++) {
            window.set(i, trimToolResults(window.get(i)));
        }
        return new Result(window, start);
    }

    private static ObjectNode trimToolResults(ObjectNode message) {
        if (MessageJson.blocksOfType(message, "tool_result").isEmpty()) return message;
        ObjectNode copy = message.deepCopy();
        if (copy.get("content") instanceof ArrayNode arr) {
            arr.forEach(b -> {
                if (b instanceof ObjectNode o && "tool_result".equals(MessageJson.type(o))) {
                    o.put("content", TRIMMED);
                }
            });
        }
        return copy;
    }
}
