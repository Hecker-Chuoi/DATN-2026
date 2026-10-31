package com.datn.workservice.repository;

import com.datn.workservice.entity.UserGitHubToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserGitHubTokenRepository extends JpaRepository<UserGitHubToken, UUID> {

    Optional<UserGitHubToken> findByUserId(UUID userId);

    Optional<UserGitHubToken> findTopByOrderByCreatedAtDesc();

    boolean existsByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
