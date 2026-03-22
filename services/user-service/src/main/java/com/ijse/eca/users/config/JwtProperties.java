package com.ijse.eca.users.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank(message = "security.jwt.secret must not be blank") String secret,
        @NotBlank(message = "security.jwt.issuer must not be blank") String issuer,
        @Min(value = 1, message = "security.jwt.expires-minutes must be at least 1") long expiresMinutes
) {
}
