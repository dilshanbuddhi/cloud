package com.ijse.eca.products.service;

import com.ijse.eca.products.api.dto.CreateProductRequest;
import com.ijse.eca.products.api.dto.UpdateProductRequest;
import com.ijse.eca.products.domain.Product;
import com.ijse.eca.products.repo.ProductRepository;
import com.ijse.eca.products.storage.StorageClient;
import com.ijse.eca.products.storage.StorageClientFactory;
import com.ijse.eca.products.web.NotFoundException;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final StorageClientFactory storageClientFactory;

    public ProductService(ProductRepository productRepository, StorageClientFactory storageClientFactory) {
        this.productRepository = productRepository;
        this.storageClientFactory = storageClientFactory;
    }

    public Product create(CreateProductRequest request) {
        return productRepository.save(new Product(request.name(), request.price(), null));
    }

    public List<Product> list() {
        return productRepository.findAll();
    }

    public Product getById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    public Product update(String id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        product.setName(request.name());
        product.setPrice(request.price());
        return productRepository.save(product);
    }

    public void delete(String id) {
        if (productRepository.findById(id).isEmpty()) {
            throw new NotFoundException("Product not found");
        }
        productRepository.deleteById(id);
    }

    public Product uploadImage(String productId, String filename, String contentType, InputStream inputStream) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        StorageClient storage = storageClientFactory.get();
        String objectName = productId + "-" + sanitizeFilename(filename);
        String url = storage.put(objectName, contentType, inputStream);
        product.setImageUrl(url);
        Product updated = productRepository.save(product);
        log.info("Saved product image URL. productId={}, imageUrl={}", productId, url);
        return updated;
    }

    private static String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "image";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
