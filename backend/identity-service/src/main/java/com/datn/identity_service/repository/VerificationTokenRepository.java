package com.datn.identity_service.repository;

import com.datn.identity_service.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByTokenAndType(String token, VerificationToken.Type type);
    void deleteByUserIdAndType(UUID userId, VerificationToken.Type type);
}
