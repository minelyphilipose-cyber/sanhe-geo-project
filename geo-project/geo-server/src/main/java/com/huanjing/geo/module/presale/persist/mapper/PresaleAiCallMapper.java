package com.huanjing.geo.module.presale.persist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PresaleAiCallMapper extends BaseMapper<PresaleAiCall> {
    int insertForCurrentRun(@Param("row") PresaleAiCall row,
                            @Param("generationAttempt") long generationAttempt);
}
