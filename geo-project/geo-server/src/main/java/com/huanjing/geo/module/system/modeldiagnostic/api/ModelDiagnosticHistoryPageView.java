package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticHistoryPage;

import java.util.List;

public record ModelDiagnosticHistoryPageView(List<ModelDiagnosticRunSummaryView> records,
                                             long total,
                                             int page,
                                             int size) {

    public ModelDiagnosticHistoryPageView {
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static ModelDiagnosticHistoryPageView from(ModelDiagnosticHistoryPage page) {
        return new ModelDiagnosticHistoryPageView(
                page.records().stream().map(ModelDiagnosticRunSummaryView::from).toList(),
                page.total(), page.page(), page.size());
    }
}
