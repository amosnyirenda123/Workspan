package com.workspan.workspanbackend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/", "/api/health"})
    public HealthResponse health() {
        return new HealthResponse("Backend Spring Boot opérationnel !");
    }

    public record HealthResponse(String message) {
    }
}
