package com.datn.aiservice.agent.tool;

import com.anthropic.models.beta.messages.BetaTool;
import com.datn.aiservice.agent.llm.MessageJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    private static AgentTool tool(String name, Map<String, Object> params) {
        return new AgentTool() {
            public String name() { return name; }
            public String description() { return "mô tả " + name; }
            public Map<String, Object> parameters() { return params; }
            public ToolKind kind() { return ToolKind.READ; }
            public ToolResult execute(Map<String, Object> args, ToolContext ctx) { return ToolResult.ok("{}"); }
        };
    }

    @Test
    void schemaKeepsPropertiesRequiredAndEnums() {
        Map<String, Object> params = Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "description", "Mã công việc"),
                        "priority", Map.of("type", "string", "enum", List.of("LOW", "HIGH"))),
                "required", List.of("code"));

        BetaTool t = ToolRegistry.toClaudeTool(tool("get_work_item", params));
        JsonNode json = MessageJson.mapper().valueToTree(t);

        assertThat(json.get("name").asText()).isEqualTo("get_work_item");
        assertThat(json.get("description").asText()).isEqualTo("mô tả get_work_item");
        JsonNode schema = json.get("input_schema");
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties").get("code").get("description").asText()).isEqualTo("Mã công việc");
        assertThat(schema.get("properties").get("priority").get("enum")).hasSize(2);
        assertThat(schema.get("required").get(0).asText()).isEqualTo("code");
    }

    @Test
    void toolsAreSortedByNameForStablePrefix() {
        Map<String, Object> empty = Map.of("type", "object", "properties", Map.of());
        List<BetaTool> tools = ToolRegistry.toClaudeTools(List.of(tool("zeta", empty), tool("alpha", empty)));
        assertThat(tools).extracting(BetaTool::name).containsExactly("alpha", "zeta");
    }
}
