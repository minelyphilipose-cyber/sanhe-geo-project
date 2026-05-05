package com.huanjing.geo.module.content.douyin.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DouyinImageUploadRequest {
    /** Official field: header access-token. Required. Reviewed 2026-05-05. */
    private String accessToken;

    /** Official field: query open_id. Required. Reviewed 2026-05-05. */
    private String openId;

    /** Official field: multipart part image. Required. Reviewed 2026-05-05. */
    @JsonIgnore
    private byte[] imageBytes;

    /** Multipart filename for image. Our client requires it; exact platform behavior needs real联调. */
    private String filename;

    /** Per-file content type. Optional; exact platform MIME handling needs real联调. */
    private String contentType;
}
