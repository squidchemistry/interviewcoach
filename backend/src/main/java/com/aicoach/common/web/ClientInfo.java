package com.aicoach.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Who is calling, as far as the server can tell. {@code ip} is the real client address: Tomcat's RemoteIpValve
 * resolves {@code X-Forwarded-For} from trusted (private-network) proxies only.
 */
public record ClientInfo(String ip, String userAgent) {

    private static final int MAX_USER_AGENT = 512;

    public static ClientInfo from(HttpServletRequest request) {
        String ua = request.getHeader(HttpHeaders.USER_AGENT);
        if (ua != null && ua.length() > MAX_USER_AGENT) {
            ua = ua.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), ua);
    }
}
