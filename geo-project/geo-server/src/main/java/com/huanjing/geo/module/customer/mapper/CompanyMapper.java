package com.huanjing.geo.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.customer.entity.Company;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CompanyMapper extends BaseMapper<Company> {

    @Select("SELECT id FROM company WHERE id = #{companyId} FOR UPDATE")
    Long lockCompanyForUpdate(@Param("companyId") Long companyId);
}
