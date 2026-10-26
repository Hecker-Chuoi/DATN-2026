package com.datn.identity_service.service;

import com.datn.identity_service.client.NotificationClient;
import com.datn.identity_service.client.WorkspaceClient;
import com.datn.identity_service.entity.User;
import com.datn.identity_service.entity.VerificationToken;
import com.datn.identity_service.exception.InvalidTokenException;
import com.datn.identity_service.exception.ResourceNotFoundException;
import com.datn.identity_service.repository.UserRepository;
import com.datn.identity_service.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Xác thực email sau đăng ký và đặt lại mật khẩu qua link email.
 * Trạng thái "chưa xác thực" = users.status INACTIVE + Keycloak user disabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final NotificationClient notificationClient;
    private final WorkspaceClient workspaceClient;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.verification.email-ttl-hours:24}")
    private long emailTtlHours;

    @Value("${app.verification.reset-ttl-minutes:30}")
    private long resetTtlMinutes;

    // ─── Xác thực email ─────────────────────────────────────────────────────

    /** Tạo token mới (vô hiệu token cũ) và gửi email xác thực bất đồng bộ theo template. */
    @Transactional
    public boolean sendEmailVerification(User user) {
        String token = issueToken(user, VerificationToken.Type.EMAIL_VERIFY, Duration.ofHours(emailTtlHours));
        String link = frontendUrl + "/verify-email?token=" + token;
        notificationClient.sendTemplate(user.getEmail(), "[DATN-2026] Xác thực email của bạn", "verify-email", Map.of(
                "recipient_name", displayName(user),
                "link", link,
                "email_ttl_hours", String.valueOf(emailTtlHours)));
        return true;
    }

    @Transactional
    public User verifyEmail(String token) {
        VerificationToken vt = requireUsable(token, VerificationToken.Type.EMAIL_VERIFY);
        User user = userRepository.findById(vt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng của token này"));
        activateIfPending(user);
        vt.setUsedAt(LocalDateTime.now());
        tokenRepository.save(vt);
        log.info("Email verified for user {}", user.getUsername());
        return user;
    }

    /** Bật Keycloak user + users.status=ACTIVE nếu đang chờ xác thực; không làm gì nếu đã ACTIVE/SUSPENDED. */
    private void activateIfPending(User user) {
        if (user.getStatus() != User.UserStatus.INACTIVE) return;
        keycloakService.activateVerifiedUser(user.getKeycloakId());
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);
        workspaceClient.createDefaultWorkspace(user.getId());
    }

    /** identifier = email hoặc username. Chỉ gửi khi tài khoản đang chờ xác thực. */
    @Transactional
    public void resendVerification(String identifier) {
        Optional<User> found = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier));
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy tài khoản với thông tin: " + identifier);
        }
        User user = found.get();
        if (user.getStatus() != User.UserStatus.INACTIVE) {
            throw new InvalidTokenException("Tài khoản này đã được xác thực, bạn có thể đăng nhập");
        }
        sendEmailVerification(user);
    }

    // ─── Quên mật khẩu ──────────────────────────────────────────────────────

    /** Luôn kết thúc lặng lẽ để không lộ email nào tồn tại. */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getStatus() == User.UserStatus.SUSPENDED) return;
            String token = issueToken(user, VerificationToken.Type.PASSWORD_RESET, Duration.ofMinutes(resetTtlMinutes));
            String link = frontendUrl + "/reset-password?token=" + token;
            // Template notification-service/resources/templates/forgot-password.html
            notificationClient.sendTemplate(user.getEmail(), "[DATN-2026] Đặt lại mật khẩu", "forgot-password", Map.of(
                    "recipient_name", displayName(user),
                    "link", link,
                    "ttl_minutes", String.valueOf(resetTtlMinutes)));
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        VerificationToken vt = requireUsable(token, VerificationToken.Type.PASSWORD_RESET);
        User user = userRepository.findById(vt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng của token này"));
        keycloakService.resetUserPassword(user.getKeycloakId(), newPassword);
        // Link reset chỉ đến được qua email → đã chứng minh sở hữu email, kích hoạt luôn nếu chưa verify
        activateIfPending(user);
        vt.setUsedAt(LocalDateTime.now());
        tokenRepository.save(vt);
        log.info("Password reset via email for user {}", user.getUsername());
        sendPasswordChangedAlert(user);
    }

    /** Gửi email cảnh báo theo template khi mật khẩu được thay đổi thành công. */
    public void sendPasswordChangedAlert(User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
        String now = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        notificationClient.sendTemplate(user.getEmail(), "[DATN-2026] Mật khẩu tài khoản của bạn đã được thay đổi", "password-changed", Map.of(
                "recipient_name", displayName(user),
                "username", user.getUsername(),
                "changed_at", now,
                "link", frontendUrl + "/login"));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private String issueToken(User user, VerificationToken.Type type, Duration ttl) {
        tokenRepository.deleteByUserIdAndType(user.getId(), type);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(VerificationToken.builder()
                .userId(user.getId())
                .token(token)
                .type(type)
                .expiresAt(LocalDateTime.now().plus(ttl))
                .build());
        return token;
    }

    private VerificationToken requireUsable(String token, VerificationToken.Type type) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Thiếu token");
        }
        VerificationToken vt = tokenRepository.findByTokenAndType(token.trim(), type)
                .orElseThrow(() -> new InvalidTokenException("Liên kết không hợp lệ"));
        if (vt.getUsedAt() != null) {
            throw new InvalidTokenException("Liên kết này đã được sử dụng");
        }
        if (!vt.isUsable()) {
            throw new InvalidTokenException("Liên kết đã hết hạn, vui lòng yêu cầu lại");
        }
        return vt;
    }

    private static String displayName(User user) {
        String full = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }

    private static String layout(String title, String body, String link, String buttonLabel) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#0f172a\">"
                + "<h2 style=\"color:#2563eb;margin:0 0 16px\">" + title + "</h2>"
                + "<p style=\"font-size:14px;line-height:1.6\">" + body + "</p>"
                + "<p style=\"margin:24px 0\"><a href=\"" + link + "\" style=\"background:#2563eb;color:#fff;text-decoration:none;padding:10px 18px;border-radius:6px;font-weight:600\">"
                + buttonLabel + "</a></p>"
                + "<p style=\"font-size:12px;color:#64748b\">Nếu nút không hoạt động, sao chép liên kết sau vào trình duyệt:<br/>"
                + "<a href=\"" + link + "\">" + link + "</a></p>"
                + "</div>";
    }
}
