package com.ijse.eca.products.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firestore")
public record FirestoreProperties(
        String projectId,
        String productsCollection
) {
}

