package com.datn.aiservice.agent.llm;

import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageJsonTest {

    /** Phản hồi API mẫu: thinking (có signature) + text + tool_use. */
    private static final String RESPONSE = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
             "content":[
               {"type":"thinking","thinking":"","signature":"sig-abc"},
               {"type":"text","text":"Để tôi tìm."},
               {"type":"tool_use","id":"toolu_1","name":"search_work_items","input":{"keyword":"login","limit":5}}
             ],
             "stop_reason":"tool_use","stop_sequence":null,
             "usage":{"input_tokens":10,"output_tokens":20}}
            """;

    @Test
    void responseIsStoredVerbatimIncludingThinkingSignature() throws Exception {
        BetaMessage res = MessageJson.mapper().readValue(RESPONSE, BetaMessage.class);

        ObjectNode stored = MessageJson.fromResponse(res);

        assertThat(MessageJson.role(stored)).isEqualTo("assistant");
        List<ObjectNode> blocks = MessageJson.blocks(stored);
        assertThat(blocks).extracting(MessageJson::type).containsExactly("thinking", "text", "tool_use");
        assertThat(blocks.get(0).path("signature").asText()).isEqualTo("sig-abc");
        assertThat(MessageJson.toolInput(blocks.get(2))).containsEntry("keyword", "login").containsEntry("limit", 5);
    }

    @Test
    void storedJsonRoundTripsThroughSdkParamUnchanged() throws Exception {
        ObjectNode stored = MessageJson.fromResponse(MessageJson.mapper().readValue(RESPONSE, BetaMessage.class));
        ObjectNode reloaded = MessageJson.parse(stored.toString());

        BetaMessageParam param = MessageJson.toParam(reloaded);
        JsonNode back = MessageJson.mapper().valueToTree(param);

        assertThat(back).isEqualTo(stored);
    }

    @Test
    void userMessageWithToolResultAndContextConvertsToParam() {
        ObjectNode user = MessageJson.user(List.of(
                MessageJson.toolResult("toolu_1", "{\"error\":\"không có quyền\"}", true),
                MessageJson.text(MessageJson.CONTEXT_OPEN + "\n- Dự án đang mở: x\n</context>"),
                MessageJson.text("Còn việc nào khác?")));

        JsonNode back = MessageJson.mapper().valueToTree(MessageJson.toParam(user));

        assertThat(back).isEqualTo(user);
        assertThat(back.get("content").get(0).get("is_error").asBoolean()).isTrue();
    }

    @Test
    void visibleTextHidesContextBlockAndThinking() {
        ObjectNode user = MessageJson.user(List.of(
                MessageJson.text(MessageJson.CONTEXT_OPEN + "\n- Thời gian: ...\n</context>"),
                MessageJson.text("Hôm nay tôi có việc gì?")));
        ObjectNode onlyResults = MessageJson.user(List.of(MessageJson.toolResult("toolu_1", "{}", false)));

        assertThat(MessageJson.visibleText(user)).isEqualTo("Hôm nay tôi có việc gì?");
        assertThat(MessageJson.isUserText(user)).isTrue();
        assertThat(MessageJson.isUserText(onlyResults)).isFalse();
    }
}
