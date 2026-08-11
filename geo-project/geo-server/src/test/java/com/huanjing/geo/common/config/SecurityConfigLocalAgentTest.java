package com.huanjing.geo.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.exception.GlobalExceptionHandler;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.common.security.JwtAuthenticationFilter;
import com.huanjing.geo.common.security.JwtTokenProvider;
import com.huanjing.geo.common.security.PublicDashboardRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityConfigLocalAgentTest {

    @Test
    void localAgentRuntimeStatusReachesSignedRequestVerificationWithoutJwt() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestWebConfig.class, SecurityConfig.class, GlobalExceptionHandler.class, TestController.class);
            context.refresh();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean(FilterChainProxy.class))
                    .build();

            mockMvc.perform(post("/api/v1/local-agent/runtime-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("helper access is required"));
            mockMvc.perform(get("/api/v1/local-agent/session/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("helper access is required"));
        }
    }

    @Test
    void extensionTokenEndpointsReachTheirOwnVerificationWithoutJwt() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestWebConfig.class, SecurityConfig.class, GlobalExceptionHandler.class, TestController.class);
            context.refresh();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean(FilterChainProxy.class))
                    .build();

            mockMvc.perform(get("/api/v1/extension/runtime-config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("extension token is required"));
            mockMvc.perform(post("/api/v1/extension/runtime-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("extension token is required"));
            mockMvc.perform(post("/api/v1/extension/brands/990006013/browser-environment-login-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("extension token is required for brand 990006013"));
        }
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtTokenProvider.class), mock(RedisTemplate.class));
        }

        @Bean
        PublicDashboardRateLimitFilter publicDashboardRateLimitFilter(ObjectMapper objectMapper) {
            return new PublicDashboardRateLimitFilter(mock(StringRedisTemplate.class), objectMapper);
        }
    }

    @RestController
    static class TestController {

        @PostMapping("/api/v1/local-agent/runtime-status")
        R<Void> runtimeStatus() {
            throw new BizException(400, "helper access is required");
        }

        @GetMapping("/api/v1/local-agent/session/status")
        R<Void> localAgentSessionStatus() {
            throw new BizException(400, "helper access is required");
        }

        @GetMapping("/api/v1/extension/runtime-config")
        R<Void> extensionRuntimeConfig() {
            throw new BizException(400, "extension token is required");
        }

        @PostMapping("/api/v1/extension/runtime-status")
        R<Void> extensionRuntimeStatus() {
            throw new BizException(400, "extension token is required");
        }

        @PostMapping("/api/v1/extension/brands/{brandId}/browser-environment-login-status")
        R<Void> brandLoginStatus(@PathVariable Long brandId) {
            throw new BizException(400, "extension token is required for brand " + brandId);
        }
    }
}
