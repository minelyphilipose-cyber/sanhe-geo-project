package com.huanjing.geo.module.system.modeldiagnostic.history;

import java.util.List;

public record ModelDiagnosticHistoryPage(List<ModelDiagnosticRunSummary> records,
                                         long total,
                                         int page,
                                         int size) {
    public ModelDiagnosticHistoryPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
