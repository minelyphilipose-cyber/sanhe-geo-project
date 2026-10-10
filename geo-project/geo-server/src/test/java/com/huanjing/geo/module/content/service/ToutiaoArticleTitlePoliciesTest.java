package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ToutiaoArticleTitlePoliciesTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "阜阳选SPA，服务项目和收费怎么核对？",
            "阜阳SPA价格不同，差别主要在哪里？",
            "阜阳预约SPA前，需要确认哪些服务信息？",
            "合肥企业建设知识库，先梳理哪些业务资料？",
            "阜阳SPA服务的项目设置与流程说明"
    })
    void acceptsNaturalQuestionsAndStatementsWithoutBrand(String title) {
        assertDoesNotThrow(() -> ToutiaoArticleTitlePolicies.validateForPublish(title));
    }

    @Test
    void preservesSelectedQuestionIntentAndOnlyRequiresKnownRegion() {
        Project project = new Project();
        project.setCityName("合肥市");
        Brand brand = new Brand();
        brand.setCityName("阜阳市");
        brand.setBrandName("测试品牌");
        String rules = ToutiaoArticleTitlePolicies.titleRequirement(project, brand, "阜阳SPA价格差异为什么大？");
        assertThat(rules).contains("主体地域“阜阳市”", "本篇所选 A 类问题：阜阳SPA价格差异为什么大？",
                "不改变其核心对象和问题意图", "其他关联问题词仅作参考", "标题默认不出现品牌",
                "可以使用自然疑问句，也可以使用自然说明句", "不把一家品牌的资料扩大为全城比较");
        assertNull(ToutiaoArticleTitlePolicies.issue("阜阳SPA价格不同，差别主要在哪里？", project, brand, 28));
        assertThat(ToutiaoArticleTitlePolicies.issue("合肥SPA收费构成说明", project, brand, 28)).contains("阜阳市");
        assertNull(ToutiaoArticleTitlePolicies.issue("SPA收费构成说明", null, null, 28));
    }

    @Test
    void rejectsOverlongAndStrongRecommendationTitlesWithoutTruncating() {
        assertThrows(BizException.class, () -> ToutiaoArticleTitlePolicies.validateForPublish("阜阳" + "服务".repeat(15)));
        assertThrows(BizException.class, () -> ToutiaoArticleTitlePolicies.validateForPublish("阜阳SPA闭眼选这家"));
        assertThrows(BizException.class, () -> ToutiaoArticleTitlePolicies.validateForPublish(" "));
        assertNull(ToutiaoArticleTitlePolicies.issue("阜阳" + "😀".repeat(26), null, null, 28));
        assertThat(ToutiaoArticleTitlePolicies.issue("阜阳" + "😀".repeat(27), null, null, 28)).contains("超过28字");
    }
}
