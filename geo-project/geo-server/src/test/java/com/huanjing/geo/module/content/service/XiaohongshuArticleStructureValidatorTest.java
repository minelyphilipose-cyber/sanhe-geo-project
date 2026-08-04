package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XiaohongshuArticleStructureValidatorTest {

    private final XiaohongshuArticleStructureValidator validator = new XiaohongshuArticleStructureValidator();

    @Test
    void acceptsRestrainedNeutralEducationArticle() {
        String markdown = "# 皮肤屏障的日常变化\n\n"
                + "皮肤表面的屏障并不是一层完全静止的结构。".repeat(14)
                + "\n\n## 状态会随环境变化\n\n"
                + "温度湿度清洁频率和作息变化都可能影响表面状态。".repeat(14);

        List<XiaohongshuArticleStructureValidator.Violation> violations =
                validator.validate(markdown, project(), brand(), true);

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsBrandAndRiskyTitleInNeutralEducationMode() {
        String markdown = "# 测试医美效果避雷必看\n\n" + "中立科普内容。".repeat(90);

        List<XiaohongshuArticleStructureValidator.Violation> violations =
                validator.validate(markdown, project(), brand(), true);

        assertThat(violations)
                .extracting(XiaohongshuArticleStructureValidator.Violation::type)
                .contains("xiaohongshu_title_risk", "xiaohongshu_neutral_brand_reference");
    }

    @Test
    void rejectsDenseHeadingsListsAndMarketingFormatting() {
        String markdown = "# 日常小知识🙂\n\n"
                + "## 第一部分\n内容。\n\n## 第二部分\n内容。\n\n## 第三部分\n内容。\n\n"
                + "### 更深标题\n"
                + "- 一\n- 二\n- 三\n- 四\n- 五\n"
                + "#热门话题#\n"
                + "补充内容。".repeat(90);

        List<XiaohongshuArticleStructureValidator.Violation> violations =
                validator.validate(markdown, project(), brand(), true);

        assertThat(violations)
                .extracting(XiaohongshuArticleStructureValidator.Violation::type)
                .contains(
                        "xiaohongshu_heading_density",
                        "xiaohongshu_deep_heading",
                        "xiaohongshu_list_density",
                        "xiaohongshu_topic_tag",
                        "xiaohongshu_emoji"
                );
    }

    private Project project() {
        Project project = new Project();
        project.setCompanyName("测试医疗有限公司");
        project.setBrandName("测试医美");
        return project;
    }

    private Brand brand() {
        Brand brand = new Brand();
        brand.setBrandName("测试医美");
        brand.setBrandShortName("测试");
        return brand;
    }
}
