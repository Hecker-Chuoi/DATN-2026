package com.datn.identity_service.service;

import com.datn.identity_service.dto.response.TokenResponse;
import com.datn.identity_service.exception.KeycloakException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for all Keycloak Admin API interactions:
 * - Create / update users
 * - Assign realm roles to users
 * - Exchange credentials for JWT token pair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final Keycloak keycloakAdminClient;
    private final RestTemplate restTemplate;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    // ──────────────────────────────────────────────────────────────
    // User Creation
    // ──────────────────────────────────────────────────────────────

    /**
     * Creates a new user in Keycloak and returns the Keycloak user ID.
     *
     * @param username  username
     * @param password  plain-text password (Keycloak hashes it)
     * @param email     email
     * @param firstName first name
     * @param lastName  last name
     * @return Keycloak user UUID string
     */
    public String createUser(String username, String password, String email,
                             String firstName, String lastName) {
        UserRepresentation user = buildUserRepresentation(username, email, firstName, lastName);

        CredentialRepresentation credential = buildPasswordCredential(password);
        user.setCredentials(Collections.singletonList(credential));

        UsersResource usersResource = getRealmResource().users();
        try (Response response = usersResource.create(user)) {
            int status = response.getStatus();
            if (status == 201) {
                // Extract the Keycloak user ID from the Location header
                String location = response.getHeaderString("Location");
                String keycloakId = location.substring(location.lastIndexOf('/') + 1);
                log.info("Created Keycloak user '{}' with id '{}'", username, keycloakId);
                return keycloakId;
            } else if (status == 409) {
                throw new KeycloakException("User already exists in Keycloak: " + username);
            } else {
                String body = response.readEntity(String.class);
                throw new KeycloakException("Failed to create Keycloak user. Status: " + status + ", Body: " + body);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Role Assignment
    // ──────────────────────────────────────────────────────────────

    /**
     * Assigns a set of realm roles to a Keycloak user.
     * Roles that don't exist in the realm are silently ignored.
     *
     * @param keycloakUserId Keycloak UUID of the user
     * @param roleNames      set of role names (e.g. "USER", "ADMIN")
     */
    public void assignRealmRoles(String keycloakUserId, Set<String> roleNames) {
        RealmResource realmResource = getRealmResource();
        UserResource userResource = realmResource.users().get(keycloakUserId);

        List<RoleRepresentation> rolesToAssign = roleNames.stream()
                .map(roleName -> {
                    try {
                        return realmResource.roles().get(roleName).toRepresentation();
                    } catch (Exception e) {
                        log.warn("Role '{}' not found in realm '{}', skipping.", roleName, realm);
                        return null;
                    }
                })
                .filter(role -> role != null)
                .collect(Collectors.toList());

        if (!rolesToAssign.isEmpty()) {
            userResource.roles().realmLevel().add(rolesToAssign);
            log.info("Assigned roles {} to Keycloak user '{}'", roleNames, keycloakUserId);
        }
    }

    /**
     * Replaces all current realm roles of a user with a new set.
     */
    public void updateRealmRoles(String keycloakUserId, Set<String> newRoleNames) {
        RealmResource realmResource = getRealmResource();
        UserResource userResource = realmResource.users().get(keycloakUserId);

        // Remove all current realm roles
        List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();
        if (!currentRoles.isEmpty()) {
            userResource.roles().realmLevel().remove(currentRoles);
        }

        // Assign new roles
        assignRealmRoles(keycloakUserId, newRoleNames);
    }

    // ──────────────────────────────────────────────────────────────
    // Token Exchange (Login)
    // ──────────────────────────────────────────────────────────────

    /**
     * Exchanges username & password for a Keycloak JWT token pair
     * using Direct Access Grant (Resource Owner Password Credentials).
     *
     * @param username username
     * @param password plain-text password
     * @return {@link TokenResponse} containing access_token, refresh_token, etc.
     */
    @SuppressWarnings("unchecked")
    public TokenResponse login(String username, String password) {
        String tokenEndpoint = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("grant_type", "password");
        formParams.add("client_id", "datn-backend");
        formParams.add("username", username);
        formParams.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formParams, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, request, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw new KeycloakException("Empty response from Keycloak token endpoint");
            }

            return TokenResponse.builder()
                    .accessToken((String) body.get("access_token"))
                    .refreshToken((String) body.get("refresh_token"))
                    .expiresIn(((Number) body.get("expires_in")).longValue())
                    .refreshExpiresIn(((Number) body.get("refresh_expires_in")).longValue())
                    .tokenType((String) body.get("token_type"))
                    .sessionState((String) body.get("session_state"))
                    .build();

        } catch (HttpClientErrorException e) {
            log.warn("Login failed for user '{}': {}", username, e.getStatusCode());
            throw new KeycloakException("Invalid credentials");
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────

    private RealmResource getRealmResource() {
        return keycloakAdminClient.realm(realm);
    }

    private UserRepresentation buildUserRepresentation(String username, String email,
                                                        String firstName, String lastName) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return user;
    }

    private CredentialRepresentation buildPasswordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }
}
