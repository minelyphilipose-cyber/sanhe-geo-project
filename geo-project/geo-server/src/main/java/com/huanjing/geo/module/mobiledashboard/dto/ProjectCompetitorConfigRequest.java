package com.huanjing.geo.module.mobiledashboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectCompetitorConfigRequest {
    @Valid
    @Size(max = 3)
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long id;
        @NotBlank
        @Size(max = 128)
        private String competitorName;
        @Size(max = 20)
        private List<@Size(max = 128) String> aliases = new ArrayList<>();
        @Size(max = 500)
        private String advantages;
        @Size(max = 500)
        private String disadvantages;
        @NotNull
        private Integer displayOrder;
        private Boolean active = true;
        private String qaStatus;
    }
}
