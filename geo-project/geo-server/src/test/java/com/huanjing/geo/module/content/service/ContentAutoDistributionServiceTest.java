package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionBatch;
import com.huanjing.geo.module.content.entity.ContentAutoDistributionItem;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.BatchArticlePublishItemMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionBatchMapper;
import com.huanjing.geo.module.content.mapper.ContentAutoDistributionItemMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.mapper.PublishSiteMapper;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.SystemAlertService;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentAutoDistributionServiceTest {

    private ProjectChannelAllocationMapper allocationMapper;
    private KeywordGroupResultMapper keywordGroupResultMapper;
    private ContentAutoDistributionBatchMapper batchMapper;
    private ContentAutoDistributionItemMapper itemMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private SelfMediaScheduleCapabilityService scheduleCapabilityService;
    private BrowserEnvironmentService browserEnvironmentService;
    private ContentAutoDistributionService service;

    @BeforeEach
    void setUp() {
        initTableInfo(ContentAutoDistributionBatch.class);
        initTableInfo(ContentAutoDistributionItem.class);
        allocationMapper = mock(ProjectChannelAllocationMapper.class);
        keywordGroupResultMapper = mock(KeywordGroupResultMapper.class);
        batchMapper = mock(ContentAutoDistributionBatchMapper.class);
        itemMapper = mock(ContentAutoDistributionItemMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        scheduleCapabilityService = mock(SelfMediaScheduleCapabilityService.class);
        browserEnvironmentService = mock(BrowserEnvironmentService.class);
        service = new ContentAutoDistributionService(
                mock(ProjectMapper.class),
                allocationMapper,
                keywordGroupResultMapper,
                mock(BrandMapper.class),
                mock(CompanyMapper.class),
                mock(PublishSiteMapper.class),
                mock(SysUserMapper.class),
                batchMapper,
                itemMapper,
                mock(BatchArticleGenerationTaskMapper.class),
                mock(BatchArticlePublishItemMapper.class),
                selfMediaAccountMapper,
                mock(SelfMediaPublishScheduleMapper.class),
                mock(BatchArticleGenerationService.class),
                mock(BatchArticlePublishService.class),
                mock(SelfMediaPublishScheduleService.class),
                scheduleCapabilityService,
                browserEnvironmentService,
                mock(SystemAlertService.class),
                mock(ForumBoardRoutingService.class),
                mock(StringRedisTemplate.class)
        );
    }

    @Test
    void createProjectPlanPlansOnlyMatchingSelfMediaPlatformQuotaChannel() {
        Project project = new Project();
        project.setId(11L);
        project.setCompanyId(7L);
        project.setBrandId(3L);
        ProjectChannelAllocation allocation = new ProjectChannelAllocation();
        allocation.setChannelCode("self_media:zhihu");
        allocation.setAllocatedCount(1);
        KeywordGroupResult question = new KeywordGroupResult();
        question.setId(101L);
        question.setKeywordText("如何选择服务商");
        SelfMediaAccount zhihu = account(201L, "zhihu");
        SelfMediaAccount toutiao = account(202L, "toutiao");
        BrowserEnvironmentAccount binding = new BrowserEnvironmentAccount();
        binding.setId(301L);

        when(batchMapper.selectCount(any())).thenReturn(0L);
        when(allocationMapper.selectList(any())).thenReturn(List.of(allocation));
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(11L, "'A'")).thenReturn(List.of(question));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of(toutiao, zhihu));
        when(scheduleCapabilityService.readiness("zhihu"))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(browserEnvironmentService.validateForTaskCreation(zhihu)).thenReturn(binding);
        when(itemMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            ContentAutoDistributionBatch batch = invocation.getArgument(0);
            batch.setId(500L);
            return 1;
        }).when(batchMapper).insert(any(ContentAutoDistributionBatch.class));

        service.createProjectPlan(project, LocalDate.of(2026, 6, 8), 900L);

        ArgumentCaptor<ContentAutoDistributionItem> itemCaptor = ArgumentCaptor.forClass(ContentAutoDistributionItem.class);
        verify(itemMapper).insert(itemCaptor.capture());
        ContentAutoDistributionItem item = itemCaptor.getValue();
        assertEquals("self_media:zhihu", item.getChannelCode());
        assertEquals(ArticlePromptChannels.SELF_MEDIA, item.getChannelGroupCode());
        assertEquals("zhihu", item.getContentStyle());
        assertEquals(DistributionTargetKind.MP_ACCOUNT, item.getTargetKind());
        assertEquals(201L, item.getTargetId());
        assertEquals(101L, item.getQuestionId());
    }

    @Test
    void createProjectPlanSkipsWithPlatformHintWhenSelfMediaAccountMissing() {
        Project project = new Project();
        project.setId(12L);
        project.setCompanyId(7L);
        project.setBrandId(3L);
        ProjectChannelAllocation allocation = new ProjectChannelAllocation();
        allocation.setChannelCode("self_media:zhihu");
        allocation.setAllocatedCount(2);

        when(batchMapper.selectCount(any())).thenReturn(0L);
        when(allocationMapper.selectList(any())).thenReturn(List.of(allocation));
        when(selfMediaAccountMapper.selectList(any())).thenReturn(List.of(account(202L, "toutiao")));

        service.createProjectPlan(project, LocalDate.of(2026, 6, 8), 900L);

        ArgumentCaptor<ContentAutoDistributionBatch> batchCaptor = ArgumentCaptor.forClass(ContentAutoDistributionBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        ContentAutoDistributionBatch batch = batchCaptor.getValue();
        assertEquals("skipped", batch.getStatus());
        assertEquals("自媒体平台 / 知乎 无可用账号，请先在品牌下配置并启用账号", batch.getErrorMessage());
        verify(itemMapper, never()).insert(any(ContentAutoDistributionItem.class));
    }

    private SelfMediaAccount account(Long id, String platform) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(id);
        account.setBrandId(3L);
        account.setPlatform(platform);
        account.setAccountName(platform + "-account");
        account.setStatus("active");
        return account;
    }

    private void initTableInfo(Class<?> entityClass) {
        TableName tableName = entityClass.getAnnotation(TableName.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), tableName == null ? "" : tableName.value()),
                entityClass
        );
    }
}
