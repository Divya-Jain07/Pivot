package com.pivot.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Fix #2 — CORS Configuration.
 *
 * Restricts API access to the deployed frontend origin only.
 * Set the FRONTEND_URL environment variable on your backend host
 * (e.g. https://your-app.vercel.app). Defaults to localhost:5173 for local dev.
 *
 * Note: /api/orders/webhook is excluded from the mapping intentionally —
 * Razorpay webhook calls are server-to-server and CORS does not apply to them.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
