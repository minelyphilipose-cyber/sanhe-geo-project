package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.util.EntityMatchTextNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class MobileEntityMentionMatcher {
    public MatchResult match(String responseText, List<String> focusNames, List<ProjectCompetitorConfigService.CompetitorEntity> competitors) {
        boolean focusMatched = containsAny(responseText, focusNames);
        List<Long> matchedCompetitorIds = new ArrayList<>();
        if (competitors != null) {
            for (ProjectCompetitorConfigService.CompetitorEntity competitor : competitors) {
                if (competitor == null) {
                    continue;
                }
                List<String> names = new ArrayList<>();
                names.add(competitor.name());
                if (competitor.aliases() != null) {
                    names.addAll(competitor.aliases());
                }
                if (containsAny(responseText, names)) {
                    matchedCompetitorIds.add(competitor.id());
                }
            }
        }
        return new MatchResult(focusMatched, matchedCompetitorIds);
    }

    boolean containsAny(String responseText, List<String> names) {
        if (!StringUtils.hasText(responseText) || names == null || names.isEmpty()) {
            return false;
        }
        String raw = responseText.trim();
        String normalizedText = EntityMatchTextNormalizer.normalize(raw);
        for (String name : names) {
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String trimmed = name.trim();
            if (raw.contains(trimmed)) {
                return true;
            }
            String normalizedName = EntityMatchTextNormalizer.normalize(trimmed);
            if (StringUtils.hasText(normalizedName) && normalizedText.contains(normalizedName)) {
                return true;
            }
        }
        return false;
    }

    public record MatchResult(boolean focusMatched, List<Long> matchedCompetitorIds) {
        public boolean anyMatched() {
            return focusMatched || (matchedCompetitorIds != null && !matchedCompetitorIds.isEmpty());
        }
    }
}
