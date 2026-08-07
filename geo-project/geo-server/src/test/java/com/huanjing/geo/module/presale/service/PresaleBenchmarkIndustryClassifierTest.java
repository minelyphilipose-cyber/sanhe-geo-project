package com.huanjing.geo.module.presale.service;

import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleBenchmarkIndustryClassifierTest {

    @Mock private SysDictItemMapper dictMapper;
    @Mock private PresaleLlmInvoker llmInvoker;

    @Test
    void selectedIndustryLabelUsesDirectMatchWithoutModelCall() throws Exception {
        when(dictMapper.selectList(any())).thenReturn(List.of(item("automotive", "汽车")));
        PresaleBenchmarkIndustryClassifier classifier = classifier();

        var result = classifier.classify("汽车", 7L, false);

        assertEquals("automotive", result.benchmarkIndustryKey());
        assertEquals("DIRECT", result.source());
        assertEquals("HIGH", result.confidence());
        assertNull(result.modelId());
        verify(llmInvoker, never()).classifyBenchmarkIndustry(any(), any());
    }

    @Test
    void manualIndustryCallsQwenOnceAndFreezesAllowedSemanticResult() throws Exception {
        when(dictMapper.selectList(any())).thenReturn(List.of(
                item("automotive", "汽车"), item("auto_aftermarket", "汽车后市场")));
        when(llmInvoker.classifyBenchmarkIndustry(any(), any())).thenReturn(new LlmCallResult(
                "{\"industry_key\":\"auto_aftermarket\",\"confidence\":\"HIGH\"}",
                1, 1, 1L, 0, CallStatus.SUCCESS, "qwen", "通义千问", "qwen-plus", "通义千问 Plus"));
        PresaleBenchmarkIndustryClassifier classifier = classifier();

        var result = classifier.classify("汽车贴膜改装店", 7L, true);

        assertEquals("auto_aftermarket", result.benchmarkIndustryKey());
        assertEquals("LLM", result.source());
        assertEquals("HIGH", result.confidence());
        assertEquals("qwen-plus", result.modelId());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmInvoker).classifyBenchmarkIndustry(any(), prompt.capture());
        org.junit.jupiter.api.Assertions.assertTrue(prompt.getValue().contains("汽车贴膜改装店"));
    }

    @Test
    void manualIndustryIsDeferredWithoutBlockingReportCreation() throws Exception {
        when(dictMapper.selectList(any())).thenReturn(List.of(item("automotive", "汽车")));

        var result = classifier().classifyDirectlyOrDefer("汽车贴膜改装店");

        assertNull(result.benchmarkIndustryKey());
        assertEquals("PENDING", result.source());
        assertNull(result.confidence());
        verify(llmInvoker, never()).classifyBenchmarkIndustry(any(), any());
    }

    @Test
    void lowConfidenceOrUnknownKeyFallsBackToGlobal() throws Exception {
        when(dictMapper.selectList(any())).thenReturn(List.of(item("automotive", "汽车")));
        when(llmInvoker.classifyBenchmarkIndustry(any(), any())).thenReturn(new LlmCallResult(
                "{\"industry_key\":\"unknown\",\"confidence\":\"LOW\"}",
                1, 1, 1L, 0, CallStatus.SUCCESS));

        var result = classifier().classify("不锈钢非标加工厂", 7L, false);

        assertEquals("_ALL_", result.benchmarkIndustryKey());
        assertEquals("FALLBACK", result.source());
        assertEquals("LOW", result.confidence());
    }

    private PresaleBenchmarkIndustryClassifier classifier() {
        return new PresaleBenchmarkIndustryClassifier(dictMapper, llmInvoker, new ObjectMapper());
    }

    private SysDictItem item(String key, String value) {
        SysDictItem item = new SysDictItem();
        item.setDictKey(key);
        item.setDictValue(value);
        item.setEnabled(true);
        item.setSortOrder(1);
        return item;
    }
}
