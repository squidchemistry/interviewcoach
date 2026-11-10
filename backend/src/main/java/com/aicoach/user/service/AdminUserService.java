package com.aicoach.user.service;

import com.aicoach.audit.service.AuditService;
import com.aicoach.common.error.AppException;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.web.PageResponse;
import com.aicoach.user.domain.Role;
import com.aicoach.user.domain.User;
import com.aicoach.user.dto.AdminUserResponse;
import com.aicoach.user.repository.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminUserService {

    static final String ROLE_CHANGED = "USER_ROLE_CHANGED";

    private final UserRepository users;
    private final AuditService audit;

    public AdminUserService(UserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String query, Role role, int page, int pageSize) {
        String pattern = StringUtils.hasText(query) ? "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%" : null;
        PageRequest pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(users.searchActive(pattern, role, pageable), UserMapper::toAdminResponse);
    }

    /**
     * Changes a user's role. The new role reaches the user's access tokens at their next refresh (≤ 15 minutes),
     * because the role is read from the database whenever an access token is issued.
     */
    @Transactional
    public AdminUserResponse changeRole(UUID actorId, UUID userId, Role newRole) {
        if (actorId.equals(userId)) {
            throw new AppException(ErrorCode.CONFLICT, "You cannot change your own role");
        }
        User user = users.findById(userId).filter(u -> !u.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User not found"));
        Role previous = user.getRole();
        if (previous != newRole) {
            user.changeRole(newRole);
            users.flush();
            audit.record(actorId, ROLE_CHANGED, "USER", userId, Map.of("role", previous), Map.of("role", newRole));
        }
        return UserMapper.toAdminResponse(user);
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
