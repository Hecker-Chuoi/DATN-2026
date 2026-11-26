package com.datn.aiservice.agent.llm;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tin nhắn lưu và xử lý ở dạng JSON đúng định dạng Messages API của Claude:
 * {@code {"role":"user|assistant","content":[{"type":"text"|"tool_use"|"tool_result"|"thinking",...}]}}.
 * Chỉ đổi sang {@link BetaMessageParam} lúc gửi request, nên tin của model (kể cả thinking + signature)
 * được gửi lại nguyên vẹn đúng như API đã trả.
 */
public final class MessageJson {

    /** Khối ngữ cảnh hệ thống tự gắn vào tin người dùng; không hiển thị trên giao diện. */
    public static final String CONTEXT_OPEN = "<context>";

    private static final ObjectMapper M = ObjectMappers.jsonMapper();

    private MessageJson() {}

    public static ObjectMapper mapper() {
        return M;
    }

    public static ObjectNode parse(String json) {
        try {
            return (ObjectNode) M.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Tin nhắn lưu trong DB không phải JSON hợp lệ", e);
        }
    }

    /** Tin model trả về → JSON (giữ nguyên mọi block, gồm thinking + signature). */
    public static ObjectNode fromResponse(BetaMessage message) {
        return M.valueToTree(message.toParam());
    }

    public static BetaMessageParam toParam(ObjectNode message) {
        return M.convertValue(message, BetaMessageParam.class);
    }

    public static ObjectNode user(List<ObjectNode> blocks) {
        return message("user", blocks);
    }

    public static ObjectNode assistant(List<ObjectNode> blocks) {
        return message("assistant", blocks);
    }

    private static ObjectNode message(String role, List<ObjectNode> blocks) {
        ObjectNode n = M.createObjectNode();
        n.put("role", role);
        ArrayNode arr = n.putArray("content");
        blocks.forEach(arr::add);
        return n;
    }

    public static ObjectNode text(String text) {
        ObjectNode b = M.createObjectNode();
        b.put("type", "text");
        b.put("text", text);
        return b;
    }

    public static ObjectNode toolResult(String toolUseId, String content, boolean isError) {
        ObjectNode b = M.createObjectNode();
        b.put("type", "tool_result");
        b.put("tool_use_id", toolUseId);
        b.put("content", content);
        if (isError) b.put("is_error", true);
        return b;
    }

    public static String role(ObjectNode message) {
        return message.path("role").asText("");
    }

    /** Các block của tin; content dạng chuỗi được coi như một block text. */
    public static List<ObjectNode> blocks(ObjectNode message) {
        JsonNode c = message.get("content");
        List<ObjectNode> out = new ArrayList<>();
        if (c == null || c.isNull()) return out;
        if (c.isTextual()) {
            out.add(text(c.asText()));
            return out;
        }
        for (JsonNode b : c) {
            if (b instanceof ObjectNode o) out.add(o);
        }
        return out;
    }

    public static String type(ObjectNode block) {
        return block.path("type").asText("");
    }

    public static boolean isContextText(String text) {
        return text != null && text.stripLeading().startsWith(CONTEXT_OPEN);
    }

    /** Text người đọc thấy được: các block text, bỏ khối {@code <context>}. Không gồm thinking. */
    public static String visibleText(ObjectNode message) {
        StringBuilder sb = new StringBuilder();
        for (ObjectNode b : blocks(message)) {
            if (!"text".equals(type(b))) continue;
            String t = b.path("text").asText("");
            if (!isContextText(t)) sb.append(t);
        }
        return sb.toString().trim();
    }

    /** Tin USER có câu người dùng gõ (không chỉ gồm tool_result / ngữ cảnh). */
    public static boolean isUserText(ObjectNode message) {
        return "user".equals(role(message)) && !visibleText(message).isEmpty();
    }

    public static List<ObjectNode> blocksOfType(ObjectNode message, String type) {
        return blocks(message).stream().filter(b -> type.equals(type(b))).toList();
    }

    public static Map<String, Object> toolInput(ObjectNode toolUse) {
        JsonNode in = toolUse.get("input");
        if (in == null || !in.isObject()) return new LinkedHashMap<>();
        return M.convertValue(in, new TypeReference<LinkedHashMap<String, Object>>() {});
    }
}
