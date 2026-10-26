package com.datn.identity_service.repository;

import com.datn.identity_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByKeycloakId(String keycloakId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    java.util.List<User> findByIdIn(java.util.List<UUID> ids);

    @Query("SELECT u FROM User u WHERE u.id IN :ids OR u.keycloakId IN :keycloakIds")
    java.util.List<User> findByIdsOrKeycloakIds(@Param("ids") java.util.List<UUID> ids, @Param("keycloakIds") java.util.List<String> keycloakIds);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.username) LIKE LOWER(:search) OR " +
           "LOWER(u.email) LIKE LOWER(:search) OR " +
           "LOWER(u.firstName) LIKE LOWER(:search) OR " +
           "LOWER(u.lastName) LIKE LOWER(:search)")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    @Modifying
    @Query(value = "DELETE FROM role_assignments WHERE user_id = :userId", nativeQuery = true)
    void deleteProjectRoleAssignmentsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "UPDATE work_items SET assignee_id = NULL WHERE assignee_id = :userId", nativeQuery = true)
    void unassignWorkItemsByUserId(@Param("userId") UUID userId);
}
