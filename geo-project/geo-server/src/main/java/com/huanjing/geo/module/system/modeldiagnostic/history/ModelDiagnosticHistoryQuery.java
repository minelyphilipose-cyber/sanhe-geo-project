package com.huanjing.geo.module.system.modeldiagnostic.history;

import java.time.LocalDateTime;

public record ModelDiagnosticHistoryQuery(int page,
                                          int size,
                                          Long platformConfigId,
                                          String requestedModelId,
                                          String diagnosticMode,
                                          String status,
                                          String conclusion,
                                          LocalDateTime createdFrom,
                                          LocalDateTime createdTo) {
}
