package com.huanjing.geo.module.content.douyin.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DouyinCreateImageTextRequest {
    /** Official field: header access-token. Required. Reviewed 2026-05-05. */
    private String accessToken;

    /** Official field: query open_id. Required. Reviewed 2026-05-05. */
    private String openId;

    /** Official field: image_list. Required, 1-30 images. Reviewed 2026-05-05. */
    @JsonProperty("image_list")
    private List<String> imageList;

    /** Official field: text. <= 1000 chars per public error code 2114001. Reviewed 2026-05-05. */
    private String text;

    /** Official field: at_users. Optional. Reviewed 2026-05-05. */
    @JsonProperty("at_users")
    private List<String> atUsers;

    /** Official field: download_type. Optional. Reviewed 2026-05-05. */
    @JsonProperty("download_type")
    private Integer downloadType;

    /** Official field: private_status. Optional. Reviewed 2026-05-05. */
    @JsonProperty("private_status")
    private Integer privateStatus;

    /** Official field: micro_app_id. Optional. Reviewed 2026-05-05. */
    @JsonProperty("micro_app_id")
    private String microAppId;

    /** Official field: micro_app_title. Optional. Reviewed 2026-05-05. */
    @JsonProperty("micro_app_title")
    private String microAppTitle;

    /** Official field: micro_app_url. Optional. Reviewed 2026-05-05. */
    @JsonProperty("micro_app_url")
    private String microAppUrl;

    /** Official field: music_id. Optional. Reviewed 2026-05-05. */
    @JsonProperty("music_id")
    private Long musicId;

    /** Official field: poi_commerce. Optional. Reviewed 2026-05-05. */
    @JsonProperty("poi_commerce")
    private Boolean poiCommerce;

    /** Official field: poi_id. Optional. Reviewed 2026-05-05. */
    @JsonProperty("poi_id")
    private String poiId;

    /** Official field: task_id. Optional. Reviewed 2026-05-05. */
    @JsonProperty("task_id")
    private Long taskId;

    /** Official field: agent_client_key. Optional. Reviewed 2026-05-05. */
    @JsonProperty("agent_client_key")
    private String agentClientKey;
}
