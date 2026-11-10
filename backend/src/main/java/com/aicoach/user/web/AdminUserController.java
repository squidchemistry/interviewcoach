package com.aicoach.user.web;

import com.aicoach.common.security.AuthenticatedUser;
import com.aicoach.common.web.PageResponse;
import com.aicoach.user.domain.Role;
import com.aicoach.user.dto.AdminUserResponse;
import com.aicoach.user.dto.UpdateRoleRequest;
import com.aicoach.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (enforced for all of {@code /api/v1/admin/**} in the security filter chain). */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUsers;

    public AdminUserController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    @GetMapping
    @Operation(summary = "Search active users by email or name, newest first")
    public PageResponse<AdminUserResponse> search(
            @RequestParam(name = "q", required = false) @Size(max = 100) String q,
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return adminUsers.search(q, role, page, pageSize);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change a user's role (audited); takes effect at the user's next token refresh")
    @ApiResponse(responseCode = "409", description = "CONFLICT when changing your own role")
    public AdminUserResponse changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return adminUsers.changeRole(AuthenticatedUser.id(jwt), id, request.role());
    }
}
