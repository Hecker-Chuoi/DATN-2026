package com.datn.aiservice.controller;

import com.datn.aiservice.agent.AgentOrchestrator;
import com.datn.aiservice.agent.store.ConversationStore;
import com.datn.aiservice.agent.tool.ToolContext;
import com.datn.aiservice.dto.*;
import com.datn.aiservice.entity.AiConversation;
import com.datn.aiservice.enums.ConversationStatus;
import com.datn.aiservice.enums.InteractionStatus;
import com.datn.aiservice.repository.AiPendingInteractionRepository;
import com.datn.aiservice.service.ChatViewService;
import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.ConflictException;
import com.datn.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/** API chat của trợ lý AI. Gửi tin / trả lời tương tác trả về SSE (text/event-stream), còn lại bọc ApiResponse. */
@RestController
@RequestMapping("/api/v1/ai/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final AgentOrchestrator orchestrator;
    private final ConversationStore store;
    private final ChatViewService viewService;
    private final AiPendingInteractionRepository pendingRepo;
    private final ExecutorService agentExecutor;

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationView>> create(@AuthenticationPrincipal Jwt jwt,
                                                                @RequestBody(required = false) CreateConversationRequest req) {
        UUID ws = req == null ? null : req.workspaceId();
        UUID pj = req == null ? null : req.projectId();
        AiConversation c = store.create(jwt.getSubject(), ws, pj);
        return ResponseEntity.ok(ApiResponse.success(ConversationView.of(c), "Đã tạo hội thoại"));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationView>>> list(@AuthenticationPrincipal Jwt jwt,
                                                                    @RequestParam(required = false) UUID projectId) {
        List<ConversationView> out = store.listOwned(jwt.getSubject(), projectId).stream().map(ConversationView::of).toList();
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<List<MessageView>>> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        store.getOwned(id, jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(viewService.messages(id)));
    }

    @PatchMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<ConversationView>> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                                @Valid @RequestBody UpdateConversationRequest req) {
        AiConversation c = store.getOwned(id, jwt.getSubject());
        if (req.title() != null && !req.title().isBlank()) c.setTitle(req.title().trim());
        if (Boolean.TRUE.equals(req.archived())) c.setStatus(ConversationStatus.ARCHIVED);
        else if (Boolean.FALSE.equals(req.archived()) && c.getStatus() == ConversationStatus.ARCHIVED) c.setStatus(ConversationStatus.ACTIVE);
        return ResponseEntity.ok(ApiResponse.success(ConversationView.of(store.save(c)), "Đã cập nhật hội thoại"));
    }

    /** Gửi tin → SSE. Tương tác đang chờ (nếu có) sẽ bị bỏ qua. */
    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody SendMessageRequest req) {
        AiConversation c = store.getOwned(id, jwt.getSubject());
        ToolContext ctx = toContext(jwt, c, req.context());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseAgentEventSink sink = new SseAgentEventSink(emitter);
        agentExecutor.submit(() -> orchestrator.chat(ctx, req.text().trim(), sink));
        return emitter;
    }

    /** Duyệt / từ chối thẻ hành động hoặc trả lời câu hỏi ngược → SSE tiếp tục vòng lặp. */
    @PostMapping(value = "/conversations/{id}/interactions/{interactionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID interactionId,
                             @RequestBody InteractionAnswerRequest req) {
        AiConversation c = store.getOwned(id, jwt.getSubject());
        var pi = pendingRepo.findByIdAndConversationId(interactionId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tương tác"));
        if (pi.getStatus() != InteractionStatus.PENDING) {
            throw new ConflictException("Tương tác này đã được xử lý");
        }
        ToolContext ctx = toContext(jwt, c, req.context());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseAgentEventSink sink = new SseAgentEventSink(emitter);
        agentExecutor.submit(() -> orchestrator.resume(ctx, interactionId, req, sink));
        return emitter;
    }

    private static ToolContext toContext(Jwt jwt, AiConversation c, ChatContextRequest r) {
        UUID ws = r != null && r.workspaceId() != null ? r.workspaceId() : c.getWorkspaceId();
        UUID pj = r != null && r.projectId() != null ? r.projectId() : c.getProjectId();
        UUID wi = r == null ? null : r.workItemId();
        return new ToolContext(jwt.getSubject(), ws, pj, wi, c.getId(),
                ToolContext.zoneOrDefault(r == null ? null : r.timezone()));
    }
}
