package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poll_batch_shard_items")
public class PollBatchShardItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shardId;
    private Long batchId;
    private Long keywordResultId;
    private String keywordTextSnapshot;
    private Integer sortOrder;
    private String status;
    private Long pollResultId;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
