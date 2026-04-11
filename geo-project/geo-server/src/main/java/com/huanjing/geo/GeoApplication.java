package com.huanjing.geo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.huanjing.geo.module.*.mapper")
public class GeoApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeoApplication.class, args);
    }
}
