package com.datn.workservice.specification;

import com.datn.workservice.entity.Project;
import com.datn.workservice.entity.Team;
import com.datn.workservice.entity.TeamMember;
import com.datn.workservice.enums.ProjectStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class ProjectSpecification extends BaseSpecification {

    public static Specification<Project> search(String keyword) {
        return searchByKeyword(keyword, "name", "key", "description");
    }

    public static Specification<Project> hasStatus(ProjectStatus status) {
        return hasFieldEqual("status", status);
    }

    public static Specification<Project> hasTeamId(UUID teamId) {
        return hasFieldEqual("teamId", teamId);
    }

    public static Specification<Project> isTemplate(Boolean isTemplate) {
        return hasFieldEqual("isTemplate", isTemplate);
    }

    public static Specification<Project> isNotTemplate() {
        return (root, query, cb) -> cb.equal(root.get("isTemplate"), false);
    }

    public static Specification<Project> isAccessibleByUser(UUID userId) {
        return (root, query, cb) -> {
            if (userId == null) return cb.disjunction();

            // 1. Dự án do User tự tạo (Creator)
            Predicate isCreator = cb.equal(root.get("createdBy"), userId);

            // 2. Dự án thuộc Team mà User là thành viên
            Subquery<UUID> memberTeamSubquery = query.subquery(UUID.class);
            Root<TeamMember> teamMemberRoot = memberTeamSubquery.from(TeamMember.class);
            memberTeamSubquery.select(teamMemberRoot.get("id").get("teamId"))
                    .where(cb.equal(teamMemberRoot.get("id").get("userId"), userId));

            Predicate isTeamMember = cb.and(
                    cb.isNotNull(root.get("teamId")),
                    root.get("teamId").in(memberTeamSubquery)
            );

            // 3. Dự án thuộc Team do User làm chủ sở hữu (Owner/Leader)
            Subquery<UUID> ownerTeamSubquery = query.subquery(UUID.class);
            Root<Team> teamRoot = ownerTeamSubquery.from(Team.class);
            ownerTeamSubquery.select(teamRoot.get("id"))
                    .where(cb.equal(teamRoot.get("ownerId"), userId));

            Predicate isTeamOwner = cb.and(
                    cb.isNotNull(root.get("teamId")),
                    root.get("teamId").in(ownerTeamSubquery)
            );

            return cb.or(isCreator, isTeamMember, isTeamOwner);
        };
    }
}
