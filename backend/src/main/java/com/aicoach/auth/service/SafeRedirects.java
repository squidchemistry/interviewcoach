package com.aicoach.auth.service;

import java.util.regex.Pattern;

/**
 * Open-redirect protection: post-login redirect targets must be same-site relative paths such as
 * {@code /profile?tab=1}. Absolute URLs, protocol-relative {@code //evil.com} and backslash tricks are rejected.
 */
public final class SafeRedirects {

    public static final String DEFAULT = "/";
    private static final Pattern SAFE_PATH = Pattern.compile("^/(?![/\\\\])[A-Za-z0-9/_.~%?=&-]{0,200}$");

    private SafeRedirects() {
    }

    public static boolean isSafe(String path) {
        return path != null && SAFE_PATH.matcher(path).matches() && !path.contains("//");
    }

    public static String sanitize(String path) {
        return isSafe(path) ? path : DEFAULT;
    }
}
