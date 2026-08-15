package com.datn.identity_service.service;

import com.datn.identity_service.dto.response.TokenResponse;
import com.datn.identity_service.exception.BadCredentialsException;
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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final Keycloak keycloakAdminClient;
    private final RestTemplate restTemplate;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.public-url:${keycloak.server-url}}")
    private String publicUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client.client-id}")
    private String clientId;

    @Value("${keycloak.client.client-secret}")
    private String clientSecret;

    /**
     * Creates a new user in Keycloak and returns the Keycloak user ID.
     */
    public String createUser(String username, String password, String email,
            String firstName, String lastName) {
        // Init Object user of keycloak = userDetail
        UserRepresentation user = buildUserRepresentation(username, email, firstName, lastName);
        // define what field is credential(password,...)
        CredentialRepresentation credential = buildPasswordCredential(password);
        user.setCredentials(Collections.singletonList(credential));

        // call keycloak to create new user
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

    /**
     * Assigns a set of realm roles to a Keycloak user.
     * Roles that don't exist in the realm are silently ignored.
     */
    public void assignRealmRoles(String keycloakUserId, Set<String> roleNames) {
        // call keycloak to get created user
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
        formParams.add("client_id", clientId);
        formParams.add("client_secret", clientSecret); // required for Confidential clients
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
            log.error("Keycloak login failed for user '{}'. Status: {}, Response: {}",
                    username, e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new BadCredentialsException("Invalid username or password");
            }
            throw new KeycloakException("Keycloak connection error during login. Status: " + e.getStatusCode());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Google OAuth2 (via Keycloak Identity Broker)
    // ──────────────────────────────────────────────────────────────

    /**
     * Builds the Keycloak Authorization URL that initiates Google login.
     * The {@code kc_idp_hint=google} parameter skips Keycloak’s login page
     * and redirects the user directly to Google’s consent screen.
     *
     * @param redirectUri the frontend URI Keycloak will redirect back to with the
     *                    code
     * @return fully-formed authorization URL
     */
    public String getGoogleAuthorizationUrl(String redirectUri) {
        String encodedRedirectUri;
        try {
            encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new KeycloakException("Invalid redirect URI: " + redirectUri);
        }

        return publicUrl + "/realms/" + realm + "/protocol/openid-connect/auth"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&scope=openid+profile+email"
                + "&redirect_uri=" + encodedRedirectUri
                + "&kc_idp_hint=google"
                + "&prompt=login"; // Forces Keycloak to ignore existing SSO session and re-authenticate with Google
    }

    /**
     * Exchanges an OAuth2 Authorization Code for a Keycloak JWT token pair.
     * Called after the frontend receives the {@code code} param from Keycloak
     * callback.
     *
     * @param code        authorization code issued by Keycloak
     * @param redirectUri must exactly match the URI used in
     *                    {@link #getGoogleAuthorizationUrl}
     * @return {@link TokenResponse} containing access_token, refresh_token, etc.
     */
    @SuppressWarnings("unchecked")
    public TokenResponse exchangeCodeForToken(String code, String redirectUri) {
        String tokenEndpoint = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("grant_type", "authorization_code");
        formParams.add("client_id", clientId);
        formParams.add("client_secret", clientSecret);
        formParams.add("code", code);
        formParams.add("redirect_uri", redirectUri);

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
            log.warn("Authorization code exchange failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("Failed to exchange authorization code: " + e.getMessage());
        }
    }

    /**
     * Refreshes an expired JWT access token using a Keycloak refresh token.
     *
     * @param refreshToken Keycloak refresh token
     * @return {@link TokenResponse} containing new access_token, refresh_token, etc.
     */
    @SuppressWarnings("unchecked")
    public TokenResponse refreshToken(String refreshToken) {
        String tokenEndpoint = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("grant_type", "refresh_token");
        formParams.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            formParams.add("client_secret", clientSecret);
        }
        formParams.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formParams, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenEndpoint, request, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw new KeycloakException("Empty response from Keycloak token refresh");
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
            log.warn("Token refresh failed: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new KeycloakException("Phiên đăng nhập đã hết hạn: " + e.getResponseBodyAsString());
        }
    }

    /**
     * Terminates user session on Keycloak using refresh token.
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String logoutEndpoint = serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            formParams.add("client_secret", clientSecret);
        }
        formParams.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formParams, headers);

        try {
            restTemplate.postForEntity(logoutEndpoint, request, Void.class);
            log.info("Successfully terminated Keycloak session for refresh token.");
        } catch (Exception e) {
            log.warn("Failed to logout from Keycloak: {}", e.getMessage());
        }
    }

    /**
     * Fetches user claims from Keycloak’s UserInfo endpoint using an access token.
     * Returns a map of standard OIDC claims: sub, email, given_name, family_name,
     * preferred_username.
     *
     * @param accessToken a valid Keycloak JWT access token
     * @return map of user claims
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserInfoFromKeycloak(String accessToken) {
        String userInfoEndpoint = serverUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userInfoEndpoint, org.springframework.http.HttpMethod.GET, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new KeycloakException("Empty response from Keycloak UserInfo endpoint");
            }
            return body;
        } catch (HttpClientErrorException e) {
            log.warn("UserInfo request failed: {}", e.getStatusCode());
            throw new KeycloakException("Failed to fetch user info from Keycloak");
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
