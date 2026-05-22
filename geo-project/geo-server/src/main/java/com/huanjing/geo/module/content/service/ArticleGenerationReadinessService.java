package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.dto.ArticleGenerationReadinessDtos.BaseItem;
import com.huanjing.geo.module.content.dto.ArticleGenerationReadinessDtos.ReadinessReport;
import com.huanjing.geo.module.content.dto.ArticleGenerationReadinessDtos.SceneImpact;
import com.huanjing.geo.module.content.dto.ArticleGenerationReadinessDtos.SceneItem;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleGenerationReadinessService {

    public static final String WARNING_DEAL_CONTACT_MISSING = "deal_contact_missing";
    public static final String WARNING_DEAL_CONTACT_HIDDEN = "deal_contact_hidden";
    private static final Set<String> KNOWN_WARNING_CODES = Set.of(
            WARNING_DEAL_CONTACT_MISSING,
            WARNING_DEAL_CONTACT_HIDDEN
    );

    private static final String STATUS_OK = "ok";
    private static final String STATUS_WARNING = "warning";
    private static final String STATUS_CRITICAL = "critical";
    private static final Set<String> SUPPORTED_SCENES = Set.of("brand", "decision", "deal", "compare", "qa", "function");
    private static final Map<String, String> SCENE_NAMES = Map.of(
            "brand", "品牌场景",
            "decision", "决策场景",
            "deal", "成交场景",
            "compare", "对比场景",
            "qa", "问答场景",
            "function", "功能场景"
    );
    private static final Map<String, List<String>> SCENE_FOCUS_CODES = new LinkedHashMap<>();

    static {
        SCENE_FOCUS_CODES.put("brand", List.of("brandName", "brandPositioning", "brandIntro",
                "brandQualificationDescription", "brandCaseDescription"));
        SCENE_FOCUS_CODES.put("decision", List.of("category", "mainBusiness", "brandPositioning", "targetAudience"));
        SCENE_FOCUS_CODES.put("deal", List.of("contactBlock", "mainBusiness", "brandQualificationDescription"));
        SCENE_FOCUS_CODES.put("compare", List.of("category", "mainBusiness", "brandPositioning", "targetAudience"));
        SCENE_FOCUS_CODES.put("qa", List.of("category", "mainBusiness"));
        SCENE_FOCUS_CODES.put("function", List.of("mainBusiness", "brandIntro", "targetAudience"));
    }

    private final ProjectMapper projectMapper;
    private final BrandMapper brandMapper;
    private final CurrentUserService currentUserService;
    private final BrandAccessService brandAccessService;
    private final BatchArticlePromptBuilder promptBuilder;

    public ReadinessReport inspect(Long projectId, List<String> questionSceneCodes) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.update");
        Project project = requireActiveProject(projectId);
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        Brand brand = loadBrand(project, operator);
        List<BaseItem> baseItems = baseItems(project, brand);
        List<SceneImpact> sceneImpacts = normalizeScenes(questionSceneCodes).stream()
                .map(scene -> sceneImpact(scene, baseItems))
                .toList();
        return new ReadinessReport(
                project.getId(),
                score(baseItems),
                overallStatus(baseItems, sceneImpacts),
                baseItems,
                sceneImpacts
        );
    }

    public boolean hasContactBase(Brand brand) {
        return StringUtils.hasText(promptBuilder.buildContactBlock(brand, "full"));
    }

    public List<String> detectTaskReadinessWarningCodes(String questionSceneCode,
                                                        String contactDisclosureMode,
                                                        Brand brand) {
        if (!"deal".equals(trimToNull(questionSceneCode))) {
            return List.of();
        }
        if (!hasContactBase(brand)) {
            return List.of(WARNING_DEAL_CONTACT_MISSING);
        }
        if ("none".equals(trimToNull(contactDisclosureMode))) {
            return List.of(WARNING_DEAL_CONTACT_HIDDEN);
        }
        return List.of();
    }

    public boolean isKnownWarningCode(String code) {
        return KNOWN_WARNING_CODES.contains(code);
    }

    private Project requireActiveProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        if (!"active".equals(project.getStatus())) {
            throw new BizException(ContentErrorCodes.ARTICLE_BAD_REQUEST, "Only active project can generate articles");
        }
        return project;
    }

    private Brand loadBrand(Project project, SysUser operator) {
        if (project.getBrandId() == null) {
            return null;
        }
        brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
        Brand brand = brandMapper.selectById(project.getBrandId());
        if (brand == null || brand.getDeletedAt() != null) {
            return null;
        }
        return brand;
    }

    private List<BaseItem> baseItems(Project project, Brand brand) {
        List<BaseItem> items = new ArrayList<>();
        items.add(baseItem("brandName", "品牌名称", brandValue(brand, Brand::getBrandName), "brand.brandName"));
        items.add(baseItem("mainBusiness", "主营业务", brandValue(brand, Brand::getMainBusiness), "brand.mainBusiness"));
        items.add(baseItem("brandPositioning", "品牌定位", brandValue(brand, Brand::getBrandPositioning), "brand.brandPositioning"));
        items.add(baseItem("brandIntro", "品牌介绍", brandValue(brand, Brand::getBusinessIntro), "brand.businessIntro"));
        items.add(baseItem("brandQualificationDescription", "资质背书", brandValue(brand, Brand::getBrandQualificationDescription),
                "brand.brandQualificationDescription"));
        items.add(baseItem("brandCaseDescription", "案例/客户证明", brandValue(brand, Brand::getBrandCaseDescription),
                "brand.brandCaseDescription"));
        items.add(baseItem("targetAudience", "目标受众", project == null ? null : project.getTargetAudience(), "project.targetAudience"));
        items.add(contactBaseItem(brand));
        items.add(baseItem("category", "行业/品类", brandValue(brand, Brand::getIndustry), "brand.industry"));
        return items;
    }

    private BaseItem baseItem(String code, String label, String value, String source) {
        boolean ok = StringUtils.hasText(value);
        return new BaseItem(
                code,
                label,
                ok ? STATUS_OK : "missing",
                ok ? "normal" : STATUS_WARNING,
                ok ? "" : label + "未填写，相关模板会缺少可引用事实。",
                source
        );
    }

    private BaseItem contactBaseItem(Brand brand) {
        boolean ok = hasContactBase(brand);
        return new BaseItem(
                "contactBlock",
                "联系方式",
                ok ? STATUS_OK : "missing",
                ok ? "normal" : STATUS_WARNING,
                ok ? "" : "缺少可露出的联系方式，成交场景生成前需要确认。",
                "hasContactBase(full contact block)"
        );
    }

    private SceneImpact sceneImpact(String scene, List<BaseItem> baseItems) {
        Map<String, BaseItem> baseMap = new LinkedHashMap<>();
        for (BaseItem item : baseItems) {
            baseMap.put(item.code(), item);
        }
        List<String> focusCodes = SCENE_FOCUS_CODES.getOrDefault(scene, List.of());
        List<SceneItem> missingItems = focusCodes.stream()
                .map(baseMap::get)
                .filter(item -> item != null && "missing".equals(item.status()))
                .map(item -> sceneItem(scene, item))
                .toList();
        int okCount = (int) focusCodes.stream()
                .map(baseMap::get)
                .filter(item -> item != null && STATUS_OK.equals(item.status()))
                .count();
        String status = missingItems.stream().anyMatch(item -> STATUS_CRITICAL.equals(item.severity()))
                ? STATUS_CRITICAL
                : missingItems.isEmpty() ? STATUS_OK : STATUS_WARNING;
        int sceneScore = focusCodes.isEmpty() ? 100 : Math.round(okCount * 100f / focusCodes.size());
        return new SceneImpact(scene, SCENE_NAMES.getOrDefault(scene, scene), status, sceneScore, missingItems);
    }

    private SceneItem sceneItem(String scene, BaseItem item) {
        if ("deal".equals(scene) && "contactBlock".equals(item.code())) {
            return new SceneItem(
                    item.code(),
                    STATUS_CRITICAL,
                    "成交场景缺少联系方式，继续生成前需要确认。",
                    WARNING_DEAL_CONTACT_MISSING,
                    true
            );
        }
        return new SceneItem(item.code(), STATUS_WARNING, sceneMessage(scene, item), null, false);
    }

    private String sceneMessage(String scene, BaseItem item) {
        return SCENE_NAMES.getOrDefault(scene, scene) + "建议补全" + item.label() + "。";
    }

    private String overallStatus(List<BaseItem> baseItems, List<SceneImpact> sceneImpacts) {
        if (sceneImpacts.stream().anyMatch(item -> STATUS_CRITICAL.equals(item.status()))) {
            return STATUS_CRITICAL;
        }
        if (baseItems.stream().anyMatch(item -> "missing".equals(item.status()))
                || sceneImpacts.stream().anyMatch(item -> STATUS_WARNING.equals(item.status()))) {
            return STATUS_WARNING;
        }
        return STATUS_OK;
    }

    private int score(List<BaseItem> baseItems) {
        if (baseItems.isEmpty()) {
            return 100;
        }
        long ok = baseItems.stream().filter(item -> STATUS_OK.equals(item.status())).count();
        return Math.round(ok * 100f / baseItems.size());
    }

    private List<String> normalizeScenes(List<String> questionSceneCodes) {
        if (questionSceneCodes == null || questionSceneCodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> scenes = new LinkedHashSet<>();
        for (String code : questionSceneCodes) {
            String normalized = trimToNull(code);
            if (normalized != null && SUPPORTED_SCENES.contains(normalized)) {
                scenes.add(normalized);
            }
        }
        return List.copyOf(scenes);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String brandValue(Brand brand, BrandValueGetter getter) {
        return brand == null ? null : getter.get(brand);
    }

    @FunctionalInterface
    private interface BrandValueGetter {
        String get(Brand brand);
    }
}
