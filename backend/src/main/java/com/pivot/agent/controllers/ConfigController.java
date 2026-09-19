package com.pivot.agent.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fix #4 — Exposes non-secret public configuration to the frontend.
 *
 * Razorpay's key_id is intentionally public (it appears in browser JS),
 * but serving it from the backend means the frontend and backend always
 * use the same value — eliminating the rotation/environment mismatch risk.
 *
 * GET /api/config → { "razorpayKeyId": "rzp_..." }
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @GetMapping
    public Map<String, String> getConfig() {
        return Map.of("razorpayKeyId", razorpayKeyId);
    }
}
