package com.ijse.eca.products.api.dto;

public record ProductResponse(
        String id,
        String name,
        double price,
        String imageUrl
) {
}
