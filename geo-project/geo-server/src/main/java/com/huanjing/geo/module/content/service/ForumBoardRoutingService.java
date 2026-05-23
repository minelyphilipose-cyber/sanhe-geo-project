package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.service.adapter.DiscuzForumProfile;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.PublishSite;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ForumBoardRoutingService {

    private static final String FUYANG = "阜阳";

    private final ObjectMapper objectMapper;

    public Integer resolveForumFid(PublishSite site, Project project, Brand brand, Integer requestedFid) {
        if (requestedFid != null) {
            return requestedFid;
        }
        DiscuzForumProfile profile = parseDiscuzProfile(site);
        if (profile == null || !profile.hasBoards()) {
            return null;
        }
        List<DiscuzForumProfile.Board> enabledBoards = profile.getBoards().stream()
                .filter(Objects::nonNull)
                .filter(DiscuzForumProfile.Board::isEnabled)
                .filter(board -> board.getFid() != null && board.getFid() > 0)
                .toList();
        if (enabledBoards.isEmpty()) {
            return null;
        }
        if (isFuyangServiceArea(brand)) {
            Integer fuyangFid = findBoardByNameContains(enabledBoards, FUYANG);
            if (fuyangFid != null) {
                return fuyangFid;
            }
        }
        Integer industryFid = findIndustryBoard(enabledBoards, brand);
        if (industryFid != null) {
            return industryFid;
        }
        return profile.resolveBoard(null)
                .map(DiscuzForumProfile.Board::getFid)
                .orElse(null);
    }

    private DiscuzForumProfile parseDiscuzProfile(PublishSite site) {
        if (site == null || !"discuz_http".equalsIgnoreCase(site.getIntegrationMethod())
                || !StringUtils.hasText(site.getContentConstraints())) {
            return null;
        }
        try {
            return objectMapper.readValue(site.getContentConstraints(), DiscuzForumProfile.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isFuyangServiceArea(Brand brand) {
        return brand != null
                && StringUtils.hasText(brand.getServiceArea())
                && normalize(brand.getServiceArea()).contains(FUYANG);
    }

    private Integer findBoardByNameContains(List<DiscuzForumProfile.Board> boards, String keyword) {
        return boards.stream()
                .filter(board -> StringUtils.hasText(board.getName()))
                .filter(board -> normalize(board.getName()).contains(keyword))
                .map(DiscuzForumProfile.Board::getFid)
                .findFirst()
                .orElse(null);
    }

    private Integer findIndustryBoard(List<DiscuzForumProfile.Board> boards, Brand brand) {
        if (brand == null || !StringUtils.hasText(brand.getIndustry())) {
            return null;
        }
        String industry = normalize(brand.getIndustry());
        return boards.stream()
                .filter(board -> StringUtils.hasText(board.getName()))
                .filter(board -> industry.contains(normalize(board.getName())))
                .map(DiscuzForumProfile.Board::getFid)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
