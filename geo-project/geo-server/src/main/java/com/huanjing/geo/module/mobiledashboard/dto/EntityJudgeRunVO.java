package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EntityJudgeRunVO {
    private int scanned;
    private int judged;
    private int skipped;
    private int failed;
    private int budgetBlocked;
    private String budgetReason;
}
