package com.delvin.loan.service;

import com.delvin.loan.common.AccountType;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.RefreshToken;
import com.delvin.loan.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long TTL_DAYS = 7;

    @Mock
    private RefreshTokenRepository repository;

    @InjectMocks
    private RefreshTokenService service;

    @BeforeEach
    void setTtl() {
        ReflectionTestUtils.setField(service, "refreshTtlDays", TTL_DAYS);
    }

    private String sha256(String raw) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private RefreshToken stored(String familyId, Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setId("RT-1");
        token.setTokenHash("hash");
        token.setSubject("staff@example.com");
        token.setAccountType(AccountType.USER.name());
        token.setFamilyId(familyId);
        token.setIssuedAt(Instant.parse("2026-09-01T00:00:00Z"));
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }

    private RefreshToken captureSaved() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("an issued token is returned to the caller but never stored in the clear")
    void onlyTheHashIsStored() throws Exception {
        RefreshTokenService.IssuedToken issued =
                service.issue("staff@example.com", AccountType.USER);

        RefreshToken saved = captureSaved();

        assertThat(issued.token()).isNotBlank();
        assertThat(saved.getTokenHash()).isNotEqualTo(issued.token());
        assertThat(saved.getTokenHash()).isEqualTo(sha256(issued.token()));
        assertThat(saved.getTokenHash()).hasSize(64);
    }

    @Test
    @DisplayName("an issued token carries the subject, account type and a fresh family")
    void issueRecordsTheOwner() {
        service.issue("customer@example.com", AccountType.CUSTOMER);

        RefreshToken saved = captureSaved();

        assertThat(saved.getSubject()).isEqualTo("customer@example.com");
        assertThat(saved.getAccountType()).isEqualTo("CUSTOMER");
        assertThat(saved.getFamilyId()).isNotBlank();
    }

    @Test
    @DisplayName("an issued token expires after the configured number of days")
    void issueHonoursTheConfiguredTtl() {
        RefreshTokenService.IssuedToken issued =
                service.issue("staff@example.com", AccountType.USER);

        Duration life = Duration.between(Instant.now(), issued.expiresAt());

        assertThat(life).isCloseTo(Duration.ofDays(TTL_DAYS), Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("two tokens issued in a row are different")
    void tokensAreUnpredictable() {
        String first = service.issue("staff@example.com", AccountType.USER).token();
        String second = service.issue("staff@example.com", AccountType.USER).token();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("rotating a valid token revokes it and issues a replacement in the same family")
    void rotationRevokesAndReplaces() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        RefreshToken current = stored("FAM-1", now.plusSeconds(3600), null);

        when(repository.findByTokenHash(any())).thenReturn(Optional.of(current));

        RefreshTokenService.RotationResult result = service.rotate("raw-token", now);

        assertThat(current.getRevokedAt()).isEqualTo(now);
        assertThat(result.subject()).isEqualTo("staff@example.com");
        assertThat(result.token().token()).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getFamilyId()).isEqualTo("FAM-1");
    }

    @Test
    @DisplayName("the replacement is a different token from the one it replaces")
    void rotationIssuesADifferentToken() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(stored("FAM-1", now.plusSeconds(3600), null)));

        assertThat(service.rotate("raw-token", now).token().token()).isNotEqualTo("raw-token");
    }

    @Test
    @DisplayName("an unknown refresh token is refused")
    void unknownTokenIsRefused() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope", Instant.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("session has expired")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank refresh token is refused without touching the database")
    void blankTokenIsRefused(String raw) {
        assertThatThrownBy(() -> service.rotate(raw, Instant.now()))
                .isInstanceOf(BusinessException.class);

        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("an expired refresh token is refused")
    void expiredTokenIsRefused() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(stored("FAM-1", now.minusSeconds(1), null)));

        assertThatThrownBy(() -> service.rotate("raw-token", now))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a token expiring exactly now is already expired, because the bound is exclusive")
    void expiryBoundIsExclusive() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(stored("FAM-1", now, null)));

        assertThatThrownBy(() -> service.rotate("raw-token", now))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("replaying an already used token revokes the whole chain, not just that token")
    void replayRevokesTheFamily() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        Instant usedAt = now.minusSeconds(60);

        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(stored("FAM-1", now.plusSeconds(3600), usedAt)));

        assertThatThrownBy(() -> service.rotate("raw-token", now))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(repository).revokeFamily("FAM-1", now);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("the refusal message never says which of the three reasons applied")
    void refusalIsUniform() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");

        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        String unknown = catchMessage(() -> service.rotate("a", now));

        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(stored("FAM-1", now.minusSeconds(1), null)));
        String expired = catchMessage(() -> service.rotate("b", now));

        assertThat(unknown).isEqualTo(expired);
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected a refusal");
        } catch (BusinessException e) {
            return e.getMessage();
        }
    }

    @Test
    @DisplayName("signing out revokes the whole chain so no copy still works")
    void logoutRevokesTheFamily() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(stored("FAM-9", now.plusSeconds(3600), null)));

        service.revoke("raw-token", now);

        verify(repository).revokeFamily("FAM-9", now);
    }

    @Test
    @DisplayName("signing out with an unknown token is silent, because there is nothing to protect")
    void logoutWithUnknownTokenIsSilent() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("nope", Instant.now());

        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("signing out with no token at all does not reach the database")
    void logoutWithNoTokenIsSilent() {
        service.revoke(null, Instant.now());
        service.revoke("  ", Instant.now());

        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("every token for one person can be revoked at once")
    void revokeAllForSubject() {
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        when(repository.revokeAllForSubject(eq("staff@example.com"), eq(now))).thenReturn(3);

        assertThat(service.revokeAllFor("staff@example.com", now)).isEqualTo(3);
    }
}
