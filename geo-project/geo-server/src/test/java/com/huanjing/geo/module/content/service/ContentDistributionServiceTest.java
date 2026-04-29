package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticleDraftVersionMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.PackagePublishConfigMapper;
import com.huanjing.geo.module.content.mapper.ProjectPublishQuotaMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.PublishSite;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentDistributionServiceTest {

    @Test
    void requireSite_frameworkRow_throws400() {
        Long frameworkSiteId = 99L;
        PublishSite mockSite = new PublishSite();
        mockSite.setId(frameworkSiteId);
        mockSite.setIsFramework(1);

        PublishSiteMapper publishSiteMapper = mock(PublishSiteMapper.class);
        when(publishSiteMapper.selectById(frameworkSiteId)).thenReturn(mockSite);
        ContentDistributionService service = newService(publishSiteMapper);

        BizException ex = assertThrows(
                BizException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "requireSite", frameworkSiteId)
        );

        assertEquals(400, ex.getCode());
        assertEquals("framework site is not a valid publish target", ex.getMessage());
    }

    private ContentDistributionService newService(PublishSiteMapper publishSiteMapper) {
        return new ContentDistributionService(
                mock(ArticleDraftMapper.class),
                mock(ArticleDraftVersionMapper.class),
                mock(DistributionTaskMapper.class),
                mock(PackagePublishConfigMapper.class),
                mock(ProjectPublishQuotaMapper.class),
                mock(ProjectMapper.class),
                publishSiteMapper,
                mock(CurrentUserService.class),
                mock(SystemAlertService.class),
                List.of(),
                mock(BrandMapper.class)
        );
    }
}
