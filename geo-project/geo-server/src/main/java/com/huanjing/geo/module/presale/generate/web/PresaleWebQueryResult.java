package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;

public record PresaleWebQueryResult(LlmCallResult callResult,
                                    PresaleSearchEvidence evidence,
                                    String evidenceJson) {
}
