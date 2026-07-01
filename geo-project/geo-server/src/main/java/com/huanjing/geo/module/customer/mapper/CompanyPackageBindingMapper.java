package com.huanjing.geo.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CompanyPackageBindingMapper extends BaseMapper<CompanyPackageBinding> {

    @Select("""
            SELECT *
            FROM company_package_binding
            WHERE company_id = #{companyId}
              AND status = 'active'
              AND active_flag = 1
            LIMIT 1
            """)
    CompanyPackageBinding selectActiveByCompanyId(@Param("companyId") Long companyId);

    @Update("""
            UPDATE company_package_binding
            SET status = 'inactive',
                active_flag = NULL,
                unbound_at = #{unboundAt}
            WHERE id = #{id}
              AND status = 'active'
              AND active_flag = 1
            """)
    int markInactive(@Param("id") Long id, @Param("unboundAt") LocalDateTime unboundAt);

    @Update("""
            UPDATE company_package_binding
            SET active_flag = NULL
            WHERE company_id = #{companyId}
              AND status = 'inactive'
              AND active_flag IS NOT NULL
            """)
    int clearInactiveActiveFlags(@Param("companyId") Long companyId);
}
