package com.huanjing.geo.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.storage")
public class StorageProperties {

    private Provider provider = Provider.MINIO;
    private boolean readFallbackToMinio = false;
    private Cos cos = new Cos();
    private Migration migration = new Migration();

    public enum Provider {
        MINIO,
        COS
    }

    @Data
    public static class Cos {
        private String region;
        /**
         * Tencent COS bucket name must include appid, for example geo-files-1250000000.
         */
        private String bucket;
        /**
         * Optional internal endpoint override for Tencent Cloud VPC access.
         */
        private String endpoint;
        private String secretId;
        private String secretKey;
    }

    @Data
    public static class Migration {
        private boolean executeEnabled = false;
    }
}
