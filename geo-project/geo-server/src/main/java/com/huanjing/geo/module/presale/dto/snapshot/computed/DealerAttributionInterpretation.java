package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DealerAttributionInterpretation {
    public static final String WEAK_TRANSFER_COPY =
            "代理品牌在大模型中具有较高认知度，但该认知尚未稳定关联至本门店。当前曝光更多来自代理品牌，门店主体的独立认知仍有提升空间。";
    public static final String INSUFFICIENT_COPY =
            "当前代理品牌与门店关联样本不足，暂不对品牌认知传递程度作明确判断。";
    public static final String OBSERVATION_COPY =
            "当前样本未触发“代理品牌强、门店认知弱”的判断，建议结合门店命中与品牌关联指标持续观察。";

    @JsonProperty("weak_transfer")
    private Boolean weakTransfer;
    @JsonProperty("sample_sufficient")
    private Boolean sampleSufficient;
    private String narrative;
}
