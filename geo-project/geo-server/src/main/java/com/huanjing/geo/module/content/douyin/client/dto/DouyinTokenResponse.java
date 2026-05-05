package com.huanjing.geo.module.content.douyin.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.huanjing.geo.module.content.douyin.client.jackson.LenientLongDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DouyinTokenResponse {
    /** Official field: data.access_token. Reviewed 2026-05-05. */
    @JsonProperty("access_token")
    private String accessToken;

    /** Official field: data.refresh_token. Reviewed 2026-05-05. */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /** Official field: data.open_id. Reviewed 2026-05-05. */
    @JsonProperty("open_id")
    private String openId;

    /** Official field: data.expires_in. Reviewed 2026-05-05. Lenient String/number parsing. */
    @JsonProperty("expires_in")
    @JsonDeserialize(using = LenientLongDeserializer.class)
    private Long expiresIn;

    /** Official field: data.refresh_expires_in. Reviewed 2026-05-05. Lenient String/number parsing. */
    @JsonProperty("refresh_expires_in")
    @JsonDeserialize(using = LenientLongDeserializer.class)
    private Long refreshExpiresIn;

    /** Official field: data.scope. Reviewed 2026-05-05. */
    private String scope;

    /** Official field: data.error_code. Reviewed 2026-05-05. */
    @JsonProperty("error_code")
    private Long errorCode;

    /** Official field: data.description. Reviewed 2026-05-05. */
    private String description;

    /** Official field: message. Reviewed 2026-05-05. */
    private String message;

    /** Official field: data.log_id. Reviewed 2026-05-05. */
    @JsonProperty("log_id")
    private String logId;

    private String rawBody;
}
