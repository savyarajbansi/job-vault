package com.project8.jobvault.auth;

import com.project8.jobvault.users.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final TokenGenerator tokenGenerator;
    private final JwtProperties properties;
    private final Clock clock;

    public RefreshTokenService(
        RefreshTokenRepository refreshTokenRepository,
        TokenHasher tokenHasher,
        TokenGenerator tokenGenerator,
        JwtProperties properties,
        Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RefreshTokenResult issueRefreshToken(UserAccount user) {
        enforceSessionLimit(user.getId());
        String rawToken = tokenGenerator.generateUrlSafeToken(properties.getRefreshTokenBytes());
        Instant expiresAt = clock.instant().plus(Duration.ofDays(properties.getRefreshTokenDays()));
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHasher.hash(rawToken));
        token.setExpiresAt(expiresAt);
        token.setRevoked(false);
        refreshTokenRepository.save(token);
        return new RefreshTokenResult(rawToken, expiresAt);
    }

    @Transactional
    public RefreshTokenRotation rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw refreshInvalid("missing_refresh_token");
        }
        String hash = tokenHasher.hash(rawToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> refreshInvalid("refresh_token_not_found"));
        Instant now = clock.instant();
        if (storedToken.isRevoked()) {
            throw refreshInvalid("refresh_token_revoked");
        }
        if (storedToken.getReplacedByToken() != null) {
            revokeAllTokens(storedToken.getUser());
            throw refreshInvalid("refresh_token_reuse_detected");
        }
        if (storedToken.getExpiresAt().isBefore(now)) {
            storedToken.setRevoked(true);
            storedToken.setRevokedAt(now);
            refreshTokenRepository.save(storedToken);
            throw refreshInvalid("refresh_token_expired");
        }
        RefreshTokenResult replacement = createReplacementToken(storedToken.getUser(), now);
        RefreshToken replacementEntity = new RefreshToken();
        replacementEntity.setUser(storedToken.getUser());
        replacementEntity.setTokenHash(tokenHasher.hash(replacement.token()));
        replacementEntity.setExpiresAt(replacement.expiresAt());
        replacementEntity.setRevoked(false);
        refreshTokenRepository.save(replacementEntity);

        storedToken.setRevoked(true);
        storedToken.setRevokedAt(now);
        storedToken.setReplacedByToken(replacementEntity);
        refreshTokenRepository.save(storedToken);
        return new RefreshTokenRotation(storedToken.getUser(), replacement);
    }

    @Transactional
    public void revokeAllTokens(UserAccount user) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(user.getId());
        Instant now = clock.instant();
        for (RefreshToken token : tokens) {
            if (!token.isRevoked()) {
                token.setRevoked(true);
                token.setRevokedAt(now);
                refreshTokenRepository.save(token);
            }
        }
    }

    private RefreshTokenResult createReplacementToken(UserAccount user, Instant now) {
        String rawToken = tokenGenerator.generateUrlSafeToken(properties.getRefreshTokenBytes());
        Instant expiresAt = now.plus(Duration.ofDays(properties.getRefreshTokenDays()));
        return new RefreshTokenResult(rawToken, expiresAt);
    }

    private void enforceSessionLimit(java.util.UUID userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository
            .findAllByUserIdAndRevokedFalseOrderByExpiresAtAsc(userId);
        Instant now = clock.instant();
        int maxSessions = properties.getMaxSessions();
        int activeCount = 0;
        for (RefreshToken token : activeTokens) {
            if (token.getExpiresAt().isAfter(now)) {
                activeCount++;
            }
        }
        if (activeCount < maxSessions) {
            return;
        }
        int toRevoke = activeCount - maxSessions + 1;
        for (RefreshToken token : activeTokens) {
            if (toRevoke <= 0) {
                break;
            }
            if (token.getExpiresAt().isAfter(now)) {
                token.setRevoked(true);
                token.setRevokedAt(now);
                refreshTokenRepository.save(token);
                toRevoke--;
            }
        }
    }

    private AuthErrorException refreshInvalid(String reason) {
        return new AuthErrorException(
            AuthErrorCodes.REFRESH_INVALID,
            AuthErrorCodes.MESSAGE_REFRESH_INVALID,
            HttpStatus.UNAUTHORIZED,
            Map.of("reason", reason)
        );
    }
}
