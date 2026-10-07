package com.pivot.agent.services;

import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.models.ProductCapability;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerFitService {

    public double calculateFit(ExtractedState state, ProductCapability capability) {
        double score = 20.0; // Base score
        
        // 1. Use Case Fit (40 max)
        double useCaseScore = 0;
        List<String> customerUseCases = state.useCases();
        if (customerUseCases != null && !customerUseCases.isEmpty() && capability.getUseCases() != null) {
            long matchCount = customerUseCases.stream()
                .filter(c -> capability.getUseCases().stream().anyMatch(u -> 
                    u.toLowerCase().contains(c.toLowerCase()) || c.toLowerCase().contains(u.toLowerCase())
                ))
                .count();
            if (matchCount > 0) {
                useCaseScore = Math.min(25.0, (double) matchCount / customerUseCases.size() * 25.0);
            }
        } else {
            useCaseScore = 12.5; // neutral score if no use cases requested
        }
        score += useCaseScore;
        
        // 2. Performance Fit (25 max)
        Map<String, String> priorities = state.priorities();
        
        // Infer HIGH performance requirement if use cases imply it
        if (priorities == null || !priorities.containsKey("performance")) {
            if (customerUseCases != null && customerUseCases.stream().anyMatch(c -> {
                String cl = c.toLowerCase();
                return cl.contains("programming") || cl.contains("gaming") || cl.contains("video editing") || cl.contains("rendering");
            })) {
                if (priorities == null) priorities = new java.util.HashMap<>();
                else priorities = new java.util.HashMap<>(priorities);
                priorities.put("performance", "HIGH");
            }
        }
        
        double perfScore = 12.5; // Neutral
        if (priorities != null && priorities.containsKey("performance")) {
            String prio = priorities.get("performance").toUpperCase();
            String prodPerf = capability.getPerformance();
            
            if ("HIGH".equals(prio)) {
                if ("HIGH".equals(prodPerf)) perfScore = 25.0;
                else if ("MEDIUM".equals(prodPerf)) perfScore = 15.0;
                else perfScore = 5.0; 
            } else if ("MEDIUM".equals(prio)) {
                if ("HIGH".equals(prodPerf)) perfScore = 20.0;
                else if ("MEDIUM".equals(prodPerf)) perfScore = 25.0;
                else perfScore = 10.0;
            } else if ("LOW".equals(prio)) {
                if ("LOW".equals(prodPerf)) perfScore = 25.0;
                else if ("MEDIUM".equals(prodPerf)) perfScore = 15.0;
                else perfScore = 10.0;
            }
        }
        score += perfScore;
        
        // 3. Portability Fit (15 max)
        double portScore = 7.5; // Neutral
        if (priorities != null && priorities.containsKey("portability")) {
            String prio = priorities.get("portability").toUpperCase();
            String prodPort = capability.getPortability();
            
            if ("HIGH".equals(prio)) {
                if ("HIGH".equals(prodPort)) portScore = 15.0;
                else if ("MEDIUM".equals(prodPort)) portScore = 10.0;
                else portScore = 2.0; 
            } else if ("MEDIUM".equals(prio)) {
                if ("HIGH".equals(prodPort)) portScore = 12.0;
                else if ("MEDIUM".equals(prodPort)) portScore = 15.0;
                else portScore = 5.0;
            } else if ("LOW".equals(prio)) {
                if ("LOW".equals(prodPort)) portScore = 15.0;
                else if ("MEDIUM".equals(prodPort)) portScore = 10.0;
                else portScore = 5.0;
            }
        }
        score += portScore;
        score += calculateExplicitPreferenceFit(priorities, capability);
        
        return Math.min(100.0, score);
    }

    private double calculateExplicitPreferenceFit(Map<String, String> priorities, ProductCapability capability) {
        if (priorities == null || priorities.isEmpty()) {
            return 0.0;
        }

        double weightedFit = 0.0;
        double totalWeight = 0.0;
        for (Map.Entry<String, String> entry : priorities.entrySet()) {
            String key = entry.getKey().replaceAll("[^a-zA-Z]", "").toLowerCase(Locale.ROOT);
            String importance = entry.getValue() == null ? "" : entry.getValue().toUpperCase(Locale.ROOT);
            double priorityWeight = switch (importance) {
                case "HIGH" -> 1.0;
                case "MEDIUM" -> 0.65;
                case "LOW" -> 0.35;
                default -> 0.0;
            };
            if (priorityWeight == 0.0) {
                continue;
            }

            Double capabilityFit = switch (key) {
                case "touchscreen" -> capability.getTouchScreen() == null ? 0.5 : capability.getTouchScreen() ? 1.0 : 0.0;
                case "ram", "ramgb" -> scaledFit(capability.getRamGb(), 16.0);
                case "storage", "storagegb" -> scaledFit(capability.getStorageGb(), 1024.0);
                case "battery", "batterywh" -> scaledFit(capability.getBatteryWh(), 60.0);
                default -> null;
            };
            if (capabilityFit != null) {
                weightedFit += capabilityFit * priorityWeight;
                totalWeight += priorityWeight;
            }
        }

        return totalWeight == 0.0 ? 0.0 : 70.0 * weightedFit / totalWeight;
    }

    private double scaledFit(Number value, double target) {
        return value == null ? 0.5 : Math.min(1.0, Math.max(0.0, value.doubleValue() / target));
    }
}
