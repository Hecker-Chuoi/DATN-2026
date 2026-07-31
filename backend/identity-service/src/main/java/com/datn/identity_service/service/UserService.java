package com.datn.identity_service.service;

import com.datn.identity_service.dto.request.RegisterRequest;
import com.datn.identity_service.dto.request.UpdateRolesRequest;
import com.datn.identity_service.dto.response.UserResponse;
import com.datn.identity_service.entity.User;
import com.datn.identity_service.exception.ResourceNotFoundException;
import com.datn.identity_service.exception.UserAlreadyExistsException;
import com.datn.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates user registration, profile retrieval, and admin role management.
 * Acts as the bridge between Keycloak and the backend database (datn_main_db).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> DEFAULT_ROLES = Set.of("USER");

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    // ──────────────────────────────────────────────────────────────
    // Registration
    // ──────────────────────────────────────────────────────────────

    /**
     * Registers a new user:
     * 1. Validate username/email uniqueness in the local DB
     * 2. Create user in Keycloak
     * 3. Assign default role 'USER' in Keycloak
     * 4. Persist user entity in the local database
     *
     * @param request registration details
     * @return saved {@link UserResponse}
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // 1. Check uniqueness
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email '" + request.getEmail() + "' is already registered");
        }

        // 2. Create user in Keycloak
        String keycloakId = keycloakService.createUser(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName()
        );

        // 3. Assign default realm role 'USER' in Keycloak
        keycloakService.assignRealmRoles(keycloakId, DEFAULT_ROLES);

        // 4. Persist to local DB
        User user = User.builder()
                .keycloakId(keycloakId)
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(Set.copyOf(DEFAULT_ROLES))
                .status(User.UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);
        log.info("Registered new user: username='{}', keycloakId='{}'", user.getUsername(), keycloakId);

        return UserResponse.from(user);
    }

    // ──────────────────────────────────────────────────────────────
    // Current User Profile
    // ──────────────────────────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     * The Keycloak JWT `sub` claim holds the Keycloak user ID.
     *
     * @param principal the Spring Security principal (JwtAuthenticationToken)
     * @return {@link UserResponse}
     */
    public UserResponse getCurrentUser(Principal principal) {
        String keycloakId = extractKeycloakId(principal);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for keycloakId: " + keycloakId));

        return UserResponse.from(user);
    }

    // ──────────────────────────────────────────────────────────────
    // Admin Operations
    // ──────────────────────────────────────────────────────────────

    /**
     * Returns all users from the local database.
     */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Updates RBAC roles for a user:
     * 1. Update roles in Keycloak realm
     * 2. Update roles in local database
     *
     * @param userId  local DB user UUID
     * @param request new set of roles
     * @return updated {@link UserResponse}
     */
    @Transactional
    public UserResponse updateUserRoles(UUID userId, UpdateRolesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // 1. Update roles in Keycloak
        keycloakService.updateRealmRoles(user.getKeycloakId(), request.getRoles());

        // 2. Update roles in local DB
        user.setRoles(request.getRoles());
        user = userRepository.save(user);

        log.info("Updated roles for user '{}' to {}", user.getUsername(), request.getRoles());
        return UserResponse.from(user);
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private String extractKeycloakId(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            return jwt.getSubject(); // 'sub' = Keycloak user UUID
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass());
    }
}
