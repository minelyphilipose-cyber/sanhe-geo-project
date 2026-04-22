package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleReusePersistenceServiceTest {

    @Mock
    private PresaleAiCallMapper aiCallMapper;
    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @Mock
    private ReuseDecisionService reuseDecisionService;

    @InjectMocks
    private PresaleReusePersistenceService service;

    @Test
    void retry_replaceAnalyze_transactionRollback_noNewAnalyzeCallResidue_logicBranch() {
        PlatformCallContext ctx = new PlatformCallContext(
                200L, 1, "kimi", 33L, "", "Acme", 1L, false
        );
        PresaleAiCall reusedQuery = new PresaleAiCall();
        reusedQuery.setId(555L);

        PresaleAiCall newAnalyze = new PresaleAiCall();
        PresaleAiPromptResult newResult = new PresaleAiPromptResult();

        when(reuseDecisionService.normalizeCompetitor(anyString())).thenAnswer(inv -> {
            String val = inv.getArgument(0, String.class);
            return val == null ? "" : val.trim();
        });
        doThrow(new RuntimeException("insert prompt_result failed"))
                .when(aiPromptResultMapper).insert(any(PresaleAiPromptResult.class));

        assertThrows(RuntimeException.class,
                () -> service.replaceFailedAnalyzeAndResult(ctx, reusedQuery, newAnalyze, newResult));

        verify(aiPromptResultMapper, times(1)).delete(any());
        verify(aiCallMapper, times(1)).delete(any());
        verify(aiCallMapper, times(1)).insert(any(PresaleAiCall.class));
        verify(aiPromptResultMapper, times(1)).insert(any(PresaleAiPromptResult.class));
    }
}

