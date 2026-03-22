package com.ijse.eca.products.api;

import com.ijse.eca.products.api.dto.CreateProductRequest;
import com.ijse.eca.products.api.dto.ProductResponse;
import com.ijse.eca.products.api.dto.UpdateProductRequest;
import com.ijse.eca.products.domain.Product;
import com.ijse.eca.products.service.ProductService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return toResponse(productService.create(request));
    }

    @GetMapping("/products")
    public List<ProductResponse> list() {
        return productService.list().stream().map(ProductController::toResponse).toList();
    }

    @GetMapping("/products/{id}")
    public ProductResponse getById(@PathVariable("id") String id) {
        return toResponse(productService.getById(id));
    }

    @PutMapping("/products/{id}")
    public ProductResponse update(@PathVariable("id") String id, @Valid @RequestBody UpdateProductRequest request) {
        return toResponse(productService.update(id, request));
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        productService.delete(id);
    }

    @PostMapping(value = "/products/{id}/image", consumes = "multipart/form-data")
    public ProductResponse uploadImage(@PathVariable("id") String id, @RequestPart("file") MultipartFile file)
            throws IOException {
        Product updated = productService.uploadImage(
                id,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream()
        );
        return toResponse(updated);
    }

    private static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getImageUrl());
    }
}
