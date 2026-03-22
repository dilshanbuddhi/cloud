package com.ijse.eca.products.storage;

import com.ijse.eca.products.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalStorageClient implements StorageClient {
    private final StorageProperties storageProperties;

    public LocalStorageClient(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public String put(String objectName, String contentType, InputStream inputStream) {
        String safeName = StringUtils.hasText(objectName) ? objectName : UUID.randomUUID().toString();
        if (storageProperties.local() == null || storageProperties.local().baseDir() == null) {
            throw new IllegalStateException("Local storage is not configured. Set storage.local.base-dir in your config.");
        }
        Path baseDir = Path.of(storageProperties.local().baseDir());
        try {
            Files.createDirectories(baseDir);
            Path target = baseDir.resolve(safeName);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            // In dev, we expose files via /products/images/**
            return "/products/images/" + safeName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
    }
}
