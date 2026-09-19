package com.datn.workservice.specification;

import com.datn.workservice.entity.Project;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

public class ProjectSpecification extends BaseSpecification {

    /**
     * @param workspaceIds phạm vi workspace được phép xem. null = không giới hạn
     *                     (chỉ dùng cho lời gọi nội bộ), danh sách rỗng = không
     *                     có quyền ở đâu nên trả trang trống. Trước đây tham số
     *                     này là một UUID optional và không được enforce, nên bỏ
     *                     trống nó là liệt kê MỌI project trong hệ thống.
     */
    public static Specification<Project> withFilters(
            List<UUID> workspaceIds,
            UUID teamId,
            String search,
            Boolean isTemplate,
            boolean includeDeleted
    ) {
        Specification<Project> spec = (root, query, cb) -> cb.conjunction();

        if (workspaceIds != null) {
            spec = spec.and(hasFieldIn("workspaceId", workspaceIds));
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
