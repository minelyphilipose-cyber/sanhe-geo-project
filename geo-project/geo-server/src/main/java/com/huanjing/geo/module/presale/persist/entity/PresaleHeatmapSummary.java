package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_heatmap_summary")
public class PresaleHeatmapSummary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configVersion;
    private String heatmapPattern;
    private String bandOverride;
    private String summaryTemplate;
    private String colorLegendTemplate;
    private Integer sortOrder;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
