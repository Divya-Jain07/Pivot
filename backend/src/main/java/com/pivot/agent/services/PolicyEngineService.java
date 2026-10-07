package com.pivot.agent.services;

import com.pivot.agent.dto.ActionCandidate;
import com.pivot.agent.models.Merchant;
import com.pivot.agent.models.Product;
import com.pivot.agent.models.ExtractedState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PolicyEngineService {

    public List<ActionCandidate> enforcePolicy(List<ActionCandidate> candidates, Merchant merchant, ExtractedState state) {
        List<ActionCandidate> mandatoryPassed = new ArrayList<>();
        
        for (ActionCandidate candidate : candidates) {
            String rejectionReason = evaluatePolicy(candidate, merchant, state);
            if (rejectionReason == null) {
                mandatoryPassed.add(candidate);
            } else {
                candidate.setStatus("REJECTED");
                candidate.setRejectionReason(rejectionReason);
            }
        }
        
        List<ActionCandidate> preferencePassed = new ArrayList<>();
        for (ActionCandidate candidate : mandatoryPassed) {
            boolean preferenceViolated = false;
            if (state.negativePreferences() != null) {
                for (String negPref : state.negativePreferences()) {
                    String neg = negPref.toLowerCase();
                    if (candidate.getActionName().toLowerCase().contains(neg) || 
                        candidate.getType().toLowerCase().contains(neg)) {
                        preferenceViolated = true;
                        break;
                    }
                }
            }
            if (!preferenceViolated) {
                preferencePassed.add(candidate);
            } else {
                candidate.setStatus("REJECTED");
                candidate.setRejectionReason("Excluded by customer preference");
                candidate.setPreferenceCompromised(true);
            }
        }
        
        if (preferencePassed.isEmpty() && !mandatoryPassed.isEmpty()) {
            for (ActionCandidate candidate : mandatoryPassed) {
                if (candidate.isPreferenceCompromised()) {
                    candidate.setStatus(null);
                    candidate.setRejectionReason(null);
                }
            }
            return mandatoryPassed;
        }
        
        return preferencePassed;
    }

    private String evaluatePolicy(ActionCandidate candidate, Merchant merchant, ExtractedState state) {
        if (candidate.getBaseProduct().getInventory() <= 0) {
            return "Product is out of stock.";
        }
        
        if (candidate.getBundledProducts() != null) {
            for (Product p : candidate.getBundledProducts()) {
                if (p.getInventory() <= 0) {
                    return "Bundled product " + p.getName() + " is out of stock.";
                }
            }
        }

        if (candidate.getDiscountPercent() > merchant.getMaxDiscountPercent()) {
            return candidate.getDiscountPercent() + "% discount exceeds maximum allowable discount of " + merchant.getMaxDiscountPercent() + "%";
        }
        
        if (candidate.getDiscountPercent() > 0.0) {
            boolean validStep = false;
            for (Double step : merchant.getDiscountSteps()) {
                if (Math.abs(step - candidate.getDiscountPercent()) < 0.001) {
                    validStep = true;
                    break;
                }
            }
            if (!validStep) {
                return candidate.getDiscountPercent() + "% discount is not a valid discount step.";
            }
        }

        double totalPrice = candidate.getBaseProduct().getPrice();
        double totalCost = candidate.getBaseProduct().getCost();
        
        if (candidate.getBundledProducts() != null) {
            for (Product p : candidate.getBundledProducts()) {
                totalPrice += p.getPrice();
                totalCost += p.getCost();
            }
        }
        
        double finalPrice = totalPrice * (1.0 - (candidate.getDiscountPercent() / 100.0));
        if (finalPrice <= 0) return "Final price is zero or negative.";

        if (state.budget() != null && state.budget() > 0) {
            double maximumPrice = Boolean.TRUE.equals(state.isStrictBudget()) ? state.budget() : state.budget() + 10_000.0;
            if (finalPrice > maximumPrice) {
                return Boolean.TRUE.equals(state.isStrictBudget())
                        ? "Final price exceeds the strict budget."
                        : "Final price exceeds the flexible budget tolerance of 10000.";
            }
        }

        if (hasRequiredTouchscreen(state) && !Boolean.TRUE.equals(candidate.getBaseProduct().getTouchScreen())) {
            return "Required touchscreen capability is unavailable or unverified.";
        }
        
        double margin = ((finalPrice - totalCost) / finalPrice) * 100.0;
        
        if (margin < merchant.getMinMarginPercent()) {
            return String.format("Margin of %.1f%% falls below minimum requirement of %.1f%%", margin, merchant.getMinMarginPercent());
        }
        
        return null;
    }

    private boolean hasRequiredTouchscreen(ExtractedState state) {
        if (state.priorities() == null) {
            return false;
        }
        return state.priorities().entrySet().stream()
                .anyMatch(entry -> "touchscreen".equalsIgnoreCase(entry.getKey().replaceAll("[^a-zA-Z]", ""))
                        && "REQUIRED".equalsIgnoreCase(entry.getValue()));
    }
}
