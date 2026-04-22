package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;

public record ReuseSnapshot(boolean hasAnalyzeSuccess, PresaleAiCall querySuccessCall) {
}

