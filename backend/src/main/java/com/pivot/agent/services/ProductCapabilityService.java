package com.pivot.agent.services;

import com.pivot.agent.models.Product;
import com.pivot.agent.models.ProductCapability;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ProductCapabilityService {

    public static final List<String> ALLOWED_USE_CASES = List.of(
            "student",
            "browsing",
            "light work",
            "productivity",
            "business",
            "programming",
            "multitasking",
            "video editing",
            "gaming",
            "travel"
    );

    public static List<String> getAllowedUseCases() {
        return ALLOWED_USE_CASES;
    }

    public static List<String> normalizeUseCases(List<String> rawUseCases) {
        if (rawUseCases == null || rawUseCases.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawUseCase : rawUseCases) {
            if (rawUseCase == null) {
                continue;
            }
            String value = rawUseCase.trim();
            if (value.isEmpty()) {
                continue;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            if (ALLOWED_USE_CASES.contains(lower)) {
                normalized.add(lower);
            }
        }
        return new ArrayList<>(normalized);
    }

    public ProductCapability evaluate(Product product) {
        String performance = determinePerformance(product);
        String portability = determinePortability(product);
        List<String> useCases = normalizeUseCases(product.getUseCases());

        return ProductCapability.builder()
                .productId(product.getProductId())
                .performance(performance)
                .portability(portability)
                .useCases(useCases)
                .build();
    }

    private String determinePerformance(Product product) {
        if (product == null) {
            return "UNKNOWN";
        }

        Integer ramGb = product.getRamGb();
        String cpuTier = product.getCpuTier() == null ? null : product.getCpuTier().toLowerCase(Locale.ROOT);
        Boolean dedicatedGpu = product.getDedicatedGpu();

        if (ramGb != null || cpuTier != null || dedicatedGpu != null) {
            if ((ramGb != null && ramGb >= 16) && ("high".equals(cpuTier) || Boolean.TRUE.equals(dedicatedGpu))) {
                return "HIGH";
            }
            if ("high".equals(cpuTier) || "medium".equals(cpuTier) || (ramGb != null && ramGb >= 8)) {
                return "MEDIUM";
            }
            if (ramGb != null && ramGb > 0) {
                return "LOW";
            }
        }

        String featuresStr = product.getFeatures() == null ? "" : String.join(" ", product.getFeatures()).toLowerCase(Locale.ROOT);
        if (featuresStr.contains("32gb") || featuresStr.contains("16gb") || featuresStr.contains("i7") || featuresStr.contains("ryzen 7") || featuresStr.contains("i9") || featuresStr.contains("rtx") || featuresStr.contains("3060") || featuresStr.contains("3070") || featuresStr.contains("3080")) {
            return "HIGH";
        }
        if (featuresStr.contains("8gb") || featuresStr.contains("i5") || featuresStr.contains("ryzen 5") || featuresStr.contains("ultra 5")) {
            return "MEDIUM";
        }
        if (featuresStr.contains("4gb") || featuresStr.contains("i3") || featuresStr.contains("celeron") || featuresStr.contains("pentium") || featuresStr.contains("ryzen 3")) {
            return "LOW";
        }
        return "UNKNOWN";
    }

    private String determinePortability(Product product) {
        if (product == null) {
            return "UNKNOWN";
        }

        if (product.getWeightKg() != null) {
            if (product.getWeightKg() <= 1.3) {
                return "HIGH";
            }
            if (product.getWeightKg() <= 1.7) {
                return "MEDIUM";
            }
            return "LOW";
        }

        String featuresStr = product.getFeatures() == null ? "" : String.join(" ", product.getFeatures()).toLowerCase(Locale.ROOT);
        if (featuresStr.contains("1.1kg") || featuresStr.contains("1.2kg") || featuresStr.contains("1.3kg")) {
            return "HIGH";
        }
        if (featuresStr.contains("1.4kg") || featuresStr.contains("1.5kg") || featuresStr.contains("1.6kg") || featuresStr.contains("1.7kg")) {
            return "MEDIUM";
        }
        if (featuresStr.contains("1.8kg") || featuresStr.contains("2.0kg") || featuresStr.contains("gaming")) {
            return "LOW";
        }
        return "UNKNOWN";
    }
}
