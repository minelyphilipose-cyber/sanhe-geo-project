package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.image.CompressedImage;
import com.huanjing.geo.common.image.ImageCompressionService;
import com.huanjing.geo.common.storage.MinioStorageService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.entity.BrandProfileVersion;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.mapper.BrandProfileVersionMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BrandProfileService {

    private static final long MAX_UPLOAD_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> MATERIAL_CATEGORIES = Set.of("brand_image", "case", "qualification", "other");

    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandProfileVersionMapper brandProfileVersionMapper;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;
    private final MinioStorageService minioStorageService;
    private final ImageCompressionService imageCompressionService;
    private final BrandImageFolderService brandImageFolderService;
    private final BrandMaterialPublicUrlService publicUrlService;
    private final ObjectMapper objectMapper;

    public List<BrandMaterial> listMaterials(Long brandId, String category) {
        return listMaterials(brandId, category, null);
    }

    public List<BrandMaterial> listMaterials(Long brandId, String category, Long folderId) {
        Brand brand = requireAccessibleBrand(brandId, true);
        LambdaQueryWrapper<BrandMaterial> wrapper = new LambdaQueryWrapper<BrandMaterial>()
                .eq(BrandMaterial::getBrandId, brand.getId())
                .orderByDesc(BrandMaterial::getCreatedAt);
        if (StringUtils.hasText(category)) {
            wrapper.eq(BrandMaterial::getCategory, category.trim());
        }
        if (folderId != null) {
            brandImageFolderService.requireFolder(brand.getId(), folderId);
            wrapper.eq(BrandMaterial::getFolderId, folderId);
        }
        return brandMaterialMapper.selectList(wrapper);
    }

    public BrandMaterial materialDetail(Long brandId, Long materialId) {
        requireAccessibleBrand(brandId, true);
        BrandMaterial material = brandMaterialMapper.selectById(materialId);
        if (material == null || !brandId.equals(material.getBrandId())) {
            throw new BizException(404, "Material not found");
        }
        return material;
    }

    public byte[] readMaterialBytes(Long brandId, Long materialId) {
        BrandMaterial material = materialDetail(brandId, materialId);
        if (!StringUtils.hasText(material.getObjectKey())) {
            throw new BizException(400, "Material object key is empty");
        }
        return minioStorageService.getObjectBytes(material.getObjectKey());
    }

    public InputStream openMaterialStream(Long brandId, Long materialId) {
        BrandMaterial material = materialDetail(brandId, materialId);
        return openVerifiedMaterialStream(material);
    }

    public InputStream openVerifiedMaterialStream(BrandMaterial material) {
        if (!StringUtils.hasText(material.getObjectKey())) {
            throw new BizException(400, "Material object key is empty");
        }
        return minioStorageService.openObjectStream(material.getObjectKey());
    }

    public byte[] readVerifiedMaterialBytes(BrandMaterial material) {
        if (!StringUtils.hasText(material.getObjectKey())) {
            throw new BizException(400, "Material object key is empty");
        }
        return minioStorageService.getObjectBytes(material.getObjectKey());
    }

    public String buildMaterialPreviewUrl(Long brandId, Long materialId) {
        BrandMaterial material = materialDetail(brandId, materialId);
        if (!StringUtils.hasText(material.getObjectKey())) {
            throw new BizException(400, "Material object key is empty");
        }
        return minioStorageService.buildPresignedDownloadUrl(material.getObjectKey(), 600);
    }

    public String buildMaterialPublicUrl(Long brandId, Long materialId) {
        return publicUrlService.buildPublicStreamUrl(materialDetail(brandId, materialId));
    }

    @Transactional
    public BrandMaterial uploadMaterial(Long brandId, String category, MultipartFile file) {
        return uploadMaterial(brandId, category, null, file);
    }

    @Transactional
    public BrandMaterial uploadMaterial(Long brandId, String category, Long folderId, MultipartFile file) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("brand.material.upload");
        Brand brand = requireAccessibleBrand(brandId, false);
        validateCategory(category);
        Long targetFolderId = null;
        if ("brand_image".equals(category)) {
            targetFolderId = folderId == null
                    ? brandImageFolderService.requireActiveFolderForSelection(
                            brand.getId(),
                            brandImageFolderService.ensureDefaultFolder(brandId, operator.getId()).getId()).getId()
                    : brandImageFolderService.requireActiveFolderForSelection(brand.getId(), folderId).getId();
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "Upload file is empty");
        }
        if (file.getSize() > MAX_UPLOAD_FILE_SIZE) {
            throw new BizException(400, "Upload file exceeds 10MB limit");
        }

        boolean imageUpload = isImageUpload(file, originalName(file));
        if ("brand_image".equals(category) && !imageUpload) {
            throw new BizException(400, "品牌形象素材必须是图片文件");
        }

        String originalName = originalName(file);
        String safeFileName;
        String safeFileType;
        String objectKey;
        String fileUrl;
        long fileSize;
        if (imageUpload) {
            CompressedImage image = imageCompressionService.compressToLimit(file);
            safeFileName = trimToLength(image.fileName(), 255);
            safeFileType = trimToLength(image.fileType(), 64);
            objectKey = buildObjectKey(brandId, image.fileName());
            fileUrl = minioStorageService.uploadBytes(image.bytes(), objectKey, image.contentType());
            fileSize = image.size();
        } else {
            safeFileName = trimToLength(originalName, 255);
            safeFileType = trimToLength(resolveFileTypeBySuffix(originalName), 64);
            objectKey = buildObjectKey(brandId, originalName);
            fileUrl = minioStorageService.upload(file, objectKey, file.getContentType());
            fileSize = file.getSize();
        }

        BrandMaterial material = new BrandMaterial();
        material.setBrandId(brandId);
        material.setFolderId(targetFolderId);
        material.setCategory(category);
        material.setFileName(safeFileName);
        material.setFileType(safeFileType);
        material.setFileUrl(fileUrl);
        material.setObjectKey(objectKey);
        material.setFileSize(fileSize);
        material.setCreatedBy(operator.getId());
        brandMaterialMapper.insert(material);
        createProfileVersionSnapshot(brand, operator.getId(), "material.upload");

        activityLogService.logAction(
                operator.getId(),
                "brand.material.upload",
                "brand",
                brandId,
                null,
                Map.of("materialId", material.getId(), "category", category, "fileName", safeFileName),
                null
        );
        return material;
    }

    @Transactional
    public void deleteMaterial(Long brandId, Long materialId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("brand.material.delete");
        requireAccessibleBrand(brandId, false);
        BrandMaterial material = brandMaterialMapper.selectById(materialId);
        if (material == null || !brandId.equals(material.getBrandId())) {
            throw new BizException(404, "Material not found");
        }
        brandMaterialMapper.deleteById(materialId);
        if (StringUtils.hasText(material.getObjectKey())) {
            minioStorageService.remove(material.getObjectKey());
        }
        Brand brand = brandMapper.selectById(brandId);
        createProfileVersionSnapshot(brand, operator.getId(), "material.delete");
        activityLogService.logAction(
                operator.getId(),
                "brand.material.delete",
                "brand",
                brandId,
                Map.of("materialId", material.getId(), "category", material.getCategory(), "fileName", material.getFileName()),
                null,
                null
        );
    }

    public Page<BrandProfileVersion> pageVersions(Long brandId, long current, long size) {
        requireAccessibleBrand(brandId, true);
        return brandProfileVersionMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<BrandProfileVersion>()
                        .eq(BrandProfileVersion::getBrandId, brandId)
                        .orderByDesc(BrandProfileVersion::getVersionNo)
        );
    }

    public BrandProfileVersion getVersionDetail(Long brandId, Integer versionNo) {
        requireAccessibleBrand(brandId, true);
        BrandProfileVersion version = brandProfileVersionMapper.selectOne(
                new LambdaQueryWrapper<BrandProfileVersion>()
                        .eq(BrandProfileVersion::getBrandId, brandId)
                        .eq(BrandProfileVersion::getVersionNo, versionNo)
                        .last("LIMIT 1")
        );
        if (version == null) {
            throw new BizException(404, "Brand profile version not found");
        }
        return version;
    }

    @Transactional
    public void createProfileVersionSnapshot(Brand brand, Long operatorUserId, String changeReason) {
        if (brand == null || brand.getId() == null) {
            return;
        }
        try {
            Integer nextVersionNo = nextVersionNo(brand.getId());
            List<BrandMaterial> materials = brandMaterialMapper.selectList(
                    new LambdaQueryWrapper<BrandMaterial>()
                            .eq(BrandMaterial::getBrandId, brand.getId())
                            .orderByDesc(BrandMaterial::getCreatedAt)
            );

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("brand", snapshotBrand(brand));
            snapshot.put("materials", materials);
            String snapshotJson = objectMapper.writeValueAsString(snapshot);

            BrandProfileVersion version = new BrandProfileVersion();
            version.setBrandId(brand.getId());
            version.setVersionNo(nextVersionNo);
            version.setSnapshotJson(snapshotJson);
            version.setChangeReason(StringUtils.hasText(changeReason) ? changeReason.trim() : null);
            version.setCreatedBy(operatorUserId == null ? 0L : operatorUserId);
            brandProfileVersionMapper.insert(version);
        } catch (Exception ex) {
            throw new BizException(500, "Create brand profile version failed");
        }
    }

    private Integer nextVersionNo(Long brandId) {
        BrandProfileVersion latest = brandProfileVersionMapper.selectOne(
                new LambdaQueryWrapper<BrandProfileVersion>()
                        .eq(BrandProfileVersion::getBrandId, brandId)
                        .orderByDesc(BrandProfileVersion::getVersionNo)
                        .last("LIMIT 1")
        );
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private void validateCategory(String category) {
        if (!MATERIAL_CATEGORIES.contains(category)) {
            throw new BizException(400, "Invalid material category");
        }
    }

    private String buildObjectKey(Long brandId, String originalName) {
        String date = LocalDate.now().toString().replace("-", "");
        String random = UUID.randomUUID().toString().replace("-", "");
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > -1 && dot < originalName.length() - 1) {
            ext = originalName.substring(dot);
        }
        return "brand/" + brandId + "/" + date + "/" + random + ext;
    }

    private String originalName(MultipartFile file) {
        return StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unknown";
    }

    private boolean isImageUpload(MultipartFile file, String fileName) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String suffix = resolveFileTypeBySuffix(fileName);
        return Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg").contains(suffix);
    }
    private String resolveFileTypeBySuffix(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "unknown";
        }
        String normalized = fileName.trim();
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return "unknown";
        }
        return normalized.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private Brand requireAccessibleBrand(Long brandId, boolean readOnly) {
        SysUser user = currentUserService.requireCurrentUser();
        if (readOnly) {
            currentUserService.ensurePermission("company.read");
        } else {
            currentUserService.ensurePermission("brand.update");
        }

        Brand brand = brandMapper.selectById(brandId);
        if (brand == null || brand.getDeletedAt() != null) {
            throw new BizException(404, "Brand not found");
        }
        Company company = companyMapper.selectById(brand.getCompanyId());
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        currentUserService.ensurePartnerResourceAccess(user, company.getPartnerId(), "brand");
        return brand;
    }

    private Map<String, Object> snapshotBrand(Brand brand) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", brand.getId());
        snapshot.put("companyId", brand.getCompanyId());
        snapshot.put("brandName", brand.getBrandName());
        snapshot.put("brandShortName", brand.getBrandShortName());
        snapshot.put("brandSlug", brand.getBrandSlug());
        snapshot.put("mainBusiness", brand.getMainBusiness());
        snapshot.put("coreProducts", brand.getCoreProducts());
        snapshot.put("brandPositioning", brand.getBrandPositioning());
        snapshot.put("serviceArea", brand.getServiceArea());
        snapshot.put("provinceCode", brand.getProvinceCode());
        snapshot.put("provinceName", brand.getProvinceName());
        snapshot.put("cityCode", brand.getCityCode());
        snapshot.put("cityName", brand.getCityName());
        snapshot.put("districtCode", brand.getDistrictCode());
        snapshot.put("districtName", brand.getDistrictName());
        snapshot.put("website", brand.getWebsite());
        snapshot.put("officialAccount", brand.getOfficialAccount());
        snapshot.put("videoAccount", brand.getVideoAccount());
        snapshot.put("douyinAccount", brand.getDouyinAccount());
        snapshot.put("phone", brand.getPhone());
        snapshot.put("publicPhone", brand.getPublicPhone());
        snapshot.put("publicAddress", brand.getPublicAddress());
        snapshot.put("selfMediaPublishLocationName", brand.getSelfMediaPublishLocationName());
        snapshot.put("wechat", brand.getWechat());
        snapshot.put("description", brand.getDescription());
        snapshot.put("businessIntro", brand.getBusinessIntro());
        snapshot.put("brandQualificationDescription", brand.getBrandQualificationDescription());
        snapshot.put("brandCaseDescription", brand.getBrandCaseDescription());
        snapshot.put("forbiddenPhrases", brand.getForbiddenPhrases());
        snapshot.put("status", brand.getStatus());
        return snapshot;
    }
}
