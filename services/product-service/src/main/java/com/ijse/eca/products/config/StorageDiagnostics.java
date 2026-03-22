package com.ijse.eca.products.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StorageDiagnostics implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StorageDiagnostics.class);

    private final StorageProperties storageProperties;
    private final Environment env;

    public StorageDiagnostics(StorageProperties storageProperties, Environment env) {
        this.storageProperties = storageProperties;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        String propBucket = (storageProperties != null && storageProperties.gcs() != null) ? storageProperties.gcs().bucket() : null;
        String propProject = (storageProperties != null && storageProperties.gcs() != null) ? storageProperties.gcs().projectId() : null;
        String envBucket = env.getProperty("GCS_BUCKET");
        String envBucket2 = env.getProperty("storage.gcs.bucket");
        String envProject = env.getProperty("GCS_PROJECT_ID");
        String defaultProject = com.google.cloud.ServiceOptions.getDefaultProjectId();

        log.info("[StorageDiagnostics] StorageProperties.gcs.bucket='{}', StorageProperties.gcs.projectId='{}'", propBucket, propProject);
        log.info("[StorageDiagnostics] Env storage.gcs.bucket='{}', Env GCS_BUCKET='{}', Env GCS_PROJECT_ID='{}'", envBucket2, envBucket, envProject);
        log.info("[StorageDiagnostics] GCP Default Project from SDK='{}'", defaultProject);
    }
}
