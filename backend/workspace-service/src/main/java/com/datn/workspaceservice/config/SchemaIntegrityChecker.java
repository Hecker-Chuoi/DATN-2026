package com.datn.workspaceservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Cảnh báo lúc boot khi FK teams/projects -> workspaces bị thiếu.
 * <p>
 * Vì sao cần: migration V3 đặt FK trong history của workspace-service nhưng bảng
 * teams/projects lại thuộc history của work-service. Trên một DB mới, nếu
 * workspace-service migrate TRƯỚC work-service thì V3 no-op (bảng chưa tồn tại)
 * nhưng Flyway vẫn đánh dấu đã applied, và FK sẽ VĨNH VIỄN không được tạo.
 * <p>
 * Không thể phòng ngừa bằng migration repeatable (R__) vì nó chỉ chạy lại khi
 * checksum đổi. Nên xử lý bằng PHÁT HIỆN: log WARN để người vận hành biết cần
 * thêm một migration V4 sửa chữa. Thứ tự boot đúng trên DB mới là
 * identity-service, rồi work-service, rồi workspace-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaIntegrityChecker implements ApplicationRunner {

    private static final String CHECK_SQL = """
            SELECT to_regclass(?) IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        warnIfMissing("public.teams", "fk_teams_workspace");
        warnIfMissing("public.projects", "fk_projects_workspace");
    }

    private void warnIfMissing(String table, String constraint) {
        try {
            Boolean missing = jdbcTemplate.queryForObject(CHECK_SQL, Boolean.class, table, constraint);
            if (Boolean.TRUE.equals(missing)) {
                log.warn("Bảng {} đã tồn tại nhưng THIẾU ràng buộc {}. "
                                + "Khả năng cao workspace-service đã migrate trước work-service, "
                                + "khiến V3 no-op nhưng vẫn bị Flyway đánh dấu applied. "
                                + "Cần thêm migration V4 để tạo lại FK này.",
                        table, constraint);
            } else {
                log.debug("Ràng buộc {} hợp lệ.", constraint);
            }
        } catch (Exception e) {
            log.warn("Không kiểm tra được ràng buộc {}: {}", constraint, e.getMessage());
        }
    }
}
