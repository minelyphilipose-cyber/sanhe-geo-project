package com.huanjing.geo.module.content.douyin.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DouyinRefreshAccessTokenRequest {
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    /** Official field: client_key. Required. Reviewed 2026-05-05. */
    @JsonProperty("client_key")
    private String clientKey;

    /** Official field: grant_type. Required, fixed refresh_token. Reviewed 2026-05-05. */
    @Builder.Default
    @JsonProperty("grant_type")
    private String grantType = GRANT_TYPE_REFRESH_TOKEN;

    /** Official field: refresh_token. Required. Reviewed 2026-05-05. */
    @JsonProperty("refresh_token")
    private String refreshToken;
}
