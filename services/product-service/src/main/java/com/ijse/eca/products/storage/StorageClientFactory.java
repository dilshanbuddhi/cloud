package com.ijse.eca.products.storage;

import com.ijse.eca.products.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StorageClientFactory {
    private static final Logger log = LoggerFactory.getLogger(StorageClientFactory.class);

    private final StorageProperties storageProperties;
    private final LocalStorageClient localStorageClient;
    private final GcsStorageClient gcsStorageClient;

    public StorageClientFactory(
            StorageProperties storageProperties,
            LocalStorageClient localStorageClient,
            org.springframework.beans.factory.ObjectProvider<GcsStorageClient> gcsProvider
    ) {
        this.storageProperties = storageProperties;
        this.localStorageClient = localStorageClient;
        this.gcsStorageClient = gcsProvider.getIfAvailable();
    }

    public StorageClient get() {
        String provider = storageProperties == null ? null : storageProperties.provider();

        // If provider explicitly set to local -> use local
        if ("local".equalsIgnoreCase(provider)) {
            log.info("Using local storage provider for product image upload");
            return localStorageClient;
        }

        // If provider explicitly set to gcs -> require gcs client
        if ("gcs".equalsIgnoreCase(provider)) {
            if (gcsStorageClient == null) {
                throw new IllegalStateException("storage.provider=gcs but GCS client not available");
            }
            log.info("Using GCS storage provider for product image upload (explicit)");
            return gcsStorageClient;
        }

        // If provider not set, prefer GCS when available (makes cloud runs easier)
        if (gcsStorageClient != null) {
            log.info("storage.provider not set; defaulting to GCS because GCS client is available");
            return gcsStorageClient;
        }

        // Fallback to local
        log.info("storage.provider not set and GCS client not available; using local storage provider");
        return localStorageClient;
    }
}
