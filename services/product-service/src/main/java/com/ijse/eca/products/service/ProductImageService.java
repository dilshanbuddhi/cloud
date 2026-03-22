package com.ijse.eca.products.service;

import com.ijse.eca.products.config.StorageProperties;
import com.ijse.eca.products.domain.Product;
import com.ijse.eca.products.repo.ProductRepository;
import com.ijse.eca.products.storage.GcsObjectContent;
import com.ijse.eca.products.storage.GcsStorageClient;
import com.ijse.eca.products.web.NotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductImageService {
    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);
    private static final String LOCAL_PREFIX = "/products/images/";

    private final ProductRepository productRepository;
    private final StorageProperties storageProperties;
    private final ObjectProvider<GcsStorageClient> gcsClient;

    public ProductImageService(
            ProductRepository productRepository,
            StorageProperties storageProperties,
            ObjectProvider<GcsStorageClient> gcsClient) {
        this.productRepository = productRepository;
        this.storageProperties = storageProperties;
        this.gcsClient = gcsClient;
    }

    public record ProductImageBytes(byte[] body, String contentType) {}

    /**
     * Streams product image bytes using server credentials (GCS) or local disk.
     * Use from GET /products/{id}/image/view so browsers are not blocked by private buckets.
     */
    public ProductImageBytes loadImageForProduct(String productId) {
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        String url = product.getImageUrl();
        if (!StringUtils.hasText(url)) {
            throw new NotFoundException("Product has no image");
        }

        if (url.startsWith(LOCAL_PREFIX)) {
            return readLocal(url);
        }

        Optional<BucketObject> gcs = parseStorageGoogleapisUrl(url);
        if (gcs.isPresent()) {
            return readGcs(gcs.get());
        }

        log.warn("Cannot proxy image for product {} — unsupported imageUrl format", productId);
        throw new NotFoundException("Image cannot be served");
    }

    private ProductImageBytes readLocal(String url) {
        String name = url.substring(LOCAL_PREFIX.length());
        if (!StringUtils.hasText(name) || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new NotFoundException("Invalid image path");
        }
        if (storageProperties.local() == null || !StringUtils.hasText(storageProperties.local().baseDir())) {
            throw new NotFoundException("Local storage not configured");
        }
        Path baseDir = Path.of(storageProperties.local().baseDir());
        Path file = baseDir.resolve(name);
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("Image file not found");
        }
        try {
            byte[] data = Files.readAllBytes(file);
            String ct = Files.probeContentType(file);
            if (!StringUtils.hasText(ct)) {
                ct = guessContentType(name);
            }
            return new ProductImageBytes(data, ct);
        } catch (IOException e) {
            log.error("Failed to read local image {}", file, e);
            throw new NotFoundException("Image read failed");
        }
    }

    private ProductImageBytes readGcs(BucketObject ref) {
        GcsStorageClient gcs = gcsClient.getIfAvailable();
        if (gcs == null) {
            throw new NotFoundException("GCS client not available");
        }
        Optional<GcsObjectContent> content = gcs.readObject(ref.bucket(), ref.object());
        return content
                .map(c -> new ProductImageBytes(c.data(), c.contentType()))
                .orElseThrow(() -> new NotFoundException("Image not found in GCS"));
    }

    private static String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    /** Parses https://storage.googleapis.com/BUCKET/OBJECT */
    static Optional<BucketObject> parseStorageGoogleapisUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return Optional.empty();
        }
        try {
            URI u = URI.create(url.trim());
            String scheme = u.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
                return Optional.empty();
            }
            String host = u.getHost();
            if (host == null) {
                return Optional.empty();
            }
            if (!host.equalsIgnoreCase("storage.googleapis.com")) {
                return Optional.empty();
            }
            String rawPath = u.getRawPath();
            if (!StringUtils.hasText(rawPath) || "/".equals(rawPath)) {
                return Optional.empty();
            }
            String path = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
            int slash = path.indexOf('/');
            if (slash <= 0 || slash >= path.length() - 1) {
                return Optional.empty();
            }
            String bucket = URLDecoder.decode(path.substring(0, slash), StandardCharsets.UTF_8);
            String object = URLDecoder.decode(path.substring(slash + 1), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(bucket) || !StringUtils.hasText(object)) {
                return Optional.empty();
            }
            return Optional.of(new BucketObject(bucket, object));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private record BucketObject(String bucket, String object) {}
}
