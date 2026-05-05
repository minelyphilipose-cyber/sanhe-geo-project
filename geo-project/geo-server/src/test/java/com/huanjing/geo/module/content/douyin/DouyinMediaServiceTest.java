package com.huanjing.geo.module.content.douyin;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadResponse;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaMaterialMapping;
import com.huanjing.geo.module.content.mapper.SelfMediaMaterialMappingMapper;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DouyinMediaServiceTest {
    private BrandMaterialMapper brandMaterialMapper;
    private SelfMediaMaterialMappingMapper mappingMapper;
    private MinioStorageService minioStorageService;
    private DouyinTokenService douyinTokenService;
    private DouyinClient douyinClient;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private DouyinMediaService service;

    @BeforeEach
    void setUp() {
        brandMaterialMapper = mock(BrandMaterialMapper.class);
        mappingMapper = mock(SelfMediaMaterialMappingMapper.class);
        minioStorageService = mock(MinioStorageService.class);
        douyinTokenService = mock(DouyinTokenService.class);
        douyinClient = mock(DouyinClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DouyinMediaService(
                brandMaterialMapper,
                mappingMapper,
                minioStorageService,
                douyinTokenService,
                douyinClient,
                redisTemplate
        );
    }

    @Test
    void ensureUploadedImageId_existingMappingReturnsImageId() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("png", "cover.png", "materials/cover.png"));
        when(minioStorageService.getObjectBytes("materials/cover.png")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(mapping("image-existing"));

        String imageId = service.ensureUploadedImageId(account(), 10L, 20L);

        assertEquals("image-existing", imageId);
        verifyNoInteractions(douyinClient, douyinTokenService);
    }

    @Test
    void ensureUploadedImageId_materialNotFoundThrows404() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.ensureUploadedImageId(account(), 10L, 20L));

        assertEquals(404, ex.getCode());
        assertEquals("brand material not found", ex.getMessage());
    }

    @Test
    void ensureUploadedImageId_brandMismatchThrows404() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material(99L, "png", "cover.png", "materials/cover.png"));

        BizException ex = assertThrows(BizException.class,
                () -> service.ensureUploadedImageId(account(), 10L, 20L));

        assertEquals(404, ex.getCode());
        assertEquals("brand material not found", ex.getMessage());
    }

    @Test
    void ensureUploadedImageId_missingObjectKeyThrows400WithoutFileUrlFallback() {
        BrandMaterial material = material("png", "cover.png", null);
        material.setFileUrl("https://example.com/cover.png");
        when(brandMaterialMapper.selectById(20L)).thenReturn(material);

        BizException ex = assertThrows(BizException.class,
                () -> service.ensureUploadedImageId(account(), 10L, 20L));

        assertEquals(400, ex.getCode());
        assertEquals("brand material missing object key", ex.getMessage());
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void ensureUploadedImageId_unsupportedTypeThrows400() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("gif", "cover.gif", "materials/cover.gif"));

        BizException ex = assertThrows(BizException.class,
                () -> service.ensureUploadedImageId(account(), 10L, 20L));

        assertEquals(400, ex.getCode());
        assertEquals("douyin_image_type_invalid", ex.getMessage());
    }

    @Test
    void ensureUploadedImageId_tooLargeThrows400() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("jpg", "cover.jpg", "materials/cover.jpg"));
        when(minioStorageService.getObjectBytes("materials/cover.jpg")).thenReturn(new byte[20 * 1024 * 1024 + 1]);

        BizException ex = assertThrows(BizException.class,
                () -> service.ensureUploadedImageId(account(), 10L, 20L));

        assertEquals(400, ex.getCode());
        assertEquals("douyin_image_too_large", ex.getMessage());
    }

    @Test
    void ensureUploadedImageId_lockNotAcquiredThenMappingAppearsReturnsImageId() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("png", "cover.png", "materials/cover.png"));
        when(minioStorageService.getObjectBytes("materials/cover.png")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(null, mapping("image-after-wait"));
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(false);

        String imageId = service.ensureUploadedImageId(account(), 10L, 20L);

        assertEquals("image-after-wait", imageId);
        verifyNoInteractions(douyinClient, douyinTokenService);
    }

    @Test
    void ensureUploadedImageId_lockNotAcquiredAndMappingStillMissingThrows429() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("png", "cover.png", "materials/cover.png"));
        when(minioStorageService.getObjectBytes("materials/cover.png")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(null, null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> service.ensureUploadedImageId(account(), 10L, 20L));

        assertEquals(429, ex.getCode());
        assertEquals("douyin image uploading", ex.getMessage());
    }

    @Test
    void ensureUploadedImageId_lockAcquiredThenDoubleCheckHitAvoidsUpload() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("png", "cover.png", "materials/cover.png"));
        when(minioStorageService.getObjectBytes("materials/cover.png")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(null, mapping("image-double-check"));
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);

        String imageId = service.ensureUploadedImageId(account(), 10L, 20L);

        assertEquals("image-double-check", imageId);
        verifyNoInteractions(douyinClient, douyinTokenService);
    }

    @Test
    void ensureUploadedImageId_uploadSuccessInsertsMapping() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("jpg", "cover.jpg", "materials/cover.jpg"));
        when(minioStorageService.getObjectBytes("materials/cover.jpg")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(null, null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("access-token");
        when(douyinClient.uploadImage(any())).thenReturn(uploadResponse("image-new", 800, 600));

        String imageId = service.ensureUploadedImageId(account(), 10L, 20L);

        assertEquals("image-new", imageId);
        ArgumentCaptor<DouyinImageUploadRequest> requestCaptor = ArgumentCaptor.forClass(DouyinImageUploadRequest.class);
        verify(douyinClient).uploadImage(requestCaptor.capture());
        assertEquals("access-token", requestCaptor.getValue().getAccessToken());
        assertEquals("open-1", requestCaptor.getValue().getOpenId());
        assertEquals("cover.jpg", requestCaptor.getValue().getFilename());
        assertEquals("image/jpeg", requestCaptor.getValue().getContentType());

        ArgumentCaptor<SelfMediaMaterialMapping> rowCaptor = ArgumentCaptor.forClass(SelfMediaMaterialMapping.class);
        verify(mappingMapper).insert(rowCaptor.capture());
        assertEquals(1L, rowCaptor.getValue().getSelfMediaAccountId());
        assertEquals(20L, rowCaptor.getValue().getBrandMaterialId());
        assertEquals("douyin_image", rowCaptor.getValue().getMediaType());
        assertEquals("image-new", rowCaptor.getValue().getPlatformMediaId());
        assertTrue(rowCaptor.getValue().getExtraJson().contains("\"width\":800"));
        assertTrue(rowCaptor.getValue().getExtraJson().contains("\"height\":600"));
    }

    @Test
    void uploadAccessTokenInvalidEvictsAndRetriesOnce() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("png", "cover.png", "materials/cover.png"));
        when(minioStorageService.getObjectBytes("materials/cover.png")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(null, null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("old-token", "fresh-token");
        when(douyinClient.uploadImage(any()))
                .thenThrow(new DouyinAuthException(200, 28001008L, "token invalid", "log", false, "{}"))
                .thenReturn(uploadResponse("image-retry", 800, 600));

        String imageId = service.ensureUploadedImageId(account(), 10L, 20L);

        assertEquals("image-retry", imageId);
        verify(douyinTokenService).evictAccessToken(any(SelfMediaAccount.class));
        ArgumentCaptor<DouyinImageUploadRequest> requestCaptor = ArgumentCaptor.forClass(DouyinImageUploadRequest.class);
        verify(douyinClient, org.mockito.Mockito.times(2)).uploadImage(requestCaptor.capture());
        assertEquals("old-token", requestCaptor.getAllValues().get(0).getAccessToken());
        assertEquals("fresh-token", requestCaptor.getAllValues().get(1).getAccessToken());
    }

    @Test
    void uploadAuthErrorOtherCodeDoesNotRetry() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("png", "cover.png", "materials/cover.png"));
        when(minioStorageService.getObjectBytes("materials/cover.png")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(null, null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("access-token");
        when(douyinClient.uploadImage(any()))
                .thenThrow(new DouyinAuthException(200, 10013L, "client key invalid", "log", false, "{}"));

        assertThrows(DouyinAuthException.class, () -> service.ensureUploadedImageId(account(), 10L, 20L));

        verify(douyinTokenService, never()).evictAccessToken(any());
        verify(douyinClient).uploadImage(any());
    }

    @Test
    void lockValueMismatchDoesNotDeleteLock() {
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("png", "cover.png", "materials/cover.png"));
        when(minioStorageService.getObjectBytes("materials/cover.png")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(null, null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("other-lock");
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("access-token");
        when(douyinClient.uploadImage(any())).thenReturn(uploadResponse("image-new", 800, 600));

        service.ensureUploadedImageId(account(), 10L, 20L);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void lockValueMatchDeletesLock() {
        AtomicReference<String> lockValue = new AtomicReference<>();
        when(brandMaterialMapper.selectById(20L)).thenReturn(material("png", "cover.png", "materials/cover.png"));
        when(minioStorageService.getObjectBytes("materials/cover.png")).thenReturn(bytes());
        when(mappingMapper.selectOne(any())).thenReturn(null, null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
                .thenAnswer(invocation -> {
                    lockValue.set(invocation.getArgument(1));
                    return true;
                });
        when(valueOperations.get(anyString())).thenAnswer(invocation -> lockValue.get());
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("access-token");
        when(douyinClient.uploadImage(any())).thenReturn(uploadResponse("image-new", 800, 600));

        service.ensureUploadedImageId(account(), 10L, 20L);

        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("douyin:material_lock:image:1:"));
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(1L);
        account.setBrandId(10L);
        account.setPlatform("douyin");
        account.setPlatformAccountId("open-1");
        account.setStatus("active");
        return account;
    }

    private BrandMaterial material(String fileType, String fileName, String objectKey) {
        return material(10L, fileType, fileName, objectKey);
    }

    private BrandMaterial material(Long brandId, String fileType, String fileName, String objectKey) {
        BrandMaterial material = new BrandMaterial();
        material.setId(20L);
        material.setBrandId(brandId);
        material.setFileType(fileType);
        material.setFileName(fileName);
        material.setObjectKey(objectKey);
        return material;
    }

    private SelfMediaMaterialMapping mapping(String imageId) {
        SelfMediaMaterialMapping mapping = new SelfMediaMaterialMapping();
        mapping.setId(100L);
        mapping.setSelfMediaAccountId(1L);
        mapping.setBrandMaterialId(20L);
        mapping.setContentHash("hash");
        mapping.setMediaType("douyin_image");
        mapping.setPlatformMediaId(imageId);
        return mapping;
    }

    private DouyinImageUploadResponse uploadResponse(String imageId, Integer width, Integer height) {
        return DouyinImageUploadResponse.builder()
                .imageId(imageId)
                .width(width)
                .height(height)
                .build();
    }

    private byte[] bytes() {
        return "image-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
