package com.huanjing.geo.common.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${geo.minio.endpoint}") String endpoint,
            @Value("${geo.minio.access-key}") String accessKey,
            @Value("${geo.minio.secret-key}") String secretKey,
            @Value("${geo.minio.region:us-east-1}") String region
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .region(region)
                .credentials(accessKey, secretKey)
                .build();
    }
}
