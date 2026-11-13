package com.datn.aiservice.agent.tool;

import com.datn.aiservice.agent.tool.remote.RemoteToolLoader;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Tool;
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

    public static Tool toGeminiTool(List<AgentTool> tools) {
        List<FunctionDeclaration> decls = tools.stream()
                .map(t -> FunctionDeclaration.builder()
                        .name(t.name())
                        .description(t.description())
                        .parametersJsonSchema(t.parameters())
                        .build())
                .toList();
        return Tool.builder().functionDeclarations(decls).build();
    }
}
