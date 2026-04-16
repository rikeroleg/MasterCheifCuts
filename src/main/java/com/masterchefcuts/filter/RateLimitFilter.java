package com.masterchefcuts.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter: max 10 POST requests per minute per IP on
 * /api/auth/login and /api/auth/register.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MS   = 60_000L;

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/resend-verification",
            "/api/auth/refresh"
    );

    private final Map<String, Deque<Long>> requestTimes = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if ("POST".equalsIgnoreCase(request.getMethod())
                && RATE_LIMITED_PATHS.contains(request.getServletPath())) {

            String ip  = getClientIp(request);
            long   now = System.currentTimeMillis();

            Deque<Long> times = requestTimes.computeIfAbsent(ip, k -> new ArrayDeque<>());
            synchronized (times) {
                // Evict timestamps outside the sliding window
                while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                    times.pollFirst();
                }
                if (times.size() >= MAX_REQUESTS) {
                    response.setContentType("application/json");
                    response.setStatus(429);
                    response.getWriter().write(
                            "{\"error\":\"Too many requests — please wait before trying again\"}");
                    return;
                }
                times.addLast(now);
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
