package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandImageFolderProject;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderProjectMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandImageFolderService;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SelfMediaPublishMaterialSelectionService {
    private static final String COVER_FOLDER_NAME = "封面";
    private static final String ILLUSTRATION_FOLDER_PREFIX = "插图";
    private static final String BRAND_IMAGE_CATEGORY = "brand_image";
    private static final int DOUYIN_IMAGE_TEXT_MAX_IMAGES = 6;
    private static final long DOUYIN_IMAGE_TEXT_MAX_IMAGE_BYTES = 50L * 1024L * 1024L;
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(([^\\s)]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern MATERIAL_API_PATH_PATTERN = Pattern.compile(".*/api/brands/(\\d+)/materials/(\\d+)/(?:stream|preview-url)$");
    private static final Pattern PUBLIC_MATERIAL_API_PATH_PATTERN = Pattern.compile(".*/api/public/brand-materials/(\\d+)/stream$");
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final BrandImageFolderMapper folderMapper;
    private final BrandImageFolderProjectMapper folderProjectMapper;
    private final BrandMaterialMapper materialMapper;

    public Selection select(Project project, ArticleDraft article, String markdown) {
        Long brandId = materialBrandId(project, article);
        if (brandId == null) {
            return Selection.empty();
        }
        List<Long> contentImageIds = resolveImageMaterialIds(brandId, markdown);
        Long coverId = resolveCoverMaterialId(brandId, article, contentImageIds);
        List<Long> imageIds = contentImageIds.isEmpty() && coverId != null ? List.of(coverId) : contentImageIds;
        return new Selection(coverId, imageIds);
    }

    /**
     * Reuses the existing article material selection rules for Douyin image-text
     * publishing, then fills the list from project-related illustration folders.
     * The article cover is always placed first when it can be resolved.
     */
    public List<Long> selectDouyinImageTextImages(Project project, ArticleDraft article, String markdown) {
        Long brandId = materialBrandId(project, article);
        if (brandId == null) {
            return List.of();
        }
        List<Long> contentImageIds = resolveImageMaterialIds(brandId, markdown);
        Long coverId = resolveCoverMaterialId(brandId, article, contentImageIds);
        LinkedHashSet<Long> selected = new LinkedHashSet<>();
        addDouyinImage(brandId, selected, coverId);
        for (Long materialId : contentImageIds) {
            addDouyinImage(brandId, selected, materialId);
            if (selected.size() >= DOUYIN_IMAGE_TEXT_MAX_IMAGES) {
                return List.copyOf(selected);
            }
        }
        for (Long folderId : illustrationFolderIds(brandId, project == null ? null : project.getId())) {
            List<BrandMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<BrandMaterial>()
                    .eq(BrandMaterial::getBrandId, brandId)
                    .eq(BrandMaterial::getCategory, BRAND_IMAGE_CATEGORY)
                    .eq(BrandMaterial::getFolderId, folderId)
                    .isNotNull(BrandMaterial::getObjectKey)
                    .orderByDesc(BrandMaterial::getCreatedAt)
                    .orderByDesc(BrandMaterial::getId));
            for (BrandMaterial material : materials) {
                if (isUsableDouyinImage(brandId, material)) {
                    selected.add(material.getId());
                }
                if (selected.size() >= DOUYIN_IMAGE_TEXT_MAX_IMAGES) {
                    return List.copyOf(selected);
                }
            }
        }
        return List.copyOf(selected);
    }

    private void addDouyinImage(Long brandId, Set<Long> selected, Long materialId) {
        if (materialId == null || selected.size() >= DOUYIN_IMAGE_TEXT_MAX_IMAGES) {
            return;
        }
        BrandMaterial material = materialMapper.selectById(materialId);
        if (isUsableDouyinImage(brandId, material)) {
            selected.add(materialId);
        }
    }

    private List<Long> illustrationFolderIds(Long brandId, Long projectId) {
        List<BrandImageFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brandId)
                .likeRight(BrandImageFolder::getFolderName, ILLUSTRATION_FOLDER_PREFIX)
                .eq(BrandImageFolder::getStatus, BrandImageFolderService.STATUS_ACTIVE)
                .orderByDesc(BrandImageFolder::getUpdatedAt)
                .orderByDesc(BrandImageFolder::getId));
        if (folders.isEmpty()) {
            return List.of();
        }
        Set<Long> relatedFolderIds = projectId == null
                ? Set.of()
                : folderProjectMapper.selectList(new LambdaQueryWrapper<BrandImageFolderProject>()
                        .eq(BrandImageFolderProject::getProjectId, projectId)
                        .in(BrandImageFolderProject::getFolderId,
                                folders.stream().map(BrandImageFolder::getId).toList()))
                .stream()
                .map(BrandImageFolderProject::getFolderId)
                .collect(java.util.stream.Collectors.toSet());
        return folders.stream()
                .sorted((left, right) -> Boolean.compare(
                        relatedFolderIds.contains(right.getId()),
                        relatedFolderIds.contains(left.getId())))
                .map(BrandImageFolder::getId)
                .toList();
    }

    private Long materialBrandId(Project project, ArticleDraft article) {
        if (article != null && article.getSubjectBrandId() != null) {
            return article.getSubjectBrandId();
        }
        return project == null ? null : project.getBrandId();
    }

    private Long resolveCoverMaterialId(Long brandId, ArticleDraft article, List<Long> contentImageIds) {
        Long articleCoverId = resolveSingleUrl(brandId, article == null ? null : article.getCoverImageUrl());
        if (articleCoverId != null) {
            return articleCoverId;
        }
        if (contentImageIds != null && !contentImageIds.isEmpty()) {
            return contentImageIds.get(0);
        }
        Long coverFolderId = coverFolderId(brandId);
        Long coverFolderMaterialId = newestBrandImageId(brandId, coverFolderId);
        return coverFolderMaterialId == null ? newestBrandImageId(brandId, null) : coverFolderMaterialId;
    }

    private List<Long> resolveImageMaterialIds(Long brandId, String markdown) {
        LinkedHashSet<String> urls = extractImageUrls(markdown);
        if (urls.isEmpty()) {
            return List.of();
        }
        Map<String, Long> resolved = new LinkedHashMap<>();
        resolveByStoredFileUrl(brandId, urls).forEach(material -> resolved.put(material.getFileUrl(), material.getId()));
        for (String url : urls) {
            resolved.computeIfAbsent(url, ignored -> resolveSingleUrl(brandId, url));
        }
        return resolved.values().stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private List<BrandMaterial> resolveByStoredFileUrl(Long brandId, Set<String> urls) {
        return materialMapper.selectList(new LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getBrandId, brandId)
                        .eq(BrandMaterial::getCategory, BRAND_IMAGE_CATEGORY)
                        .in(BrandMaterial::getFileUrl, urls))
                .stream()
                .filter(material -> isUsableImage(brandId, material))
                .filter(material -> StringUtils.hasText(material.getFileUrl()))
                .toList();
    }

    private Long resolveSingleUrl(Long brandId, String value) {
        String normalized = normalizeUrl(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Long idFromApiUrl = resolveIdFromMaterialApiUrl(brandId, normalized);
        if (idFromApiUrl != null) {
            return idFromApiUrl;
        }
        BrandMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BrandMaterial>()
                .eq(BrandMaterial::getBrandId, brandId)
                .eq(BrandMaterial::getCategory, BRAND_IMAGE_CATEGORY)
                .eq(BrandMaterial::getFileUrl, normalized)
                .last("LIMIT 1"));
        return isUsableImage(brandId, material) ? material.getId() : null;
    }

    private Long resolveIdFromMaterialApiUrl(Long brandId, String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            Matcher publicMatcher = PUBLIC_MATERIAL_API_PATH_PATTERN.matcher(uri.getPath());
            if (publicMatcher.matches()) {
                return usableMaterialId(brandId, Long.valueOf(publicMatcher.group(1)));
            }
            Matcher matcher = MATERIAL_API_PATH_PATTERN.matcher(uri.getPath());
            if (!matcher.matches()) {
                return null;
            }
            Long urlBrandId = Long.valueOf(matcher.group(1));
            if (!brandId.equals(urlBrandId)) {
                return null;
            }
            return usableMaterialId(brandId, Long.valueOf(matcher.group(2)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long usableMaterialId(Long brandId, Long materialId) {
        BrandMaterial material = materialId == null ? null : materialMapper.selectById(materialId);
        return isUsableImage(brandId, material) ? material.getId() : null;
    }

    private Long coverFolderId(Long brandId) {
        BrandImageFolder folder = folderMapper.selectOne(new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brandId)
                .eq(BrandImageFolder::getFolderName, COVER_FOLDER_NAME)
                .eq(BrandImageFolder::getStatus, BrandImageFolderService.STATUS_ACTIVE)
                .last("LIMIT 1"));
        return folder == null ? null : folder.getId();
    }

    private Long newestBrandImageId(Long brandId, Long folderId) {
        BrandMaterial material = materialMapper.selectList(new LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getBrandId, brandId)
                        .eq(BrandMaterial::getCategory, BRAND_IMAGE_CATEGORY)
                        .eq(folderId != null, BrandMaterial::getFolderId, folderId)
                        .isNotNull(BrandMaterial::getObjectKey)
                        .orderByDesc(BrandMaterial::getCreatedAt))
                .stream()
                .filter(candidate -> isUsableImage(brandId, candidate))
                .findFirst()
                .orElse(null);
        return material == null ? null : material.getId();
    }

    private boolean isUsableImage(Long brandId, BrandMaterial material) {
        return material != null
                && brandId.equals(material.getBrandId())
                && BRAND_IMAGE_CATEGORY.equals(material.getCategory())
                && StringUtils.hasText(material.getObjectKey())
                && IMAGE_TYPES.contains(normalizeType(material.getFileType()))
                && activeFolder(brandId, material.getFolderId());
    }

    private boolean isUsableDouyinImage(Long brandId, BrandMaterial material) {
        return isUsableImage(brandId, material)
                && Set.of("jpg", "jpeg", "png", "webp").contains(normalizeType(material.getFileType()))
                && (material.getFileSize() == null
                || material.getFileSize() <= DOUYIN_IMAGE_TEXT_MAX_IMAGE_BYTES);
    }

    private boolean activeFolder(Long brandId, Long folderId) {
        if (folderId == null) {
            return false;
        }
        BrandImageFolder folder = folderMapper.selectById(folderId);
        return folder != null
                && brandId.equals(folder.getBrandId())
                && BrandImageFolderService.STATUS_ACTIVE.equalsIgnoreCase(folder.getStatus());
    }

    private LinkedHashSet<String> extractImageUrls(String markdown) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!StringUtils.hasText(markdown)) {
            return result;
        }
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String url = normalizeUrl(matcher.group(1));
            if (StringUtils.hasText(url)) {
                result.add(url);
            }
        }
        Document document = Jsoup.parseBodyFragment(markdown);
        document.select("img[src], img[data-src]").forEach(image -> {
            String url = normalizeUrl(StringUtils.hasText(image.attr("src")) ? image.attr("src") : image.attr("data-src"));
            if (StringUtils.hasText(url)) {
                result.add(url);
            }
        });
        return result;
    }

    private String normalizeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("<") && trimmed.endsWith(">"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String normalizeType(String fileType) {
        return StringUtils.hasText(fileType) ? fileType.trim().toLowerCase(Locale.ROOT) : "";
    }

    public record Selection(Long coverMaterialId, List<Long> imageMaterialIds) {
        private static Selection empty() {
            return new Selection(null, List.of());
        }

        public List<Long> imageMaterialIds() {
            return imageMaterialIds == null ? List.of() : new ArrayList<>(imageMaterialIds);
        }
    }
}
