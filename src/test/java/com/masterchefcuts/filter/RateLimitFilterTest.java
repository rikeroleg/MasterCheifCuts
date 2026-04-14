package com.masterchefcuts.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    // ── non-rate-limited path ─────────────────────────────────────────────────

    @Test
    void nonRateLimitedPath_requestPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/listings");
        req.setServletPath("/api/listings");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    // ── under the limit ───────────────────────────────────────────────────────

    @Test
    void loginPath_underLimit_requestPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setServletPath("/api/auth/login");
        req.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    // ── over the limit ────────────────────────────────────────────────────────

    @Test
    void loginPath_exceedsLimit_returns429() throws Exception {
        String ip = "192.168.1.50";

        // Exhaust 10 allowed requests
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setServletPath("/api/auth/login");
            req.setRemoteAddr(ip);
            filter.doFilterInternal(req, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        // 11th request should be blocked
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setServletPath("/api/auth/login");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, mock(FilterChain.class));

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Too many requests");
    }

    @Test
    void forgotPasswordPath_exceedsLimit_returns429() throws Exception {
        String ip = "172.16.0.5";

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
            req.setServletPath("/api/auth/forgot-password");
            req.setRemoteAddr(ip);
            filter.doFilterInternal(req, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
        req.setServletPath("/api/auth/forgot-password");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, mock(FilterChain.class));

        assertThat(res.getStatus()).isEqualTo(429);
    }

    // ── IP isolation ──────────────────────────────────────────────────────────

    @Test
    void differentIPs_doNotShareQuota() throws Exception {
        // Exhaust quota for IP A
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setServletPath("/api/auth/login");
            req.setRemoteAddr("10.0.0.1");
            filter.doFilterInternal(req, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        // IP B should still be allowed
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setServletPath("/api/auth/login");
        req.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    // ── X-Forwarded-For header ────────────────────────────────────────────────

    @Test
    void xForwardedFor_usedAsClientIp() throws Exception {
        String proxyIp = "203.0.113.5";

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setServletPath("/api/auth/login");
            req.addHeader("X-Forwarded-For", proxyIp + ", 10.0.0.99");
            filter.doFilterInternal(req, new MockHttpServletResponse(), mock(FilterChain.class));
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setServletPath("/api/auth/login");
        req.addHeader("X-Forwarded-For", proxyIp + ", 10.0.0.99");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, mock(FilterChain.class));

        assertThat(res.getStatus()).isEqualTo(429);
    }
}
