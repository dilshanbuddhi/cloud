package com.ijse.eca.platform.apigateway;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class FrontendController {

    private final Resource indexHtml = new ClassPathResource("static/index.html");

    @GetMapping(value = {"/", "/index.html", "/pos", "/app"}, produces = MediaType.TEXT_HTML_VALUE)
    public Mono<Resource> frontend() {
        return Mono.just(indexHtml);
    }
}
