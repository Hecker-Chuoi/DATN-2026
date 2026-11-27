package com.datn.aiservice.agent.tool.builtin;

import com.datn.aiservice.agent.tool.AgentTool;
import com.datn.aiservice.agent.tool.ToolContext;
import com.datn.aiservice.agent.tool.ToolKind;
import com.datn.aiservice.agent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Câu hỏi ngược: dừng vòng lặp, frontend hiện chip lựa chọn. Không có execute. */
@Component
public class AskUserTool implements AgentTool {

    @Override public String name() { return "ask_user"; }
    @Override public ToolKind kind() { return ToolKind.INTERRUPT; }

    @Override
    public String description() {
        return "Hỏi lại người dùng khi thiếu thông tin bắt buộc hoặc có nhiều kết quả khớp (nhiều người cùng tên, "
                + "nhiều công việc giống nhau). Đưa 2–5 lựa chọn rõ ràng. Mỗi lượt chỉ hỏi một câu. "
                + "Kết quả trả về gồm selectedValues (các value đã chọn) và freeText (nếu người dùng gõ thêm).";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of("type", "string", "description", "Câu hỏi ngắn gọn"),
                        "options", Map.of(
                                "type", "array",
                                "description", "2–5 lựa chọn",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "value", Map.of("type", "string", "description", "Giá trị máy đọc (mã công việc, tên người…)"),
                                                "label", Map.of("type", "string", "description", "Nhãn hiển thị"),
                                                "description", Map.of("type", "string", "description", "Mô tả thêm (tùy chọn)")),
                                        "required", List.of("value", "label"))),
                        "multi_select", Map.of("type", "boolean", "description", "Cho chọn nhiều hay không"),
                        "allow_free_text", Map.of("type", "boolean", "description", "Cho phép gõ câu trả lời tự do")),
                "required", List.of("question", "options"));
    }

    @Override
    public String summarize(Map<String, Object> args) {
        return String.valueOf(args.getOrDefault("question", "Cần bạn chọn"));
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        throw new UnsupportedOperationException("ask_user không thực thi trực tiếp");
    }
}
