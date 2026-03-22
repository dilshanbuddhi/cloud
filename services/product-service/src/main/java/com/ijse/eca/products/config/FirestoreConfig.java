package com.ijse.eca.products.config;

import com.google.cloud.ServiceOptions;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(FirestoreProperties.class)
public class FirestoreConfig {
    @Bean(destroyMethod = "close")
    public Firestore firestore(FirestoreProperties properties) {
        String projectId = resolveProjectId(properties.projectId());
        return FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .build()
                .getService();
    }

    private static String resolveProjectId(String configuredProjectId) {
        if (StringUtils.hasText(configuredProjectId)) {
            return configuredProjectId;
        }

        String envProjectId = System.getenv("FIRESTORE_PROJECT_ID");
        if (!StringUtils.hasText(envProjectId)) {
            envProjectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        }
        if (!StringUtils.hasText(envProjectId)) {
            envProjectId = System.getenv("GCLOUD_PROJECT");
        }
        if (StringUtils.hasText(envProjectId)) {
            return envProjectId;
        }

        String defaultProjectId = ServiceOptions.getDefaultProjectId();
        if (StringUtils.hasText(defaultProjectId)) {
            return defaultProjectId;
        }

        throw new IllegalStateException(
                "Firestore project id is missing. Set 'firestore.project-id' (Config Server) or env FIRESTORE_PROJECT_ID/GOOGLE_CLOUD_PROJECT."
        );
    }
}
