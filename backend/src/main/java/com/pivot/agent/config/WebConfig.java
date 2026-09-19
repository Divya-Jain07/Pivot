package com.pivot.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers application-wide MVC interceptors.
 * CorsConfig is a separate WebMvcConfigurer bean — Spring merges both.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Fix #3 — rate limiter applies to all API routes
        registry.addInterceptor(new RateLimitInterceptor())
                .addPathPatterns("/api/**");
    }
}
