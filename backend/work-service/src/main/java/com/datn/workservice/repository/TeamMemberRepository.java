package com.datn.workservice.repository;

import com.datn.workservice.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMember.TeamMemberId> {
    List<TeamMember> findByIdTeamId(UUID teamId);
    List<TeamMember> findByIdUserId(UUID userId);
}
