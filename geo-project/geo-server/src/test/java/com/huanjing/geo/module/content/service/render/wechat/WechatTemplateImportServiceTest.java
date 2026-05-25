package com.huanjing.geo.module.content.service.render.wechat;

import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.TemplateParseResponse;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.BodyStyle;
import com.huanjing.geo.module.content.dto.render.WechatRenderDtos.RoleSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

class WechatTemplateImportServiceTest {

    private final WechatTemplateImportService service = new WechatTemplateImportService(new WechatHtmlSanitizer());

    @Test
    void parse135FlattensVisualModulesInsideParagraphContainerInDomOrder() {
        String html = """
                <section class="article135" data-role="outer">
                  <section data-role="paragraph">
                    <section class="_135editor" data-id="us-4854484">
                      <section><strong>11</strong><span class="135brush">编号模块</span></section>
                    </section>
                    <p><img data-src="https://example.com/a.png" alt="a"></p>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices()).hasSize(2);
        assertThat(response.getSlices().get(0).getOrder()).isEqualTo(1);
        assertThat(response.getSlices().get(0).getSuggestedRole()).isEqualTo("highlight_block");
        assertThat(response.getSlices().get(0).getHtml()).contains("{{content}}");
        assertThat(response.getSlices().get(1).getOrder()).isEqualTo(2);
        assertThat(response.getSlices().get(1).getSuggestedRole()).isEqualTo("image_block");
        assertThat(response.getSlices().get(1).getHtml()).contains("{{imageUrl}}");
    }

    @Test
    void parse135WarnsAndSanitizesBackgroundImages() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-id="164342" style="background-image:url(https://example.com/bg.png)">
                    <section class="135brush">重点段落</section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices()).hasSize(1);
        assertThat(response.getSlices().get(0).getWarnings()).contains("检测到 background-image，一期会清除该背景图样式");
        assertThat(response.getSlices().get(0).getHtml()).doesNotContain("background-image");
    }

    @Test
    void parse135UsesAutoskipBrushAsContentSoRepeatedLabeledBoxesConverge() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-tools="135编辑器" data-id="135242">
                    <section>
                      <section><strong class="135brush" data-brushtype="text">需求举例</strong></section>
                      <section>
                        <section data-autoskip="1" class="135brush">
                          <p>第一段正文内容</p>
                        </section>
                      </section>
                    </section>
                  </section>
                  <section class="_135editor" data-tools="135编辑器" data-id="135242">
                    <section>
                      <section><strong class="135brush" data-brushtype="text">核心问题</strong></section>
                      <section>
                        <section data-autoskip="1" class="135brush">
                          <p>第二段完全不同的正文内容</p>
                        </section>
                      </section>
                    </section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices()).hasSize(2);
        assertThat(response.getRoles()).hasSize(1);
        assertThat(response.getRoles().get(0).getReuseCount()).isEqualTo(2);
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getRoles().get(0).getWrapperHtml()).contains("{{content}}");
        assertThat(response.getRoles().get(0).getWrapperHtml()).doesNotContain("第一段正文内容");
    }

    @Test
    void parse135SeparatesQuoteAndHighlightModulesByChannelRule() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-tools="135编辑器" data-id="164342">
                    <section>
                      <svg><path></path></svg>
                      <section class="135brush"><p>引用观点内容</p></section>
                    </section>
                  </section>
                  <section class="_135editor" data-tools="135编辑器" data-id="135242">
                    <section>
                      <section><strong class="135brush" data-brushtype="text">需求举例</strong></section>
                      <section>
                        <section data-autoskip="1" class="135brush"><p>重点段落内容</p></section>
                      </section>
                    </section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices()).extracting("suggestedRole")
                .containsExactly("quote_block", "highlight_block");
        assertThat(response.getWarnings()).isEmpty();
    }

    @Test
    void parseRespectsExplicitGenericSourceTypeEvenWhenHtmlLooksLike135() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-id="135242">正文</section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "generic");

        assertThat(response.getSourceType()).isEqualTo("generic");
        assertThat(response.getSlices()).hasSize(1);
    }

    @Test
    void parse135PreservesTitleModuleWithoutBrush() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-role="title" data-tools="135编辑器" data-id="167466">
                    <section style="margin: 10px auto; display: flex; overflow: hidden; align-items: flex-end;">
                      <section style="width: 100%;" data-width="100%">
                        <section style="text-align: left;">
                          <span style="font-size: 40px;color: #d8d8d8;"><em><strong>0</strong><strong class="autonum">1</strong></em></span>
                          <span style="color:#000000"><span style="font-size: 16px;"><strong>这是第一段</strong></span></span>
                        </section>
                        <section style="width: 100%;height: 1px;border-top: 1px solid #ff0000;" data-width="100%"></section>
                      </section>
                    </section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices()).hasSize(1);
        assertThat(response.getSlices().get(0).getSuggestedRole()).isEqualTo("heading");
        assertThat(response.getRoles().get(0).getWrapperHtml()).contains("{{content}}");
        assertThat(response.getRoles().get(0).getWrapperHtml()).contains("border-top");
        assertThat(response.getRoles().get(0).getWrapperHtml()).contains("{{index}}");
        assertThat(response.getRoles().get(0).getWrapperHtml()).doesNotContain("0{{index}}");
        assertThat(response.getRoles().get(0).getWrapperHtml()).doesNotContain("{{index}}{{index}}");
        assertThat(response.getRoles().get(0).getWrapperHtml()).doesNotContain("这是第一段");
    }

    @Test
    void parse135ClearsSplitNumberLabelsBeforeAutonum() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-role="title" data-tools="135编辑器" data-id="167466">
                    <section>
                      <span><strong>0</strong></span>
                      <span><strong class="autonum">3</strong></span>
                      <span><strong>这是第三段</strong></span>
                    </section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");
        String wrapperHtml = response.getRoles().get(0).getWrapperHtml();

        assertThat(wrapperHtml).contains("{{index}}");
        assertThat(wrapperHtml).doesNotContain("0{{index}}");
        assertThat(wrapperHtml).doesNotContain("3{{index}}");
        assertThat(wrapperHtml).doesNotContain("{{index}}{{index}}");
        assertThat(wrapperHtml).doesNotContain("这是第三段");
    }

    @Test
    void parse135PreservesTitleTextWhenTitleNumberHasTwoDigits() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-role="title" data-tools="135编辑器" data-id="167466">
                    <section>
                      <span><em><strong>10</strong></em></span>
                      <span><strong>第十段标题</strong></span>
                    </section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");
        String wrapperHtml = response.getRoles().get(0).getWrapperHtml();

        assertThat(wrapperHtml).contains("{{index}}");
        assertThat(wrapperHtml).contains("{{content}}");
        assertThat(wrapperHtml).doesNotContain("<strong>10</strong>");
        assertThat(wrapperHtml).doesNotContain("第十段标题");
    }

    @Test
    void parse135TemplateHighlightAllowsListTags() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-tools="135编辑器" data-id="168538">
                    <section>
                      <section data-autoskip="1" class="135brush" data-role="list">
                        <ol class="list-paddingleft-2" style="list-style-type: decimal;margin:0px;padding:0 0 0 30px;">
                          <li><p>这是第一点</p></li>
                          <li><p>这是第二点</p></li>
                        </ol>
                      </section>
                    </section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getRoles().get(0).getWrapperHtml()).contains("{{content}}");
        assertThat(response.getSlices().get(0).getHtml()).contains("{{content}}");
        assertThat(response.getSlices().get(0).getPreviewHtml()).contains("<ol");
        assertThat(response.getSlices().get(0).getPreviewHtml()).contains("<li");
        assertThat(response.getSlices().get(0).getPreviewHtml()).contains("这是第一点");
    }

    @Test
    void parse135SeparatesIntroBoxFromLabeledListHighlight() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-tools="135编辑器" data-id="custom-intro">
                    <section style="background-color:#f0f0f0;">
                      <section data-autoskip="1" class="135brush"><p>AI科技的发展没有终点。</p></section>
                    </section>
                  </section>
                  <section class="_135editor" data-tools="135编辑器" data-id="custom-list">
                    <section>
                      <section><strong>这个就是示例</strong></section>
                      <section data-autoskip="1" class="135brush" data-role="list">
                        <ol><li><p>这是第一点</p></li><li><p>这是第二点</p></li></ol>
                      </section>
                    </section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices()).extracting("suggestedRole")
                .containsExactly("quote_block", "highlight_block");
        assertThat(response.getRoles()).extracting("role")
                .contains("quote_block", "highlight_block");
        assertThat(response.getRoles().stream()
                .filter(role -> "highlight_block".equals(role.getRole()))
                .findFirst()
                .orElseThrow()
                .getWrapperHtml()).contains("这个就是示例").contains("{{content}}");
    }

    @Test
    void parse135UsesStructureInsteadOfTemplateSpecificDataIds() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-tools="135编辑器" data-id="unknown-quote-module">
                    <section>
                      <svg><path></path></svg>
                      <section class="135brush"><p>引用观点内容</p></section>
                    </section>
                  </section>
                  <section class="_135editor" data-tools="135编辑器" data-id="unknown-labeled-module">
                    <section>
                      <section><strong class="135brush" data-brushtype="text">需求举例</strong></section>
                      <section>
                        <section data-autoskip="1" class="135brush"><p>重点段落内容</p></section>
                      </section>
                    </section>
                  </section>
                  <section class="_135editor" data-tools="135编辑器" data-id="unknown-cta-module">
                    <section><strong>联系我们</strong></section>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices()).extracting("suggestedRole")
                .containsExactly("quote_block", "highlight_block", "ending_cta");
    }

    @Test
    void parse135ImageBlockKeepsWidthStyle() {
        String html = """
                <section class="article135" data-role="outer">
                  <section data-role="paragraph" class="_135editor">
                    <p><img src="https://example.com/a.png" style="vertical-align: baseline; width: 100%;box-sizing:border-box;max-width:100% !important;" alt="a"></p>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices().get(0).getSuggestedRole()).isEqualTo("image_block");
        assertThat(response.getSlices().get(0).getHtml()).contains("width:100%");
        assertThat(response.getSlices().get(0).getHtml()).contains("{{imageUrl}}");
    }

    @Test
    void parse135ParagraphWithoutBrushKeepsTextCarrierStyle() {
        String html = """
                <section class="article135" data-role="outer">
                  <section data-role="paragraph" class="_135editor">
                    <p style="margin: 0 0 12px;">
                      <span style="font-size: 14px;color: #333333;line-height: 1.75em;letter-spacing: 1.5px;">
                        这是一段带样式的普通正文。
                      </span>
                    </p>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getSlices()).hasSize(1);
        assertThat(response.getSlices().get(0).getSuggestedRole()).isEqualTo("paragraph");
        assertThat(response.getRoles().get(0).getWrapperSafe()).isTrue();
        String wrapperHtml = response.getRoles().get(0).getWrapperHtml();
        assertThat(wrapperHtml).contains("{{content}}");
        assertThat(wrapperHtml).contains("font-size:14px");
        assertThat(wrapperHtml).contains("line-height:1.75em");
        assertThat(wrapperHtml).contains("letter-spacing:1.5px");
        assertThat(wrapperHtml).contains("<p style=\"margin:0 0 12px\">");
        assertThat(wrapperHtml).doesNotContain("这是一段带样式的普通正文");
        assertThat(response.getBodyStyle()).isNotNull();
        assertThat(response.getBodyStyle().getFontSize()).isEqualTo("14px");
        assertThat(response.getBodyStyle().getLineHeight()).isEqualTo("1.75em");
        assertThat(response.getBodyStyle().getLetterSpacing()).isEqualTo("0.5px");
        assertThat(response.getBodyStyle().getColor()).isEqualTo("#333333");
        assertThat(response.getBodyStyle().getParagraphMargin()).isEqualTo("0 0 12px");
    }

    @Test
    void parseWarnsWhenParagraphWrapperContainsDecorations() {
        String html = """
                <section data-role="paragraph" style="background-color:#f4f4f4;border:1px solid #ddd;">
                  <p>这是一段被误绑定为普通正文的卡片样式。</p>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "generic");

        assertThat(response.getRoles()).hasSize(1);
        assertThat(response.getRoles().get(0).getRole()).isEqualTo("paragraph");
        assertThat(response.getRoles().get(0).getWrapperSafe()).isFalse();
        assertThat(response.getWarnings()).extracting("type").contains("paragraph_wrapper_unsafe");
    }

    @Test
    void parse135ParagraphWithoutBrushClearsAllSampleTextWhenMultipleTextNodesExist() {
        String html = """
                <section class="article135" data-role="outer">
                  <section data-role="paragraph" class="_135editor">
                    <p style="margin: 0 0 12px;">
                      <span style="font-size: 14px;">第一段示例</span>
                      <span style="color: #333333;">第二段示例</span>
                    </p>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");
        String wrapperHtml = response.getRoles().get(0).getWrapperHtml();

        assertThat(wrapperHtml).contains("{{content}}");
        assertThat(wrapperHtml).contains("margin:0 0 12px");
        assertThat(response.getBodyStyle()).isNotNull();
        assertThat(response.getBodyStyle().getFontSize()).isEqualTo("14px");
        assertThat(response.getBodyStyle().getColor()).isEqualTo("#333333");
        assertThat(response.getBodyStyle().getLetterSpacing()).isEqualTo("0.5px");
        assertThat(wrapperHtml).doesNotContain("第一段示例");
        assertThat(wrapperHtml).doesNotContain("第二段示例");
    }

    @Test
    void parse135ParagraphWithoutTypographyUsesChannelDefaultBodyStyle() {
        String html = """
                <section class="article135" data-role="outer">
                  <section data-role="paragraph" class="_135editor">
                    <p>这是一段没有内联字体样式的普通正文。</p>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");
        String wrapperHtml = response.getRoles().get(0).getWrapperHtml();

        assertThat(response.getSlices().get(0).getSuggestedRole()).isEqualTo("paragraph");
        assertThat(wrapperHtml).contains("{{content}}");
        assertThat(wrapperHtml).doesNotContain("font-size:14px");
        assertThat(wrapperHtml).doesNotContain("line-height:1.75");
        assertThat(wrapperHtml).doesNotContain("letter-spacing:0.5px");
        assertThat(response.getBodyStyle()).isNotNull();
        assertThat(response.getBodyStyle().getFontSize()).isEqualTo("14px");
        assertThat(response.getBodyStyle().getLineHeight()).isEqualTo("1.75");
        assertThat(response.getBodyStyle().getLetterSpacing()).isEqualTo("0.5px");
        assertThat(response.getBodyStyle().getColor()).isEqualTo("#333333");
        assertThat(response.getBodyStyle().getTextAlign()).isEqualTo("justify");
        assertThat(response.getBodyStyle().getParagraphMargin()).isEqualTo("0 0 14px");
        assertThat(wrapperHtml).doesNotContain("这是一段没有内联字体样式的普通正文");
    }

    @Test
    void parseGenericParagraphWithoutTypographyDoesNotUse135BodyStyle() {
        String html = """
                <section data-role="paragraph" class="_135editor">
                  <p>通用 HTML 的普通正文。</p>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "generic");
        String wrapperHtml = response.getRoles().get(0).getWrapperHtml();

        assertThat(response.getBodyStyle()).isNull();
        assertThat(wrapperHtml).contains("{{content}}");
        assertThat(wrapperHtml).doesNotContain("font-size:14px");
        assertThat(wrapperHtml).doesNotContain("line-height:1.75em");
        assertThat(wrapperHtml).doesNotContain("letter-spacing: 1.5px");
    }

    @Test
    void parse135BodyStyleIgnoresBrushAndKeepsChannelDefaultsForMissingFields() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-id="highlight">
                    <section data-autoskip="1" class="135brush" style="font-size:16px;line-height:2;color:#ff0000;letter-spacing:1.5px;">
                      <p>这是重点框内容，不应该作为正文基调。</p>
                    </section>
                  </section>
                  <section data-role="paragraph" class="_135editor">
                    <p style="line-height:125%;margin-bottom:8px;">这是一段真正的普通正文，用于提取正文行高。</p>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getBodyStyle()).isNotNull();
        assertThat(response.getBodyStyle().getFontSize()).isEqualTo("14px");
        assertThat(response.getBodyStyle().getLineHeight()).isEqualTo("125%");
        assertThat(response.getBodyStyle().getLetterSpacing()).isEqualTo("0.5px");
        assertThat(response.getBodyStyle().getColor()).isEqualTo("#333333");
        assertThat(response.getBodyStyle().getParagraphMargin()).isEqualTo("0 0 8px");
    }

    @Test
    void parse135BodyStyleUsesMajorityOverEffectiveSamplesOnly() {
        String html = """
                <section class="article135" data-role="outer">
                  <section data-role="paragraph" class="_135editor"><p style="line-height:125%;">第一段真正的普通正文。</p></section>
                  <section data-role="paragraph" class="_135editor"><p style="line-height:125%;">第二段真正的普通正文。</p></section>
                  <section data-role="paragraph" class="_135editor"><p style="line-height:150%;">第三段真正的普通正文。</p></section>
                  <section data-role="paragraph" class="_135editor"><p>第四段没有行高设置，不参与行高分母。</p></section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getBodyStyle()).isNotNull();
        assertThat(response.getBodyStyle().getLineHeight()).isEqualTo("125%");
    }

    @Test
    void parse135BodyStyleIgnoresCaretColorResidue() {
        String html = """
                <section class="article135" data-role="outer">
                  <section data-role="paragraph" class="_135editor">
                    <p style="caret-color:red;">这是一段带编辑器光标残留的普通正文。</p>
                  </section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getBodyStyle()).isNotNull();
        assertThat(response.getBodyStyle().getColor()).isEqualTo("#333333");
    }

    @Test
    void parse135BodyStyleUsesDefaultWhenThereAreNoTrueParagraphSamples() {
        String html = """
                <section class="article135" data-role="outer">
                  <section class="_135editor" data-role="title"><section><strong>01</strong><strong>标题</strong></section></section>
                  <section class="_135editor"><section data-autoskip="1" class="135brush"><p>重点框内容</p></section></section>
                </section>
                """;

        TemplateParseResponse response = service.parse(html, "source_135");

        assertThat(response.getBodyStyle()).isNotNull();
        assertThat(response.getBodyStyle().getFontSize()).isEqualTo("14px");
        assertThat(response.getBodyStyle().getLineHeight()).isEqualTo("1.75");
        assertThat(response.getBodyStyle().getLetterSpacing()).isEqualTo("0.5px");
    }

    @Test
    void normalize135BodyStyleKeepsDefaultsWhenOnlyPartialOverrideProvided() {
        BodyStyle override = new BodyStyle();
        override.setLineHeight("125%");

        BodyStyle normalized = service.normalizeBodyStyle(override, "source_135");

        assertThat(normalized).isNotNull();
        assertThat(normalized.getFontSize()).isEqualTo("14px");
        assertThat(normalized.getLineHeight()).isEqualTo("125%");
        assertThat(normalized.getLetterSpacing()).isEqualTo("0.5px");
        assertThat(normalized.getColor()).isEqualTo("#333333");
    }

    @Test
    void normalizeRolesPersistsWrapperSafetyFlag() {
        RoleSchema safe = new RoleSchema();
        safe.setWrapperHtml("<section><p>{{content}}</p></section>");
        RoleSchema unsafe = new RoleSchema();
        unsafe.setWrapperHtml("<section style=\"background:#f6f6f6\"><p>{{content}}</p></section>");

        Map<String, RoleSchema> roles = service.normalizeRoles(Map.of(
                "paragraph", safe,
                "highlight_block", unsafe
        ));

        assertThat(roles.get("paragraph").getWrapperSafe()).isTrue();
        assertThat(roles.get("highlight_block").getWrapperSafe()).isFalse();
    }
}
