package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_channel_style_module")
public class MedicalChannelStyleModule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String channelGroupCode;
    private String channelSubCode;
    private String channelTier;
    private String stylePrompt;
    private Boolean highRisk;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
