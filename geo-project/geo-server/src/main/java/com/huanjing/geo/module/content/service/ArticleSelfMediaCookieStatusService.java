package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;
import com.huanjing.geo.module.content.dto.SelfMediaCookieStatusAccountVO;
import com.huanjing.geo.module.content.dto.SelfMediaCookieStatusBatchRequest;
import com.huanjing.geo.module.content.dto.SelfMediaCookieStatusBatchResponse;
import com.huanjing.geo.module.content.dto.SelfMediaCookieStatusItemVO;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaCookieCredentialMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleSelfMediaCookieStatusService {

    private static final int MAX_ARTICLE_IDS = 50;
    private static final Set<String> DEFAULT_PLATFORMS = Set.of("toutiao", "zhihu");
    private static final String AUTH_MODE_COOKIE = "COOKIE";

    private final ArticleDraftMapper articleDraftMapper;
    private final ProjectMapper projectMapper;
    private final SelfMediaAccountMapper accountMapper;
    private final SelfMediaCookieCredentialMapper credentialMapper;
    private final CurrentUserService currentUserService;
    private final BrandAccessService brandAccessService;

    public SelfMediaCookieStatusBatchResponse batch(SelfMediaCookieStatusBatchRequest request) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        List<Long> articleIds = normalizeArticleIds(request.articleIds());
        Set<String> platforms = normalizePlatforms(request.platforms());

        List<ArticleDraft> articles = articleDraftMapper.selectBatchIds(articleIds);
        Map<Long, ArticleDraft> articleById = articles.stream()
                .collect(Collectors.toMap(ArticleDraft::getId, Function.identity()));
        Map<Long, Project> projectById = loadProjects(articles);
        Map<Long, Long> articleBrandById = resolveAndAuthorizeBrands(articleIds, articleById, projectById, operator);
        Map<Long, List<SelfMediaAccount>> accountsByBrand = loadAccounts(articleBrandById.values(), platforms);
        Map<Long, SelfMediaCookieCredential> credentialByAccount = loadCredentials(accountsByBrand);

        List<SelfMediaCookieStatusItemVO> items = new ArrayList<>();
        for (Long articleId : articleIds) {
            Long brandId = articleBrandById.get(articleId);
            List<SelfMediaCookieStatusAccountVO> accounts = accountsByBrand.getOrDefault(brandId, List.of())
                    .stream()
                    .map(account -> toAccountVO(account, credentialByAccount.get(account.getId())))
                    .toList();
            items.add(new SelfMediaCookieStatusItemVO(articleId, brandId, accounts));
        }
        return new SelfMediaCookieStatusBatchResponse(items);
    }

    private List<Long> normalizeArticleIds(List<Long> articleIds) {
        List<Long> normalized = articleIds == null ? List.of() : articleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new BizException(400, "articleIds is required");
        }
        if (normalized.size() > MAX_ARTICLE_IDS) {
            throw new BizException(400, "articleIds size must be <= " + MAX_ARTICLE_IDS);
        }
        return normalized;
    }

    private Set<String> normalizePlatforms(List<String> platforms) {
        Set<String> normalized = platforms == null ? Set.of() : platforms.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return normalized.isEmpty() ? DEFAULT_PLATFORMS : normalized;
    }

    private Map<Long, Project> loadProjects(List<ArticleDraft> articles) {
        List<Long> projectIds = articles.stream()
                .map(ArticleDraft::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return projectMapper.selectBatchIds(projectIds).stream()
                .filter(project -> project.getDeletedAt() == null)
                .collect(Collectors.toMap(Project::getId, Function.identity()));
    }

    private Map<Long, Long> resolveAndAuthorizeBrands(List<Long> articleIds,
                                                       Map<Long, ArticleDraft> articleById,
                                                       Map<Long, Project> projectById,
                                                       SysUser operator) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long articleId : articleIds) {
            ArticleDraft article = articleById.get(articleId);
            if (article == null) {
                throw new BizException(404, "Article not found: " + articleId);
            }
            Project project = projectById.get(article.getProjectId());
            if (project == null) {
                throw new BizException(404, "Project not found for article: " + articleId);
            }
            currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
            brandAccessService.requireBrandAccess(project.getBrandId(), operator.getId(), BrandAccessAction.OPERATE);
            result.put(articleId, project.getBrandId());
        }
        return result;
    }

    private Map<Long, List<SelfMediaAccount>> loadAccounts(Iterable<Long> brandIds, Set<String> platforms) {
        List<Long> uniqueBrandIds = new ArrayList<>();
        for (Long brandId : brandIds) {
            if (brandId != null && !uniqueBrandIds.contains(brandId)) {
                uniqueBrandIds.add(brandId);
            }
        }
        if (uniqueBrandIds.isEmpty()) {
            return Map.of();
        }
        return accountMapper.selectList(new LambdaQueryWrapper<SelfMediaAccount>()
                        .isNull(SelfMediaAccount::getDeletedAt)
                        .in(SelfMediaAccount::getBrandId, uniqueBrandIds)
                        .in(SelfMediaAccount::getPlatform, platforms)
                        .eq(SelfMediaAccount::getAuthMode, AUTH_MODE_COOKIE)
                        .orderByAsc(SelfMediaAccount::getPlatform)
                        .orderByDesc(SelfMediaAccount::getUpdatedAt))
                .stream()
                .collect(Collectors.groupingBy(SelfMediaAccount::getBrandId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, SelfMediaCookieCredential> loadCredentials(Map<Long, List<SelfMediaAccount>> accountsByBrand) {
        List<Long> accountIds = accountsByBrand.values().stream()
                .flatMap(List::stream)
                .map(SelfMediaAccount::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return credentialMapper.selectActiveMetaByAccountIds(accountIds)
                .stream()
                .collect(Collectors.groupingBy(SelfMediaCookieCredential::getSelfMediaAccountId))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .max(Comparator.comparing(SelfMediaCookieCredential::getVersion))
                                .orElse(null)
                ));
    }

    private SelfMediaCookieStatusAccountVO toAccountVO(SelfMediaAccount account, SelfMediaCookieCredential credential) {
        boolean accountActive = "active".equalsIgnoreCase(account.getStatus());
        boolean hasCredential = credential != null;
        String credentialStatus = hasCredential ? "VALID" : "MISSING";
        boolean canStartFill = accountActive && hasCredential;
        String reason = null;
        if (!accountActive) {
            reason = "ACCOUNT_DISABLED";
        } else if (!hasCredential) {
            reason = "COOKIE_MISSING";
        }
        return new SelfMediaCookieStatusAccountVO(
                account.getId(),
                account.getPlatform(),
                account.getAccountName(),
                account.getPlatformAccountId(),
                account.getStatus(),
                credentialStatus,
                credential == null ? null : credential.getCapturedAt(),
                canStartFill,
                reason
        );
    }
}
