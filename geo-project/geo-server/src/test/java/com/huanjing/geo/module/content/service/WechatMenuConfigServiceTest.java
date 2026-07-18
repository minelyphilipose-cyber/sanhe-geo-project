package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatMenuProperties;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.WechatMenuConfig;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.WechatMenuConfigMapper;
import com.huanjing.geo.module.content.wechat.WechatFuncInfoValidator;
import com.huanjing.geo.module.content.wechat.WechatMpClient;
import com.huanjing.geo.module.content.wechat.WechatTokenAwareExecutor;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatMenuConfigServiceTest {

    @Test
    void apiUnauthorizedIsRecordedAsMissingMenuPermission() {
        WechatMenuConfigMapper menuConfigMapper = mock(WechatMenuConfigMapper.class);
        SelfMediaAccountMapper accountMapper = mock(SelfMediaAccountMapper.class);
        WechatFuncInfoValidator funcInfoValidator = mock(WechatFuncInfoValidator.class);
        WechatTokenAwareExecutor tokenAwareExecutor = mock(WechatTokenAwareExecutor.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(60L);
        account.setBrandId(15L);
        account.setPlatform("wechat_mp");
        account.setPlatformAccountId("wx-authorizer");
        account.setScopeJson("[{\"funcscope_category\":{\"id\":15}}]");
        WechatMenuConfig config = new WechatMenuConfig();
        config.setId(7L);
        config.setSelfMediaAccountId(60L);
        config.setPublicSlug("existing-slug");

        when(accountMapper.selectById(60L)).thenReturn(account);
        when(menuConfigMapper.selectOne(any())).thenReturn(config);
        when(funcInfoValidator.hasMenuPermission(account.getScopeJson())).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(true);
        when(tokenAwareExecutor.execute(any(), any()))
                .thenThrow(new BizException(48001, "api unauthorized rid: request-id"));

        WechatMenuProperties menuProperties = new WechatMenuProperties();
        menuProperties.setWebBaseUrl("https://www.example.com");
        WechatMenuConfigService service = new WechatMenuConfigService(
                menuConfigMapper,
                accountMapper,
                menuProperties,
                funcInfoValidator,
                tokenAwareExecutor,
                mock(WechatMpClient.class),
                redisTemplate,
                new ObjectMapper(),
                mock(CurrentUserService.class),
                mock(BrandAccessService.class)
        );

        WechatMenuConfig result = service.initializeMenuAfterAuthorization(60L);

        assertThat(result.getMenuStatus()).isEqualTo("permission_missing");
        assertThat(result.getLastSyncError())
                .contains("自定义菜单 API 权限")
                .contains("48001")
                .contains("request-id");
    }
}
