package com.huanjing.geo.module.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private UserVO user;

    @Data
    @Builder
    public static class UserVO {
        private Long id;
        private String username;
        private String displayName;
        private String role;
        private Long partnerId;
    }
}
