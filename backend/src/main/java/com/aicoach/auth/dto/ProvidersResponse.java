package com.aicoach.auth.dto;

/** Which sign-in methods the server has configured (the UI hides unavailable ones). */
public record ProvidersResponse(boolean password, boolean google) {
}
