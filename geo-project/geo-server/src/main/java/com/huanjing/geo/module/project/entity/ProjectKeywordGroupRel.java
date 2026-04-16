package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_keyword_group_rel")
public class ProjectKeywordGroupRel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long keywordGroupId;
    private LocalDateTime createdAt;
}
