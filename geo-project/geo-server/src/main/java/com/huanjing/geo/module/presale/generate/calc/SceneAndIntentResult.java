package com.huanjing.geo.module.presale.generate.calc;

import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;

import java.util.List;

public record SceneAndIntentResult(
        ComputedSnapshotDTO.SceneCoverage sceneCoverage,
        List<IntentBreakdown> intentBreakdown
) {
}

