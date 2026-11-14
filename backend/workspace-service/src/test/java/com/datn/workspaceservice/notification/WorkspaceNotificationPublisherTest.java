package com.datn.workspaceservice.notification;

import com.datn.common.dto.ApiResponse;
import com.datn.workspaceservice.client.IdentityClient;
import com.datn.workspaceservice.client.dto.UserResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceNotificationPublisherTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final IdentityClient identity = mock(IdentityClient.class);
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final ObjectMapper om = new ObjectMapper();
    private final WorkspaceNotificationPublisher publisher = new WorkspaceNotificationPublisher(jdbc, identity, workspaces, om);

    private final UUID wsId = UUID.randomUUID();
    private final UUID anUsersId = UUID.randomUUID();
    private final UUID binhUsersId = UUID.randomUUID();
    private final String anSub = UUID.randomUUID().toString();
    private final String binhSub = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        when(workspaces.findById(wsId)).thenReturn(Optional.of(Workspace.builder().id(wsId).name("Mộc Group").build()));
        when(identity.getUserById(anUsersId)).thenReturn(ApiResponse.success(UserResponse.builder().id(anUsersId).keycloakId(anSub).build()));
        when(identity.getUserById(binhUsersId)).thenReturn(ApiResponse.success(UserResponse.builder().id(binhUsersId).keycloakId(binhSub).build()));
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
    void invitation_convertsUsersIdsToKeycloakSub_andWritesOutboxRow() throws Exception {
        WorkspaceInvitation inv = WorkspaceInvitation.builder().id(UUID.randomUUID()).workspaceId(wsId)
                .inviteeUserId(binhUsersId).invitedBy(anUsersId).role(WorkspaceRole.ADMIN).build();

        publisher.invitationReceived(inv, anUsersId);

        Map<String, Object> p = insertedPayload("WORKSPACE_INVITATION_RECEIVED");
        assertThat(p).containsEntry("actorId", anSub).containsEntry("workspaceId", wsId.toString())
                .containsEntry("workspaceName", "Mộc Group").containsEntry("source", "workspace-service")
                .containsEntry("entityType", "INVITATION").containsEntry("entityId", inv.getId().toString());
        assertThat((List<Map<String, Object>>) p.get("recipients"))
                .containsExactly(Map.of("userId", binhSub, "reason", "INVITEE"));
        assertThat((Map<String, Object>) p.get("data")).containsEntry("role", "ADMIN");
    }

    @Test
    void actorIsNeverARecipient() {
        publisher.roleChanged(wsId, anUsersId, WorkspaceRole.MEMBER, WorkspaceRole.ADMIN, anUsersId);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void sameRole_isIgnored() {
        publisher.roleChanged(wsId, binhUsersId, WorkspaceRole.MEMBER, WorkspaceRole.MEMBER, anUsersId);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelledInvitation_isWrittenEvenWithoutRecipients() throws Exception {
        WorkspaceInvitation inv = WorkspaceInvitation.builder().id(UUID.randomUUID()).workspaceId(wsId)
                .inviteeUserId(binhUsersId).invitedBy(anUsersId).build();
        publisher.invitationCancelled(inv, anUsersId);
        assertThat(insertedPayload("WORKSPACE_INVITATION_CANCELLED")).containsEntry("entityId", inv.getId().toString());
    }

    @Test
    void identityDown_skipsRecipient_andNeverThrows() {
        UUID unknown = UUID.randomUUID();
        when(identity.getUserById(unknown)).thenThrow(new RuntimeException("identity down"));
        publisher.memberRemoved(wsId, unknown, anUsersId);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());

        when(jdbc.update(anyString(), any(), any(), any(), any(), any())).thenThrow(new RuntimeException("db"));
        publisher.memberRemoved(wsId, binhUsersId, anUsersId); // không ném lỗi
    }

    @Test
    void unknownWorkspace_isIgnored() {
        publisher.memberRemoved(UUID.randomUUID(), binhUsersId, anUsersId);
        verify(jdbc, never()).update(eq("x"), any(), any(), any(), any(), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }
}
