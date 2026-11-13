package com.datn.aiservice.eval;

import com.datn.aiservice.agent.AgentEventSink;
import com.datn.aiservice.agent.AgentOrchestrator;
import com.datn.aiservice.agent.InteractionRequest;
import com.datn.aiservice.agent.store.ConversationStore;
import com.datn.aiservice.agent.tool.ToolContext;
import com.datn.aiservice.entity.AiConversation;
import com.datn.aiservice.entity.AiUsageLog;
import com.datn.aiservice.enums.InteractionType;
import com.datn.aiservice.repository.AiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.*;

/**
 * Bộ đánh giá 30 câu hỏi mẫu (M8). Chạy tay với Gemini thật trên một dự án mẫu:
 * <pre>
 *   -Dspring.profiles.active=tuananh,eval -Deval.user-id=&lt;sub&gt; -Deval.project-id=&lt;uuid&gt; -Deval.workspace-id=&lt;uuid&gt; -Deval.sample-code=MOC-1
 * </pre>
 * Ghi {@code eval-results.csv}, in tổng kết rồi thoát. Giãn cách giữa các câu để không vượt hạn mức miễn phí mỗi phút.
 */
@Slf4j
@Component
@Profile("eval")
@RequiredArgsConstructor
public class EvalRunner implements CommandLineRunner {

    private final AgentOrchestrator orchestrator;
    private final ConversationStore store;
    private final AiUsageLogRepository usageRepo;
    private final ApplicationContext context;

    @Value("${eval.user-id:}") private String userId;
    @Value("${eval.project-id:}") private String projectId;
    @Value("${eval.workspace-id:}") private String workspaceId;
    @Value("${eval.sample-code:}") private String sampleCode;
    @Value("${eval.delay-ms:12000}") private long delayMs;
    @Value("${eval.output:eval-results.csv}") private String output;

    @Override
    @SuppressWarnings("unchecked")
    public void run(String... args) throws Exception {
        if (userId.isBlank()) {
            log.error("Thiếu -Deval.user-id=<Keycloak sub của người chạy>");
            SpringApplication.exit(context, () -> 2);
            return;
        }
        List<Map<String, Object>> questions;
        try (InputStream in = new ClassPathResource("eval/questions.yaml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            questions = (List<Map<String, Object>>) root.get("questions");
        }

        UUID pj = projectId.isBlank() ? null : UUID.fromString(projectId);
        UUID ws = workspaceId.isBlank() ? null : UUID.fromString(workspaceId);
        List<Row> rows = new ArrayList<>();

        for (Map<String, Object> q : questions) {
            String id = String.valueOf(q.get("id"));
            String question = String.valueOf(q.get("question")).replace("{CODE}", sampleCode);
            boolean withProject = !"none".equalsIgnoreCase(String.valueOf(q.getOrDefault("context", "project")));
            List<String> expectedTools = (List<String>) q.getOrDefault("expectedTools", List.of());
            boolean expectAsk = Boolean.TRUE.equals(q.get("expectAskUser"));
            List<String> expectedAnswer = (List<String>) q.getOrDefault("expectedAnswerContains", List.of());

            AiConversation conv = store.create(userId, ws, withProject ? pj : null);
            ToolContext ctx = new ToolContext(userId, ws, withProject ? pj : null, null, conv.getId(), ZoneId.of("Asia/Ho_Chi_Minh"));
            CollectingSink sink = new CollectingSink();
            long start = System.currentTimeMillis();
            orchestrator.chat(ctx, question, sink);
            long ms = System.currentTimeMillis() - start;

            List<AiUsageLog> usage = usageRepo.findByConversationId(conv.getId());
            int in = usage.stream().mapToInt(AiUsageLog::getInputTokens).sum();
            int out = usage.stream().mapToInt(AiUsageLog::getOutputTokens).sum();
            String answer = sink.text.toString().toLowerCase(Locale.ROOT);

            Row r = new Row();
            r.id = id;
            r.question = question;
            r.toolsCalled = String.join("|", sink.tools);
            r.expectedTools = String.join("|", expectedTools);
            r.toolMatch = expectedTools.isEmpty() ? (sink.tools.isEmpty() ? "Y" : "N")
                    : (sink.tools.stream().anyMatch(expectedTools::contains) ? "Y" : "N");
            r.askedUser = sink.askedUser ? "Y" : "N";
            r.expectAskUser = expectAsk ? "Y" : "N";
            r.askMatch = (sink.askedUser == expectAsk) ? "Y" : "N";
            r.answerMatch = expectedAnswer.isEmpty() ? "-"
                    : (expectedAnswer.stream().anyMatch(s -> answer.contains(s.toLowerCase(Locale.ROOT))) ? "Y" : "N");
            r.status = sink.error != null ? "ERROR: " + sink.error : (sink.status == null ? "?" : sink.status);
            r.iterations = usage.size();
            r.durationMs = ms;
            r.inputTokens = in;
            r.outputTokens = out;
            rows.add(r);
            log.info("[EVAL] {} tools={} ask={} status={} {}ms", id, r.toolsCalled, r.askedUser, r.status, ms);

            Thread.sleep(delayMs);
        }

        writeCsv(rows);
        printSummary(rows);
        SpringApplication.exit(context, () -> 0);
    }

    private void writeCsv(List<Row> rows) throws Exception {
        Path p = Path.of(output);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
            w.println("id,question,toolsCalled,expectedTools,toolMatch,askedUser,expectAskUser,askMatch,answerMatch,status,iterations,durationMs,inputTokens,outputTokens");
            for (Row r : rows) {
                w.println(String.join(",", csv(r.id), csv(r.question), csv(r.toolsCalled), csv(r.expectedTools), r.toolMatch,
                        r.askedUser, r.expectAskUser, r.askMatch, r.answerMatch, csv(r.status),
                        String.valueOf(r.iterations), String.valueOf(r.durationMs), String.valueOf(r.inputTokens), String.valueOf(r.outputTokens)));
            }
        }
        log.info("Đã ghi {}", p.toAbsolutePath());
    }

