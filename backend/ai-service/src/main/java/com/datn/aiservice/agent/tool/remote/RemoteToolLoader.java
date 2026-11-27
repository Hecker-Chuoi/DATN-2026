package com.datn.aiservice.agent.tool.remote;

import com.datn.aiservice.agent.tool.AgentTool;
import com.datn.aiservice.agent.tool.ToolKind;
import com.datn.aiservice.config.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Đọc {@code agent-tools.yml} lúc khởi động và tạo một {@link RemoteAgentTool} cho mỗi mục. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteToolLoader {

    private final AgentProperties props;
    private final ObjectMapper mapper;

    @SuppressWarnings("unchecked")
    public List<AgentTool> load() {
        List<AgentTool> out = new ArrayList<>();
        ClassPathResource res = new ClassPathResource("agent-tools.yml");
        if (!res.exists()) {
            log.warn("Không có agent-tools.yml – không có tool nào của work-service");
            return out;
        }
        if (props.toolsApiKey() == null || props.toolsApiKey().isBlank()) {
            log.warn("AI_TOOLS_API_KEY chưa cấu hình – tool work-service sẽ báo lỗi khi gọi");
        }
        RestClient rest = RestClient.builder().baseUrl(props.workServiceUrl()).build();
        try (InputStream in = res.getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            for (Map<String, Object> t : (List<Map<String, Object>>) root.getOrDefault("tools", List.of())) {
                out.add(new RemoteAgentTool(
                        (String) t.get("name"),
                        ToolKind.valueOf(String.valueOf(t.getOrDefault("kind", "READ"))),
                        (String) t.get("path"),
                        Boolean.TRUE.equals(t.get("requiresProject")),
                        String.valueOf(t.get("description")).trim(),
                        (String) t.get("summary"),
                        (Map<String, Object>) t.getOrDefault("parameters", Map.of("type", "object", "properties", Map.of())),
                        rest, props.toolsApiKey(), mapper));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được agent-tools.yml", e);
        }
        return out;
    }
}
