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

        // 2. Create user in Keycloak
        String keycloakId = keycloakService.createUser(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName()
        );

        // 3. Assign default realm role 'USER' in Keycloak
        keycloakService.assignRealmRoles(keycloakId, Set.of("ADMIN")); //DEFAULT_ROLES

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

    /**
     * Finds an existing user by Keycloak ID, or creates a new one if first-time Google login.
     */
    @Transactional
    public UserResponse findOrCreateGoogleUser(Map<String, Object> userInfo) {
        String keycloakId      = (String) userInfo.get("sub");
        String email           = (String) userInfo.get("email");
        String givenName       = (String) userInfo.getOrDefault("given_name", "");
        String familyName      = (String) userInfo.getOrDefault("family_name", "");
        String preferredUser   = (String) userInfo.getOrDefault("preferred_username", email);

        // 1. Check if user already exists by Keycloak ID (return existing)
        return userRepository.findByKeycloakId(keycloakId)
                .map(existingUser -> {
                    log.debug("Google login: found existing user '{}'", existingUser.getUsername());
                    return UserResponse.from(existingUser);
                })
                .orElseGet(() -> {
                    // 2. New user: check email uniqueness to avoid conflicts
                    if (userRepository.existsByEmail(email)) {
                        // Edge case: email exists under a different keycloakId (manual + Google account)
                        // Link by email – update the keycloakId
                        User existingByEmail = userRepository.findByEmail(email)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
                        log.info("Google login: linking Google account to existing email '{}'", email);
                        existingByEmail.setKeycloakId(keycloakId);
                        return UserResponse.from(userRepository.save(existingByEmail));
                    }

                    // 3. Brand new user – persist to local DB
                    User newUser = User.builder()
                            .keycloakId(keycloakId)
                            .username(preferredUser)
                            .email(email)
                            .firstName(givenName)
                            .lastName(familyName)
                            .roles(Set.copyOf(DEFAULT_ROLES))
                            .status(User.UserStatus.ACTIVE)
                            .build();

                    newUser = userRepository.save(newUser);
                    log.info("Google login: created new user '{}' (keycloakId='{}')",
                            newUser.getUsername(), keycloakId);
                    return UserResponse.from(newUser);
                });
    }


    /**
     * Returns the profile of the currently authenticated user
     */
    public UserResponse getCurrentUser(Principal principal) {
        String keycloakId = extractKeycloakId(principal);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for keycloakId: " + keycloakId));

        return UserResponse.from(user);
    }


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


    private String extractKeycloakId(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            return jwt.getSubject(); // 'sub' = Keycloak user UUID
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass());
    }
}
