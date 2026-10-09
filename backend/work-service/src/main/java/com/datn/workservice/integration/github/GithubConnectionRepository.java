package com.datn.workservice.integration.github;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GithubConnectionRepository extends JpaRepository<GithubConnection, UUID> {
    Optional<GithubConnection> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
