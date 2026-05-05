package com.huanjing.geo.module.system.dto;

import lombok.Data;

@Data
public class DictItemVO {
    private String dictType;
    private String dictKey;
    private String dictValue;
    private Integer sortOrder;
    private String remark;
}
