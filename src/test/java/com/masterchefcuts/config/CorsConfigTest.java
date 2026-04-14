package com.masterchefcuts.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsFilter_beanIsCreated() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:*,https://*.masterchefcuts.com");
        CorsFilter filter = config.corsFilter();
        assertThat(filter).isNotNull();
    }
}
