package com.pivot.agent.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, String> index() {
        return Map.of(
            "status", "online",
            "service", "Pivot AI Backend",
            "message", "Backend is up and running!"
        );
    }
}
