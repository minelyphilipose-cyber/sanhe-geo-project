package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MarkdownImageReferenceValidator {
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(([^\\s)]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern PUBLIC_MATERIAL_PATH_PATTERN = Pattern.compile(".*/api/public/brand-materials/(\\d+)/stream$");
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");

    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandMaterialPublicUrlService publicUrlService;

    public void validate(Project project, String markdown) {
        Set<String> imageUrls = extractImageUrls(markdown);
        if (imageUrls.isEmpty()) {
            return;
        }
        if (project == null || project.getBrandId() == null) {
            throw new BizException(400, "文章包含图片时必须绑定品牌项目");
        }
        for (String url : imageUrls) {
            validateHttpUrl(url);
        }
        Set<String> allowedUrls = brandMaterialMapper.selectList(
                        new LambdaQueryWrapper<BrandMaterial>()
                                .eq(BrandMaterial::getBrandId, project.getBrandId())
                                .eq(BrandMaterial::getCategory, "brand_image")
                                .in(BrandMaterial::getFileUrl, imageUrls)
                ).stream()
                .filter(material -> IMAGE_TYPES.contains(normalizeType(material.getFileType())))
                .map(BrandMaterial::getFileUrl)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        List<String> invalidUrls = imageUrls.stream()
                .filter(url -> !allowedUrls.contains(url) && !isValidPublicMaterialUrl(project.getBrandId(), url))
                .toList();
        if (!invalidUrls.isEmpty()) {
            throw new BizException(400, "文章图片必须从当前项目品牌图库中选择");
        }
    }

    Set<String> extractImageUrls(String markdown) {
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

    private void validateHttpUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("unsupported scheme");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("missing host");
            }
        } catch (Exception ex) {
            throw new BizException(400, "文章图片地址仅支持当前品牌图库中的 http/https URL");
        }
    }

    private boolean isValidPublicMaterialUrl(Long brandId, String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            Matcher matcher = PUBLIC_MATERIAL_PATH_PATTERN.matcher(uri.getPath());
            if (!matcher.matches()) {
                return false;
            }
            String signature = queryParam(uri.getRawQuery(), "sig");
            BrandMaterial material = publicUrlService.verifyPublicAccess(Long.valueOf(matcher.group(1)), signature);
            return material != null
                    && brandId.equals(material.getBrandId())
                    && IMAGE_TYPES.contains(normalizeType(material.getFileType()));
        } catch (Exception ex) {
            return false;
        }
    }

    private String queryParam(String rawQuery, String name) {
        if (!StringUtils.hasText(rawQuery)) {
            return null;
        }
        String prefix = name + "=";
        for (String part : rawQuery.split("&")) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return null;
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
}
