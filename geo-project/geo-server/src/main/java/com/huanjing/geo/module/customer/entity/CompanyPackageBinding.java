package com.huanjing.geo.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("company_package_binding")
public class CompanyPackageBinding {
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private Long packagePlanId;
    private String packageType;
    private String packageName;
    private BigDecimal standardPrice;
    private Integer serviceMonths;
    private Integer questionPoolLimit;
    private String channelQuotaSnapshot;
    private String status;
    private Integer activeFlag;
    private LocalDateTime boundAt;
    private LocalDateTime unboundAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void markActive() {
        this.status = STATUS_ACTIVE;
        this.activeFlag = 1;
        this.unboundAt = null;
    }

    public void markInactive() {
        this.status = STATUS_INACTIVE;
        this.activeFlag = null;
        this.unboundAt = LocalDateTime.now();
    }

    public void setStatus(String status) {
        this.status = status;
        if (STATUS_ACTIVE.equals(status)) {
            this.activeFlag = 1;
        } else if (STATUS_INACTIVE.equals(status)) {
            this.activeFlag = null;
        }
    }
}
