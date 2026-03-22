package com.ijse.eca.products.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String provider,
        Local local,
        Gcs gcs
) {
    public record Local(String baseDir) {
    }

    public record Gcs(String projectId, String bucket) {
    }
}
