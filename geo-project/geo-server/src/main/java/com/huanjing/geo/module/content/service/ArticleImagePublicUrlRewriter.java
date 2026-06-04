package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ArticleImagePublicUrlRewriter {

    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(([^\\s)]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern MATERIAL_API_PATH_PATTERN = Pattern.compile(".*/api/brands/(\\d+)/materials/(\\d+)/(?:stream|preview-url)$");

    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandMaterialPublicUrlService publicUrlService;

    public String rewrite(Project project, String markdown) {
        if (!StringUtils.hasText(markdown) || project == null || project.getBrandId() == null) {
            return markdown;
        }
        Map<String, BrandMaterial> replacements = resolveMaterials(project.getBrandId(), extractImageUrls(markdown));
        if (replacements.isEmpty()) {
            return markdown;
        }
        String rewritten = markdown;
        for (Map.Entry<String, BrandMaterial> entry : replacements.entrySet()) {
            rewritten = rewritten.replace(entry.getKey(), publicUrlService.buildPublicStreamUrl(entry.getValue()));
        }
        return rewritten;
    }

    public String rewriteUrl(Project project, String imageUrl) {
        String normalized = normalizeUrl(imageUrl);
        if (!StringUtils.hasText(normalized) || project == null || project.getBrandId() == null) {
            return imageUrl;
        }
        Map<String, BrandMaterial> replacements = resolveMaterials(project.getBrandId(), Set.of(normalized));
        BrandMaterial material = replacements.get(normalized);
        return material == null ? imageUrl : publicUrlService.buildPublicStreamUrl(material);
    }

    private Map<String, BrandMaterial> resolveMaterials(Long brandId, Set<String> imageUrls) {
        if (imageUrls.isEmpty()) {
            return Map.of();
        }
        Map<String, BrandMaterial> resolved = new LinkedHashMap<>();
        resolveByStoredFileUrl(brandId, imageUrls).forEach(material ->
                resolved.put(material.getFileUrl(), material));

        for (String imageUrl : imageUrls) {
            if (resolved.containsKey(imageUrl)) {
                continue;
            }
            BrandMaterial material = resolveByMaterialApiUrl(brandId, imageUrl);
            if (material != null) {
                resolved.put(imageUrl, material);
            }
        }
        return resolved;
    }

    private List<BrandMaterial> resolveByStoredFileUrl(Long brandId, Set<String> imageUrls) {
        return brandMaterialMapper.selectList(
                new LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getBrandId, brandId)
                        .eq(BrandMaterial::getCategory, "brand_image")
                        .in(BrandMaterial::getFileUrl, imageUrls)
        ).stream()
                .filter(material -> StringUtils.hasText(material.getFileUrl()))
                .collect(Collectors.toList());
    }

    private BrandMaterial resolveByMaterialApiUrl(Long brandId, String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            Matcher matcher = MATERIAL_API_PATH_PATTERN.matcher(uri.getPath());
            if (!matcher.matches()) {
                return null;
            }
            Long urlBrandId = Long.valueOf(matcher.group(1));
            Long materialId = Long.valueOf(matcher.group(2));
            if (!brandId.equals(urlBrandId)) {
                return null;
            }
            BrandMaterial material = brandMaterialMapper.selectById(materialId);
            if (material == null
                    || !brandId.equals(material.getBrandId())
                    || !"brand_image".equals(material.getCategory())
                    || !StringUtils.hasText(material.getObjectKey())) {
                return null;
            }
            return material;
        } catch (Exception ex) {
            return null;
        }
    }

    private Set<String> extractImageUrls(String markdown) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
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
}
