package com.aicoach.user.service;

/** Claims taken from a verified Google ID token. */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
}
