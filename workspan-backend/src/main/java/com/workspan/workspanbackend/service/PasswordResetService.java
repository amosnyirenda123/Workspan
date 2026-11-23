package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.dto.PasswordResetCompleteRequest;
import com.workspan.workspanbackend.entity.UserAccount;
import com.workspan.workspanbackend.exception.BadRequestException;
import com.workspan.workspanbackend.exception.ErrorCode;
import com.workspan.workspanbackend.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PasswordResetService.class);
    private final UserAccountRepository users;
    private final PasswordHasher hasher;
    private final JavaMailSender mail;
    private final String from;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserAccountRepository users, PasswordHasher hasher, JavaMailSender mail,
            @Value("${workspan.mail.from:no-reply@workspan.local}") String from,
            @Value("${workspan.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.users = users; this.hasher = hasher; this.mail = mail; this.from = from; this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void request(String rawEmail) {
        users.findByEmailIgnoreCase(rawEmail.trim().toLowerCase(Locale.ROOT))
                .filter(user -> "ACTIVE".equals(user.getStatus())).ifPresent(user -> {
                    byte[] bytes = new byte[32]; random.nextBytes(bytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    user.setPasswordResetTokenHash(hashToken(token));
                    user.setPasswordResetExpiresAt(Instant.now().plusSeconds(1800));
                    users.save(user);
                    try {
                        SimpleMailMessage message = new SimpleMailMessage();
                        message.setFrom(from); message.setTo(user.getEmail()); message.setSubject("Réinitialisation du mot de passe Workspan");
                        message.setText("Bonjour " + user.getDisplayName() + ",\n\nPour choisir un nouveau mot de passe, ouvrez ce lien dans les 30 prochaines minutes :\n"
                                + frontendUrl.replaceAll("/$", "") + "/reset-password?token=" + token + "\n\nSi vous n'avez pas demandé cette réinitialisation, ignorez cet e-mail.");
                        mail.send(message);
                    } catch (RuntimeException exception) {
                        user.setPasswordResetTokenHash(null); user.setPasswordResetExpiresAt(null); users.save(user);
                        log.warn("Password reset email could not be delivered for account {}", user.getId());
                    }
                });
    }

    @Transactional
    public void complete(PasswordResetCompleteRequest request) {
        String tokenHash = hashToken(request.token());
        UserAccount user = users.findByPasswordResetTokenHash(tokenHash)
                .filter(candidate -> candidate.getPasswordResetExpiresAt() != null && candidate.getPasswordResetExpiresAt().isAfter(Instant.now()))
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new BadRequestException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        user.setPasswordHash(hasher.hash(request.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordResetTokenHash(null); user.setPasswordResetExpiresAt(null);
        users.save(user);
    }

    private String hashToken(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}
