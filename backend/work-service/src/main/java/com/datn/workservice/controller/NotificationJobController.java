package com.datn.workservice.controller;

import com.datn.common.dto.ApiResponse;
import com.datn.common.exception.BadRequestException;
import com.datn.workservice.notification.NotificationJobService;
import com.datn.workservice.notification.NotificationScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chạy ngay các job thông báo (để demo khi bảo vệ, không phải chờ tới 08:00). Chỉ Global ADMIN.
 * Chạy lại nhiều lần không sinh thông báo trùng (dedupKey).
 *
 * POST /api/v1/work/admin/notification-jobs/{job}/run[?projectId=…]
 * job = all | due-reminders | sprint-deadlines | completion-reminders | project-digest;
 * projectId (tuỳ chọn) giới hạn trong một dự án.
 */
@RestController
@RequestMapping("/api/v1/work/admin/notification-jobs")
@RequiredArgsConstructor
public class NotificationJobController {

    private final NotificationJobService jobs;

    @PostMapping("/{job}/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> run(
            @PathVariable("job") String job,
            @RequestParam(name = "projectId", required = false) UUID projectId) {
        LocalDate today = LocalDate.now(NotificationScheduler.ZONE);
        LocalDateTime now = LocalDateTime.now(NotificationScheduler.ZONE);
        Map<String, Integer> result = new LinkedHashMap<>();
        switch (job) {
            case "all" -> result.putAll(jobs.runAll(today, now, projectId));
            case "due-reminders" -> result.put(job, jobs.runDueDateReminders(today, projectId));
            case "sprint-deadlines" -> result.put(job, jobs.runSprintDeadlines(today, projectId));
            case "completion-reminders" -> result.put(job, jobs.runCompletionReminders(now, projectId));
            case "project-digest" -> result.put(job, jobs.runProjectDigest(today, projectId));
            default -> throw new BadRequestException(
                    "Job không hợp lệ: " + job + " (all | due-reminders | sprint-deadlines | completion-reminders | project-digest)");
        }
        return ResponseEntity.ok(ApiResponse.success(result, "Đã chạy job thông báo"));
    }
}
