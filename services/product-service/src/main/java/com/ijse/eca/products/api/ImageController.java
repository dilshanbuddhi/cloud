package com.ijse.eca.products.api;

import com.ijse.eca.products.config.StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImageController {
    private final StorageProperties storageProperties;

    public ImageController(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @GetMapping("/products/images/{name}")
    public ResponseEntity<Resource> get(@PathVariable("name") String name) {
        if (!"local".equalsIgnoreCase(storageProperties.provider())) {
            return ResponseEntity.notFound().build();
        }
        if (!StringUtils.hasText(name) || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        Path baseDir = Path.of(storageProperties.local().baseDir());
        Path file = baseDir.resolve(name);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(new FileSystemResource(file));
    }
}
