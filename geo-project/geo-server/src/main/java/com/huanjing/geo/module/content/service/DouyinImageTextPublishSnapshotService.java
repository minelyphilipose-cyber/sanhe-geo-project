package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.DouyinImageTextPublishSnapshot;
import com.huanjing.geo.module.content.dto.DouyinImageTextQuickPublishRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.service.BrandMaterialPublicUrlService;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DouyinImageTextPublishSnapshotService {
    private static final int MIN_IMAGE_COUNT = 4;
    private static final int MAX_IMAGE_COUNT = 6;
    private static final int MAX_TITLE_LENGTH = 20;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final long MAX_IMAGE_BYTES = 50L * 1024L * 1024L;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "webp");
    private static final List<String> REGION_DICT_TYPES =
            List.of("administrative_region", "region_code", "city_code");
    private static final Pattern HAN = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern CITY_NAME = Pattern.compile(
            "([\\p{IsHan}]{2,16}?)(?:市|自治州|地区|盟)");
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern MARKDOWN_PREFIX = Pattern.compile(
            "(?m)^\\s{0,3}(?:#{1,6}\\s+|[-*+]\\s+|>\\s*)");
    private static final Pattern MARKDOWN_DECORATION = Pattern.compile("[*_~`|]");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[。！？!?；;\\n]");

    private final BrandMaterialMapper brandMaterialMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final BrandMaterialPublicUrlService materialPublicUrlService;
    private final ObjectMapper objectMapper;

    public TopicPreview previewTopic(Brand brand) {
        if (brand == null || brand.getId() == null) {
            throw new BizException(400, "当前文章未绑定有效品牌，无法生成抖音中文话题");
        }
        ResolvedText region = resolveRegion(brand);
        ResolvedText industry = resolveIndustry(brand.getIndustry());
        return new TopicPreview(region.text(), industry.text(), "#" + region.text() + industry.text(),
                region.source(), brand.getIndustry());
    }

    public DouyinImageTextPublishSnapshot build(Brand brand, DouyinImageTextQuickPublishRequest request) {
        return build(brand, request, List.of());
    }

    public DouyinImageTextPublishSnapshot build(Brand brand,
                                                DouyinImageTextQuickPublishRequest request,
                                                List<Long> autoSelectedImageMaterialIds) {
        if (request == null) {
            throw new BizException(400, "请填写抖音图文标题和作品描述");
        }
        String title = requireText(request.getTitle(), "抖音图文标题不能为空");
        if (codePointLength(title) > MAX_TITLE_LENGTH) {
            throw new BizException(400, "抖音图文标题不能超过20字");
        }
        TopicPreview topic = previewTopic(brand);
        String description = requireText(request.getDescription(), "抖音图文作品描述不能为空");
        String descriptionBase = removeDuplicateTopic(description, topic.topicQuery());
        if (!StringUtils.hasText(descriptionBase)) {
            throw new BizException(400, "抖音图文作品描述不能为空");
        }
        String finalDescription = descriptionBase + "\n" + topic.topicQuery();
        if (codePointLength(finalDescription) > MAX_DESCRIPTION_LENGTH) {
            throw new BizException(400, "作品描述追加中文话题后不能超过1000字");
        }

        List<Long> requestedMaterialIds = distinctPositiveIds(request.getImageMaterialIds());
        List<Long> materialIds = requestedMaterialIds.isEmpty()
                ? distinctPositiveIds(autoSelectedImageMaterialIds)
                : requestedMaterialIds;
        if (materialIds.size() < MIN_IMAGE_COUNT || materialIds.size() > MAX_IMAGE_COUNT) {
            throw new BizException(400, "系统从封面图和插图文件夹自动归集的抖音图文图片不足4张");
        }
        for (Long materialId : materialIds) {
            BrandMaterial material = brandMaterialMapper.selectById(materialId);
            validateMaterial(brand, material);
            // Validate that a downloadable material URL can be generated, but never persist
            // the expiring URL in the snapshot.
            materialPublicUrlService.buildPublicStreamUrl(material);
        }
        return new DouyinImageTextPublishSnapshot(
                1,
                "image_text",
                title,
                descriptionBase,
                topic.topicRegionText(),
                topic.topicIndustryText(),
                topic.topicQuery(),
                topic.regionSourceField(),
                topic.industrySourceValue(),
                trimToNull(brand.getPublicAddress()),
                materialIds,
                materialIds.size(),
                "immediate",
                LocalDateTime.now()
        );
    }

    /**
     * Builds the immutable publish payload directly from the generated article.
     * The UI must not be responsible for trimming or reconstructing Douyin content.
     */
    public DouyinImageTextPublishSnapshot buildFromArticle(Brand brand,
                                                           ArticleDraft article,
                                                           String contentMarkdown,
                                                           List<Long> autoSelectedImageMaterialIds) {
        if (article == null) {
            throw new BizException(400, "抖音图文文章不存在");
        }
        TopicPreview topic = previewTopic(brand);
        String sourceTitle = requireText(article.getTitle(), "抖音图文标题不能为空");
        String title = truncateCodePoints(cleanTitle(sourceTitle), MAX_TITLE_LENGTH);
        String description = markdownToDescription(contentMarkdown, sourceTitle);
        int maxDescriptionBaseLength = MAX_DESCRIPTION_LENGTH - codePointLength(topic.topicQuery()) - 1;
        if (maxDescriptionBaseLength <= 0) {
            throw new BizException(400, "抖音中文话题过长，无法生成作品描述");
        }
        description = truncateAtSentenceBoundary(description, maxDescriptionBaseLength);
        if (!StringUtils.hasText(description)) {
            throw new BizException(400, "抖音图文文章正文为空，无法创建立即发布任务");
        }
        DouyinImageTextQuickPublishRequest request = new DouyinImageTextQuickPublishRequest();
        request.setTitle(title);
        request.setDescription(description);
        return build(brand, request, autoSelectedImageMaterialIds);
    }

    public String toJson(DouyinImageTextPublishSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "抖音图文发布快照序列化失败", ex);
        }
    }

    public DouyinImageTextPublishSnapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, DouyinImageTextPublishSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "抖音图文发布快照解析失败", ex);
        }
    }

    public List<String> resolveImageUrls(DouyinImageTextPublishSnapshot snapshot, Long brandId) {
        if (snapshot == null || brandId == null) {
            throw new BizException(400, "抖音图文发布快照或品牌无效");
        }
        Brand brand = new Brand();
        brand.setId(brandId);
        List<String> urls = new ArrayList<>(snapshot.imageMaterialIds().size());
        for (Long materialId : snapshot.imageMaterialIds()) {
            BrandMaterial material = brandMaterialMapper.selectById(materialId);
            validateMaterial(brand, material);
            urls.add(materialPublicUrlService.buildPublicStreamUrl(material));
        }
        if (urls.size() != snapshot.expectedImageCount()) {
            throw new BizException(400, "抖音图文图片地址数量与发布快照不一致");
        }
        return List.copyOf(urls);
    }

    private ResolvedText resolveRegion(Brand brand) {
        List<SourceValue> candidates = List.of(
                new SourceValue("cityName", brand.getCityName()),
                new SourceValue("selfMediaPublishLocationName", brand.getSelfMediaPublishLocationName()),
                new SourceValue("serviceArea", brand.getServiceArea()),
                new SourceValue("cityCode", brand.getCityCode())
        );
        for (SourceValue candidate : candidates) {
            String resolved = resolveRegionValue(candidate.value());
            if (StringUtils.hasText(resolved)) {
                return new ResolvedText(resolved, candidate.source());
            }
        }
        throw new BizException(400, "品牌地域无法解析为中文，请补充品牌城市中文名称");
    }

    private String resolveRegionValue(String raw) {
        String value = firstSegment(raw);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (!containsHan(value)) {
            value = dictValue(REGION_DICT_TYPES, value);
        }
        if (!isChineseDescription(value)) {
            return null;
        }
        Matcher cityMatcher = CITY_NAME.matcher(value);
        String normalized = cityMatcher.find() ? cityMatcher.group(1) : value;
        normalized = normalized
                .replaceFirst("^.*?(?:省|自治区|特别行政区)", "")
                .replaceFirst("(?:自治州|地区|盟|市|区|县)$", "")
                .replaceAll("[\\s，,、;；|｜]+", "");
        return isChineseDescription(normalized) ? normalized : null;
    }

    private ResolvedText resolveIndustry(String raw) {
        String value = trimToNull(raw);
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, "品牌行业不能为空，请配置中文行业");
        }
        String resolved = containsHan(value) ? value : dictValue(List.of("industry_tag"), value);
        if (!isChineseDescription(resolved)) {
            throw new BizException(400, "品牌行业无法解析为中文，请修正行业字典配置");
        }
        String normalized = resolved.replaceAll("[\\s，,、;；|｜]+", "");
        if (!isChineseDescription(normalized)) {
            throw new BizException(400, "品牌行业必须使用中文描述");
        }
        return new ResolvedText(normalized, "industry");
    }

    private String dictValue(List<String> dictTypes, String key) {
        SysDictItem item = sysDictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .in(SysDictItem::getDictType, dictTypes)
                .eq(SysDictItem::getDictKey, key)
                .eq(SysDictItem::getEnabled, true)
                .orderByAsc(SysDictItem::getSortOrder, SysDictItem::getId)
                .last("LIMIT 1"));
        return item == null ? null : trimToNull(item.getDictValue());
    }

    private void validateMaterial(Brand brand, BrandMaterial material) {
        if (material == null || material.getId() == null || !brand.getId().equals(material.getBrandId())) {
            throw new BizException(400, "所选抖音图片不存在或不属于当前品牌");
        }
        String type = normalizeFileType(material);
        if (!ALLOWED_IMAGE_TYPES.contains(type)) {
            throw new BizException(400, "抖音图文图片仅支持JPG、JPEG、PNG和WebP格式");
        }
        if (material.getFileSize() != null && material.getFileSize() > MAX_IMAGE_BYTES) {
            throw new BizException(400, "抖音图文单张图片不能超过50MB");
        }
    }

    private String normalizeFileType(BrandMaterial material) {
        String type = trimToNull(material.getFileType());
        if (!StringUtils.hasText(type) && StringUtils.hasText(material.getFileName())) {
            int dot = material.getFileName().lastIndexOf('.');
            type = dot >= 0 ? material.getFileName().substring(dot + 1) : null;
        }
        if (!StringUtils.hasText(type)) {
            return "";
        }
        return type.replaceFirst("^image/", "").replaceFirst("^\\.", "").toLowerCase();
    }

    private List<Long> distinctPositiveIds(List<Long> source) {
        LinkedHashSet<Long> values = new LinkedHashSet<>();
        for (Long value : source == null ? List.<Long>of() : source) {
            if (value != null && value > 0) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private String removeDuplicateTopic(String description, String topicQuery) {
        String normalized = description.replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        String topic = trimToNull(topicQuery);
        if (!StringUtils.hasText(topic)) {
            return normalized;
        }
        String[] lines = normalized.split("\\n", -1);
        int end = lines.length;
        while (end > 0 && topic.equals(lines[end - 1].trim())) {
            end--;
        }
        return String.join("\n", java.util.Arrays.copyOf(lines, end)).trim();
    }

    private String cleanTitle(String value) {
        return value.replaceFirst("^\\s*#{1,6}\\s*", "").trim();
    }

    private String markdownToDescription(String markdown, String articleTitle) {
        String text = trimToNull(markdown);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        text = MARKDOWN_IMAGE.matcher(text).replaceAll("");
        text = MARKDOWN_LINK.matcher(text).replaceAll("$1");
        text = text.replaceAll("(?is)<(?:br\\s*/?|/p|/div|/li|/h[1-6])>", "\n");
        text = HTML_TAG.matcher(text).replaceAll("");
        text = MARKDOWN_PREFIX.matcher(text).replaceAll("");
        text = MARKDOWN_DECORATION.matcher(text).replaceAll("");
        text = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        String fullTitle = cleanTitle(articleTitle);
        String[] lines = text.split("\\n", -1);
        int firstContentLine = 0;
        while (firstContentLine < lines.length && !StringUtils.hasText(lines[firstContentLine])) {
            firstContentLine++;
        }
        if (firstContentLine < lines.length
                && normalizeComparable(lines[firstContentLine]).equals(normalizeComparable(fullTitle))) {
            lines[firstContentLine] = "";
            text = String.join("\n", lines).replaceFirst("^\\s+", "").trim();
        }
        return text;
    }

    private String truncateAtSentenceBoundary(String value, int maxCodePoints) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized) || codePointLength(normalized) <= maxCodePoints) {
            return normalized;
        }
        String candidate = truncateCodePoints(normalized, maxCodePoints);
        Matcher matcher = SENTENCE_BOUNDARY.matcher(candidate);
        int preferredBoundary = -1;
        int minimumBoundary = Math.max(1, candidate.length() * 2 / 3);
        while (matcher.find()) {
            if (matcher.end() >= minimumBoundary) {
                preferredBoundary = matcher.end();
            }
        }
        return (preferredBoundary > 0 ? candidate.substring(0, preferredBoundary) : candidate)
                .replaceFirst("[，、：,:\\s]+$", "")
                .trim();
    }

    private String truncateCodePoints(String value, int maxCodePoints) {
        if (!StringUtils.hasText(value) || codePointLength(value) <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end).trim();
    }

    private int codePointLength(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private String normalizeComparable(String value) {
        return cleanTitle(String.valueOf(value))
                .replaceAll("[\\s，、：；。！？,.!?:;—\\-]+", "");
    }

    private String firstSegment(String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.split("[/,，、;；|｜\\s]+", 2)[0];
    }

    private boolean containsHan(String value) {
        return StringUtils.hasText(value) && HAN.matcher(value).find();
    }

    private boolean isChineseDescription(String value) {
        return containsHan(value) && !LATIN.matcher(value).find();
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(400, message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record TopicPreview(String topicRegionText,
                               String topicIndustryText,
                               String topicQuery,
                               String regionSourceField,
                               String industrySourceValue) {
    }

    private record ResolvedText(String text, String source) {
    }

    private record SourceValue(String source, String value) {
    }
}
