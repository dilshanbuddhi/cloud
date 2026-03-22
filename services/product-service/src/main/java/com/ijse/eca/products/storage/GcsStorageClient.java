package com.ijse.eca.products.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.util.Optional;
import com.ijse.eca.products.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GcsStorageClient implements StorageClient {
    private static final Logger log = LoggerFactory.getLogger(GcsStorageClient.class);

    // Lazily initialized (avoid failing at bean creation time)
    private volatile Storage storage;
    private final StorageProperties storageProperties;
    private final Environment env;

    public GcsStorageClient(StorageProperties storageProperties, Environment env) {
        this.storageProperties = storageProperties;
        this.env = env;
        log.info("GcsStorageClient instantiated (storage init deferred)");
    }

    @Override
    public String put(String objectName, String contentType, InputStream inputStream) {
        String name = StringUtils.hasText(objectName) ? objectName : UUID.randomUUID().toString();
        String bucket = resolveBucket(storageProperties);
        BlobInfo blobInfo = BlobInfo.newBuilder(bucket, name)
                .setContentType(contentType)
                .build();
        try {
            getStorage().createFrom(blobInfo, inputStream);
            String publicUrl = "https://storage.googleapis.com/" + bucket + "/" + name;
            log.info("Uploaded product image to GCS. bucket={}, object={}", bucket, name);
            return publicUrl;
        } catch (IOException e) {
            log.error("I/O error while uploading image to GCS. bucket={}, object={}", bucket, name, e);
            throw new IllegalStateException("Failed to upload to GCS: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("GCS upload failed. bucket={}, object={}", bucket, name, e);
            throw new IllegalStateException("Failed to upload to GCS: " + e.getMessage(), e);
        }
    }

    /**
     * Read an object using application credentials (works for private buckets).
     * Used to proxy images to the browser when public GCS URLs return 403.
     */
    public Optional<GcsObjectContent> readObject(String bucket, String objectName) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectName)) {
            return Optional.empty();
        }
        try {
            Blob blob = getStorage().get(BlobId.of(bucket, objectName));
            if (blob == null || !blob.exists()) {
                return Optional.empty();
            }
            byte[] data = blob.getContent();
            String ct = blob.getContentType();
            if (!StringUtils.hasText(ct)) {
                ct = "application/octet-stream";
            }
            return Optional.of(new GcsObjectContent(data, ct));
        } catch (RuntimeException e) {
            log.warn("Failed to read GCS object bucket={} object={}: {}", bucket, objectName, e.getMessage());
            return Optional.empty();
        }
    }

    private Storage getStorage() {
        Storage result = storage;
        if (result == null) {
            synchronized (this) {
                result = storage;
                if (result == null) {
                    storage = result = buildStorage(storageProperties);
                }
            }
        }
        return result;
    }

    private Storage buildStorage(StorageProperties props) {
        try {
            StorageOptions.Builder builder = StorageOptions.newBuilder();
            String projectId = resolveProjectId(props);
            if (StringUtils.hasText(projectId)) {
                builder.setProjectId(projectId);
                log.info("Using explicit GCS project id: {}", projectId);
            } else {
                String defaultProject = com.google.cloud.ServiceOptions.getDefaultProjectId();
                log.info("No explicit GCS project id configured; default project: {}", defaultProject);
                // do not call setProjectId() to allow SDK to resolve default credentials/project automatically
            }
            return builder.build().getService();
        } catch (RuntimeException e) {
            log.error("Failed to initialize GCS Storage client: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize GCS client using Application Default Credentials: " + e.getMessage(), e);
        }
    }

    private String resolveProjectId(StorageProperties props) {
        String projectId = (props != null && props.gcs() != null) ? props.gcs().projectId() : null;
        if (!StringUtils.hasText(projectId) && env != null) {
            projectId = env.getProperty("storage.gcs.project-id");
            if (!StringUtils.hasText(projectId)) {
                projectId = env.getProperty("GCS_PROJECT_ID");
            }
        }
        return projectId;
    }

    private String resolveBucket(StorageProperties props) {
        String bucket = null;
        String source = null;

        // 1) try StorageProperties first
        if (props != null && props.gcs() != null && StringUtils.hasText(props.gcs().bucket())) {
            bucket = props.gcs().bucket();
            source = "StorageProperties.gcs.bucket";
        }

        // 2) try Spring Environment properties
        if (!StringUtils.hasText(bucket) && env != null) {
            bucket = env.getProperty("storage.gcs.bucket");
            if (StringUtils.hasText(bucket)) {
                source = "Environment:storage.gcs.bucket";
            }
            if (!StringUtils.hasText(bucket)) {
                bucket = env.getProperty("GCS_BUCKET");
                if (StringUtils.hasText(bucket)) {
                    source = "Environment:GCS_BUCKET";
                }
            }
        }

        // 3) try JVM system property
        if (!StringUtils.hasText(bucket)) {
            bucket = System.getProperty("storage.gcs.bucket");
            if (StringUtils.hasText(bucket)) {
                source = "SystemProperty:storage.gcs.bucket";
            }
        }

        // 4) try environment variables again (explicit)
        if (!StringUtils.hasText(bucket)) {
            bucket = System.getenv("GCS_BUCKET");
            if (StringUtils.hasText(bucket)) {
                source = "SystemEnv:GCS_BUCKET";
            }
        }
        if (!StringUtils.hasText(bucket)) {
            bucket = System.getenv("BUCKET");
            if (StringUtils.hasText(bucket)) {
                source = "SystemEnv:BUCKET";
            }
        }

        if (StringUtils.hasText(bucket)) {
            log.info("Resolved GCS bucket='{}' from {}", bucket, source);
            return bucket;
        }

        // If we reach here, nothing found — log the current detected values to help debugging
        String sp = (props != null && props.gcs() != null) ? props.gcs().bucket() : null;
        String envProp = env != null ? env.getProperty("storage.gcs.bucket") : null;
        String envVar = System.getenv("GCS_BUCKET");
        log.warn("GCS bucket not configured. StorageProperties.gcs.bucket='{}', Environment(storage.gcs.bucket)='{}', env.GCS_BUCKET='{}'", sp, envProp, envVar);

        // FALLBACK: use known bucket prod_bucket1234 to allow quick recovery/tests
        String fallback = "prod_bucket1234";
        log.warn("No GCS bucket configured — falling back to '{}' (temporary). Please set storage.gcs.bucket or env GCS_BUCKET.", fallback);
        return fallback;
    }
}
