package com.huanjing.geo.module.presale.dto.snapshot.raw;

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
public class DealerAttributionSummary {
    @JsonProperty("effective_samples")
    private Integer effectiveSamples;
    @JsonProperty("dealer_hit_rate")
    private Double dealerHitRate;
    @JsonProperty("direct_rate")
    private Double directRate;
    @JsonProperty("linked_rate")
    private Double linkedRate;
    @JsonProperty("represented_brand_organic_rate")
    private Double representedBrandOrganicRate;
    @JsonProperty("dealer_organic_hit_rate")
    private Double dealerOrganicHitRate;
    @JsonProperty("represented_brand_prompted_rate")
    private Double representedBrandPromptedRate;
    @JsonProperty("transfer_rate")
    private Double transferRate;
    @JsonProperty("brand_only_share")
    private Double brandOnlyShare;
    @JsonProperty("organic_effective_samples")
    private Integer organicEffectiveSamples;
    @JsonProperty("organic_brand_hits")
    private Integer organicBrandHits;
}
