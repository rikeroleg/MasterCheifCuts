package com.masterchefcuts.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * A {@link CsrfTokenRepository} that delegates to {@link CookieCsrfTokenRepository}
 * but overrides {@link #saveToken} to never delete the {@code XSRF-TOKEN} cookie.
 *
 * <p>Spring Security 6 calls {@code saveToken(null, request, response)} after
 * validating each CSRF token in a stateless session, which causes the cookie to
 * be cleared.  For a React SPA that reads the cookie and sends it as the
 * {@code X-XSRF-TOKEN} header this means every successful mutating request
 * would invalidate the next one.  This class wraps the repository and skips the
 * deletion so the cookie persists for the entire browser session.
 */
public class SpaCookieCsrfTokenRepository implements CsrfTokenRepository {

    private final CookieCsrfTokenRepository delegate;

    private SpaCookieCsrfTokenRepository(CookieCsrfTokenRepository delegate) {
        this.delegate = delegate;
    }

    /** Factory method — returns a repository with {@code HttpOnly=false}. */
    public static SpaCookieCsrfTokenRepository withHttpOnlyFalse() {
        return new SpaCookieCsrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return delegate.generateToken(request);
    }

    /**
     * Save the token — but if {@code token} is {@code null} (meaning Spring
     * Security wants to delete the cookie after validation), do nothing.
     */
    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        if (token == null) {
            // Do NOT delete the cookie — the SPA needs it for subsequent requests.
            return;
        }
        delegate.saveToken(token, request, response);
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return delegate.loadToken(request);
    }
}
