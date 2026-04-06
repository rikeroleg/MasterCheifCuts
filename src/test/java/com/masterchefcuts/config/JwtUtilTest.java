package com.masterchefcuts.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-at-least-32-chars-long-hmac!!");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3_600_000L);
    }

    @Test
    void generateToken_returnsNonNullJwt() {
        String token = jwtUtil.generateToken("user-1", "BUYER");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void extractId_returnsCorrectSubject() {
        String token = jwtUtil.generateToken("user-1", "BUYER");
        assertThat(jwtUtil.extractId(token)).isEqualTo("user-1");
    }

    @Test
    void extractRole_returnsCorrectRole() {
        String token = jwtUtil.generateToken("user-1", "FARMER");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("FARMER");
    }

    @Test
    void isValid_returnsTrueForValidToken() {
        String token = jwtUtil.generateToken("user-1", "BUYER");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void isValid_returnsFalseForTamperedToken() {
        assertThat(jwtUtil.isValid("tampered.eyJ.token")).isFalse();
    }

    @Test
    void isValid_returnsFalseForRandomString() {
        assertThat(jwtUtil.isValid("not-a-jwt-at-all")).isFalse();
    }
}
