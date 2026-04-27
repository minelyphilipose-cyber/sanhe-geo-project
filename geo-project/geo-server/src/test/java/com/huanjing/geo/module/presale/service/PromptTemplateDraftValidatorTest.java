package com.huanjing.geo.module.presale.service;

import com.huanjing.geo.module.presale.dto.request.PromptTemplateDraftRequest;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PromptTemplateDraftValidatorTest {

    private final PromptTemplateDraftValidator validator =
            new PromptTemplateDraftValidator(mock(PresalePromptTemplateMapper.class));

    @Test
    void validate_acceptsFullActiveTemplateSet() {
        List<PresalePromptTemplate> sources = List.of(
                source(1L, "推荐型", 0),
                source(2L, "对比型", 1)
        );
        List<PromptTemplateDraftRequest> drafts = List.of(
                draft(1L, "{brand} 在 {region} 的推荐度如何?"),
                draft(2L, "{brand} 和 {competitor} 相比有什么优势?")
        );

        assertTrue(validator.validate(drafts, sources).isEmpty());
    }

    @Test
    void validate_rejectsCompetitorVarMismatchFromSourceTemplate() {
        List<PresalePromptTemplate> sources = List.of(
                source(1L, "推荐型", 0),
                source(2L, "对比型", 1)
        );
        List<PromptTemplateDraftRequest> drafts = List.of(
                draft(1L, "{brand} 和 {competitor} 相比如何?"),
                draft(2L, "{brand} 的优势是什么?")
        );

        List<PromptTemplateDraftValidator.ValidationError> errors = validator.validate(drafts, sources);

        assertEquals(2, errors.stream()
                .filter(e -> "promptContent".equals(e.field()))
                .count());
    }

    @Test
    void validate_rejectsUnknownAndMalformedVariables() {
        List<PresalePromptTemplate> sources = List.of(
                source(1L, "推荐型", 0),
                source(2L, "问题型", 0),
                source(3L, "认知型", 0)
        );
        List<PromptTemplateDraftRequest> drafts = List.of(
                draft(1L, "{Competitor} 是否会被替换?"),
                draft(2L, "{ competitor } 是否会被替换?"),
                draft(3L, "{foo} 是否会被替换?")
        );

        List<PromptTemplateDraftValidator.ValidationError> errors = validator.validate(drafts, sources);

        assertTrue(errors.stream().anyMatch(e -> e.message().contains("未知变量")));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("变量格式不合法")));
    }

    @Test
    void validate_rejectsMissingOrDuplicatedSourceTemplates() {
        List<PresalePromptTemplate> sources = List.of(
                source(1L, "推荐型", 0),
                source(2L, "问题型", 0)
        );
        List<PromptTemplateDraftRequest> drafts = List.of(
                draft(1L, "{brand} 怎么样?"),
                draft(1L, "{brand} 怎么样?")
        );

        List<PromptTemplateDraftValidator.ValidationError> errors = validator.validate(drafts, sources);

        assertTrue(errors.stream().anyMatch(e -> e.message().contains("重复")));
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("问题型 Prompt 数量必须为 1 条")));
    }

    private static PromptTemplateDraftRequest draft(Long sourceTemplateId, String promptContent) {
        PromptTemplateDraftRequest draft = new PromptTemplateDraftRequest();
        draft.setSourceTemplateId(sourceTemplateId);
        draft.setPromptContent(promptContent);
        return draft;
    }

    private static PresalePromptTemplate source(Long id, String category, int hasCompetitorVar) {
        PresalePromptTemplate source = new PresalePromptTemplate();
        source.setId(id);
        source.setPromptCode("P" + id);
        source.setTemplateVersion("v3");
        source.setCategory(category);
        source.setBusinessValue("高");
        source.setPromptContent("source " + id);
        source.setHasCompetitorVar(hasCompetitorVar);
        source.setSortOrder(id.intValue());
        source.setEnabled(1);
        return source;
    }
}
