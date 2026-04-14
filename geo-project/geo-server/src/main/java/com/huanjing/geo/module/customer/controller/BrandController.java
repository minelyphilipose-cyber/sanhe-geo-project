package com.huanjing.geo.module.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.customer.dto.BrandCreateRequest;
import com.huanjing.geo.module.customer.dto.BrandUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.entity.BrandProfileVersion;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.customer.service.BrandProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.nio.charset.StandardCharsets;

@Tag(name = "Brand")
@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;
    private final BrandProfileService brandProfileService;

    @GetMapping
    public R<Page<Brand>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword
    ) {
        return R.ok(brandService.page(current, size, companyId, keyword));
    }

    @GetMapping("/{id}")
    public R<Brand> detail(@PathVariable Long id) {
        return R.ok(brandService.detail(id));
    }

    @PostMapping
    public R<Brand> create(@Valid @RequestBody BrandCreateRequest req) {
        return R.ok(brandService.create(req));
    }

    @PutMapping("/{id}")
    public R<Brand> update(@PathVariable Long id, @Valid @RequestBody BrandUpdateRequest req) {
        return R.ok(brandService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/materials/upload")
    public R<BrandMaterial> uploadMaterial(
            @PathVariable Long id,
            @RequestParam String category,
            @RequestPart("file") MultipartFile file
    ) {
        return R.ok(brandProfileService.uploadMaterial(id, category, file));
    }

    @GetMapping("/{id}/materials")
    public R<List<BrandMaterial>> listMaterials(
            @PathVariable Long id,
            @RequestParam(required = false) String category
    ) {
        return R.ok(brandProfileService.listMaterials(id, category));
    }

    @DeleteMapping("/{brandId}/materials/{materialId}")
    public R<Void> deleteMaterial(@PathVariable Long brandId, @PathVariable Long materialId) {
        brandProfileService.deleteMaterial(brandId, materialId);
        return R.ok();
    }

    @GetMapping("/{id}/versions")
    public R<Page<BrandProfileVersion>> pageVersions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size
    ) {
        return R.ok(brandProfileService.pageVersions(id, current, size));
    }

    @GetMapping("/{id}/versions/{versionNo}")
    public R<BrandProfileVersion> versionDetail(@PathVariable Long id, @PathVariable Integer versionNo) {
        return R.ok(brandProfileService.getVersionDetail(id, versionNo));
    }

    @GetMapping("/{brandId}/materials/{materialId}/stream")
    public ResponseEntity<byte[]> streamMaterial(
            @PathVariable Long brandId,
            @PathVariable Long materialId,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        BrandMaterial material = brandProfileService.materialDetail(brandId, materialId);
        byte[] bytes = brandProfileService.readMaterialBytes(brandId, materialId);
        String fileName = material.getFileName();
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        boolean forceDownload = download || !isPreviewable(mediaType);
        ContentDisposition disposition = forceDownload
                ? ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

    private boolean isPreviewable(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        return mediaType.includes(MediaType.APPLICATION_PDF) || "image".equalsIgnoreCase(mediaType.getType());
    }
}
