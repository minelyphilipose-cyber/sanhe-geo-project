package com.huanjing.geo.module.presale.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptTemplateDraftRequest {
    @NotNull(message = "源模板 ID 不能为空")
    private Long sourceTemplateId;

    @Size(max = 1000, message = "Prompt 内容最多 1000 字")
    private String promptContent;
}
