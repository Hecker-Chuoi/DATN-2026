package com.datn.workservice.notification;

import com.datn.workservice.entity.Project;
import com.datn.workservice.enums.ProjectType;
import com.datn.workservice.repository.ProjectRepository;
import com.datn.workservice.service.OutboxRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;

/** Publisher thật với OutboxRecorder giả: đọc lại các sự kiện đã "ghi vào outbox" để kiểm tra. */
final class NotificationTestSupport {

    final OutboxRecorder outbox = mock(OutboxRecorder.class);
    final NotificationRecipientResolver resolver = mock(NotificationRecipientResolver.class);
    final ProjectRepository projectRepository = mock(ProjectRepository.class);
    final NotificationEventPublisher publisher;
    final Project project;

    NotificationTestSupport() {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        publisher = new NotificationEventPublisher(outbox, resolver, projectRepository, om);
        project = Project.builder().id(UUID.randomUUID()).workspaceId(UUID.randomUUID()).key("MOC").name("Mộc Coffee")
                .leadUserId(UUID.randomUUID()).createdBy(UUID.randomUUID()).projectType(ProjectType.SOFTWARE_SCRUM)
                .isTemplate(false).build();
        lenient().when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        // mặc định: mọi người đều là thành viên
        lenient().when(resolver.onlyMembers(any(), any())).thenAnswer(inv -> inv.getArgument(1));
    }

    /** Các payload đã ghi, theo thứ tự. */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> recorded() {
        if (mockingDetails(outbox).getInvocations().isEmpty()) return List.of();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(outbox, atLeast(1)).record(eq("NOTIFICATION"), any(), anyString(), captor.capture());
        return captor.getAllValues();
    }

    List<String> types() {
        return recorded().stream().map(p -> (String) p.get("type")).toList();
    }

    Map<String, Object> only(String type) {
        List<Map<String, Object>> list = recorded().stream().filter(p -> type.equals(p.get("type"))).toList();
        if (list.size() != 1) throw new AssertionError("Mong đợi đúng 1 sự kiện " + type + " nhưng có " + list.size() + ": " + types());
        return list.get(0);
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> recipients(Map<String, Object> payload) {
        return ((List<Map<String, Object>>) payload.get("recipients")).stream()
                .collect(Collectors.toMap(r -> (String) r.get("userId"), r -> (String) r.get("reason")));
    }

    static Set<String> recipientIds(Map<String, Object> payload) {
        return recipients(payload).keySet();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> data(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("data");
    }
}
