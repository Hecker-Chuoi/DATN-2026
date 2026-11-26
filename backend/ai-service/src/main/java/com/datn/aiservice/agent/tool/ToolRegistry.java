package com.datn.aiservice.agent.tool;

import com.datn.aiservice.agent.tool.remote.RemoteToolLoader;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/** Gom tool Java (bean {@link AgentTool}) và tool từ xa (agent-tools.yml); báo lỗi khi trùng tên. */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> builtin, RemoteToolLoader loader) {
        List<AgentTool> all = new ArrayList<>(builtin);
        all.addAll(loader.load());
        for (AgentTool t : all) {
            if (tools.putIfAbsent(t.name(), t) != null) {
                throw new IllegalStateException("Trùng tên tool: " + t.name());
            }
        }
        log.info("Đã đăng ký {} tool: {}", tools.size(), tools.keySet());
    }

    public List<AgentTool> availableFor(ToolContext ctx) {
        return tools.values().stream().filter(t -> t.isAvailable(ctx)).toList();
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }

    private static final Set<String> SCHEMA_CORE_KEYS = Set.of("type", "properties", "required");

    /**
     * Tool → định nghĩa tool của Claude. Sắp theo tên để mảng {@code tools} giữ nguyên giữa các vòng
     * (cache và thinking cũ phụ thuộc vào việc system + tools không đổi).
     */
    public static List<BetaTool> toClaudeTools(List<AgentTool> tools) {
        return tools.stream()
                .sorted(Comparator.comparing(AgentTool::name))
                .map(ToolRegistry::toClaudeTool)
                .toList();
    }

    static BetaTool toClaudeTool(AgentTool t) {
        Map<String, Object> schema = t.parameters() == null ? Map.of() : t.parameters();
        BetaTool.InputSchema.Properties.Builder props = BetaTool.InputSchema.Properties.builder();
        if (schema.get("properties") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> props.putAdditionalProperty(String.valueOf(k), JsonValue.from(v)));
        }
        BetaTool.InputSchema.Builder input = BetaTool.InputSchema.builder().properties(props.build());
        if (schema.get("required") instanceof List<?> req && !req.isEmpty()) {
            input.required(req.stream().map(String::valueOf).toList());
        }
        schema.forEach((k, v) -> {
            if (!SCHEMA_CORE_KEYS.contains(k)) input.putAdditionalProperty(k, JsonValue.from(v));
        });
        return BetaTool.builder()
                .name(t.name())
                .description(t.description())
                .inputSchema(input.build())
                .build();
    }
}
