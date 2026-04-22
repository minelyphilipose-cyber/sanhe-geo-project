package com.huanjing.geo.module.presale.generate.llm;

/**
 * 单次平台调用的上下文。
 *
 * @param versionId        版本 ID
 * @param batchNo          批次号(1/2)
 * @param platformCode     平台编码
 * @param promptTemplateId prompt 模板 ID
 * @param competitorName   竞品名(batch1 固定为空串)
 * @param operatorUserId   触发者用户 ID
 * @param operatorIsManager 触发者是否 manager
 */
public record PlatformCallContext(Long versionId,
                                  Integer batchNo,
                                  String platformCode,
                                  Long promptTemplateId,
                                  String competitorName,
                                  Long operatorUserId,
                                  boolean operatorIsManager) {
}

