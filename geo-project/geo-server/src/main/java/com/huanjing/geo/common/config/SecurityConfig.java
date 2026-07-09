package com.huanjing.geo.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.common.security.JwtAuthenticationFilter;
import com.huanjing.geo.common.security.PublicDashboardRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final PublicDashboardRateLimitFilter publicDashboardRateLimitFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), R.fail(401, "Unauthorized"));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), R.fail(403, "Forbidden"));
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/share/**").permitAll()
                .requestMatchers("/api/presale/exports/render/**").permitAll()
                .requestMatchers("/api/baseline-report/exports/render/**").permitAll()
                .requestMatchers("/api/public/authority-media/previews/**").permitAll()
                .requestMatchers("/api/public/brand-materials/**").permitAll()
                .requestMatchers("/api/public/dashboard/**").permitAll()
                .requestMatchers("/api/public/mobile-dashboard/**").permitAll()
                .requestMatchers("/api/public/wechat/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/platform-configs/*/logo").permitAll()
                .requestMatchers("/api/wechat/open-platform/auth/callback").permitAll()
                .requestMatchers("/api/wechat/open-platform/events/**").permitAll()
                .requestMatchers("/api/wechat/open-platform/messages/**").permitAll()
                .requestMatchers("/api/douyin/open-platform/auth/callback").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/douyin/open-platform/webhooks").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/version-check").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/bind").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/token/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/extension/tasks").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/extension/self-media-accounts").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/cookies/capture").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/fill-token/issue").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/fill-token/consume").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/browser-environment-login-status").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/browser-environment-accounts/*/login-status").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/local-agent/sign").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/tasks/*/ack").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/tasks/*/heartbeat").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/tasks/*/fail").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/extension/tasks/*/published").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/pairing-intents").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/pairings/claim").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/runtime-status").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/local-agent/self-media-schedules/platforms").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/local-agent/self-media-schedules/claim-next").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/local-agent/self-media-schedules/publish-checks/claim-next").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/self-media-schedules/*/heartbeat").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/self-media-schedules/*/executions/failed").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/self-media-schedules/*/executions/filled").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/self-media-schedules/*/executions/scheduled").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/self-media-schedules/*/executions/published").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/self-media-schedules/*/publish-checks/published").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/self-media-schedules/*/publish-checks/unknown").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/local-agent/self-media-schedules/*/publish-checks/failed").permitAll()
                .requestMatchers("/doc.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(publicDashboardRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
