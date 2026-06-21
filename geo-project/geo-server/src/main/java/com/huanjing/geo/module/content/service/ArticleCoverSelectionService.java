package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandImageFolderService;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleCoverSelectionService {

    private static final String COVER_FOLDER_NAME = "封面";
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final BrandImageFolderMapper folderMapper;
    private final BrandMaterialMapper materialMapper;
    private final BrandMaterialPublicUrlService publicUrlService;

    public String requireManualCoverUrl(Long brandId, Long materialId) {
        if (brandId == null) {
            throw new BizException(400, "品牌信息缺失，无法选择封面");
        }
        if (materialId == null) {
            throw new BizException(400, "自媒体文章必须选择封面图片");
        }
        BrandMaterial material = materialMapper.selectById(materialId);
        if (!isUsableBrandImage(brandId, material)) {
            throw new BizException(400, "封面图片不存在或不属于当前品牌素材库");
        }
        return publicUrlService.buildPublicStreamUrl(material);
    }

    public String selectRandomCoverUrl(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return selectRandomCoverUrl(brandId, null);
    }

    public String selectRandomCoverUrlExcluding(Long brandId, String excludedUrl) {
        if (brandId == null) {
            return null;
        }
        return selectRandomCoverUrl(brandId, normalizeUrl(excludedUrl));
    }

    private String selectRandomCoverUrl(Long brandId, String excludedUrl) {
        List<BrandMaterial> coverFolderImages = coverFolderImages(brandId);
        List<BrandMaterial> candidates = excludeUrl(coverFolderImages, excludedUrl);
        if (!candidates.isEmpty()) {
            return publicUrlService.buildPublicStreamUrl(randomOne(candidates));
        }
        List<BrandMaterial> allImages = brandImages(brandId, null);
        candidates = excludeUrl(allImages, excludedUrl);
        if (candidates.isEmpty()) {
            return null;
        }
        return publicUrlService.buildPublicStreamUrl(randomOne(candidates));
    }

    private List<BrandMaterial> coverFolderImages(Long brandId) {
        BrandImageFolder coverFolder = folderMapper.selectOne(new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brandId)
                .eq(BrandImageFolder::getFolderName, COVER_FOLDER_NAME)
                .eq(BrandImageFolder::getStatus, BrandImageFolderService.STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (coverFolder == null) {
            return List.of();
        }
        return brandImages(brandId, coverFolder.getId());
    }

    private List<BrandMaterial> brandImages(Long brandId, Long folderId) {
        return materialMapper.selectList(new LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getBrandId, brandId)
                        .eq(BrandMaterial::getCategory, "brand_image")
                        .eq(folderId != null, BrandMaterial::getFolderId, folderId)
                        .isNotNull(BrandMaterial::getObjectKey)
                        .orderByDesc(BrandMaterial::getCreatedAt))
                .stream()
                .filter(material -> isUsableBrandImage(brandId, material))
                .toList();
    }

    private boolean isUsableBrandImage(Long brandId, BrandMaterial material) {
        return material != null
                && brandId.equals(material.getBrandId())
                && "brand_image".equals(material.getCategory())
                && StringUtils.hasText(material.getObjectKey())
                && IMAGE_TYPES.contains(normalizeType(material.getFileType()));
    }

    private BrandMaterial randomOne(List<BrandMaterial> materials) {
        List<BrandMaterial> shuffled = new ArrayList<>(materials);
        Collections.shuffle(shuffled);
        return shuffled.get(0);
    }

    private List<BrandMaterial> excludeUrl(List<BrandMaterial> materials, String excludedUrl) {
        if (!StringUtils.hasText(excludedUrl)) {
            return materials;
        }
        return materials.stream()
                .filter(material -> !normalizeUrl(publicUrlService.buildPublicStreamUrl(material)).equals(excludedUrl))
                .toList();
    }

    private String normalizeType(String fileType) {
        return StringUtils.hasText(fileType) ? fileType.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeUrl(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
