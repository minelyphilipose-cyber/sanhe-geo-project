package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaMaterialMappingMapper;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandImageFolderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatMediaServiceTest {

    private final BrandMaterialMapper brandMaterialMapper = mock(BrandMaterialMapper.class);
    private final BrandImageFolderService brandImageFolderService = mock(BrandImageFolderService.class);
    private final SelfMediaMaterialMappingMapper mappingMapper = mock(SelfMediaMaterialMappingMapper.class);
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final WechatTokenAwareExecutor tokenAwareExecutor = mock(WechatTokenAwareExecutor.class);
    private final WechatMpClient wechatMpClient = mock(WechatMpClient.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private WechatMediaService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(mappingMapper.selectOne(any())).thenReturn(null);
        when(tokenAwareExecutor.execute(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<String, WechatMpClient.UploadImageResult> operation = invocation.getArgument(1);
            return operation.apply("access-token");
        });
        service = new WechatMediaService(
                brandMaterialMapper,
                brandImageFolderService,
                mappingMapper,
                minioStorageService,
                tokenAwareExecutor,
                wechatMpClient,
                redisTemplate
        );
    }

    @Test
    void ensureContentImageUrlUsesManagedMaterialFilenameForPublicStreamUrl() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(7L);
        account.setBrandId(12L);
        BrandMaterial material = new BrandMaterial();
        material.setId(528L);
        material.setBrandId(12L);
        material.setCategory("brand_image");
        material.setFileName("wechat_image_528.jpg");
        material.setFileType("jpg");
        material.setObjectKey("brand/12/wechat_image_528.jpg");
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};

        when(brandMaterialMapper.selectById(528L)).thenReturn(material);
        when(minioStorageService.getObjectBytes("brand/12/wechat_image_528.jpg")).thenReturn(jpeg);
        when(wechatMpClient.uploadContentImage(eq("access-token"), eq(jpeg), anyString()))
                .thenReturn(new WechatMpClient.UploadImageResult("https://mmbiz.qpic.cn/uploaded.jpg"));

        String result = service.ensureContentImageUrl(
                account,
                "https://www.huanjingaigeo.com/api/public/brand-materials/528/stream?sig=abc&v=1"
        );

        assertEquals("https://mmbiz.qpic.cn/uploaded.jpg", result);
        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        verify(wechatMpClient).uploadContentImage(eq("access-token"), eq(jpeg), filenameCaptor.capture());
        assertEquals("wechat_image_528.jpg", filenameCaptor.getValue());
    }
}
