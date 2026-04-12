package com.project8.jobvault.auth;

import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final TokenGenerator tokenGenerator;
    private final JwtProperties jwtProperties;
    private final AuthCookieProperties cookieProperties;
    private final AuthCookieService cookieService;

    public AuthController(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            TokenGenerator tokenGenerator,
            JwtProperties jwtProperties,
            AuthCookieProperties cookieProperties,
            AuthCookieService cookieService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.tokenGenerator = tokenGenerator;
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.cookieService = cookieService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthTokensResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        UserAccount user = userAccountRepository.findByEmail(request.email())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return ResponseEntity.ok(issueTokens(user, response));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthTokensResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, cookieProperties.getRefreshTokenName())
                .orElseThrow(() -> refreshInvalid("missing_refresh_cookie"));
        if (refreshToken.isBlank()) {
            throw refreshInvalid("empty_refresh_cookie");
        }
        String csrfCookie = readCookie(request, cookieProperties.getCsrfTokenName())
                .orElseThrow(() -> refreshInvalid("missing_csrf_cookie"));
        if (csrfCookie.isBlank()) {
            throw refreshInvalid("empty_csrf_cookie");
        }
        String csrfHeader = Optional.ofNullable(request.getHeader(AuthCookieService.CSRF_HEADER))
                .orElseThrow(() -> refreshInvalid("missing_csrf_header"));
        if (!csrfCookie.equals(csrfHeader)) {
            throw refreshInvalid("csrf_mismatch");
        }
        RefreshTokenRotation rotation = refreshTokenService.rotateRefreshToken(refreshToken);
        AuthTokensResponse responseBody = issueTokens(rotation.user(), response, rotation.refreshToken());
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal JwtPrincipal principal, HttpServletResponse response) {
        UserAccount user = requireUser(principal);
        refreshTokenService.revokeAllTokens(user);
        cookieService.clearRefreshTokenCookie(response);
        cookieService.clearCsrfTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserSummary> me(@AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(toSummary(principal));
    }

    private AuthTokensResponse issueTokens(UserAccount user, HttpServletResponse response) {
        RefreshTokenResult refreshToken = refreshTokenService.issueRefreshToken(user);
        return issueTokens(user, response, refreshToken);
    }

    private AuthTokensResponse issueTokens(UserAccount user, HttpServletResponse response,
            RefreshTokenResult refreshToken) {
        AccessTokenResult accessToken = jwtTokenService.issueAccessToken(user);
        String csrfToken = tokenGenerator.generateUrlSafeToken(jwtProperties.getCsrfTokenBytes());
        cookieService.setRefreshTokenCookie(response, refreshToken.token(), refreshToken.expiresAt());
        cookieService.setCsrfTokenCookie(response, csrfToken, refreshToken.expiresAt());
        AuthUserSummary userSummary = new AuthUserSummary(user.getId(), extractRoles(user));
        return new AuthTokensResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.expiresAt(),
                userSummary);
    }

    private Set<String> extractRoles(UserAccount user) {
        Set<String> roles = new HashSet<>();
        for (Role role : user.getRoles()) {
            if (role != null && role.getName() != null) {
                roles.add(role.getName());
            }
        }
        return roles;
    }

    private AuthUserSummary toSummary(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        Set<String> roles = new HashSet<>();
        if (principal.roles() != null) {
            roles.addAll(principal.roles());
        }
        return new AuthUserSummary(principal.userId(), roles);
    }

    private UserAccount requireUser(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findById(principal.userId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid authentication"));
    }

    private Optional<String> readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private AuthErrorException refreshInvalid(String reason) {
        return new AuthErrorException(
                AuthErrorCodes.REFRESH_INVALID,
                AuthErrorCodes.MESSAGE_REFRESH_INVALID,
                HttpStatus.UNAUTHORIZED,
                Map.of("reason", reason));
    }
}
