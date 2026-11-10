package com.aicoach.user.dto;

import java.util.UUID;

public record CompanyResponse(UUID id, String name, String slug) {
}
