package com.huanjing.geo.module.system.controller;

import com.huanjing.geo.module.system.service.AiPlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public/platform-configs")
@RequiredArgsConstructor
public class PublicAiPlatformLogoController {

    private final AiPlatformConfigService aiPlatformConfigService;

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        AiPlatformConfigService.PlatformLogoResource resource = aiPlatformConfigService.loadLogoResource(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .body(resource.bytes());
    }
}
