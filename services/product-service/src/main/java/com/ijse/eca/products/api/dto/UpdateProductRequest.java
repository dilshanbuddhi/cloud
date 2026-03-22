package com.ijse.eca.products.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UpdateProductRequest(
        @NotBlank String name,
        @Positive double price
) {
}
