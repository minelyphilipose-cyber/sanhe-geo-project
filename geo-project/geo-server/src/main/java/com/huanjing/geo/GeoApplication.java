package com.huanjing.geo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan({
        "com.huanjing.geo.module.*.mapper",
        "com.huanjing.geo.module.presale.persist.mapper",
        "com.huanjing.geo.module.presale.export.persist.mapper",
        "com.huanjing.geo.module.presale.ruleengine.persist"
})
@EnableAsync
@EnableScheduling
public class GeoApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeoApplication.class, args);
    }
}
