package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class BaselineObservationCollectRequest {
    private List<String> platformCodes;
}