    private void printSummary(List<Row> rows) {
        long n = rows.size();
        long toolOk = rows.stream().filter(r -> "Y".equals(r.toolMatch)).count();
        long askOk = rows.stream().filter(r -> "Y".equals(r.askMatch)).count();
        long errors = rows.stream().filter(r -> r.status.startsWith("ERROR")).count();
        double avgIter = rows.stream().mapToInt(r -> r.iterations).average().orElse(0);
        int maxIter = rows.stream().mapToInt(r -> r.iterations).max().orElse(0);
        double avgMs = rows.stream().mapToLong(r -> r.durationMs).average().orElse(0);
        long tokens = rows.stream().mapToLong(r -> r.inputTokens + r.outputTokens).sum();
        System.out.printf(Locale.ROOT, """

                ===== TỔNG KẾT ĐÁNH GIÁ (%d câu) =====
                Chọn đúng tool      : %d/%d (%.1f%%)
                Hỏi lại hợp lý      : %d/%d (%.1f%%)
                Lỗi                 : %d
                Số vòng gọi Gemini  : trung bình %.2f, lớn nhất %d
                Thời gian trung bình: %.0f ms
                Tổng token vào+ra   : %d
                %n""", n, toolOk, n, 100.0 * toolOk / Math.max(1, n), askOk, n, 100.0 * askOk / Math.max(1, n),
                errors, avgIter, maxIter, avgMs, tokens);
    }

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\"";
    }

    private static class Row {
        String id, question, toolsCalled, expectedTools, toolMatch, askedUser, expectAskUser, askMatch, answerMatch, status;
        int iterations, inputTokens, outputTokens;
        long durationMs;
    }

    /** Gom sự kiện thay cho SSE. Gặp thẻ duyệt thì ghi nhận và dừng ở đó (không tự duyệt). */
    static class CollectingSink implements AgentEventSink {
        final StringBuilder text = new StringBuilder();
        final List<String> tools = new ArrayList<>();
        boolean askedUser;
        String status;
        String error;

        @Override public void onTextDelta(String t) { text.append(t); }
        @Override public void onToolStarted(String id, String name, String summary) { tools.add(name); }
        @Override public void onToolFinished(String id, String name, boolean ok) { }
        @Override public void onInteractionRequired(InteractionRequest r) {
            tools.add(r.toolName());
            if (r.type() == InteractionType.ASK_USER) askedUser = true;
        }
        @Override public void onNotice(String message) { }
        @Override public void onDone(String s) { status = s; }
        @Override public void onError(String message) { error = message; }
        @Override public boolean isCancelled() { return false; }
    }
}
