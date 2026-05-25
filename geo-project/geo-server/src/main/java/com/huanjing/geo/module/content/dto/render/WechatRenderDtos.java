package com.huanjing.geo.module.content.dto.render;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WechatRenderDtos {
    private WechatRenderDtos() {
    }

    @Data
    public static class TemplateParseRequest {
        @NotBlank
        private String sourceHtml;
        private String sourceType;
    }

    @Data
    public static class TemplateSaveRequest {
        @NotBlank
        private String name;
        private String description;
        @NotBlank
        private String sourceType;
        @NotBlank
        private String sourceHtml;
        @NotNull
        private Map<String, RoleSchema> roles;
        private BodyStyle bodyStyle;
    }

    @Data
    public static class TemplateUpdateRequest {
        @NotBlank
        private String name;
        private String description;
        private String status;
    }

    @Data
    public static class TemplateVersionSaveRequest {
        @NotBlank
        private String sourceType;
        @NotBlank
        private String sourceHtml;
        @NotNull
        private Map<String, RoleSchema> roles;
        private BodyStyle bodyStyle;
    }

    @Data
    public static class TemplateParseResponse {
        private String sourceType;
        private BodyStyle bodyStyle;
        private List<TemplateSlice> slices = new ArrayList<>();
        private List<TemplateRoleDraft> roles = new ArrayList<>();
        private List<RenderWarning> warnings = new ArrayList<>();
    }

    @Data
    public static class TemplateSlice {
        private String id;
        private int order;
        private String suggestedRole;
        private String role;
        private String fingerprint;
        private boolean outlier;
        private String html;
        private String previewText;
        private String previewHtml;
        private List<String> warnings = new ArrayList<>();
    }

    @Data
    public static class TemplateRoleDraft {
        private String role;
        private String wrapperHtml;
        private Boolean wrapperSafe;
        private int reuseCount;
        private List<String> sliceIds = new ArrayList<>();
        private boolean needsConfirmation;
    }

    @Data
    public static class RoleSchema {
        @NotBlank
        private String wrapperHtml;
        private Boolean wrapperSafe;
    }

    @Data
    public static class BodyStyle {
        private String fontSize;
        private String lineHeight;
        private String letterSpacing;
        private String color;
        private String textAlign;
        private String paragraphMargin;
    }

    @Data
    public static class ArticleRenderConfigResponse {
        private Long articleId;
        private String platformCode;
        private Long templateId;
        private Long templateVersionId;
        private List<ArticleBlock> blocks = new ArrayList<>();
        private RenderAnnotations annotations = new RenderAnnotations();
        private Map<String, Object> renderConfig;
        private List<RenderWarning> warnings = new ArrayList<>();
    }

    @Data
    public static class ArticleRenderSaveRequest {
        @NotNull
        private Long templateVersionId;
        private RenderAnnotations annotations = new RenderAnnotations();
        private Map<String, Object> renderConfig;
    }

    @Data
    public static class ArticleRenderPreviewRequest {
        private Long templateVersionId;
        private RenderAnnotations annotations;
        private Map<String, Object> renderConfig;
    }

    @Data
    public static class ArticleRenderPreviewResponse {
        private String html;
        private List<RenderWarning> warnings = new ArrayList<>();
    }

    @Data
    public static class RenderAnnotations {
        private List<RenderMark> marks = new ArrayList<>();
        private List<RenderInsert> inserts = new ArrayList<>();
    }

    @Data
    public static class RenderMark {
        private String blockId;
        private Integer order;
        private String role;
    }

    @Data
    public static class RenderInsert {
        private String afterBlockId;
        private String role;
        private String content;
    }

    @Data
    public static class ArticleBlock {
        private String id;
        private String type;
        private String defaultRole;
        private List<String> allowedRoles = new ArrayList<>();
        private String text;
        private String html;
        private String imageUrl;
        private String imageAlt;
        private String contentHash;
        private int order;
    }

    @Data
    public static class RenderWarning {
        private String type;
        private String blockId;
        private String role;
        private String message;

        public static RenderWarning of(String type, String message) {
            RenderWarning warning = new RenderWarning();
            warning.setType(type);
            warning.setMessage(message);
            return warning;
        }

        public static RenderWarning of(String type, String blockId, String role, String message) {
            RenderWarning warning = of(type, message);
            warning.setBlockId(blockId);
            warning.setRole(role);
            return warning;
        }
    }
}
