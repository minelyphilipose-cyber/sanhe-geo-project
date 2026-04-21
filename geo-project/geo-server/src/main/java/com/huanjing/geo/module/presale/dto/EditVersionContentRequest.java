package com.huanjing.geo.module.presale.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * PATCH /api/presale/reports/{id}/versions/{versionNo}/content 请求体。
 *
 * <p>只允许编辑 L3(editable_content_json)。L1 raw / L2 computed 在派生/生成阶段
 * 固化,编辑接口不接受;若前端误传也会被忽略。</p>
 *
 * <p>v1 不记录派生原因(定稿条款:V62 不扩表),因此本 DTO 也不含 reason 字段。</p>
 */
@Data
public class EditVersionContentRequest {

    /**
     * L3 编辑层快照 JSON 字符串。
     * <p>前端在提交前已经合并好 viewModel → editableContent,后端只负责落库,
     * 不再做结构校验(结构由 P1·B TS 类型 + P1·E 规则引擎兜底)。</p>
     */
    @NotBlank(message = "editableContentJson must not be blank")
    private String editableContentJson;
}
