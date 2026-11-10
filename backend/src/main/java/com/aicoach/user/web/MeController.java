package com.aicoach.user.web;

import com.aicoach.common.security.AuthenticatedUser;
import com.aicoach.common.security.RefreshTokenCookies;
import com.aicoach.user.dto.DeleteAccountRequest;
import com.aicoach.user.dto.ProfileResponse;
import com.aicoach.user.dto.UpdateProfileRequest;
import com.aicoach.user.dto.UserResponse;
import com.aicoach.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me", description = "The signed-in user's account and interview profile")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final UserService userService;
    private final RefreshTokenCookies cookies;

    public MeController(UserService userService, RefreshTokenCookies cookies) {
        this.userService = userService;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "Current user, including the interview profile once onboarding is done")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.me(AuthenticatedUser.id(jwt));
    }

    @PutMapping("/profile")
    @Operation(summary = "Create or replace the interview profile (target role, experience, companies, language)")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, e.g. unknown company id")
    public ProfileResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(AuthenticatedUser.id(jwt), request);
    }

    @DeleteMapping
    @Operation(summary = "Delete the account: personal data is erased and every session is revoked")
    @ApiResponse(responseCode = "204", description = "Deleted; refresh cookie cleared")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(AuthenticatedUser.id(jwt));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }
}
