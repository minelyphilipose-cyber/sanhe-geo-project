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
public class DouyinImageUploadResponse {
    /** Official field: data.image.image_id. Reviewed 2026-05-05. */
    @JsonProperty("image_id")
    private String imageId;

    /** Official field: data.image.width. Reviewed 2026-05-05. */
    private Integer width;

    /** Official field: data.image.height. Reviewed 2026-05-05. */
    private Integer height;

    /** Official field: data.error_code. Reviewed 2026-05-05. */
    @JsonProperty("error_code")
    private Long errorCode;

    /** Official field: data.description. Reviewed 2026-05-05. */
    private String description;

    /** Official field: extra.error_code. Reviewed 2026-05-05. */
    @JsonProperty("extra_error_code")
    private Long extraErrorCode;

    /** Official field: extra.description. Reviewed 2026-05-05. */
    @JsonProperty("extra_description")
    private String extraDescription;

    /** Official field: extra.logid. Reviewed 2026-05-05. */
    @JsonProperty("logid")
    private String logId;

    /** Official field: extra.now. Reviewed 2026-05-05. Lenient String/number parsing. */
    @JsonDeserialize(using = LenientLongDeserializer.class)
    private Long now;

    /** Official field: extra.sub_error_code. Reviewed 2026-05-05. */
    @JsonProperty("sub_error_code")
    private Long subErrorCode;

    /** Official field: extra.sub_description. Reviewed 2026-05-05. */
    @JsonProperty("sub_description")
    private String subDescription;

    private String rawBody;
}
