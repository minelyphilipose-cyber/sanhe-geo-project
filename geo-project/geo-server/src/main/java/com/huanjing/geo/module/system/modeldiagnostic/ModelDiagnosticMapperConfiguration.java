package com.huanjing.geo.module.system.modeldiagnostic;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.huanjing.geo.module.system.modeldiagnostic.mapper")
public class ModelDiagnosticMapperConfiguration {
}
