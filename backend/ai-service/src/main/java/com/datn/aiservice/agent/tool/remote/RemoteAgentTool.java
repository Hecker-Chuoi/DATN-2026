package com.datn.aiservice.agent.tool.remote;

import com.datn.aiservice.agent.tool.AgentTool;
import com.datn.aiservice.agent.tool.ToolContext;
import com.datn.aiservice.agent.tool.ToolKind;
import com.datn.aiservice.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Một lớp chung cho mọi tool của work-service: POST {work-service-url}/internal/ai-tools/{path}
 * kèm X-API-Key + X-User-Id, body = input của tool + projectId/workItemId từ ngữ cảnh.
 */
@Slf4j
public class RemoteAgentTool implements AgentTool {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final String name;
    private final ToolKind kind;
    private final String path;
    private final boolean requiresProject;
    private final String description;
    private final String summaryTemplate;
    private final Map<String, Object> parameters;
    private final RestClient rest;
    private final String apiKey;
    private final ObjectMapper mapper;

    public RemoteAgentTool(String name, ToolKind kind, String path, boolean requiresProject, String description,
                           String summaryTemplate, Map<String, Object> parameters,
                           RestClient rest, String apiKey, ObjectMapper mapper) {
        this.name = name;
        this.kind = kind;
        this.path = path;
        this.requiresProject = requiresProject;
        this.description = description;
        this.summaryTemplate = summaryTemplate;
        this.parameters = parameters;
        this.rest = rest;
        this.apiKey = apiKey;
        this.mapper = mapper;
    }

    @Override public String name() { return name; }
    @Override public String description() { return description; }
    @Override public Map<String, Object> parameters() { return parameters; }
    @Override public ToolKind kind() { return kind; }

    @Override
    public boolean isAvailable(ToolContext ctx) {
        return !requiresProject || ctx.projectId() != null;
    }

    /** "{field}" → giá trị tham số; "{count}" → số phần tử của mảng đầu tiên trong args. */
    @Override
    public String summarize(Map<String, Object> args) {
        if (summaryTemplate == null || summaryTemplate.isBlank()) return name;
        Matcher m = PLACEHOLDER.matcher(summaryTemplate);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object v = args.get(key);
            if (v == null && "count".equals(key)) {
                v = args.values().stream().filter(List.class::isInstance).map(o -> ((List<?>) o).size()).findFirst().orElse(null);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        if (apiKey == null || apiKey.isBlank()) {
            return ToolResult.error("ai-service chưa cấu hình AI_TOOLS_API_KEY nên không gọi được work-service");
        }
        Map<String, Object> body = new LinkedHashMap<>(args);
        body.put("projectId", ctx.projectId());
        body.put("workItemId", ctx.workItemId());
        try {
            String json = rest.post()
                    .uri("/internal/ai-tools/" + path)
                    .header("X-API-Key", apiKey)
                    .header("X-User-Id", ctx.userId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = json == null ? null : mapper.readTree(json);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || data.isNull()) {
                return ToolResult.ok(root != null && root.hasNonNull("message") ? root.get("message").asText() : "OK");
            }
            return ToolResult.ok(mapper.writeValueAsString(data));
        } catch (RestClientResponseException e) {
            String msg = extractMessage(e.getResponseBodyAsString());
            return switch (e.getStatusCode().value()) {
                case 401 -> ToolResult.error("work-service từ chối ai-service (sai AI_TOOLS_API_KEY)");
                case 403 -> ToolResult.error("Bạn không có quyền thực hiện thao tác này" + (msg.isBlank() ? "" : ": " + msg));
                case 404 -> ToolResult.error("Không tìm thấy" + (msg.isBlank() ? "" : ": " + msg));
                default -> ToolResult.error(msg.isBlank() ? "work-service trả lỗi " + e.getStatusCode().value() : msg);
            };
        } catch (ResourceAccessException e) {
            log.warn("Không kết nối được work-service: {}", e.getMessage());
            return ToolResult.error("Không kết nối được work-service, thử lại sau");
        } catch (Exception e) {
            log.warn("Tool {} lỗi: {}", name, e.toString());
            return ToolResult.error("Lỗi gọi công cụ: " + e.getMessage());
        }
    }

    private String extractMessage(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode n = mapper.readTree(body);
            return n.hasNonNull("message") ? n.get("message").asText() : "";
        } catch (Exception e) {
            return body.length() > 300 ? body.substring(0, 300) : body;
        }
    }
}
