package com.datn.aiservice.service;

import com.datn.aiservice.entity.AiUserMemory;
import com.datn.aiservice.entity.AiUserSettings;
import com.datn.aiservice.enums.MemoryCategory;
import com.datn.aiservice.enums.MemoryScope;
import com.datn.aiservice.repository.AiUserMemoryRepository;
import com.datn.aiservice.repository.AiUserSettingsRepository;
import com.datn.common.exception.BadRequestException;
import com.datn.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bộ nhớ dài hạn của chính user (ai_user_memories) + cài đặt bật/tắt. */
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final AiUserMemoryRepository memoryRepo;
    private final AiUserSettingsRepository settingsRepo;

    @Transactional(readOnly = true)
    public boolean isEnabled(String userId) {
        return settingsRepo.findById(userId).map(AiUserSettings::isMemoryEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public AiUserSettings settings(String userId) {
        return settingsRepo.findById(userId).orElseGet(() ->
                AiUserSettings.builder().userId(userId).memoryEnabled(true).build());
    }

    @Transactional
    public AiUserSettings updateSettings(String userId, boolean memoryEnabled) {
        AiUserSettings s = settings(userId);
        s.setMemoryEnabled(memoryEnabled);
        return settingsRepo.save(s);
    }

    /** Mục GLOBAL + mục PROJECT của dự án hiện tại, mới nhất trước, tối đa 50. */
    @Transactional(readOnly = true)
    public List<AiUserMemory> forPrompt(String userId, UUID projectId) {
        return memoryRepo.findTop50ByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId).stream()
                .filter(m -> m.getScope() == MemoryScope.GLOBAL
                        || (projectId != null && projectId.equals(m.getProjectId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiUserMemory> list(String userId) {
        return memoryRepo.findTop50ByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public AiUserMemory save(String userId, String content, MemoryScope scope, MemoryCategory category,
                             UUID projectId, UUID sourceConversationId) {
        if (content == null || content.isBlank()) throw new BadRequestException("Nội dung ghi nhớ trống");
        if (scope == MemoryScope.PROJECT && projectId == null) scope = MemoryScope.GLOBAL;
        return memoryRepo.save(AiUserMemory.builder()
                .userId(userId).content(content.trim()).scope(scope).category(category)
                .projectId(scope == MemoryScope.PROJECT ? projectId : null)
                .sourceConversationId(sourceConversationId).build());
    }

    @Transactional
    public AiUserMemory update(String userId, UUID id, String content, MemoryScope scope, MemoryCategory category) {
        AiUserMemory m = memoryRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mục ghi nhớ"));
        if (content != null && !content.isBlank()) m.setContent(content.trim());
        if (scope != null) m.setScope(scope);
        if (category != null) m.setCategory(category);
        return memoryRepo.save(m);
    }

    @Transactional
    public void delete(String userId, UUID id) {
        AiUserMemory m = memoryRepo.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mục ghi nhớ"));
        m.setDeletedAt(Instant.now());
        memoryRepo.save(m);
    }
}
