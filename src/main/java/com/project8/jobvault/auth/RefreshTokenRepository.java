package com.project8.jobvault.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findAllByUserId(UUID userId);

	List<RefreshToken> findAllByUserIdAndRevokedFalseOrderByExpiresAtAsc(UUID userId);
}
