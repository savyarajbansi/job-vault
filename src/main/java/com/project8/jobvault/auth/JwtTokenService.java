package com.project8.jobvault.auth;

import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public AccessTokenResult issueAccessToken(UserAccount user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.getAccessTokenMinutes()));
        List<String> roles = extractRoles(user.getRoles());
        String token = Jwts.builder()
            .issuer(properties.getIssuer())
            .subject(user.getId().toString())
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(expiresAt))
            .claim("roles", roles)
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
        return new AccessTokenResult(token, expiresAt);
    }

    public JwtPrincipal parseAccessToken(String token) {
        JwtParser parser = Jwts.parser()
            .requireIssuer(properties.getIssuer())
            .clock(() -> java.util.Date.from(clock.instant()))
            .verifyWith(signingKey)
            .build();
        Jws<Claims> claimsJws = parser.parseSignedClaims(token);
        Claims claims = claimsJws.getPayload();
        UUID userId = parseUserId(claims.getSubject());
        List<String> roles = extractRolesFromClaims(claims.get("roles"));
        return new JwtPrincipal(userId, roles);
    }

    private List<String> extractRoles(Collection<Role> roles) {
        List<String> names = new ArrayList<>();
        if (roles == null) {
            return names;
        }
        for (Role role : roles) {
            if (role != null && role.getName() != null) {
                names.add(role.getName());
            }
        }
        return names;
    }

    private List<String> extractRolesFromClaims(Object rolesClaim) {
        List<String> roles = new ArrayList<>();
        if (rolesClaim instanceof Collection<?> collection) {
            for (Object role : collection) {
                if (role != null) {
                    roles.add(role.toString());
                }
            }
        }
        return roles;
    }

    private UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Invalid subject", ex);
        }
    }
}
