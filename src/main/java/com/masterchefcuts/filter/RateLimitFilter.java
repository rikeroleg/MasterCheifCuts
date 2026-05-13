package com.masterchefcuts.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding-window rate limiter applied to auth endpoints and user-facing mutation
 * endpoints to prevent brute-force, spam, and abuse.
 *
 * Limits are per-IP and evaluated independently per path prefix.
 */
@Component
@Order(1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    /**
     * Path prefix → [maxRequests, windowMs].
     * Auth endpoints: 10 req/min (brute-force protection).
     * Mutation endpoints: lower limits to prevent spam/abuse.
     */
    private static final Map<String, int[]> PATH_LIMITS = Map.of(
            "/api/auth/login",               new int[]{10, 60},
            "/api/auth/register",            new int[]{10, 60},
            "/api/auth/forgot-password",     new int[]{10, 60},
            "/api/auth/resend-verification", new int[]{10, 60},
            "/api/auth/refresh",             new int[]{10, 60},
            "/api/reviews",                  new int[]{5,  60},
            "/api/animal-requests",          new int[]{3,  60},
            "/api/waitlist",                 new int[]{10, 60},
            "/api/comments",                 new int[]{20, 60},
            "/api/contact",                  new int[]{3,  60}
    );

    // key: "ip:path" → sliding window of request timestamps
    private final Map<String, Deque<Long>> requestTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRateLimitLogAt = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupAt = new AtomicLong(0L);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String path = request.getServletPath();
            int[] limit = matchLimit(path);

            if (limit != null) {
                int maxRequests  = limit[0];
                long windowMs    = (long) limit[1] * 1_000L;
                String ip        = getClientIp(request);
                String bucketKey = ip + ":" + path;
                long now         = System.currentTimeMillis();

                Deque<Long> times = requestTimes.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
                synchronized (times) {
                    // Evict timestamps outside the sliding window
                    while (!times.isEmpty() && now - times.peekFirst() > windowMs) {
                        times.pollFirst();
                    }
                    if (times.size() >= maxRequests) {
                        Long lastLoggedAt = lastRateLimitLogAt.get(bucketKey);
                        if (lastLoggedAt == null || now - lastLoggedAt >= windowMs) {
                            log.warn("Rate limit exceeded: ip={} path={}", ip, path);
                            lastRateLimitLogAt.put(bucketKey, now);
                        } else {
                            log.debug("Rate limit exceeded (suppressed warn): ip={} path={}", ip, path);
                        }
                        response.setContentType("application/json");
                        response.setStatus(429);
                        response.getWriter().write(
                                "{\"error\":\"Too many requests \u2014 please wait before trying again\"}");
                        return;
                    }
                    times.addLast(now);
                }

                // Best-effort eviction of stale entries from the map
                long previousCleanupAt = lastCleanupAt.getAndUpdate(
                        prev -> (now - prev >= WINDOW_MS) ? now : prev
                );
                if (now - previousCleanupAt >= WINDOW_MS) {
                    requestTimes.entrySet().removeIf(e -> {
                        synchronized (e.getValue()) {
                            return e.getValue().isEmpty();
                        }
                    });
                    lastRateLimitLogAt.entrySet().removeIf(e -> !requestTimes.containsKey(e.getKey()));
                }
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Match the request path against configured limits.
     * Exact match first, then prefix match (for paths like /api/waitlist/{id}/join).
     */
    private int[] matchLimit(String path) {
        // Exact match
        int[] exact = PATH_LIMITS.get(path);
        if (exact != null) return exact;
        // Prefix match (e.g. /api/waitlist/123/join matches /api/waitlist)
        for (Map.Entry<String, int[]> entry : PATH_LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey() + "/") || path.startsWith(entry.getKey() + "?")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take the rightmost entry — added by the trusted upstream proxy (Cloud Run LB).
            // The leftmost entries can be spoofed by a malicious client.
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
