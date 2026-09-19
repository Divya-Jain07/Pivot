package com.pivot.agent.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fix #3 — Per-IP rate limiting using Bucket4j.
 *
 * Limits:
 *   /api/chat/**       → 20 requests / minute  (Gemini cost protection)
 *   /api/orders/create → 5 requests / minute   (Razorpay cost protection)
 *
 * Returns HTTP 429 with a plain error message when a bucket is exhausted.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    // Separate bucket stores per route group, keyed by client IP
    private final ConcurrentHashMap<String, Bucket> chatBuckets   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> orderBuckets  = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String ip  = resolveClientIp(request);
        String uri = request.getRequestURI();

        Bucket bucket = null;

        if (uri.startsWith("/api/chat/")) {
            bucket = chatBuckets.computeIfAbsent(ip, k -> buildBucket(20));
        } else if (uri.equals("/api/orders/create")) {
            bucket = orderBuckets.computeIfAbsent(ip, k -> buildBucket(5));
        }

        if (bucket != null && !bucket.tryConsume(1)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please slow down.\"}");
            return false;
        }

        return true;
    }

    /** Creates a Bucket that allows {@code requestsPerMinute} tokens, refilled every minute. */
    private Bucket buildBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Resolves the real client IP, respecting the X-Forwarded-For header set by
     * reverse proxies (Render, Railway, Vercel, etc.).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For can be a comma-separated list; the first entry is the client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
