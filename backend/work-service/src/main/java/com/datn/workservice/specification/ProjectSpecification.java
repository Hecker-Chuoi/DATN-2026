package com.datn.workservice.specification;

import com.datn.workservice.entity.Project;
import org.springframework.data.jpa.domain.Specification;

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
}
