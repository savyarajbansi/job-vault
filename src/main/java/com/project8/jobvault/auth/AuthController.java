package com.project8.jobvault.auth;

import com.project8.jobvault.users.Role;
import com.project8.jobvault.users.RoleRepository;
import com.project8.jobvault.users.UserAccount;
import com.project8.jobvault.users.UserAccountFactory;
import com.project8.jobvault.users.UserAccountRepository;
import com.project8.jobvault.security.CorsProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    private static final String ROLE_JOB_SEEKER = "JOB_SEEKER";
    private static final String ROLE_EMPLOYER = "EMPLOYER";

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final UserAccountFactory userAccountFactory;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final TokenGenerator tokenGenerator;
    private final JwtProperties jwtProperties;
    private final AuthCookieProperties cookieProperties;
    private final AuthCookieService cookieService;
    private final Set<String> allowedOrigins;

    public AuthController(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            UserAccountFactory userAccountFactory,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            TokenGenerator tokenGenerator,
            JwtProperties jwtProperties,
            AuthCookieProperties cookieProperties,
            AuthCookieService cookieService,
            CorsProperties corsProperties) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.userAccountFactory = userAccountFactory;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.tokenGenerator = tokenGenerator;
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
        this.cookieService = cookieService;
        this.allowedOrigins = normalizeAllowedOrigins(corsProperties.getAllowedOrigins());
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

    @PostMapping("/auth/register")
    public ResponseEntity<AuthTokensResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        String email = normalizeEmail(request.email());
        if (userAccountRepository.findByEmail(email).isPresent()) {
            throw registrationInvalid(HttpStatus.CONFLICT, "email_exists");
        }
        String requestedRole = normalizeRole(request.role());
        Role role = resolveRegistrationRole(requestedRole);

        UserAccount user = userAccountFactory.newRegisteredUser(
                email,
                passwordEncoder.encode(request.password()),
                normalizeDisplayName(request.displayName()),
                role);

        UserAccount saved = userAccountRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(issueTokens(saved, response));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthTokensResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, cookieProperties.getRefreshTokenName())
                .orElseThrow(() -> refreshInvalid("missing_refresh_cookie"));
        if (refreshToken.isBlank()) {
            throw refreshInvalid("empty_refresh_cookie");
        }
        validateCsrfDoubleSubmit(request);
        validateOriginAndReferer(request);
        RefreshTokenRotation rotation = refreshTokenService.rotateRefreshToken(refreshToken);
        AuthTokensResponse responseBody = issueTokens(rotation.user(), response, rotation.refreshToken());
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        validateCsrfDoubleSubmit(request);
        validateOriginAndReferer(request);
        UserAccount user = requireUser(principal);
        refreshTokenService.revokeAllTokens(user);
        cookieService.clearRefreshTokenCookie(response);
        cookieService.clearCsrfTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserSummary> me(@AuthenticationPrincipal JwtPrincipal principal) {
        UserAccount user = requireUser(principal);
        return ResponseEntity.ok(toSummary(user));
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
        return new AuthTokensResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken.expiresAt(),
                toSummary(user));
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

    private AuthUserSummary toSummary(UserAccount user) {
        return new AuthUserSummary(user.getId(), user.getEmail(), user.getDisplayName(), extractRoles(user));
    }

    private UserAccount requireUser(JwtPrincipal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        UUID userId = principal.userId();
        if (userId == null) {
            throw new BadCredentialsException("Invalid authentication");
        }
        return userAccountRepository.findById(userId)
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

    private AuthErrorException registrationInvalid(HttpStatus status, String reason) {
        String message = switch (reason) {
            case "email_exists" -> AuthErrorCodes.MESSAGE_REGISTRATION_EMAIL_EXISTS;
            case "invalid_role" -> AuthErrorCodes.MESSAGE_REGISTRATION_INVALID_ROLE;
            case "role_not_configured" -> AuthErrorCodes.MESSAGE_REGISTRATION_ROLE_MISSING;
            default -> AuthErrorCodes.MESSAGE_REGISTRATION_INVALID;
        };
        return new AuthErrorException(
                AuthErrorCodes.REGISTRATION_INVALID,
                message,
                status,
                Map.of("reason", reason));
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String normalized = displayName.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return ROLE_JOB_SEEKER;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private Role resolveRegistrationRole(String requestedRole) {
        if (!ROLE_JOB_SEEKER.equals(requestedRole) && !ROLE_EMPLOYER.equals(requestedRole)) {
            throw registrationInvalid(HttpStatus.BAD_REQUEST, "invalid_role");
        }
        return roleRepository.findByName(requestedRole)
                .orElseThrow(() -> registrationInvalid(HttpStatus.SERVICE_UNAVAILABLE, "role_not_configured"));
    }

    private void validateCsrfDoubleSubmit(HttpServletRequest request) {
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
    }

    private void validateOriginAndReferer(HttpServletRequest request) {
        String origin = normalizeHeader(request.getHeader("Origin"));
        String referer = normalizeHeader(request.getHeader("Referer"));
        if (origin == null && referer == null) {
            throw refreshInvalid("missing_origin_or_referer");
        }
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw refreshInvalid("invalid_origin");
        }
        if (referer != null && !isAllowedReferer(referer)) {
            throw refreshInvalid("invalid_referer");
        }
    }

    private boolean isAllowedReferer(String referer) {
        for (String allowedOrigin : allowedOrigins) {
            if (referer.equals(allowedOrigin) || referer.startsWith(allowedOrigin + "/")) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Set<String> normalizeAllowedOrigins(List<String> configuredOrigins) {
        if (configuredOrigins == null) {
            return Set.of();
        }
        Set<String> origins = new LinkedHashSet<>();
        for (String configuredOrigin : configuredOrigins) {
            String normalized = normalizeHeader(configuredOrigin);
            if (normalized != null) {
                origins.add(normalized);
            }
        }
        return origins;
    }
}