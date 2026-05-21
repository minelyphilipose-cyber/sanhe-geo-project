package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ArticlePromptTemplateDtos {
    private ArticlePromptTemplateDtos() {
    }

    public record TemplateVO(
            Long id,
            String name,
            String description,
            String channelGroupCode,
            String channelGroupName,
            String channelSubCode,
            String channelSubName,
            String agentSiteModule,
            String articleTypeCode,
            String articleTypeName,
            String questionSceneCode,
            String questionSceneName,
            Integer weight,
            Integer sortOrder,
            String status,
            String sampleOutputUrl,
            String contactDisclosureMode,
            Long currentVersionId,
            Integer currentVersionNo,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record TemplateDetailVO(
            TemplateVO template,
            VersionVO currentVersion,
            List<VersionVO> versions
    ) {
    }

    public record VersionVO(
            Long id,
            Long templateId,
            Integer versionNo,
            String systemPrompt,
            String userPromptTemplate,
            String variablesJson,
            String qualityRulesJson,
            String status,
            LocalDateTime createdAt,
            LocalDateTime publishedAt
    ) {
    }

    public record TemplateSaveRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 500) String description,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String channelGroupCode,
            @Size(max = 64) @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String channelSubCode,
            @Size(max = 32) String agentSiteModule,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String articleTypeCode,
            @Size(max = 32) @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String questionSceneCode,
            @NotNull @Min(0) @Max(100) Integer weight,
            Integer sortOrder,
            @NotBlank @Size(max = 32) String status,
            @Size(max = 500) String sampleOutputUrl,
            @Size(max = 32) String contactDisclosureMode,
            @NotBlank String systemPrompt,
            @NotBlank String userPromptTemplate,
            String variablesJson,
            String qualityRulesJson,
            Boolean publish
    ) {
    }

    public record VersionCreateRequest(
            @NotBlank String systemPrompt,
            @NotBlank String userPromptTemplate,
            String variablesJson,
            String qualityRulesJson,
            Boolean publish
    ) {
    }

    public record WeightUpdateRequest(@NotNull @Min(0) @Max(100) Integer weight) {
    }
}
