package com.masterchefcuts.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Eagerly evaluates the deferred CSRF token on every request so that
 * {@link com.masterchefcuts.config.SpaCookieCsrfTokenRepository} writes the
 * {@code XSRF-TOKEN} cookie in the response before the body is committed.
 *
 * <p>Spring Security 6 uses a lazy (deferred) token that is only resolved when
 * the token value is first accessed.  Without this filter, GET requests never
 * trigger the token to be written to a cookie, and the SPA has no token to
 * include as the {@code X-XSRF-TOKEN} header on subsequent mutating requests.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // forces deferred load → triggers Set-Cookie header
        }
        filterChain.doFilter(request, response);
    }
}
