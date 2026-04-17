package com.masterchefcuts.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsConfigurationSource_beanIsCreated() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:*,https://*.masterchefcuts.com");
        CorsConfigurationSource source = config.corsConfigurationSource();
        assertThat(source).isNotNull();
    }
}
