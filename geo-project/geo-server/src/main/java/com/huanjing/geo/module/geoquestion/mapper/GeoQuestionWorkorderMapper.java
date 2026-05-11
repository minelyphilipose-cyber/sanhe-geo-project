package com.huanjing.geo.module.geoquestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.geoquestion.entity.GeoQuestionWorkorder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GeoQuestionWorkorderMapper extends BaseMapper<GeoQuestionWorkorder> {
    @Select("SELECT id FROM geo_question_workorder WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
