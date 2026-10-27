package com.datn.identity_service.service;

import com.datn.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Dọn dẹp các tham chiếu tới user trong các bảng của work-service (cùng database datn_main_db).
 * Chạy trong transaction riêng (REQUIRES_NEW) và best-effort: nếu bảng chưa tồn tại
 * (work-service chưa từng chạy) thì chỉ ghi log, không làm hỏng giao dịch xóa user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkDataCleanupService {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupUserReferences(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        for (UUID id : userIds) {
            try {
                userRepository.deleteProjectRoleAssignmentsByUserId(id);
                userRepository.unassignWorkItemsByUserId(id);
            } catch (Exception ex) {
                log.warn("Could not clean up work-service references for user {}: {}", id, ex.getMessage());
                throw ex; // rollback riêng transaction này, không ảnh hưởng giao dịch cha
            }
        }
    }
}
