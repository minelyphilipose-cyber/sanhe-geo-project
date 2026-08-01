package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandImageFolderProject;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderProjectMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandImageFolderService;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.project.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ArticleAutoImageInsertionService {

    private static final String ILLUSTRATION_FOLDER_PREFIX = "插图";
    private static final int SHORT_ARTICLE_IMAGE_COUNT = 1;
    private static final int MEDIUM_ARTICLE_IMAGE_COUNT = 2;
    private static final int LONG_ARTICLE_IMAGE_COUNT = 3;
    private static final int DOUYIN_BODY_IMAGE_COUNT = 5;
    private static final int SHORT_ARTICLE_TEXT_LENGTH = 600;
    private static final int LONG_ARTICLE_TEXT_LENGTH = 1500;
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*]\\(([^)]+)\\)");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[[^\\]]*]\\(([^)]+)\\)");
    private static final Pattern HTML_IMAGE_BLOCK_PATTERN = Pattern.compile("(?is)(?:<p\\b[^>]*>\\s*)?<img\\b[^>]*>(?:\\s*</p>)?");
    private static final Map<String, ChannelImagePolicy> CHANNEL_POLICIES = Map.of(
            ArticlePromptChannels.AGENT_SITE, ChannelImagePolicy.body(),
            ArticlePromptChannels.AUTHORITY_MEDIA, ChannelImagePolicy.body(),
            ArticlePromptChannels.FORUM, ChannelImagePolicy.body(),
            ArticlePromptChannels.INDUSTRY_SITE, ChannelImagePolicy.body()
    );
    private static final Set<String> SELF_MEDIA_EXCLUDED_SUB_CODES = Set.of("xiaohongshu");
    private static final Set<String> SELF_MEDIA_STRIP_IMAGE_SUB_CODES = Set.of("toutiao");
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> TRAILING_SECTIONS = Set.of("结语", "总结", "免责声明", "联系方式", "联系我们");

    private final BrandImageFolderMapper folderMapper;
    private final BrandImageFolderProjectMapper folderProjectMapper;
    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandMaterialPublicUrlService publicUrlService;

    public String insertForChannel(Project project, String channelGroupCode, String contentMarkdown) {
        return insertForChannel(project, channelGroupCode, null, contentMarkdown, null);
    }

    public String insertForChannel(Project project,
                                   String channelGroupCode,
                                   String channelSubCode,
                                   String contentMarkdown,
                                   String excludedImageUrl) {
        ChannelImagePolicy policy = resolvePolicy(channelGroupCode, channelSubCode);
        if (!StringUtils.hasText(contentMarkdown)) {
            return contentMarkdown;
        }
        if (policy.mode() == ImageInsertionMode.STRIP) {
            return stripImageReferences(contentMarkdown);
        }
        if (policy.mode() == ImageInsertionMode.NONE || project == null || project.getBrandId() == null) {
            return contentMarkdown;
        }
        Set<String> excludedUrls = existingImageUrls(contentMarkdown);
        addExcludedUrl(excludedUrls, excludedImageUrl);
        boolean douyinImageText = isDouyinImageText(channelGroupCode, channelSubCode);
        int imageCount = douyinImageText ? DOUYIN_BODY_IMAGE_COUNT : bodyImageCount(contentMarkdown);
        List<ImageRef> images = selectRandomIllustrationImages(project, imageCount, excludedUrls);
        if (images.isEmpty()) {
            return contentMarkdown;
        }
        return insertImagesAfterParagraphs(contentMarkdown, images, douyinImageText);
    }

    public String insertForTargetChannel(Project project, String targetChannel, String contentMarkdown, String excludedImageUrl) {
        String channel = trimToNull(targetChannel);
        if (channel == null) {
            return insertForChannel(project, targetChannel, contentMarkdown);
        }
        String prefix = ArticlePromptChannels.SELF_MEDIA + ":";
        if (channel.startsWith(prefix)) {
            return insertForChannel(project, ArticlePromptChannels.SELF_MEDIA, channel.substring(prefix.length()),
                    contentMarkdown, excludedImageUrl);
        }
        return insertForChannel(project, channel, contentMarkdown);
    }

    public String insertSelectedHeadImage(Project project, Long materialId, String contentMarkdown) {
        if (project == null || project.getBrandId() == null || materialId == null || !StringUtils.hasText(contentMarkdown)) {
            return contentMarkdown;
        }
        BrandMaterial material = brandMaterialMapper.selectOne(
                new LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getId, materialId)
                        .eq(BrandMaterial::getBrandId, project.getBrandId())
                        .eq(BrandMaterial::getCategory, "brand_image")
                        .isNotNull(BrandMaterial::getFileUrl)
                        .isNotNull(BrandMaterial::getObjectKey)
        );
        if (material == null
                || !StringUtils.hasText(material.getFileUrl())
                || !StringUtils.hasText(material.getObjectKey())
                || !IMAGE_TYPES.contains(normalizeType(material.getFileType()))) {
            return contentMarkdown;
        }
        ImageRef image = toPublicImageRef(material);
        if (image == null || !StringUtils.hasText(image.url())) {
            return contentMarkdown;
        }
        return insertImageAfterOpeningParagraph(contentMarkdown, image);
    }

    private ChannelImagePolicy resolvePolicy(String channelGroupCode, String channelSubCode) {
        String groupCode = trimToNull(channelGroupCode);
        if (ArticlePromptChannels.SELF_MEDIA.equals(groupCode)) {
            String subCode = trimToNull(channelSubCode);
            if (subCode == null) {
                return ChannelImagePolicy.none();
            }
            subCode = ArticlePromptChannels.canonicalSubCode(ArticlePromptChannels.SELF_MEDIA, subCode);
            if (SELF_MEDIA_STRIP_IMAGE_SUB_CODES.contains(subCode)) {
                return ChannelImagePolicy.strip();
            }
            if (!ArticlePromptChannels.SELF_MEDIA_SUBS.contains(subCode)
                    || SELF_MEDIA_EXCLUDED_SUB_CODES.contains(subCode)) {
                return ChannelImagePolicy.none();
            }
            return ChannelImagePolicy.body();
        }
        return CHANNEL_POLICIES.getOrDefault(groupCode, ChannelImagePolicy.none());
    }

    private String stripImageReferences(String markdown) {
        String text = HTML_IMAGE_BLOCK_PATTERN.matcher(markdown).replaceAll("");
        text = MARKDOWN_IMAGE_PATTERN.matcher(text).replaceAll("");
        return text
                .replaceAll("(?m)^[ \\t]+$", "")
                .replaceAll("\\R{3,}", "\n\n")
                .trim();
    }

    private List<ImageRef> selectRandomIllustrationImages(Project project, int limit, Set<String> excludedUrls) {
        Long brandId = project == null ? null : project.getBrandId();
        if (brandId == null) {
            return List.of();
        }
        if (limit <= 0) {
            return List.of();
        }
        Set<String> normalizedExcludedUrls = normalizeUrls(excludedUrls);
        Map<String, ImageRef> refs = new LinkedHashMap<>();
        for (Long folderId : illustrationFolderIds(brandId, project.getId())) {
            addShuffledImageRefs(refs, selectUsableBrandImages(brandId, List.of(folderId)),
                    normalizedExcludedUrls, limit);
            if (refs.size() >= limit) {
                break;
            }
        }
        if (refs.size() < limit) {
            addShuffledImageRefs(refs, selectUsableBrandImages(brandId, null),
                    normalizedExcludedUrls, limit);
        }
        return refs.values().stream().toList();
    }

    private void addShuffledImageRefs(Map<String, ImageRef> refs,
                                      List<BrandMaterial> candidates,
                                      Set<String> normalizedExcludedUrls,
                                      int limit) {
        List<BrandMaterial> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        for (BrandMaterial material : shuffled) {
            ImageRef ref = toPublicImageRef(material);
            if (ref == null || !StringUtils.hasText(ref.url())) {
                continue;
            }
            String normalizedUrl = normalizeUrl(ref.url());
            if (!normalizedExcludedUrls.contains(normalizedUrl)) {
                refs.putIfAbsent(normalizedUrl, ref);
            }
            if (refs.size() >= limit) {
                return;
            }
        }
    }

    private List<BrandMaterial> selectUsableBrandImages(Long brandId, List<Long> folderIds) {
        return brandMaterialMapper.selectList(
                new LambdaQueryWrapper<BrandMaterial>()
                        .eq(BrandMaterial::getBrandId, brandId)
                        .eq(BrandMaterial::getCategory, "brand_image")
                        .in(folderIds != null && !folderIds.isEmpty(), BrandMaterial::getFolderId, folderIds)
                        .isNotNull(BrandMaterial::getFileUrl)
                        .isNotNull(BrandMaterial::getObjectKey)
        ).stream()
                .filter(material -> StringUtils.hasText(material.getFileUrl()))
                .filter(material -> StringUtils.hasText(material.getObjectKey()))
                .filter(material -> material.getFileSize() == null || material.getFileSize() > 0)
                .filter(material -> IMAGE_TYPES.contains(normalizeType(material.getFileType())))
                .toList();
    }

    private List<Long> illustrationFolderIds(Long brandId, Long projectId) {
        List<BrandImageFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<BrandImageFolder>()
                        .eq(BrandImageFolder::getBrandId, brandId)
                        .likeRight(BrandImageFolder::getFolderName, ILLUSTRATION_FOLDER_PREFIX)
                        .eq(BrandImageFolder::getStatus, BrandImageFolderService.STATUS_ACTIVE))
                .stream().toList();
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
                .filter(Objects::nonNull)
                .toList();
    }

    private ImageRef toPublicImageRef(BrandMaterial material) {
        return new ImageRef(
                StringUtils.hasText(material.getFileName()) ? material.getFileName().trim() : "品牌图片",
                publicUrlService.buildPublicStreamUrl(material)
        );
    }

    private String insertImagesAfterParagraphs(String markdown,
                                               List<ImageRef> images,
                                               boolean appendRemainingImages) {
        List<String> lines = new ArrayList<>(List.of(markdown.split("\\R", -1)));
        List<Integer> paragraphEnds = paragraphEndIndexes(lines);
        if (paragraphEnds.isEmpty()) {
            return appendImages(markdown, images);
        }

        List<ImageInsert> inserts = new ArrayList<>();
        int insertCount = Math.min(images.size(), paragraphEnds.size());
        for (int i = 0; i < insertCount; i++) {
            inserts.add(new ImageInsert(paragraphEnds.get(distributedIndex(paragraphEnds.size(), insertCount, i)), images.get(i)));
        }
        inserts.sort((left, right) -> Integer.compare(right.lineIndex(), left.lineIndex()));

        for (ImageInsert insert : inserts) {
            lines.add(insert.lineIndex() + 1, "");
            lines.add(insert.lineIndex() + 2, markdownImage(insert.material()));
            lines.add(insert.lineIndex() + 3, "");
        }
        String result = String.join("\n", lines).trim();
        return appendRemainingImages && insertCount < images.size()
                ? appendImages(result, images.subList(insertCount, images.size()))
                : result;
    }

    private String insertImageAfterOpeningParagraph(String markdown, ImageRef image) {
        List<String> lines = new ArrayList<>(List.of(markdown.split("\\R", -1)));
        List<Integer> paragraphEnds = paragraphEndIndexes(lines);
        if (!paragraphEnds.isEmpty()) {
            int insertIndex = paragraphEnds.get(0);
            lines.add(insertIndex + 1, "");
            lines.add(insertIndex + 2, markdownImage(image));
            lines.add(insertIndex + 3, "");
            return String.join("\n", lines).trim();
        }
        return appendImages(markdown, List.of(image));
    }

    private List<Integer> paragraphEndIndexes(List<String> lines) {
        List<Integer> result = new ArrayList<>();
        boolean inCodeBlock = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock || !isEligibleInsertionLine(lines, i)) {
                continue;
            }
            if (!isParagraphLine(line)) {
                continue;
            }
            int next = i + 1;
            if (next >= lines.size() || !isParagraphLine(lines.get(next).trim())
                    || !isEligibleInsertionLine(lines, next)) {
                result.add(i);
            }
        }
        return result;
    }

    private boolean isEligibleInsertionLine(List<String> lines, int index) {
        String line = lines.get(index).trim();
        if (isTableLine(line) || isTrailingSectionLine(line)) {
            return false;
        }
        int previous = index - 1;
        int next = index + 1;
        return !isImageLine(lines, previous) && !isImageLine(lines, next);
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
                && !line.startsWith("```")
                && !isTableLine(line);
    }

    private boolean isTableLine(String line) {
        return line.startsWith("|") && line.endsWith("|");
    }

    private boolean isImageLine(List<String> lines, int index) {
        if (index < 0 || index >= lines.size()) {
            return false;
        }
        String line = lines.get(index).trim();
        return line.startsWith("!") || line.startsWith("<img");
    }

    private boolean isTrailingSectionLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String normalized = line.replace("#", "").trim();
        return TRAILING_SECTIONS.stream().anyMatch(normalized::contains);
    }

    private int distributedIndex(int paragraphCount, int imageCount, int imageIndex) {
        if (imageCount <= 1) {
            return Math.max(0, paragraphCount / 2 - 1);
        }
        double slot = (double) (imageIndex + 1) / (imageCount + 1);
        return Math.min(paragraphCount - 1, Math.max(0, (int) Math.round(slot * paragraphCount) - 1));
    }

    private String appendImages(String markdown, List<ImageRef> images) {
        StringBuilder builder = new StringBuilder(markdown.trim());
        for (ImageRef image : images) {
            builder.append("\n\n").append(markdownImage(image));
        }
        return builder.toString();
    }

    private String markdownImage(ImageRef image) {
        return "![" + escapeMarkdownImageAlt(image.alt()) + "](" + escapeMarkdownImageUrl(image.url().trim()) + ")";
    }

    private String escapeMarkdownImageAlt(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("]", "\\]")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private String escapeMarkdownImageUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace(" ", "%20")
                .replace(")", "%29");
    }

    private String normalizeType(String fileType) {
        return StringUtils.hasText(fileType) ? fileType.trim().toLowerCase(Locale.ROOT) : "";
    }

    private int bodyImageCount(String markdown) {
        int length = plainTextLength(markdown);
        if (length < SHORT_ARTICLE_TEXT_LENGTH) {
            return SHORT_ARTICLE_IMAGE_COUNT;
        }
        if (length < LONG_ARTICLE_TEXT_LENGTH) {
            return MEDIUM_ARTICLE_IMAGE_COUNT;
        }
        return LONG_ARTICLE_IMAGE_COUNT;
    }

    private int plainTextLength(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return 0;
        }
        String text = MARKDOWN_IMAGE_PATTERN.matcher(markdown).replaceAll("");
        text = MARKDOWN_LINK_PATTERN.matcher(text).replaceAll("");
        text = text.replaceAll("(?m)^\\s*#+\\s*", "")
                .replaceAll("(?m)^\\s*[-*>|`]+\\s*", "")
                .replaceAll("\\s+", "");
        return text.length();
    }

    private Set<String> existingImageUrls(String markdown) {
        Set<String> urls = new LinkedHashSet<>();
        if (!StringUtils.hasText(markdown)) {
            return urls;
        }
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown);
        while (matcher.find()) {
            addExcludedUrl(urls, matcher.group(1));
        }
        return urls;
    }

    private void addExcludedUrl(Set<String> urls, String url) {
        String normalizedUrl = normalizeUrl(url);
        if (StringUtils.hasText(normalizedUrl)) {
            urls.add(normalizedUrl);
        }
    }

    private Set<String> normalizeUrls(Set<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        urls.forEach(url -> addExcludedUrl(normalized, url));
        return normalized;
    }

    private String normalizeUrl(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isDouyinImageText(String channelGroupCode, String channelSubCode) {
        return ArticlePromptChannels.SELF_MEDIA.equals(trimToNull(channelGroupCode))
                && "douyin".equals(ArticlePromptChannels.canonicalSubCode(
                        ArticlePromptChannels.SELF_MEDIA,
                        trimToNull(channelSubCode)
                ));
    }

    private record ImageRef(String alt, String url) {
    }

    private record ImageInsert(int lineIndex, ImageRef material) {
    }

    private enum ImageInsertionMode {
        NONE,
        STRIP,
        BODY
    }

    private record ChannelImagePolicy(ImageInsertionMode mode) {
        private static ChannelImagePolicy none() {
            return new ChannelImagePolicy(ImageInsertionMode.NONE);
        }

        private static ChannelImagePolicy strip() {
            return new ChannelImagePolicy(ImageInsertionMode.STRIP);
        }

        private static ChannelImagePolicy body() {
            return new ChannelImagePolicy(ImageInsertionMode.BODY);
        }

    }
}
