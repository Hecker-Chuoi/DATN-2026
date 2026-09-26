package com.datn.workservice.repository;

import com.datn.workservice.entity.Invitation;
import com.datn.workservice.enums.InvitationStatus;
import com.datn.workservice.enums.InvitationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    List<Invitation> findByInviteeUserIdAndStatusOrderByCreatedAtDesc(UUID inviteeUserId, InvitationStatus status);
    List<Invitation> findByTypeAndTargetIdAndStatusOrderByCreatedAtDesc(InvitationType type, UUID targetId, InvitationStatus status);
    boolean existsByTypeAndTargetIdAndInviteeUserIdAndStatus(InvitationType type, UUID targetId, UUID inviteeUserId, InvitationStatus status);
}
