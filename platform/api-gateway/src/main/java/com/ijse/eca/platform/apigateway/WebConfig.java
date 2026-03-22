package com.ijse.eca.platform.apigateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Do not register {@code /**} static resources — that can answer before Gateway routes when
 * routes are empty/misconfigured and returns 404 for API paths like {@code /auth/register}.
 * HTML is served by {@link FrontendController}; SPA uses CDN assets.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/favicon.ico", "/robots.txt")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true);
    }
}
