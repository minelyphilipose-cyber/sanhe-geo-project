package com.huanjing.geo.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.customer.entity.Brand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BrandMapper extends BaseMapper<Brand> {

    @Select("""
            SELECT id
            FROM brand
            WHERE deleted_at IS NULL
            ORDER BY id DESC
            """)
    List<Long> selectActiveBrandIds();
}
