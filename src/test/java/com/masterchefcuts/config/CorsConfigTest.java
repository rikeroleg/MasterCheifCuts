package com.masterchefcuts.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsFilter_beanIsCreated() {
        CorsConfig config = new CorsConfig();
        CorsFilter filter = config.corsFilter();
        assertThat(filter).isNotNull();
    }
}
