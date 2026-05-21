package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class ArticleGenerationOptionDtos {
    private ArticleGenerationOptionDtos() {
    }

    public record GenerationOptionsVO(List<ChannelGroupVO> groups) {
    }

    public record ChannelGroupVO(
            String code,
            String name,
            String description,
            List<ChannelOptionVO> channels
    ) {
    }

    public record ChannelOptionVO(
            String channelGroupCode,
            String channelGroupName,
            String channelSubCode,
            String channelSubName,
            String label,
            String description,
            String contentStyle,
            boolean enabled,
            String disabledReason,
            int templateCount,
            List<TemplateOptionVO> templates
    ) {
    }

    public record TemplateOptionVO(
            Long templateId,
            Long templateVersionId,
            String templateName,
            String channelGroupCode,
            String channelSubCode,
            String agentSiteModule,
            String articleTypeCode,
            String articleTypeName,
            String questionSceneCode,
            String questionSceneName,
            Integer weight,
            Integer sortOrder
    ) {
    }

    public record AllocationPreviewRequest(
            @NotBlank String channelGroupCode,
            String channelSubCode,
            String questionSceneCode,
            @NotNull @Min(0) Integer count
    ) {
    }

    public record AllocationPreviewResponse(
            String channelGroupCode,
            String channelSubCode,
            int totalCount,
            List<AllocationItemVO> items
    ) {
    }

    public record AllocationItemVO(
            Long templateId,
            Long templateVersionId,
            String templateName,
            String articleTypeCode,
            String articleTypeName,
            String questionSceneCode,
            String questionSceneName,
            String agentSiteModule,
            Integer weight,
            Integer count
    ) {
    }
}
