package com.huanjing.geo.module.presale.dto.response;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresalePromptTracePageVO {
    private Page<PresalePromptTraceListItemVO> page;
    private PresalePromptTraceFilterOptionsVO filterOptions;
}
