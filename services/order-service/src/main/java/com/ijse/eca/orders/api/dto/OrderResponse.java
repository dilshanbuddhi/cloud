package com.ijse.eca.orders.api.dto;

import java.time.Instant;

public record OrderResponse(
        String id,
        Long userId,
        String productId,
        int quantity,
        Instant createdAt
) {
}
