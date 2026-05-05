package com.huanjing.geo.module.content.douyin.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DouyinDtoDeserializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tokenResponse_parsesExpiresInFromString() throws Exception {
        DouyinTokenResponse response = objectMapper.readValue("""
                {
                  "access_token": "access-token",
                  "refresh_token": "refresh-token",
                  "open_id": "open-id",
                  "expires_in": "7200",
                  "refresh_expires_in": "2592000",
                  "scope": "video.create.bind",
                  "error_code": 0,
                  "message": "success",
                  "log_id": "log-id"
                }
                """, DouyinTokenResponse.class);

        assertEquals(7200L, response.getExpiresIn());
        assertEquals(2592000L, response.getRefreshExpiresIn());
    }

    @Test
    void tokenResponse_parsesExpiresInFromNumber() throws Exception {
        DouyinTokenResponse response = objectMapper.readValue("""
                {
                  "expires_in": 7200,
                  "refresh_expires_in": 2592000
                }
                """, DouyinTokenResponse.class);

        assertEquals(7200L, response.getExpiresIn());
        assertEquals(2592000L, response.getRefreshExpiresIn());
    }
}
