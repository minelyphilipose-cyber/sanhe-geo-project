package com.huanjing.geo.module.customer.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.customer.dto.BrandCreateRequest;
import com.huanjing.geo.module.customer.dto.BrandOfferingRequest;
import com.huanjing.geo.module.customer.dto.BrandOfferingVO;
import com.huanjing.geo.module.customer.dto.BrandImageFolderRequest;
import com.huanjing.geo.module.customer.dto.BrandImageFolderVO;
import com.huanjing.geo.module.customer.dto.BrandMaterialVO;
import com.huanjing.geo.module.customer.dto.BrandStatementRegenerateRequest;
import com.huanjing.geo.module.customer.dto.BrandStatementUpdateRequest;
import com.huanjing.geo.module.customer.dto.BrandUpdateRequest;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.entity.BrandProfileVersion;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolPreviewResponse;
import com.huanjing.geo.module.content.service.ThirdPartySubjectRotationService;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.service.BrandStatementDispatchService;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.customer.service.BrandOfferingService;
import com.huanjing.geo.module.customer.service.BrandImageFolderService;
import com.huanjing.geo.module.customer.service.BrandProfileService;
import com.huanjing.geo.module.customer.service.BrandStatementService;
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
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Tag(name = "Brand")
@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;
    private final BrandOfferingService brandOfferingService;
    private final BrandImageFolderService brandImageFolderService;
    private final BrandProfileService brandProfileService;
    private final BrandStatementService brandStatementService;
    private final BrandStatementDispatchService brandStatementDispatchService;
    private final ThirdPartySubjectRotationService thirdPartySubjectRotationService;

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

    @GetMapping("/{id}/third-party-subject-pool")
    public R<ThirdPartySubjectPoolPreviewResponse> thirdPartySubjectPool(@PathVariable Long id,
                                                                         @RequestParam(required = false) Integer candidateLimit,
                                                                         @RequestParam(required = false) Integer excludedLimit) {
        brandService.detail(id);
        return R.ok(thirdPartySubjectRotationService.previewPool(id, candidateLimit, excludedLimit));
    }

    @PostMapping("/{id}/geo-site/test")
    public R<Map<String, Object>> testGeoSite(@PathVariable Long id) {
        return R.ok(brandService.testGeoSite(id));
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

    @GetMapping("/{id}/offerings")
    public R<List<BrandOfferingVO>> listOfferings(@PathVariable Long id,
                                                  @RequestParam(required = false) String status) {
        return R.ok(brandOfferingService.list(id, status));
    }

    @PostMapping("/{id}/offerings")
    public R<BrandOfferingVO> createOffering(@PathVariable Long id,
                                             @Valid @RequestBody BrandOfferingRequest req) {
        return R.ok(brandOfferingService.create(id, req));
    }

    @PutMapping("/{id}/offerings/{offeringId}")
    public R<BrandOfferingVO> updateOffering(@PathVariable Long id,
                                             @PathVariable Long offeringId,
                                             @Valid @RequestBody BrandOfferingRequest req) {
        return R.ok(brandOfferingService.update(id, offeringId, req));
    }

    @DeleteMapping("/{id}/offerings/{offeringId}")
    public R<Void> deleteOffering(@PathVariable Long id, @PathVariable Long offeringId) {
        brandOfferingService.delete(id, offeringId);
        return R.ok();
    }

    @GetMapping("/{id}/statement")
    public R<Map<String, Object>> statementDetail(@PathVariable Long id) {
        return R.ok(brandStatementService.detail(id));
    }

    @PutMapping("/{id}/statement")
    public R<Map<String, Object>> saveStatementDraft(@PathVariable Long id, @Valid @RequestBody BrandStatementUpdateRequest req) {
        return R.ok(brandStatementService.saveDraft(id, req));
    }

    @PostMapping("/{id}/statement/lock")
    public R<Map<String, Object>> lockStatement(@PathVariable Long id) {
        return R.ok(brandStatementService.lock(id));
    }

    @PostMapping("/{id}/statement/unlock")
    public R<Map<String, Object>> unlockStatement(@PathVariable Long id) {
        return R.ok(brandStatementService.unlock(id));
    }

    @PostMapping("/{id}/statement/regenerate")
    public R<DispatchTask> regenerateStatement(@PathVariable Long id, @RequestBody(required = false) BrandStatementRegenerateRequest req) {
        brandStatementService.ensureRegeneratePermission(id);
        Long projectId = req == null ? null : req.getProjectId();
        String remark = req == null ? null : req.getRemark();
        return R.ok(brandStatementDispatchService.enqueueRegeneration(id, projectId, remark));
    }

    @PostMapping("/{id}/materials/upload")
    public R<BrandMaterialVO> uploadMaterial(
            @PathVariable Long id,
            @RequestParam String category,
            @RequestParam(required = false) Long folderId,
            @RequestPart("file") MultipartFile file
    ) {
        return R.ok(brandProfileService.toMaterialVO(brandProfileService.uploadMaterial(id, category, folderId, file)));
    }

    @GetMapping("/{id}/materials")
    public R<List<BrandMaterialVO>> listMaterials(
            @PathVariable Long id,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long folderId
    ) {
        return R.ok(brandProfileService.listMaterialViews(id, category, folderId));
    }

    @GetMapping("/{id}/image-folders")
    public R<List<BrandImageFolderVO>> listImageFolders(
            @PathVariable Long id,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "true") boolean includeMaterials
    ) {
        return R.ok(brandImageFolderService.listFolders(id, projectId, tag, activeOnly, includeMaterials));
    }

    @PostMapping("/{id}/image-folders")
    public R<BrandImageFolderVO> createImageFolder(@PathVariable Long id,
                                                   @Valid @RequestBody BrandImageFolderRequest req) {
        return R.ok(brandImageFolderService.createFolder(id, req));
    }

    @PutMapping("/{id}/image-folders/{folderId}")
    public R<BrandImageFolderVO> updateImageFolder(@PathVariable Long id,
                                                   @PathVariable Long folderId,
                                                   @Valid @RequestBody BrandImageFolderRequest req) {
        return R.ok(brandImageFolderService.updateFolder(id, folderId, req));
    }

    @DeleteMapping("/{id}/image-folders/{folderId}")
    public R<Void> deleteImageFolder(@PathVariable Long id, @PathVariable Long folderId) {
        brandImageFolderService.deleteFolder(id, folderId);
        return R.ok();
    }

    @GetMapping("/{id}/image-folder-tags")
    public R<List<String>> suggestImageFolderTags(@PathVariable Long id,
                                                  @RequestParam(required = false) String keyword) {
        return R.ok(brandImageFolderService.suggestTags(id, keyword));
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
        byte[] bytes = brandProfileService.readVerifiedMaterialBytes(material);
        String fileName = material.getFileName();
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        boolean forceDownload = download || !isPreviewable(mediaType);
        ContentDisposition disposition = forceDownload
                ? ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

    @GetMapping("/{brandId}/materials/{materialId}/preview-url")
    public R<Map<String, String>> materialPreviewUrl(
            @PathVariable Long brandId,
            @PathVariable Long materialId
    ) {
        String url = brandProfileService.buildMaterialPreviewUrl(brandId, materialId);
        return R.ok(Map.of("url", url));
    }

    private boolean isPreviewable(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        return mediaType.includes(MediaType.APPLICATION_PDF) || "image".equalsIgnoreCase(mediaType.getType());
    }
}
