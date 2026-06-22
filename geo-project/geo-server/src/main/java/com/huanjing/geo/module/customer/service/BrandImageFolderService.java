package com.huanjing.geo.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.dto.BrandImageFolderRequest;
import com.huanjing.geo.module.customer.dto.BrandImageFolderVO;
import com.huanjing.geo.module.customer.dto.BrandMaterialVO;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.BrandImageFolder;
import com.huanjing.geo.module.customer.entity.BrandImageFolderProject;
import com.huanjing.geo.module.customer.entity.BrandImageFolderTag;
import com.huanjing.geo.module.customer.entity.BrandMaterial;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderMapper;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderProjectMapper;
import com.huanjing.geo.module.customer.mapper.BrandImageFolderTagMapper;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.BrandMaterialMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandImageFolderService {
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    private static final String DEFAULT_FOLDER_NAME = "默认图库";
    private static final int MAX_TAG_LENGTH = 10;

    private final BrandMapper brandMapper;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final BrandMaterialMapper brandMaterialMapper;
    private final BrandImageFolderMapper folderMapper;
    private final BrandImageFolderProjectMapper folderProjectMapper;
    private final BrandImageFolderTagMapper folderTagMapper;
    private final CurrentUserService currentUserService;
    private final BrandMaterialPublicUrlService publicUrlService;

    public List<BrandImageFolderVO> listFolders(Long brandId, Long projectId, String tag, boolean activeOnly, boolean includeMaterials) {
        Brand brand = requireAccessibleBrand(brandId, true);
        Long normalizedProjectId = validateProjectFilter(brand.getId(), projectId);
        Set<Long> tagMatchedFolderIds = queryTagMatchedFolderIds(brand.getId(), tag);
        if (StringUtils.hasText(tag) && tagMatchedFolderIds.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<BrandImageFolder> wrapper = new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brand.getId())
                .orderByDesc(BrandImageFolder::getDefaultFlag)
                .orderByDesc(BrandImageFolder::getUpdatedAt)
                .orderByDesc(BrandImageFolder::getCreatedAt);
        if (activeOnly) {
            wrapper.eq(BrandImageFolder::getStatus, STATUS_ACTIVE);
        }
        if (!tagMatchedFolderIds.isEmpty()) {
            wrapper.in(BrandImageFolder::getId, tagMatchedFolderIds);
        }
        List<BrandImageFolder> folders = folderMapper.selectList(wrapper);
        if (folders.isEmpty()) {
            return List.of();
        }
        List<Long> folderIds = folders.stream().map(BrandImageFolder::getId).toList();
        Map<Long, List<Long>> projectMap = loadProjectIds(folderIds);
        Map<Long, List<String>> tagMap = loadTags(folderIds);
        Map<Long, List<BrandMaterialVO>> materialMap = includeMaterials ? loadMaterialViews(folderIds) : Map.of();
        Map<Long, Integer> materialCountMap = loadMaterialCounts(folderIds);

        return folders.stream()
                .map(folder -> {
                    BrandImageFolderVO vo = BrandImageFolderVO.from(folder);
                    List<Long> projectIds = projectMap.getOrDefault(folder.getId(), List.of());
                    vo.setProjectIds(projectIds);
                    vo.setTags(tagMap.getOrDefault(folder.getId(), List.of()));
                    vo.setMaterials(materialMap.getOrDefault(folder.getId(), List.of()));
                    vo.setMaterialCount(materialCountMap.getOrDefault(folder.getId(), 0));
                    vo.setProjectRelated(normalizedProjectId != null && projectIds.contains(normalizedProjectId));
                    return vo;
                })
                .sorted(folderComparator(normalizedProjectId))
                .toList();
    }

    public List<String> suggestTags(Long brandId, String keyword) {
        Brand brand = requireAccessibleBrand(brandId, true);
        List<BrandImageFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brand.getId())
                .select(BrandImageFolder::getId));
        if (folders.isEmpty()) {
            return List.of();
        }
        Set<Long> folderIds = folders.stream().map(BrandImageFolder::getId).collect(Collectors.toSet());
        String normalized = normalizeSearch(keyword);
        return folderTagMapper.selectList(new LambdaQueryWrapper<BrandImageFolderTag>()
                        .in(BrandImageFolderTag::getFolderId, folderIds)
                        .like(StringUtils.hasText(normalized), BrandImageFolderTag::getTagName, normalized)
                        .orderByAsc(BrandImageFolderTag::getTagName))
                .stream()
                .map(BrandImageFolderTag::getTagName)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(20)
                .toList();
    }

    @Transactional
    public BrandImageFolderVO createFolder(Long brandId, BrandImageFolderRequest req) {
        SysUser operator = currentUserService.requireCurrentUser();
        Brand brand = requireAccessibleBrand(brandId, false);
        BrandImageFolder folder = new BrandImageFolder();
        folder.setBrandId(brand.getId());
        folder.setFolderName(validateFolderName(req.getFolderName()));
        folder.setDescription(trimToNull(req.getDescription(), 500));
        folder.setStatus(normalizeStatus(req.getStatus()));
        folder.setDefaultFlag(false);
        folder.setCreatedBy(operator.getId());
        folderMapper.insert(folder);
        replaceProjects(folder, normalizeProjectIds(brand.getId(), req.getProjectIds()));
        replaceTags(folder.getId(), normalizeTags(req.getTags()));
        return listFolders(brand.getId(), null, null, false, false).stream()
                .filter(item -> folder.getId().equals(item.getId()))
                .findFirst()
                .orElseGet(() -> BrandImageFolderVO.from(folder));
    }

    @Transactional
    public BrandImageFolderVO updateFolder(Long brandId, Long folderId, BrandImageFolderRequest req) {
        Brand brand = requireAccessibleBrand(brandId, false);
        BrandImageFolder folder = requireFolder(brand.getId(), folderId);
        folder.setFolderName(validateFolderName(req.getFolderName()));
        folder.setDescription(trimToNull(req.getDescription(), 500));
        folder.setStatus(normalizeStatus(req.getStatus()));
        folderMapper.updateById(folder);
        replaceProjects(folder, normalizeProjectIds(brand.getId(), req.getProjectIds()));
        replaceTags(folder.getId(), normalizeTags(req.getTags()));
        return listFolders(brand.getId(), null, null, false, false).stream()
                .filter(item -> folder.getId().equals(item.getId()))
                .findFirst()
                .orElseGet(() -> BrandImageFolderVO.from(folder));
    }

    @Transactional
    public void deleteFolder(Long brandId, Long folderId) {
        Brand brand = requireAccessibleBrand(brandId, false);
        BrandImageFolder folder = requireFolder(brand.getId(), folderId);
        if (Boolean.TRUE.equals(folder.getDefaultFlag())) {
            throw new BizException(400, "默认图库不能删除");
        }
        if (!STATUS_DISABLED.equalsIgnoreCase(folder.getStatus())) {
            throw new BizException(400, "请先停用图库文件夹后再删除");
        }
        brandMaterialMapper.update(null, new UpdateWrapper<BrandMaterial>()
                .eq("brand_id", brand.getId())
                .eq("folder_id", folder.getId())
                .set("folder_id", null));
        folderProjectMapper.delete(new QueryWrapper<BrandImageFolderProject>()
                .eq("folder_id", folder.getId()));
        folderTagMapper.delete(new QueryWrapper<BrandImageFolderTag>()
                .eq("folder_id", folder.getId()));
        folderMapper.deleteById(folder.getId());
    }

    @Transactional
    public BrandImageFolder ensureDefaultFolder(Long brandId, Long operatorId) {
        BrandImageFolder existing = folderMapper.selectOne(new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brandId)
                .eq(BrandImageFolder::getDefaultFlag, true)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        existing = folderMapper.selectOne(new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brandId)
                .eq(BrandImageFolder::getFolderName, DEFAULT_FOLDER_NAME)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setDefaultFlag(true);
            folderMapper.updateById(existing);
            return existing;
        }
        BrandImageFolder folder = new BrandImageFolder();
        folder.setBrandId(brandId);
        folder.setFolderName(DEFAULT_FOLDER_NAME);
        folder.setDescription("历史品牌素材自动归档");
        folder.setStatus(STATUS_ACTIVE);
        folder.setDefaultFlag(true);
        folder.setCreatedBy(operatorId == null ? 0L : operatorId);
        folderMapper.insert(folder);
        return folder;
    }

    public BrandImageFolder requireActiveFolderForSelection(Long brandId, Long folderId) {
        BrandImageFolder folder = requireFolder(brandId, folderId);
        if (!STATUS_ACTIVE.equalsIgnoreCase(folder.getStatus())) {
            throw new BizException(400, "图片所在文件夹已停用，无法选取");
        }
        return folder;
    }

    public BrandImageFolder requireFolder(Long brandId, Long folderId) {
        if (folderId == null) {
            throw new BizException(400, "folderId required");
        }
        BrandImageFolder folder = folderMapper.selectById(folderId);
        if (folder == null || !brandId.equals(folder.getBrandId())) {
            throw new BizException(404, "Image folder not found");
        }
        return folder;
    }

    private Brand requireAccessibleBrand(Long brandId, boolean readOnly) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission(readOnly ? "company.read" : "brand.update");
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

    private Long validateProjectFilter(Long brandId, Long projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null || !brandId.equals(project.getBrandId())) {
            throw new BizException(400, "项目不属于当前品牌");
        }
        return projectId;
    }

    private List<Long> normalizeProjectIds(Long brandId, List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = projectIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .in(Project::getId, ids)
                .isNull(Project::getDeletedAt));
        Set<Long> validIds = projects.stream()
                .filter(project -> brandId.equals(project.getBrandId()))
                .map(Project::getId)
                .collect(Collectors.toSet());
        if (validIds.size() != ids.size()) {
            throw new BizException(400, "存在不属于当前品牌的项目");
        }
        return new ArrayList<>(ids);
    }

    private void replaceProjects(BrandImageFolder folder, List<Long> projectIds) {
        folderProjectMapper.delete(new LambdaQueryWrapper<BrandImageFolderProject>()
                .eq(BrandImageFolderProject::getFolderId, folder.getId()));
        for (Long projectId : projectIds) {
            BrandImageFolderProject rel = new BrandImageFolderProject();
            rel.setFolderId(folder.getId());
            rel.setProjectId(projectId);
            folderProjectMapper.insert(rel);
        }
    }

    private void replaceTags(Long folderId, List<String> tags) {
        folderTagMapper.delete(new LambdaQueryWrapper<BrandImageFolderTag>()
                .eq(BrandImageFolderTag::getFolderId, folderId));
        for (String tag : tags) {
            BrandImageFolderTag row = new BrandImageFolderTag();
            row.setFolderId(folderId);
            row.setTagName(tag);
            folderTagMapper.insert(row);
        }
    }

    private Set<Long> queryTagMatchedFolderIds(Long brandId, String tag) {
        String normalized = normalizeSearch(tag);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        List<BrandImageFolder> folders = folderMapper.selectList(new LambdaQueryWrapper<BrandImageFolder>()
                .eq(BrandImageFolder::getBrandId, brandId)
                .select(BrandImageFolder::getId));
        if (folders.isEmpty()) {
            return Set.of();
        }
        Set<Long> brandFolderIds = folders.stream().map(BrandImageFolder::getId).collect(Collectors.toSet());
        return folderTagMapper.selectList(new LambdaQueryWrapper<BrandImageFolderTag>()
                        .in(BrandImageFolderTag::getFolderId, brandFolderIds)
                        .like(BrandImageFolderTag::getTagName, normalized))
                .stream()
                .map(BrandImageFolderTag::getFolderId)
                .collect(Collectors.toSet());
    }

    private Map<Long, List<Long>> loadProjectIds(List<Long> folderIds) {
        return folderProjectMapper.selectList(new LambdaQueryWrapper<BrandImageFolderProject>()
                        .in(BrandImageFolderProject::getFolderId, folderIds)
                        .orderByAsc(BrandImageFolderProject::getProjectId))
                .stream()
                .collect(Collectors.groupingBy(
                        BrandImageFolderProject::getFolderId,
                        LinkedHashMap::new,
                        Collectors.mapping(BrandImageFolderProject::getProjectId, Collectors.toList())
                ));
    }

    private Map<Long, List<String>> loadTags(List<Long> folderIds) {
        return folderTagMapper.selectList(new LambdaQueryWrapper<BrandImageFolderTag>()
                        .in(BrandImageFolderTag::getFolderId, folderIds)
                        .orderByAsc(BrandImageFolderTag::getTagName))
                .stream()
                .collect(Collectors.groupingBy(
                        BrandImageFolderTag::getFolderId,
                        LinkedHashMap::new,
                        Collectors.mapping(BrandImageFolderTag::getTagName, Collectors.toList())
                ));
    }

    private Map<Long, List<BrandMaterialVO>> loadMaterialViews(List<Long> folderIds) {
        return brandMaterialMapper.selectList(new LambdaQueryWrapper<BrandMaterial>()
                        .in(BrandMaterial::getFolderId, folderIds)
                        .orderByDesc(BrandMaterial::getCreatedAt))
                .stream()
                .map(this::toMaterialVO)
                .collect(Collectors.groupingBy(BrandMaterialVO::getFolderId, LinkedHashMap::new, Collectors.toList()));
    }

    private BrandMaterialVO toMaterialVO(BrandMaterial material) {
        String publicUrl = null;
        if ("brand_image".equals(material.getCategory()) && StringUtils.hasText(material.getObjectKey())) {
            publicUrl = publicUrlService.buildPublicStreamUrl(material);
        }
        return BrandMaterialVO.from(material, publicUrl);
    }

    private Map<Long, Integer> loadMaterialCounts(List<Long> folderIds) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        List<BrandMaterial> materials = brandMaterialMapper.selectList(new LambdaQueryWrapper<BrandMaterial>()
                .in(BrandMaterial::getFolderId, folderIds)
                .select(BrandMaterial::getFolderId));
        for (BrandMaterial material : materials) {
            if (material.getFolderId() != null) {
                counts.merge(material.getFolderId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private Comparator<BrandImageFolderVO> folderComparator(Long projectId) {
        return (a, b) -> {
            if (projectId != null) {
                int related = Boolean.compare(Boolean.TRUE.equals(b.getProjectRelated()), Boolean.TRUE.equals(a.getProjectRelated()));
                if (related != 0) {
                    return related;
                }
            }
            int active = Boolean.compare(STATUS_ACTIVE.equalsIgnoreCase(b.getStatus()), STATUS_ACTIVE.equalsIgnoreCase(a.getStatus()));
            if (active != 0) {
                return active;
            }
            return Long.compare(Objects.requireNonNullElse(a.getId(), 0L), Objects.requireNonNullElse(b.getId(), 0L));
        };
    }

    private String validateFolderName(String name) {
        String trimmed = trimToNull(name, 128);
        if (!StringUtils.hasText(trimmed)) {
            throw new BizException(400, "文件夹名称不能为空");
        }
        return trimmed;
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return STATUS_ACTIVE;
        }
        String status = value.trim().toLowerCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BizException(400, "Invalid folder status");
        }
        return status;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags) {
            String trimmed = StringUtils.hasText(tag) ? tag.trim() : null;
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.length() > MAX_TAG_LENGTH) {
                throw new BizException(400, "标签不能超过10个字");
            }
            result.add(trimmed);
        }
        return new ArrayList<>(result);
    }

    private String normalizeSearch(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimToNull(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
