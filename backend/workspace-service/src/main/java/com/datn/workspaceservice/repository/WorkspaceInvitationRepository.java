package com.datn.workspaceservice.repository;

import com.datn.workspaceservice.entity.WorkspaceInvitation;
import com.datn.workspaceservice.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    List<WorkspaceInvitation> findByInviteeUserIdAndStatusOrderByCreatedAtDesc(UUID inviteeUserId, InvitationStatus status);

    List<WorkspaceInvitation> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, InvitationStatus status);

    boolean existsByWorkspaceIdAndInviteeUserIdAndStatus(UUID workspaceId, UUID inviteeUserId, InvitationStatus status);
}
