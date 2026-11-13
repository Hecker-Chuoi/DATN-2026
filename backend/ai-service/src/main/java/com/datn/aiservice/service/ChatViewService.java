package com.datn.aiservice.service;

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
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/** Chuyển lịch sử Gemini Content + bản ghi tương tác thành dạng hiển thị cho frontend. */
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
            Content c = ConversationStore.toContent(m);
            List<Part> parts = c.parts().orElse(List.of());

            if (m.getRole() == MessageRole.USER) {
                // FunctionResponse → đánh dấu ok/lỗi cho các tool của tin MODEL trước đó (cùng thứ tự)
                List<FunctionResponse> frs = parts.stream().filter(p -> p.functionResponse().isPresent())
                        .map(p -> p.functionResponse().get()).toList();
                if (lastTools != null) {
                    for (int i = 0; i < frs.size() && i < lastTools.size(); i++) {
                        boolean ok = !frs.get(i).response().orElse(Map.of()).containsKey("error");
                        MessageView.ToolCallView t = lastTools.get(i);
                        lastTools.set(i, new MessageView.ToolCallView(t.id(), t.name(), t.summary(), ok));
                    }
                }
                String text = parts.stream().filter(p -> p.text().isPresent()).map(p -> p.text().get())
                        .collect(Collectors.joining()).trim();
                if (!text.isEmpty()) {
                    out.add(new MessageView(m.getId(), "user", text, List.of(), List.of(), m.getCreatedAt()));
                }
                continue;
            }

            String text = parts.stream()
                    .filter(p -> p.text().isPresent() && !p.thought().orElse(false))
                    .map(p -> p.text().get()).collect(Collectors.joining()).trim();
            List<MessageView.ToolCallView> tools = new ArrayList<>();
            int idx = 0;
            for (Part p : parts) {
                if (p.functionCall().isEmpty()) continue;
                FunctionCall fc = p.functionCall().get();
                String name = fc.name().orElse("");
                Map<String, Object> args = fc.args().orElse(Map.of());
                String summary = registry.find(name).map(t -> t.summarize(args)).orElse(name);
                tools.add(new MessageView.ToolCallView(fc.id().orElse(m.getId() + ":" + idx), name, summary, null));
                idx++;
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
