package com.huanjing.geo.module.presale.generate.llm;

import java.util.List;
import com.huanjing.geo.module.presale.dto.AttributionMode;

/**
 * 单次平台调用的上下文。
 *
 * @param versionId        版本 ID
 * @param batchNo          批次号(1/2)
 * @param platformCode     平台编码
 * @param promptTemplateId prompt 模板 ID
 * @param competitorName   竞品名(batch1 固定为空串)
 * @param brandName        品牌名(Analyze 模板注入)
 * @param industry         客户行业展示值(Analyze/Judge 用于语义消歧)
 * @param industryRole     客户身份展示值(Analyze/Judge 用于语义消歧)
 * @param representedBrands 代理/经销类客户所代理的品牌(仅作归属消歧)
 * @param operatorUserId   触发者用户 ID
 * @param operatorIsManager 触发者是否 manager
 * @param generationAttempt 报告生成轮次；正式编排中必须大于 0
 */
public record PlatformCallContext(Long versionId,
                                  Integer batchNo,
                                  String platformCode,
                                  Long promptTemplateId,
                                  String competitorName,
                                  String brandName,
                                  String industry,
                                  String industryRole,
                                  List<String> representedBrands,
                                  Long operatorUserId,
                                  boolean operatorIsManager,
                                  long generationAttempt,
                                  String attributionMode) {

    public PlatformCallContext {
        representedBrands = representedBrands == null ? List.of() : List.copyOf(representedBrands);
        attributionMode = AttributionMode.fromNullable(attributionMode).name();
    }

    public PlatformCallContext(Long versionId,
                               Integer batchNo,
                               String platformCode,
                               Long promptTemplateId,
                               String competitorName,
                               String brandName,
                               String industry,
                               String industryRole,
                               List<String> representedBrands,
                               Long operatorUserId,
                               boolean operatorIsManager,
                               long generationAttempt) {
        this(versionId, batchNo, platformCode, promptTemplateId, competitorName, brandName,
                industry, industryRole, representedBrands, operatorUserId, operatorIsManager,
                generationAttempt, AttributionMode.STANDARD.name());
    }

    public PlatformCallContext(Long versionId,
                               Integer batchNo,
                               String platformCode,
                               Long promptTemplateId,
                               String competitorName,
                               String brandName,
                               String industry,
                               String industryRole,
                               List<String> representedBrands,
                               Long operatorUserId,
                               boolean operatorIsManager) {
        this(versionId, batchNo, platformCode, promptTemplateId, competitorName, brandName,
                industry, industryRole, representedBrands, operatorUserId, operatorIsManager, 0L,
                AttributionMode.STANDARD.name());
    }

    public PlatformCallContext(Long versionId,
                               Integer batchNo,
                               String platformCode,
                               Long promptTemplateId,
                               String competitorName,
                               String brandName,
                               String industry,
                               String industryRole,
                               Long operatorUserId,
                               boolean operatorIsManager) {
        this(versionId, batchNo, platformCode, promptTemplateId, competitorName, brandName,
                industry, industryRole, List.of(), operatorUserId, operatorIsManager, 0L,
                AttributionMode.STANDARD.name());
    }

    public PlatformCallContext(Long versionId,
                               Integer batchNo,
                               String platformCode,
                               Long promptTemplateId,
                               String competitorName,
                               String brandName,
                               Long operatorUserId,
                               boolean operatorIsManager) {
        this(versionId, batchNo, platformCode, promptTemplateId, competitorName, brandName,
                null, null, List.of(), operatorUserId, operatorIsManager, 0L,
                AttributionMode.STANDARD.name());
    }

    public boolean dealerAttribution() {
        return AttributionMode.DEALER.name().equals(attributionMode);
    }
}
