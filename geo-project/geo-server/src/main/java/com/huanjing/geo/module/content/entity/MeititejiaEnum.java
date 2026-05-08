package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meititejia_enum")
public class MeititejiaEnum {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String enumType;
    private String enumKey;
    private String enumValue;
    private String rawPayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
