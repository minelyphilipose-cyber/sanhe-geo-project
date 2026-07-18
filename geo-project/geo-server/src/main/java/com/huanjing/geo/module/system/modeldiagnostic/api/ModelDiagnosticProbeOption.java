package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticInputMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;

public record ModelDiagnosticProbeOption(String code,
                                         String version,
                                         String templateVersion,
                                         String label,
                                         ModelDiagnosticMode diagnosticMode,
                                         ModelDiagnosticTestMode testMode,
                                         ModelDiagnosticInputMode inputMode,
                                         boolean userMessageRequired) {
}
