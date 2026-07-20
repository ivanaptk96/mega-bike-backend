package com.megabike.identity.application;

import com.megabike.identity.domain.RefreshToken;
import com.megabike.identity.domain.RefreshTokenRepository;
import com.megabike.identity.domain.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private static final int REFRESH_TOKEN_BYTES = 64;
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final RefreshTokenRepository refreshTokenRepository;
	private final SecureRandom secureRandom;
	private final Clock clock;
	private final long refreshTokenTtlSeconds;

	public RefreshTokenService(
			RefreshTokenRepository refreshTokenRepository,
			@Value("${megabike.security.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds
	) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.secureRandom = new SecureRandom();
		this.clock = Clock.systemUTC();
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
	}

	@Transactional
	public CreatedRefreshToken create(UserAccount userAccount) {
		Instant now = Instant.now(clock);
		String rawToken = generateRawToken();
		/*
		 * The raw refresh token is shown to the client only once.
		 * The database stores a SHA-256 hash so a database leak does not immediately
		 * give an attacker usable long-lived refresh tokens.
		 */
		String tokenHash = hash(rawToken);

		RefreshToken refreshToken = new RefreshToken(
				UUID.randomUUID(),
				userAccount,
				tokenHash,
				now.plusSeconds(refreshTokenTtlSeconds),
				now
		);

		refreshTokenRepository.save(refreshToken);

		return new CreatedRefreshToken(rawToken, refreshToken.getExpiresAt());
	}

	@Transactional(readOnly = true)
	public Optional<RefreshToken> findUsable(String rawToken) {
		Instant now = Instant.now(clock);
		return refreshTokenRepository.findByTokenHash(hash(rawToken))
				.filter(refreshToken -> !refreshToken.isRevoked())
				.filter(refreshToken -> !refreshToken.isExpired(now));
	}

	@Transactional
	public boolean revoke(String rawToken) {
		return refreshTokenRepository.findByTokenHash(hash(rawToken))
				.map(refreshToken -> {
					if (!refreshToken.isRevoked()) {
						refreshToken.revoke(Instant.now(clock));
					}
					return true;
				})
				.orElse(false);
	}

	private String generateRawToken() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return BASE64_URL_ENCODER.encodeToString(bytes);
	}

	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to hash refresh token", exception);
		}
	}

	public record CreatedRefreshToken(String value, Instant expiresAt) {
	}
}
