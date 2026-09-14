package com.delvin.loan.service;

import com.delvin.loan.common.AccountType;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.RefreshToken;
import com.delvin.loan.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final int TOKEN_BYTES = 32;
    private static final String EXPIRED_OR_UNKNOWN = "Your session has expired. Sign in again.";

    private final RefreshTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.security.refresh-ttl-days:7}")
    private long refreshTtlDays;

    public record IssuedToken(String token, Instant expiresAt) {}

    @Transactional
    public IssuedToken issue(String subject, AccountType accountType) {
        return persist(subject, accountType, UUID.randomUUID().toString());
    }

    @Transactional
    public RotationResult rotate(String rawToken, Instant now) {

        if (rawToken == null || rawToken.isBlank()) {
            throw BusinessException.unauthorized(EXPIRED_OR_UNKNOWN);
        }

        RefreshToken stored = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> BusinessException.unauthorized(EXPIRED_OR_UNKNOWN));

        if (stored.isRevoked()) {
            log.warn("Refresh token replay detected for subject {}, revoking family {}",
                    stored.getSubject(), stored.getFamilyId());
            repository.revokeFamily(stored.getFamilyId(), now);
            throw BusinessException.unauthorized(EXPIRED_OR_UNKNOWN);
        }

        if (stored.isExpired(now)) {
            throw BusinessException.unauthorized(EXPIRED_OR_UNKNOWN);
        }

        stored.setRevokedAt(now);
        repository.save(stored);

        IssuedToken next = persist(
                stored.getSubject(),
                AccountType.valueOf(stored.getAccountType()),
                stored.getFamilyId());

        return new RotationResult(stored.getSubject(), next);
    }

    public record RotationResult(String subject, IssuedToken token) {}

    @Transactional
    public void revoke(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) return;

        repository.findByTokenHash(hash(rawToken))
                .ifPresent(stored -> repository.revokeFamily(stored.getFamilyId(), now));
    }

    @Transactional
    public int revokeAllFor(String subject, Instant now) {
        return repository.revokeAllForSubject(subject, now);
    }

    private IssuedToken persist(String subject, AccountType accountType, String familyId) {

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = Instant.now();

        RefreshToken entity = new RefreshToken();
        entity.setId(UUID.randomUUID().toString());
        entity.setTokenHash(hash(raw));
        entity.setSubject(subject);
        entity.setAccountType(accountType.name());
        entity.setFamilyId(familyId);
        entity.setIssuedAt(now);
        entity.setExpiresAt(now.plus(Duration.ofDays(refreshTtlDays)));

        repository.save(entity);

        return new IssuedToken(raw, entity.getExpiresAt());
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
