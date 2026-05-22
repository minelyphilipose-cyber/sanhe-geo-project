package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public final class ArticleGenerationReadinessDtos {

    private ArticleGenerationReadinessDtos() {
    }

    @Data
    public static class ReadinessRequest {
        @NotNull
        private Long projectId;

        @Size(max = 6)
        private List<@Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String> questionSceneCodes;
    }

    public record ReadinessReport(
            Long projectId,
            Integer score,
            String status,
            List<BaseItem> baseItems,
            List<SceneImpact> sceneImpacts
    ) {
    }

    public record BaseItem(
            String code,
            String label,
            String status,
            String severity,
            String message,
            String source
    ) {
    }

    public record SceneImpact(
            String questionSceneCode,
            String questionSceneName,
            String status,
            Integer score,
            List<SceneItem> items
    ) {
    }

    public record SceneItem(
            String code,
            String severity,
            String message,
            String warningCode,
            Boolean requiresConfirmation
    ) {
    }
}
