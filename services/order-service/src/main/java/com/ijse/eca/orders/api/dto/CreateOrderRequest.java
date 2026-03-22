package com.ijse.eca.orders.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotBlank String productId,
        @Positive int quantity
) {
}
