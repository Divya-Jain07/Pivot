package com.pivot.agent.services;

import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.models.ProductCapability;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;

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
                useCaseScore = Math.min(40.0, (double) matchCount / customerUseCases.size() * 40.0);
            }
        } else {
            useCaseScore = 20.0; // neutral score if no use cases requested
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
        
        return Math.min(100.0, score);
    }
}
