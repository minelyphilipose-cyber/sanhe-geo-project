package com.huanjing.geo.common.storage;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingObjectStorageServiceTest {

    private ObjectStorageService minioBackend;
    private ObjectStorageService cosBackend;
    private StorageProperties properties;
    private RoutingObjectStorageService service;

    @BeforeEach
    void setUp() {
        minioBackend = mock(ObjectStorageService.class);
        cosBackend = mock(ObjectStorageService.class);
        properties = new StorageProperties();
        service = new RoutingObjectStorageService(minioBackend, cosBackend, properties);
    }

    @Test
    void shouldUseCosPresignedUrlWhenObjectExists() {
        properties.setProvider(StorageProperties.Provider.COS);
        properties.setReadFallbackToMinio(true);
        when(cosBackend.presignedGetUrl("brand/a.jpg", 600)).thenReturn("https://cos/a");

        assertEquals("https://cos/a", service.presignedGetUrl("brand/a.jpg", 600));

        verify(cosBackend).stat("brand/a.jpg");
        verify(minioBackend, never()).presignedGetUrl("brand/a.jpg", 600);
    }

    @Test
    void shouldFallbackToMinioPresignedUrlWhenCosObjectIsMissing() {
        properties.setProvider(StorageProperties.Provider.COS);
        properties.setReadFallbackToMinio(true);
        when(cosBackend.stat("brand/a.jpg")).thenThrow(new BizException(404, "missing"));
        when(minioBackend.presignedGetUrl("brand/a.jpg", 600)).thenReturn("https://minio/a");

        assertEquals("https://minio/a", service.presignedGetUrl("brand/a.jpg", 600));

        verify(cosBackend, never()).presignedGetUrl("brand/a.jpg", 600);
    }

    @Test
    void shouldNotHideNonNotFoundCosFailure() {
        properties.setProvider(StorageProperties.Provider.COS);
        properties.setReadFallbackToMinio(true);
        when(cosBackend.stat("brand/a.jpg")).thenThrow(new BizException(500, "COS unavailable"));

        BizException error = assertThrows(BizException.class,
                () -> service.presignedGetUrl("brand/a.jpg", 600));

        assertEquals(500, error.getCode());
        verify(minioBackend, never()).presignedGetUrl("brand/a.jpg", 600);
    }

    @Test
    void shouldAvoidExtraStatWhenFallbackIsDisabled() {
        properties.setProvider(StorageProperties.Provider.COS);
        properties.setReadFallbackToMinio(false);
        when(cosBackend.presignedGetUrl("brand/a.jpg", 600)).thenReturn("https://cos/a");

        assertEquals("https://cos/a", service.presignedGetUrl("brand/a.jpg", 600));

        verify(cosBackend, never()).stat("brand/a.jpg");
    }
}
