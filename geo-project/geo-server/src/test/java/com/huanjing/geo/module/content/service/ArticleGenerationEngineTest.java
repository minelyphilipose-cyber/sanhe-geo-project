package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmCallStatus;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmInvokeResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.measurement.LlmCallMeasurementContext;
import com.huanjing.geo.common.llm.measurement.LlmObservationScope;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class ArticleGenerationEngineTest {

    @Test
    void douyinKeepsNineHundredCharacterBodyWithoutTruncationOrAnotherModelCall() throws Exception {
        String article = "# 服务细节如何理解\n\n" + "文".repeat(900);
        var fixture = toutiaoFixture(article, "不会调用");
        var generated = fixture.engine().generate(douyinInput());
        assertEquals(article, generated.content());
        verify(fixture.facade(), times(1)).execute(any());
        assertThat(generated.quality().issues()).extracting(BatchArticleQualityChecker.Issue::type)
                .doesNotContain("content_truncated", "body_rewritten");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {699, 901})
    void douyinRewritesOutOfRangeBodyUsingOriginalFactsAndKeepsTitle(int length) throws Exception {
        String original = "# 服务细节如何理解\n\n" + "文".repeat(length);
        String rewrittenBody = "资质覆盖服务A，单次服务60分钟。" + "细节说明".repeat(185) + "适用条件保留完整。";
        var fixture = toutiaoFixture(original, "# 新标题\n\n" + rewrittenBody);
        var generated = fixture.engine().generate(douyinInput());
        assertEquals("# 服务细节如何理解\n\n" + rewrittenBody, generated.content());
        assertEquals(20, generated.result().promptTokens());
        assertThat(generated.quality().issues()).extracting(BatchArticleQualityChecker.Issue::type)
                .contains("body_rewritten").doesNotContain("content_truncated");
        var requests = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(fixture.facade(), times(2)).execute(requests.capture());
        assertThat(requests.getAllValues().get(1).prompt()).contains("服务时长60分钟", "700—900字", original,
                "保留关键事实、单位与条件", "不使用“以某品牌为例", "不新增事实");
        assertEquals("test-run:douyin-body-rewrite", requests.getAllValues().get(1).measurementContext().runId());
    }

    @Test
    void douyinStopsAfterOneInvalidBodyRewrite() throws Exception {
        var fixture = toutiaoFixture("# 标题\n\n简短正文", "# 标题\n\n仍然太短");
        var error = org.junit.jupiter.api.Assertions.assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> fixture.engine().generate(douyinInput()));
        assertThat(error.getMessage()).contains("自动重写后篇幅仍不符合要求", "不会截断正文");
        verify(fixture.facade(), times(2)).execute(any());
    }

    private ArticleGenerationEngine.GenerateInput douyinInput() {
        var base = toutiaoInput();
        return new ArticleGenerationEngine.GenerateInput(base.project(), base.brand(), "system",
                "真实品牌资质覆盖服务A，服务时长60分钟，仅限资料中所述项目。", null, null,
                true, false, true, List.of(), 20, base.effectiveTemperature(), base.measurementContext(), 900,
                new ArticleGenerationEngine.StructureValidationContext("self_media", "douyin_image_text", false, "服务流程"));
    }

    @Test
    void toutiaoKeepsNaturalQuestionTitleAndBodyWithoutAnotherModelCall() throws Exception {
        String article = "# 阜阳SPA价格不同，差别主要在哪里？\n\n收费由服务项目与时长组成，品牌事实保持原文。";
        ToutiaoFixture fixture = toutiaoFixture(article, "不会调用");
        var generated = fixture.engine().generate(toutiaoInput());
        assertEquals(article, generated.content());
        assertEquals("阜阳SPA价格不同，差别主要在哪里？", generated.title());
        verify(fixture.facade(), times(1)).execute(any());
    }

    @Test
    void toutiaoRewritesOnlyOverlongTitleAndMeasuresTheExtraCall() throws Exception {
        String body = "\n\n收费由服务项目与时长组成。\n\n测试品牌资料、联系方式与正文结构保持原文。";
        ToutiaoFixture fixture = toutiaoFixture("# 阜阳SPA价格为什么不同以及具体服务项目时长费用包含内容有什么关系" + body,
                "# 阜阳SPA价格不同，差别主要在哪里？");
        var generated = fixture.engine().generate(toutiaoInput());
        assertEquals("# 阜阳SPA价格不同，差别主要在哪里？" + body, generated.content());
        assertThat(generated.quality().issues()).extracting(BatchArticleQualityChecker.Issue::type)
                .contains("title_rewritten").doesNotContain("title_truncated");
        assertEquals(20, generated.result().promptTokens());
        assertEquals(40, generated.result().completionTokens());
        var requests = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(fixture.facade(), times(2)).execute(requests.capture());
        LlmCallRequest rewrite = requests.getAllValues().get(1);
        assertThat(rewrite.prompt()).contains("本篇所选 A 类问题：阜阳SPA价格差异为什么大？", body,
                "不修改或输出正文", "可以使用自然疑问句，也可以使用自然说明句");
        assertEquals("test-run:toutiao-title-rewrite", rewrite.measurementContext().runId());
    }

    @Test
    void toutiaoRejectsAnInvalidRewriteRatherThanTruncatingOrReplacingBody() throws Exception {
        ToutiaoFixture fixture = toutiaoFixture("# 阜阳SPA强烈推荐\n\n收费构成正文。", "阜阳SPA闭眼选这家");
        var error = org.junit.jupiter.api.Assertions.assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> fixture.engine().generate(toutiaoInput()));
        assertThat(error.getMessage()).contains("重拟后仍不符合", "不会强行截断");
        verify(fixture.facade(), times(2)).execute(any());
    }

    @Test
    void toutiaoDoesNotExtractProseOpeningAsTitle() throws Exception {
        ToutiaoFixture fixture = toutiaoFixture("阜阳SPA服务有不同项目与时长，这里是正文开场。\n\n后续内容。", "不会调用");
        var error = org.junit.jupiter.api.Assertions.assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> fixture.engine().generate(toutiaoInput()));
        assertThat(error.getMessage()).contains("缺少独立标题", "不会截取正文");
        verify(fixture.facade(), times(1)).execute(any());
    }

    private ToutiaoFixture toutiaoFixture(String article, String rewrittenTitle) throws Exception {
        LlmCallFacade facade = mock(LlmCallFacade.class);
        ArticleModelResolver resolver = mock(ArticleModelResolver.class);
        ArticleAiDraftPromptFilter filter = mock(ArticleAiDraftPromptFilter.class);
        when(filter.filterOutboundPrompt(any(), any(), any(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        when(filter.filterGeneratedContent(any(), any(), any(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        when(resolver.resolve(any(), any(), any(), anyBoolean(), anyDouble())).thenReturn(
                new ArticleModelResolver.ModelSelection("qwen", "qwen-plus", mock(LlmModelConfig.class)));
        LlmInvokeResult original = toutiaoResult(article);
        when(facade.execute(any())).thenReturn(LlmCallResult.routed(new LlmRouteResult(
                "qwen", "通义千问", "primary", "qwen-plus", "通义千问 Plus",
                article, 100L, 1, original)), LlmCallResult.direct(toutiaoResult(rewrittenTitle)));
        return new ToutiaoFixture(new ArticleGenerationEngine(facade, resolver, mock(MarkdownImageReferenceValidator.class),
                filter, new BatchArticleQualityChecker(new ObjectMapper()), mock(ArticleTitleDuplicateChecker.class),
                new XiaohongshuArticleStructureValidator()), facade);
    }

    private LlmInvokeResult toutiaoResult(String text) {
        return new LlmInvokeResult(text, 10, 20, 100L, 0, LlmCallStatus.SUCCESS,
                "qwen", "通义千问", "qwen-plus", "通义千问 Plus");
    }

    private ArticleGenerationEngine.GenerateInput toutiaoInput() {
        Project project = new Project();
        project.setId(1L);
        Brand brand = new Brand();
        brand.setCityName("阜阳市");
        return new ArticleGenerationEngine.GenerateInput(project, brand, "system", "user", null, null,
                true, true, true, List.of(), 28, ArticleGenerationTemperatures.V2_STANDARD,
                new LlmCallMeasurementContext("test-run", null, 1L, LlmObservationScope.PROJECT, null), null,
                new ArticleGenerationEngine.StructureValidationContext("self_media", "toutiao", false, "阜阳SPA价格差异为什么大？"));
    }

    private record ToutiaoFixture(ArticleGenerationEngine engine, LlmCallFacade facade) {}

    @Test
    void rejectsProseOpeningsAndOverlongTitlesWithoutTruncatingTheirContent() {
        String opening = "在阜阳，想找到评价不错的医疗美容机构，关键要看机构是否具备规范资质、医生团队是否专业，以及服务是否贴合个人需求。";
        var error = org.junit.jupiter.api.Assertions.assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> ArticleGenerationEngine.validateDouyinTitle(opening + "\n\n后续正文。", 20));
        assertThat(error.getMessage()).contains("缺少独立标题", "不会截取正文");
        org.junit.jupiter.api.Assertions.assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> ArticleGenerationEngine.validateDouyinTitle("# " + opening + "\n\n正文。", 20));
        org.junit.jupiter.api.Assertions.assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> ArticleGenerationEngine.validateDouyinTitle("# 医生头衔挺多，到底看哪个？", 20));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> ArticleGenerationEngine.validateDouyinTitle(
                "# 医生头衔挺多，到底看哪个？\r\n\r\n先把头衔与执业信息分开看。", 20));
    }

    @Test
    void skipsTitleDuplicateJudgementForDouyinOnly() {
        assertFalse(ArticleGenerationEngine.shouldCheckTitleDuplicate(
                new ArticleGenerationEngine.StructureValidationContext("self_media", "douyin", false)));
        assertTrue(ArticleGenerationEngine.shouldCheckTitleDuplicate(
                new ArticleGenerationEngine.StructureValidationContext("self_media", "wechat", false)));
        assertTrue(ArticleGenerationEngine.shouldCheckTitleDuplicate(null));
    }

    @Test
    void generateUsesRouterWhenNoModelSpecified() throws Exception {
        LlmCallFacade llmCallFacade = mock(LlmCallFacade.class);
        ArticleModelResolver modelResolver = mock(ArticleModelResolver.class);
        ArticleAiDraftPromptFilter promptFilter = mock(ArticleAiDraftPromptFilter.class);
        BatchArticleQualityChecker qualityChecker = new BatchArticleQualityChecker(new ObjectMapper());
        when(promptFilter.filterOutboundPrompt(any(), any(), any(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
        when(promptFilter.filterGeneratedContent(any(), any(), any(), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(0));
        LlmInvokeResult invokeResult = new LlmInvokeResult(
                "# 这是一个为了验证自媒体平台标题长度限制而特意生成的超长文章标题\n\n第一句。第二句。第三句。",
                10,
                20,
                100L,
                0,
                LlmCallStatus.SUCCESS,
                "qwen",
                "通义千问",
                "qwen-plus",
                "通义千问 Plus"
        );
        when(llmCallFacade.execute(any())).thenReturn(LlmCallResult.routed(new LlmRouteResult(
                "qwen",
                "通义千问",
                "primary",
                "qwen-plus",
                "通义千问 Plus",
                invokeResult.responseText(),
                invokeResult.durationMs(),
                2,
                invokeResult
        )));

        ArticleGenerationEngine engine = new ArticleGenerationEngine(
                llmCallFacade,
                modelResolver,
                mock(MarkdownImageReferenceValidator.class),
                promptFilter,
                qualityChecker,
                mock(ArticleTitleDuplicateChecker.class),
                new XiaohongshuArticleStructureValidator()
        );

        Project project = new Project();
        project.setId(1L);
        LlmCallMeasurementContext measurementContext = new LlmCallMeasurementContext(
                "article-batch:1:task:2:infra:0:attempt:1",
                null,
                1L,
                LlmObservationScope.PROJECT,
                null
        );
        ArticleGenerationEngine.GeneratedArticle generated = engine.generate(new ArticleGenerationEngine.GenerateInput(
                project,
                new Brand(),
                "system",
                "user",
                null,
                null,
                true,
                false,
                true,
                List.of(),
                28,
                ArticleGenerationTemperatures.V2_STANDARD,
                measurementContext,
                8
        ));

        assertEquals("qwen", generated.model().platformCode());
        assertEquals("qwen-plus", generated.model().modelId());
        assertThat(generated.title().codePointCount(0, generated.title().length())).isLessThanOrEqualTo(28);
        assertThat(generated.content()).startsWith("# " + generated.title());
        assertThat(generated.quality().issues())
                .extracting(BatchArticleQualityChecker.Issue::type)
                .contains("title_truncated", "content_truncated");
        verify(modelResolver, never()).resolve(any(), any(), any(), anyBoolean());
        ArgumentCaptor<LlmCallRequest> requestCaptor = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(llmCallFacade).execute(requestCaptor.capture());
        assertEquals(measurementContext, requestCaptor.getValue().measurementContext());
        LlmRouteRequest routeRequest = requestCaptor.getValue().routeRequest();
        assertEquals(LlmFeature.ARTICLE, routeRequest.feature());
        assertEquals(LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS, routeRequest.requestTimeoutMs());
        assertEquals(LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS, routeRequest.requestTimeoutMaxMs());
        assertEquals(0, routeRequest.maxRetry());
        assertEquals(ArticleGenerationTemperatures.V2_STANDARD, routeRequest.temperature());
    }
}
