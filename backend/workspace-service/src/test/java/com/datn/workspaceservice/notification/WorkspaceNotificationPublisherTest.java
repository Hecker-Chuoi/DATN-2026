package com.datn.workspaceservice.notification;

import com.datn.workspaceservice.entity.Workspace;
import com.datn.workspaceservice.entity.WorkspaceInvitation;
import com.datn.workspaceservice.enums.WorkspaceRole;
import com.datn.workspaceservice.repository.WorkspaceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** User id của workspace-service đã là Keycloak sub: ghi thẳng vào sự kiện, không gọi identity-service. */
class WorkspaceNotificationPublisherTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final ObjectMapper om = new ObjectMapper();
    private final WorkspaceNotificationPublisher publisher = new WorkspaceNotificationPublisher(jdbc, workspaces, om);

    private final UUID wsId = UUID.randomUUID();
    private final UUID anSub = UUID.randomUUID();
    private final UUID binhSub = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(workspaces.findById(wsId)).thenReturn(Optional.of(Workspace.builder().id(wsId).name("Mộc Group").build()));
    }

    private Map<String, Object> insertedPayload(String expectedType) throws Exception {
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        List<Object> v = args.getAllValues();
        assertThat(v.get(1)).isEqualTo("NOTIFICATION");
        assertThat(v.get(3)).isEqualTo(expectedType);
        return om.readValue((String) v.get(4), new TypeReference<>() {});
    }

    @Test
    @SuppressWarnings("unchecked")
    void invitation_writesOutboxRowWithKeycloakSubs() throws Exception {
        WorkspaceInvitation inv = WorkspaceInvitation.builder().id(UUID.randomUUID()).workspaceId(wsId)
                .inviteeUserId(binhSub).invitedBy(anSub).role(WorkspaceRole.ADMIN).build();

        publisher.invitationReceived(inv, anSub);

        Map<String, Object> p = insertedPayload("WORKSPACE_INVITATION_RECEIVED");
        assertThat(p).containsEntry("actorId", anSub.toString()).containsEntry("workspaceId", wsId.toString())
                .containsEntry("workspaceName", "Mộc Group").containsEntry("source", "workspace-service")
                .containsEntry("entityType", "INVITATION").containsEntry("entityId", inv.getId().toString());
        assertThat((List<Map<String, Object>>) p.get("recipients"))
                .containsExactly(Map.of("userId", binhSub.toString(), "reason", "INVITEE"));
        assertThat((Map<String, Object>) p.get("data")).containsEntry("role", "ADMIN");
    }

    @Test
    void actorIsNeverARecipient() {
        publisher.roleChanged(wsId, anSub, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, anSub);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void sameRole_isIgnored() {
        publisher.roleChanged(wsId, binhSub, WorkspaceRole.MEMBER, WorkspaceRole.MEMBER, anSub);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelledInvitation_isWrittenEvenWithoutRecipients() throws Exception {
        WorkspaceInvitation inv = WorkspaceInvitation.builder().id(UUID.randomUUID()).workspaceId(wsId)
                .inviteeUserId(binhSub).invitedBy(anSub).build();
        publisher.invitationCancelled(inv, anSub);
        assertThat(insertedPayload("WORKSPACE_INVITATION_CANCELLED")).containsEntry("entityId", inv.getId().toString());
    }

    @Test
    void dbError_neverThrows() {
        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("db"));
        publisher.memberRemoved(wsId, binhSub, anSub); // không ném lỗi
    }

    @Test
    void unknownWorkspace_isIgnored() {
        publisher.memberRemoved(UUID.randomUUID(), binhSub, anSub);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }
}
