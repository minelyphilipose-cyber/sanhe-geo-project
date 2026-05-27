package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ArticleAutoImageInsertionService {

    private static final int REQUIRED_IMAGE_COUNT = 2;
    private static final Set<String> TARGET_CHANNELS = Set.of(
            ArticlePromptChannels.FORUM,
            ArticlePromptChannels.INDUSTRY_SITE
    );
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");

    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandMaterialPublicUrlService publicUrlService;

    public String insertForChannel(Project project, String channelGroupCode, String contentMarkdown) {
        if (!requiresImages(channelGroupCode) || project == null || project.getBrandId() == null
                || !StringUtils.hasText(contentMarkdown)) {
            return contentMarkdown;
        }
        List<ImageRef> images = selectRandomBrandImages(project.getBrandId());
        if (images.isEmpty()) {
            return contentMarkdown;
        }
        return insertImagesAfterParagraphs(contentMarkdown, images);
    }

    private boolean requiresImages(String channelGroupCode) {
        return StringUtils.hasText(channelGroupCode) && TARGET_CHANNELS.contains(channelGroupCode.trim());
    }

    private List<ImageRef> selectRandomBrandImages(Long brandId) {
        List<BrandMaterial> candidates = brandMaterialMapper.selectList(
                new LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getBrandId, brandId)
                        .eq(BrandMaterial::getCategory, "brand_image")
                        .isNotNull(BrandMaterial::getFileUrl)
                        .isNotNull(BrandMaterial::getObjectKey)
        ).stream()
                .filter(material -> StringUtils.hasText(material.getFileUrl()))
                .filter(material -> StringUtils.hasText(material.getObjectKey()))
                .filter(material -> IMAGE_TYPES.contains(normalizeType(material.getFileType())))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<BrandMaterial> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        return shuffled.stream()
                .limit(REQUIRED_IMAGE_COUNT)
                .map(this::toPublicImageRef)
                .filter(ref -> ref != null && StringUtils.hasText(ref.url()))
                .toList();
    }

    private ImageRef toPublicImageRef(BrandMaterial material) {
        return new ImageRef(
                StringUtils.hasText(material.getFileName()) ? material.getFileName().trim() : "品牌图片",
                publicUrlService.buildPublicStreamUrl(material)
        );
    }

    private String insertImagesAfterParagraphs(String markdown, List<ImageRef> images) {
        List<String> lines = new ArrayList<>(List.of(markdown.split("\\R", -1)));
        List<Integer> paragraphEnds = paragraphEndIndexes(lines);
        if (paragraphEnds.isEmpty()) {
            return appendImages(markdown, images);
        }

        List<ImageInsert> inserts = new ArrayList<>();
        inserts.add(new ImageInsert(paragraphEnds.get(firstHalfIndex(paragraphEnds)), images.get(0)));
        if (images.size() > 1) {
            inserts.add(new ImageInsert(paragraphEnds.get(secondHalfIndex(paragraphEnds)), images.get(1)));
        }
        inserts.sort((left, right) -> Integer.compare(right.lineIndex(), left.lineIndex()));

        for (ImageInsert insert : inserts) {
            lines.add(insert.lineIndex() + 1, "");
            lines.add(insert.lineIndex() + 2, markdownImage(insert.material()));
            lines.add(insert.lineIndex() + 3, "");
        }
        return String.join("\n", lines).trim();
    }

    private List<Integer> paragraphEndIndexes(List<String> lines) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!isParagraphLine(line)) {
                continue;
            }
            int next = i + 1;
            if (next >= lines.size() || !isParagraphLine(lines.get(next).trim())) {
                result.add(i);
            }
        }
        return result;
    }

    private boolean isParagraphLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        return !line.startsWith("#")
                && !line.startsWith("!")
                && !line.startsWith("<img")
                && !line.startsWith("- ")
                && !line.startsWith("* ")
                && !line.startsWith("> ")
                && !line.startsWith("```");
    }

    private int firstHalfIndex(List<Integer> paragraphEnds) {
        return Math.max(0, paragraphEnds.size() / 2 - 1);
    }

    private int secondHalfIndex(List<Integer> paragraphEnds) {
        if (paragraphEnds.size() <= 1) {
            return 0;
        }
        return Math.min(paragraphEnds.size() - 1, paragraphEnds.size() / 2 + Math.max(1, paragraphEnds.size() / 4));
    }

    private String appendImages(String markdown, List<ImageRef> images) {
        StringBuilder builder = new StringBuilder(markdown.trim());
        for (ImageRef image : images) {
            builder.append("\n\n").append(markdownImage(image));
        }
        return builder.toString();
    }

    private String markdownImage(ImageRef image) {
        return "<p><img src=\"" + escapeHtmlAttribute(image.url().trim()) + "\" alt=\""
                + escapeHtmlAttribute(image.alt()) + "\" style=\"display:block;max-width:100%;width:auto;height:auto;object-fit:contain;margin:16px auto;border-radius:6px;\" /></p>";
    }

    private String escapeHtmlAttribute(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String normalizeType(String fileType) {
        return StringUtils.hasText(fileType) ? fileType.trim().toLowerCase(Locale.ROOT) : "";
    }

    private record ImageRef(String alt, String url) {
    }

    private record ImageInsert(int lineIndex, ImageRef material) {
    }
}
