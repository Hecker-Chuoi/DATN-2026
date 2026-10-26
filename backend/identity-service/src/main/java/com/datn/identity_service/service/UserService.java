package com.datn.identity_service.service;

import com.datn.identity_service.dto.request.RegisterRequest;
import com.datn.identity_service.dto.request.UpdateRolesRequest;
import com.datn.identity_service.dto.request.AdminCreateUserRequest;
import com.datn.identity_service.dto.request.ResetPasswordRequest;
import com.datn.identity_service.dto.response.UserResponse;
import com.datn.identity_service.entity.User;
import com.datn.identity_service.exception.EmailNotVerifiedException;
import com.datn.identity_service.exception.ResourceNotFoundException;
import com.datn.identity_service.exception.UserAlreadyExistsException;
import com.datn.identity_service.repository.UserRepository;
import com.datn.identity_service.client.WorkspaceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.datn.common.dto.PageResponse;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> DEFAULT_ROLES = Set.of("USER");

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final WorkDataCleanupService workDataCleanupService;
    private final AccountVerificationService accountVerificationService;
    private final WorkspaceClient workspaceClient;


    /**
     * Registers a new user:
     * 1. Validate username/email uniqueness in the local DB
     * 2. Create user in Keycloak
     * 3. Assign default role 'USER' in Keycloak
     * 4. Persist user entity in the local database
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

        // 2. Create user in Keycloak (disabled cho tới khi xác thực email)
        String keycloakId = keycloakService.createUser(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                false
        );

        // 3. Assign default realm role 'USER' in Keycloak (ADMIN chỉ được cấp thủ công qua API admin)
        keycloakService.assignRealmRoles(keycloakId, DEFAULT_ROLES);

        // 4. Persist to local DB
        User user = User.builder()
                .keycloakId(keycloakId)
                .username(request.getUsername())
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(Set.copyOf(DEFAULT_ROLES))
                .status(User.UserStatus.INACTIVE) // chờ xác thực email (AccountVerificationService.verifyEmail -> ACTIVE)
                .build();

        user = userRepository.save(user);
        keycloakService.setUserAttribute(keycloakId, "user_id", user.getId().toString());
        log.info("Registered new user (pending email verification): username='{}', keycloakId='{}', userId='{}'",
                user.getUsername(), keycloakId, user.getId());

        // 5. Gửi email xác thực (fail-safe: lỗi gửi mail không chặn đăng ký, người dùng có thể gửi lại)
        accountVerificationService.sendEmailVerification(user);
        workspaceClient.createDefaultWorkspace(user.getId());

        return UserResponse.from(user);
    }

    /**
     * Chặn đăng nhập khi tài khoản chưa xác thực email (trước khi gọi Keycloak để có thông báo rõ ràng).
     * identifier = username hoặc email.
     */
    @Transactional(readOnly = true)
    public void assertEmailVerified(String identifier) {
        userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .filter(u -> u.getStatus() == User.UserStatus.INACTIVE)
                .ifPresent(u -> {
                    throw new EmailNotVerifiedException(
                            "Tài khoản chưa xác thực email. Vui lòng kiểm tra hộp thư " + maskEmail(u.getEmail())
                                    + " hoặc yêu cầu gửi lại email xác thực.");
                });
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String shown = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return shown + "***" + email.substring(at);
    }

    /**
     * Finds an existing user by Keycloak ID, or creates a new one if first-time Google login.
     */
    @Transactional
    public UserResponse findOrCreateGoogleUser(Map<String, Object> userInfo) {
        String keycloakId      = (String) userInfo.get("sub");
        String email           = (String) userInfo.get("email");
        String givenName       = (String) userInfo.getOrDefault("given_name", "");
        String familyName      = (String) userInfo.getOrDefault("family_name", "");
        String name            = (String) userInfo.get("name");
        String rawUser = (String) userInfo.getOrDefault("preferred_username", email);
        if ((givenName == null || givenName.isBlank()) && name != null && !name.isBlank()) {
            String[] parts = name.trim().split("\\s+", 2);
            givenName = parts[0];
            familyName = parts.length > 1 ? parts[1] : "";
        }
        if (rawUser == null || rawUser.isBlank()) {
            rawUser = (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : email;
        }

        final String resolvedUsername = rawUser != null ? rawUser : keycloakId;
        final String resolvedEmail = email != null ? email : "";
        final String resolvedGivenName = givenName != null ? givenName : "";
        final String resolvedFamilyName = familyName != null ? familyName : "";

        // 1. Check if user already exists by Keycloak ID (return existing with auto-sync)
        UserResponse response = userRepository.findByKeycloakId(keycloakId)
                .map(existingUser -> {
                    log.debug("Google login: found existing user '{}'", existingUser.getUsername());
                    boolean updated = false;
                    if ((existingUser.getEmail() == null || existingUser.getEmail().isBlank()) && !resolvedEmail.isBlank()) {
                        existingUser.setEmail(resolvedEmail);
                        updated = true;
                    }
                    if ((existingUser.getFirstName() == null || existingUser.getFirstName().isBlank()) && !resolvedGivenName.isBlank()) {
                        existingUser.setFirstName(resolvedGivenName);
                        updated = true;
                    }
                    if ((existingUser.getLastName() == null || existingUser.getLastName().isBlank()) && !resolvedFamilyName.isBlank()) {
                        existingUser.setLastName(resolvedFamilyName);
                        updated = true;
                    }
                    if (updated) {
                        existingUser = userRepository.save(existingUser);
                    }
                    return UserResponse.from(existingUser);
                })
                .orElseGet(() -> {
                    // 2. New user: check email uniqueness to avoid conflicts
                    if (!resolvedEmail.isBlank() && userRepository.existsByEmail(resolvedEmail)) {
                        // Edge case: email exists under a different keycloakId (manual + Google account)
                        // Link by email – update the keycloakId
                        User existingByEmail = userRepository.findByEmail(resolvedEmail)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + resolvedEmail));
                        log.info("Google login: linking Google account to existing email '{}'", resolvedEmail);
                        existingByEmail.setKeycloakId(keycloakId);
                        if ((existingByEmail.getFirstName() == null || existingByEmail.getFirstName().isBlank()) && !resolvedGivenName.isBlank()) {
                            existingByEmail.setFirstName(resolvedGivenName);
                        }
                        if ((existingByEmail.getLastName() == null || existingByEmail.getLastName().isBlank()) && !resolvedFamilyName.isBlank()) {
                            existingByEmail.setLastName(resolvedFamilyName);
                        }
                        return UserResponse.from(userRepository.save(existingByEmail));
                    }

                    // 3. Brand new user – persist to local DB
                    User newUser = User.builder()
                            .keycloakId(keycloakId)
                            .username(resolvedUsername)
                            .email(resolvedEmail)
                            .firstName(resolvedGivenName)
                            .lastName(resolvedFamilyName)
                            .roles(Set.copyOf(DEFAULT_ROLES))
                            .status(User.UserStatus.ACTIVE)
                            .build();

                    newUser = userRepository.save(newUser);
                    log.info("Google login: created new user '{}' (keycloakId='{}', userId='{}')",
                            newUser.getUsername(), keycloakId, newUser.getId());
                    workspaceClient.createDefaultWorkspace(newUser.getId());
                    return UserResponse.from(newUser);
                });

        if (response != null && response.getId() != null) {
            keycloakService.setUserAttribute(keycloakId, "user_id", response.getId().toString());
            keycloakService.assignRealmRoles(keycloakId, Set.of("USER"));
        }

        return response;
    }


    /**
     * Returns the profile of the currently authenticated user.
     * Looks up by email first if present to unify Google and manual logins.
     */
    @Transactional
    public UserResponse getCurrentUser(Principal principal) {
        String keycloakId = extractKeycloakId(principal);
        String email = extractEmail(principal);
 
        User user = null;
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email).orElse(null);
        }
 
        if (user == null) {
            user = userRepository.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found for keycloakId: " + keycloakId));
        } else if (!keycloakId.equals(user.getKeycloakId())) {
            log.info("Linking new Keycloak ID '{}' to existing user '{}' with email '{}'", keycloakId, user.getUsername(), email);
            user.setKeycloakId(keycloakId);
            user = userRepository.save(user);
        }

        if (user != null && user.getId() != null) {
            keycloakService.setUserAttribute(keycloakId, "user_id", user.getId().toString());
        }

        return UserResponse.from(user);
    }


    /**
     * Returns paginated and sorted users, with optional search query.
     */
    public PageResponse<UserResponse> getAllUsers(String search, Pageable pageable) {
        Page<User> userPage;
        if (search != null && !search.isBlank()) {
            String q = "%" + search.trim().toLowerCase() + "%";
            userPage = userRepository.searchUsers(q, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }
 
        List<UserResponse> content = userPage.getContent().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
 
        return PageResponse.of(
                content,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isLast(),
                userPage.isFirst()
        );
    }

    /**
     * Updates RBAC roles for a user:
     * 1. Update roles in Keycloak realm
     * 2. Update roles in local database
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


    /**
     * Finds a user by ID (used by internal microservices)
     */
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .or(() -> userRepository.findByKeycloakId(id.toString()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return UserResponse.from(user);
    }

    /**
     * Finds a user by email (used by internal microservices)
     */
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return UserResponse.from(user);
    }

    /**
     * Finds a user by username (used by internal microservices)
     */
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return UserResponse.from(user);
    }

    /**
     * Finds a user by either email or username (used for project/workspace member invitations)
     */
    public UserResponse getUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResourceNotFoundException("Identifier cannot be blank");
        }
        String clean = identifier.trim();
        if (clean.contains("@")) {
            return getUserByEmail(clean);
        }
        return userRepository.findByUsername(clean)
                .or(() -> userRepository.findByEmail(clean))
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with identifier: " + clean));
    }

    /**
     * Batch queries users by a list of IDs (used for Kanban/Project rendering)
     */
    public List<UserResponse> getUsersByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> keycloakIds = ids.stream().map(UUID::toString).toList();
        return userRepository.findByIdsOrKeycloakIds(ids, keycloakIds)
                .stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a user exists by ID
     */
    public boolean existsById(UUID id) {
        return userRepository.existsById(id);
    }

    /**
     * Admin creates a new user with only username and password.
     */
    @Transactional
    public UserResponse adminCreateUser(AdminCreateUserRequest request) {
        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username '" + username + "' is already taken");
        }
        
        String email = username.toLowerCase() + "@local.com";
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Generated email '" + email + "' is already registered");
        }
 
        // 1. Create in Keycloak
        String keycloakId = keycloakService.createUser(
                username,
                request.getPassword(),
                email,
                "",
                ""
        );
 
        // 2. Assign default roles
        keycloakService.assignRealmRoles(keycloakId, DEFAULT_ROLES);
 
        // 3. Persist to local DB
        User user = User.builder()
                .keycloakId(keycloakId)
                .username(username)
                .email(email)
                .firstName("")
                .lastName("")
                .roles(Set.copyOf(DEFAULT_ROLES))
                .status(User.UserStatus.ACTIVE)
                .build();
 
        user = userRepository.save(user);
        keycloakService.setUserAttribute(keycloakId, "user_id", user.getId().toString());
        log.info("Admin created user: username='{}', keycloakId='{}', userId='{}'", user.getUsername(), keycloakId, user.getId());
        workspaceClient.createDefaultWorkspace(user.getId());

        return UserResponse.from(user);
    }
 
    /**
     * Admin resets user password.
     */
    @Transactional
    public void resetPassword(UUID userId, ResetPasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
 
        keycloakService.resetUserPassword(user.getKeycloakId(), request.getPassword());
        log.info("Admin reset password for user '{}'", user.getUsername());
    }
 
    /**
     * Soft deletes user: sets status to SUSPENDED, disables Keycloak account,
     * and deletes memberships and unassigns tasks.
     */
    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
 
        // 1. Disable in Keycloak
        keycloakService.updateUserEnabledStatus(user.getKeycloakId(), false);
 
        // 2. Set status to SUSPENDED in DB
        user.setStatus(User.UserStatus.SUSPENDED);
        userRepository.save(user);

        // 3. Cleanup work-service references (project roles, assignee).
        //    work-service lưu user_id = Keycloak sub, nên dọn theo cả 2 định danh (Keycloak id + DB id cũ).
        Set<UUID> referenceIds = new java.util.HashSet<>();
        referenceIds.add(userId);
        try {
            if (user.getKeycloakId() != null) {
                referenceIds.add(UUID.fromString(user.getKeycloakId()));
            }
        } catch (IllegalArgumentException ignored) {
        }
        workDataCleanupService.cleanupUserReferences(referenceIds);

        log.info("Soft-deleted and cleaned up user '{}'", user.getUsername());
    }

    private String extractKeycloakId(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            return jwt.getSubject(); // 'sub' = Keycloak user UUID
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass());
    }
 
    private String extractEmail(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            return jwt.getClaimAsString("email");
        }
        return null;
    }
}
