package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.project.dto.KeywordColumnVisibilityVO;
import com.huanjing.geo.module.project.dto.KeywordRequiredColumnsVO;
import com.huanjing.geo.module.project.dto.KeywordTypeConfigVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class KeywordTypeConfigService {

    public static final String STRUCTURE_STANDARD = "standard";
    public static final String STRUCTURE_COMPARE = "compare";

    private static final Set<String> LEGACY_TYPES = Set.of("search", "location", "industry", "competitor");
    private final Map<String, KeywordTypeConfigVO> configMap = buildConfigMap();

    public List<KeywordTypeConfigVO> listConfigs() {
        return new ArrayList<>(configMap.values());
    }

    public KeywordTypeConfigVO getConfig(String rawType) {
        String type = normalizeType(rawType);
        KeywordTypeConfigVO config = configMap.get(type);
        if (config == null) {
            throw new BizException(400, "Unknown keyword group type: " + rawType);
        }
        return config;
    }

    public KeywordTypeConfigVO getConfigOrNull(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return null;
        }
        return configMap.get(normalizeType(rawType));
    }

    public boolean isKnownType(String rawType) {
        String type = normalizeType(rawType);
        return configMap.containsKey(type) || LEGACY_TYPES.contains(type);
    }

    public boolean isLegacyType(String rawType) {
        return LEGACY_TYPES.contains(normalizeType(rawType));
    }

    public String labelOf(String rawType) {
        String type = normalizeType(rawType);
        KeywordTypeConfigVO config = configMap.get(type);
        if (config != null) {
            return config.getLabel();
        }
        return switch (type) {
            case "search" -> "搜索词(历史)";
            case "location" -> "地域词(历史)";
            case "industry" -> "行业词(历史)";
            case "competitor" -> "竞品词(历史)";
            default -> type;
        };
    }

    public String normalizeType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            throw new BizException(400, "type is required");
        }
        String type = rawType.trim().toLowerCase(Locale.ROOT);
        if (!type.matches("^[a-z][a-z0-9_]{0,31}$")) {
            throw new BizException(400, "type format invalid");
        }
        return type;
    }

    private Map<String, KeywordTypeConfigVO> buildConfigMap() {
        Map<String, KeywordTypeConfigVO> map = new LinkedHashMap<>();
        put(map, "brand", "品牌词", "用户带品牌名称搜索，了解品牌", STRUCTURE_STANDARD,
                false, false, true, false,
                columns(false, true, true, true, true, false, false),
                required(false, false, true, false, false, false, false));
        put(map, "decision", "决策词", "用户已决定购买，问选哪家", STRUCTURE_STANDARD,
                true, false, true, false,
                columns(true, true, true, true, true, false, false),
                required(false, false, true, false, false, false, false));
        put(map, "transaction", "成交词", "用户要下单，问价格或购买渠道", STRUCTURE_STANDARD,
                true, false, true, false,
                columns(true, true, true, true, true, false, false),
                required(false, false, true, false, false, false, false));
        put(map, "comparison", "对比词", "用户在多个品牌或产品之间比较", STRUCTURE_COMPARE,
                false, false, true, false,
                columns(false, false, false, false, true, true, true),
                required(false, false, false, false, true, true, true));
        put(map, "qa", "问答词", "用户有使用或认知问题要解答", STRUCTURE_STANDARD,
                false, false, true, false,
                columns(false, true, true, false, true, false, false),
                required(false, false, true, false, false, false, false));
        put(map, "function", "功能词", "用户有特定功能或品质需求", STRUCTURE_STANDARD,
                true, false, true, true,
                columns(true, true, true, true, true, false, false),
                required(false, false, true, false, false, false, false));
        return Map.copyOf(map);
    }

    private void put(
            Map<String, KeywordTypeConfigVO> map,
            String type,
            String label,
            String description,
            String structure,
            boolean areaEnabledByDefault,
            boolean industryRequired,
            boolean supportsManualAdd,
            boolean functionIndustryRequired,
            KeywordColumnVisibilityVO columns,
            KeywordRequiredColumnsVO requiredColumns
    ) {
        KeywordTypeConfigVO config = new KeywordTypeConfigVO();
        config.setType(type);
        config.setLabel(label);
        config.setDescription(description);
        config.setStructure(structure);
        config.setAreaEnabledByDefault(areaEnabledByDefault);
        config.setIndustryRequired(industryRequired);
        config.setSupportsManualAdd(supportsManualAdd);
        config.setFunctionIndustryRequired(functionIndustryRequired);
        config.setColumns(columns);
        config.setRequiredColumns(requiredColumns);
        map.put(type, config);
    }

    private KeywordColumnVisibilityVO columns(boolean area, boolean prefix, boolean core, boolean industry, boolean suffix, boolean compareCore, boolean compareWord) {
        return new KeywordColumnVisibilityVO(area, prefix, core, industry, suffix, compareCore, compareWord);
    }

    private KeywordRequiredColumnsVO required(boolean area, boolean prefix, boolean core, boolean industry, boolean suffix, boolean compareCore, boolean compareWord) {
        return new KeywordRequiredColumnsVO(area, prefix, core, industry, suffix, compareCore, compareWord);
    }
}
