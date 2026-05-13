package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_page03_market_config")
public class PresalePage03MarketConfig {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String marketLabel;
    private String marketSource;
    private String appMonthlyActiveValue;
    private String appMonthlyActiveUnit;
    private String dailyActiveUsersValue;
    private String dailyActiveUsersUnit;
    private String dailyQuestionTotalValue;
    private String dailyQuestionTotalUnit;
    private String doubaoMonthlyUsageValue;
    private String doubaoMonthlyUsageUnit;
    @TableField("platform_1_name")
    private String platform1Name;
    @TableField("platform_1_value")
    private String platform1Value;
    @TableField("platform_2_name")
    private String platform2Name;
    @TableField("platform_2_value")
    private String platform2Value;
    @TableField("platform_3_name")
    private String platform3Name;
    @TableField("platform_3_value")
    private String platform3Value;
    private String platformSuffix;
    private String page03DataSource;
    private String footnote;
    private Integer questionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
