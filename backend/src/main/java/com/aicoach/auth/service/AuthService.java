package com.aicoach.auth.service;

import com.aicoach.auth.dto.AuthResponse;
import com.aicoach.auth.dto.LoginRequest;
import com.aicoach.auth.dto.RegisterRequest;
import com.aicoach.auth.service.RefreshTokenService.IssuedToken;
import com.aicoach.auth.service.TokenService.AccessToken;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.web.ClientInfo;
import com.aicoach.user.domain.User;
import com.aicoach.user.service.GoogleIdentity;
import com.aicoach.user.service.UserService;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, password login, token refresh and logout. */
@Service
public class AuthService {

    private final UserService users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptService loginAttempts;
    /** Hash of a random string, compared against when the email is unknown, so response time does not reveal it. */
    private final String timingDecoyHash;

    public AuthService(UserService users, PasswordEncoder passwordEncoder, TokenService tokenService,
            RefreshTokenService refreshTokens, LoginAttemptService loginAttempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.loginAttempts = loginAttempts;
        this.timingDecoyHash = passwordEncoder.encode("timing-decoy-" + System.nanoTime());
    }

    /** Successful authentication: the JSON body for the client and the refresh token for the cookie. */
    public record AuthResult(AuthResponse response, IssuedToken refreshToken) {
    }

    @Transactional
    public AuthResult register(RegisterRequest request, ClientInfo client) {
        User user = users.createPasswordUser(request.email(), request.name(), passwordEncoder.encode(request.password()));
        return authenticated(user, refreshTokens.issueNewFamily(user.getId(), client));
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthResult login(LoginRequest request, ClientInfo client) {
        String email = UserService.normalizeEmail(request.email());
        loginAttempts.assertNotLocked(email, client.ip());
        Optional<User> candidate = users.findActiveByEmail(email).filter(u -> u.getPasswordHash() != null);
        boolean matches;
        if (candidate.isPresent()) {
            matches = passwordEncoder.matches(request.password(), candidate.get().getPasswordHash());
        } else {
            passwordEncoder.matches(request.password(), timingDecoyHash); // same cost as a real check
            matches = false;
        }
        if (!matches) {
            loginAttempts.recordFailure(email, client.ip());
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
        }
        loginAttempts.reset(email, client.ip());
        User user = candidate.get();
        return authenticated(user, refreshTokens.issueNewFamily(user.getId(), client));
    }

    @Transactional(noRollbackFor = AppException.class)
    public AuthResult refresh(String presentedRefreshToken, ClientInfo client) {
        IssuedToken rotated = refreshTokens.rotate(presentedRefreshToken, client);
        User user = users.getActive(rotated.userId());
        return authenticated(user, rotated);
    }

    @Transactional
    public void logout(String presentedRefreshToken) {
        refreshTokens.revokeFamilyOf(presentedRefreshToken);
    }

    @Transactional
    public AuthResult loginWithGoogle(GoogleIdentity identity, ClientInfo client) {
        User user = users.resolveGoogleUser(identity);
        return authenticated(user, refreshTokens.issueNewFamily(user.getId(), client));
    }

    private AuthResult authenticated(User user, IssuedToken refreshToken) {
        AccessToken access = tokenService.issue(user);
        return new AuthResult(new AuthResponse(users.toResponse(user), access.value(), access.expiresInSeconds()),
                refreshToken);
    }
}
