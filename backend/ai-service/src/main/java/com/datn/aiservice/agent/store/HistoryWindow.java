package com.datn.aiservice.agent.store;

import com.datn.aiservice.config.AgentProperties;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cắt lịch sử theo ngân sách ký tự: cửa sổ luôn bắt đầu ở một tin USER có text (không tách cặp
 * functionCall ↔ functionResponse). FunctionResponse cũ hơn 3 lượt gần nhất được thay bằng "[đã lược bớt]".
 */
@Component
@RequiredArgsConstructor
public class HistoryWindow {

    private static final int KEEP_FULL_TURNS = 3;

    private final AgentProperties props;

    /** @param window lịch sử gửi cho Gemini; @param cutIndex số tin đầu bị bỏ (0 = không cắt). */
    public record Result(List<Content> window, int cutIndex) {}

    public Result apply(List<Content> history) {
        if (history.isEmpty()) return new Result(history, 0);

        int budget = props.historyCharBudget();
        int total = 0;
        int cut = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            total += history.get(i).toJson().length();
            if (total > budget) {
                cut = i + 1;
                break;
            }
        }
        // Dời điểm cắt tới tin USER có text gần nhất phía sau; nếu không có thì lùi về tin USER text cuối cùng.
        int start = cut;
        while (start < history.size() && !isUserText(history.get(start))) start++;
        if (start >= history.size()) {
            start = cut;
            while (start > 0 && !isUserText(history.get(start))) start--;
        }

        List<Content> window = new ArrayList<>(history.subList(start, history.size()));

        // Vị trí bắt đầu của 3 lượt (tin USER text) gần nhất; functionResponse trước đó bị rút gọn.
        int turns = 0;
        int keepFrom = 0;
        for (int i = window.size() - 1; i >= 0; i--) {
            if (isUserText(window.get(i)) && ++turns == KEEP_FULL_TURNS) {
                keepFrom = i;
                break;
            }
        }
        for (int i = 0; i < keepFrom; i++) {
            window.set(i, trimFunctionResponses(window.get(i)));
        }
        return new Result(window, start);
    }

    public static boolean isUserText(Content c) {
        return "user".equals(c.role().orElse("")) && c.parts().orElse(List.of()).stream()
                .anyMatch(p -> p.text().isPresent() && !p.text().get().isBlank());
    }

    private static Content trimFunctionResponses(Content c) {
        List<Part> parts = c.parts().orElse(List.of());
        if (parts.stream().noneMatch(p -> p.functionResponse().isPresent())) return c;
        List<Part> out = new ArrayList<>();
        for (Part p : parts) {
            if (p.functionResponse().isEmpty()) {
                out.add(p);
                continue;
            }
            FunctionResponse fr = p.functionResponse().get();
            FunctionResponse.Builder b = FunctionResponse.builder()
                    .name(fr.name().orElse(""))
                    .response(Map.of("result", "[đã lược bớt]"));
            fr.id().ifPresent(b::id);
            out.add(Part.builder().functionResponse(b.build()).build());
        }
        return Content.builder().role(c.role().orElse("user")).parts(out).build();
    }
}
