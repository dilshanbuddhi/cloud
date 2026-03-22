package com.ijse.eca.products.api;

import com.ijse.eca.products.service.ProductImageService;
import com.ijse.eca.products.service.ProductImageService.ProductImageBytes;
import com.ijse.eca.products.web.NotFoundException;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductImageViewController {
    private final ProductImageService productImageService;

    public ProductImageViewController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    /**
     * Proxies the product image through the API (uses GCS credentials) so browsers can display
     * images even when the bucket is not publicly readable.
     */
    @GetMapping("/products/{id}/image/view")
    public ResponseEntity<byte[]> viewProductImage(@PathVariable("id") String id) {
        try {
            ProductImageBytes img = productImageService.loadImageForProduct(id);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            try {
                mediaType = MediaType.parseMediaType(img.contentType());
            } catch (RuntimeException ignored) {
                // keep octet-stream
            }
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                    .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                    .body(img.body());
        } catch (NotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
