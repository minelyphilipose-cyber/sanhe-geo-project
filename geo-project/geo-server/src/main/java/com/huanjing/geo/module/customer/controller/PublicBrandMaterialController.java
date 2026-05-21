package com.huanjing.geo.module.customer.controller;

import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.customer.service.BrandProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public/brand-materials")
@RequiredArgsConstructor
public class PublicBrandMaterialController {

    private final BrandMaterialPublicUrlService publicUrlService;
    private final BrandProfileService brandProfileService;

    @GetMapping("/{materialId}/stream")
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable Long materialId,
                                                        @RequestParam("sig") String signature) {
        BrandMaterial material = publicUrlService.verifyPublicAccess(materialId, signature);
        InputStream inputStream = brandProfileService.openVerifiedMaterialStream(material);
        StreamingResponseBody body = outputStream -> {
            try (inputStream) {
                inputStream.transferTo(outputStream);
            }
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resolveContentType(material.getFileType())))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline");
        if (material.getFileSize() != null && material.getFileSize() > 0) {
            builder.contentLength(material.getFileSize());
        }
        return builder.body(body);
    }

    private String resolveContentType(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return switch (fileType.trim().toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}
