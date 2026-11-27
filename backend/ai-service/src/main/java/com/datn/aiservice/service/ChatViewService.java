package com.datn.aiservice.service;

import com.datn.aiservice.agent.llm.MessageJson;
import com.datn.aiservice.agent.store.ConversationStore;
import com.datn.aiservice.agent.tool.ToolRegistry;
import com.datn.aiservice.dto.MessageView;
import com.datn.aiservice.entity.AiMessage;
import com.datn.aiservice.entity.AiPendingInteraction;
import com.datn.aiservice.enums.InteractionType;
import com.datn.aiservice.enums.MessageRole;
import com.datn.aiservice.repository.AiPendingInteractionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** Chuyển lịch sử (JSON Messages API) + bản ghi tương tác thành dạng hiển thị cho frontend. */
@Service
@RequiredArgsConstructor
public class ChatViewService {

    private final ConversationStore store;
    private final AiPendingInteractionRepository pendingRepo;
    private final ToolRegistry registry;
    private final ObjectMapper mapper;

    public List<MessageView> messages(UUID conversationId) {
        List<AiMessage> msgs = store.messages(conversationId);
        Map<UUID, List<AiPendingInteraction>> rowsByMsg = pendingRepo.findByConversationIdOrderByCallIndexAsc(conversationId)
                .stream().collect(Collectors.groupingBy(AiPendingInteraction::getMessageId));

        List<MessageView> out = new ArrayList<>();
        List<MessageView.ToolCallView> lastTools = null;

        for (AiMessage m : msgs) {
            ObjectNode msg = ConversationStore.toNode(m);

            if (m.getRole() == MessageRole.USER) {
                // tool_result → đánh dấu ok/lỗi cho tool cùng id của tin MODEL trước đó
                if (lastTools != null) {
                    for (ObjectNode tr : MessageJson.blocksOfType(msg, "tool_result")) {
                        String id = tr.path("tool_use_id").asText();
                        boolean ok = !tr.path("is_error").asBoolean(false);
                        for (int i = 0; i < lastTools.size(); i++) {
                            MessageView.ToolCallView t = lastTools.get(i);
                            if (t.id().equals(id)) {
                                lastTools.set(i, new MessageView.ToolCallView(t.id(), t.name(), t.summary(), ok));
                            }
                        }
                    }
                }
                String text = MessageJson.visibleText(msg);
                if (!text.isEmpty()) {
                    out.add(new MessageView(m.getId(), "user", text, List.of(), List.of(), m.getCreatedAt()));
                }
                continue;
            }

            String text = MessageJson.visibleText(msg);
            List<MessageView.ToolCallView> tools = new ArrayList<>();
            for (ObjectNode tu : MessageJson.blocksOfType(msg, "tool_use")) {
                String name = tu.path("name").asText("");
                Map<String, Object> args = MessageJson.toolInput(tu);
                String summary = registry.find(name).map(t -> t.summarize(args)).orElse(name);
                tools.add(new MessageView.ToolCallView(tu.path("id").asText(), name, summary, null));
            }

            List<MessageView.InteractionView> interactions = new ArrayList<>();
            for (AiPendingInteraction r : rowsByMsg.getOrDefault(m.getId(), List.of())) {
                if (r.getType() == InteractionType.READ_RESULT) {
                    if (r.getCallIndex() < tools.size() && r.getResponse() != null) {
                        MessageView.ToolCallView t = tools.get(r.getCallIndex());
                        tools.set(r.getCallIndex(), new MessageView.ToolCallView(t.id(), t.name(), t.summary(),
                                !readMap(r.getResponse()).containsKey("error")));
                    }
                    continue;
                }
                Map<String, Object> payload = readMap(r.getPayload());
                String summary = r.getType() == InteractionType.ASK_USER
                        ? String.valueOf(payload.getOrDefault("question", r.getToolName()))
                        : String.valueOf(payload.getOrDefault("summary", r.getToolName()));
                interactions.add(new MessageView.InteractionView(r.getId(), r.getType().name(), r.getToolName(),
                        summary, payload, r.getStatus().name(), r.getResponse() == null ? null : readMap(r.getResponse())));
            }

            out.add(new MessageView(m.getId(), "model", text, tools, interactions, m.getCreatedAt()));
            lastTools = tools;
        }
        return out;
    }

    private Map<String, Object> readMap(String json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
