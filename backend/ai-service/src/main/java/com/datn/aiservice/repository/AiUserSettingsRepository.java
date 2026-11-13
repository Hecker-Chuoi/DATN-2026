package com.datn.aiservice.repository;

import com.datn.aiservice.entity.AiUserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUserSettingsRepository extends JpaRepository<AiUserSettings, String> {
}
