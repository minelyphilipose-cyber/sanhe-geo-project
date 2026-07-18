package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.websearch.QuestionPollPromptTemplate;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticInputMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ModelDiagnosticProbeCatalog {

    private static final List<ProbeDefinition> PROBES = List.of(
            new ProbeDefinition("basic_generation", "v1", null, "基础生成探针",
                    ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.STANDARD_PROBE,
                    null, "你是模型能力诊断助手。请严格按用户要求作答。",
                    "请用三句话说明什么是生成式人工智能。"),
            new ProbeDefinition("web_search_news", "v1", null, "联网搜索探针",
                    ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.STANDARD_PROBE,
                    null, "请优先联网核查，并为关键事实保留可访问来源。",
                    "请联网搜索今天的热点新闻，列出三条，并为每条内容标注可访问的来源。"),
            new ProbeDefinition("production_poll_question", "v1",
                    QuestionPollPromptTemplate.VERSION,
                    "生产问题模板", ModelDiagnosticMode.WEB_SEARCH,
                    ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                    ModelDiagnosticInputMode.USER_REQUIRED,
                    QuestionPollPromptTemplate.SYSTEM_PROMPT, null));

    public List<ModelDiagnosticProbeOption> options() {
        return PROBES.stream().map(ProbeDefinition::option).toList();
    }

    public ProbeDefinition require(String code,
                                   ModelDiagnosticMode mode,
                                   ModelDiagnosticTestMode testMode) {
        return PROBES.stream()
                .filter(probe -> probe.code().equals(code)
                        && probe.diagnosticMode() == mode
                        && probe.testMode() == testMode)
                .findFirst()
                .orElseThrow(() -> new BizException(
                        400, "Probe is missing, disabled or incompatible", 400, null));
    }

    public record ProbeDefinition(String code,
                                  String version,
                                  String templateVersion,
                                  String label,
                                  ModelDiagnosticMode diagnosticMode,
                                  ModelDiagnosticTestMode testMode,
                                  ModelDiagnosticInputMode inputMode,
                                  String systemPrompt,
                                  String fixedUserMessage) {
        ModelDiagnosticProbeOption option() {
            return new ModelDiagnosticProbeOption(
                    code, version, templateVersion, label, diagnosticMode, testMode,
                    inputMode, inputMode == ModelDiagnosticInputMode.USER_REQUIRED);
        }
    }
}
