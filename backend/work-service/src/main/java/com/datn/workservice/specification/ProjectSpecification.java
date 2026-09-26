package com.datn.workservice.specification;

import com.datn.workservice.entity.Project;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class ProjectSpecification extends BaseSpecification {

    public static Specification<Project> withFilters(
            UUID workspaceId,
            UUID teamId,
            String search,
            Boolean isTemplate,
            boolean includeDeleted
    ) {
        Specification<Project> spec = (root, query, cb) -> cb.conjunction();

        if (workspaceId != null) {
            spec = spec.and(hasFieldEqual("workspaceId", workspaceId));
        }

        if (teamId != null) {
            spec = spec.and(hasFieldEqual("teamId", teamId));
        }

        if (isTemplate != null) {
            spec = spec.and(hasFieldEqual("isTemplate", isTemplate));
        }

        if (search != null && !search.isBlank()) {
            spec = spec.and(searchByKeyword(search, "name", "key", "description"));
        }

        if (!includeDeleted) {
            spec = spec.and((root, query, cb) -> cb.isNull(root.get("deletedAt")));
        }

        return spec;
    }

    /**
     * Chỉ các dự án mà user có quyền truy cập: là thành viên (role_assignments),
     * là Project Lead hoặc là người tạo.
     */
    public static Specification<Project> accessibleBy(UUID userId, Collection<UUID> memberProjectIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("leadUserId"), userId));
            predicates.add(cb.equal(root.get("createdBy"), userId));
            if (memberProjectIds != null && !memberProjectIds.isEmpty()) {
                predicates.add(root.get("id").in(memberProjectIds));
            }
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
