package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TemplatePerspectiveDtos {
    private TemplatePerspectiveDtos() {
    }

    public record PerspectiveVO(
            String code,
            String name,
            String description,
            Boolean enabled,
            Integer sortOrder,
            Boolean thirdPartySubjectEnabled
    ) {
    }

    public record BrandChannelPerspectiveVO(
            Long id,
            Long brandId,
            String channelGroupCode,
            String channelSubCode,
            String perspectiveCode,
            String perspectiveName,
            Boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record BrandChannelPerspectiveSaveRequest(
            @NotNull Long brandId,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String channelGroupCode,
            @Size(max = 64) String channelSubCode,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String perspectiveCode,
            Boolean enabled
    ) {
    }

    public record PerspectiveStatusRequest(@NotNull Boolean enabled) {
    }

    public record ResolveResponse(
            String perspectiveCode,
            String perspectiveName,
            String matchedScope,
            Long matchedConfigId
    ) {
    }

    public record ConfigListResponse(
            List<PerspectiveVO> perspectives,
            List<BrandChannelPerspectiveVO> configs
    ) {
    }
}
