package com.huanjing.geo.module.report.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PresaleQuestionSetSaveRequest {
    @NotEmpty
    private List<PresaleQuestionItemUpsertRequest> items;
}
