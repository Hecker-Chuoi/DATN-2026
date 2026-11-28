package com.datn.aiservice.agent.tool;

/**
 * Kết quả một tool. {@code content} là text/JSON gửi lại cho Gemini;
 * {@code notice} (tùy chọn) là câu hiện cho người dùng (vd. "Đã ghi nhớ: …").
 */
public record ToolResult(String content, boolean error, String notice) {

    public static ToolResult ok(String content) {
        return new ToolResult(content, false, null);
    }

    public static ToolResult ok(String content, String notice) {
        return new ToolResult(content, false, notice);
    }

    public static ToolResult error(String message) {
        return new ToolResult(message, true, null);
    }

    public ToolResult truncated(int max) {
        if (content == null || content.length() <= max) return this;
        int cut = content.length() - max;
        return new ToolResult(content.substring(0, max) + "\n[đã cắt bớt " + cut + " ký tự]", error, notice);
    }
}
