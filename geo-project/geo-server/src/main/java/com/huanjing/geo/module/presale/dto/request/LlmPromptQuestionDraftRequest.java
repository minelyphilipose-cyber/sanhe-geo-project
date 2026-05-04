package com.huanjing.geo.module.presale.dto.request;

import com.huanjing.geo.module.presale.dto.PresalePromptCategoryCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LlmPromptQuestionDraftRequest {
    @NotNull(message = "categoryCode 不能为空")
    private PresalePromptCategoryCode categoryCode;

    @NotBlank(message = "Prompt 内容不能为空")
    @Size(max = 1000, message = "Prompt 内容最多 1000 字")
    private String promptContent;
}
