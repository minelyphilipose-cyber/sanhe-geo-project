package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.execution.ModelDiagnosticExecutionCommand;
import com.huanjing.geo.module.system.modeldiagnostic.execution.ModelDiagnosticExecutionCoordinator;
import com.huanjing.geo.module.system.modeldiagnostic.execution.ModelDiagnosticExecutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticApiService {

    private final ModelDiagnosticRequestFactory requestFactory;
    private final ModelDiagnosticExecutionCoordinator coordinator;
    private final ModelDiagnosticRunViewMapper viewMapper;

    public ModelDiagnosticRunView execute(ModelDiagnosticRunRequest request) {
        ModelDiagnosticExecutionCommand command = requestFactory.create(request);
        try {
            AiModelDiagnosticRun run = coordinator.execute(command);
            return viewMapper.toView(run);
        } catch (ModelDiagnosticExecutionException ex) {
            if (ex.category() == ErrorCategory.INVALID_REQUEST) {
                throw new BizException(400, ex.getMessage(), 400, null, ex);
            }
            throw ex;
        }
    }
}
