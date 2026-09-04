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
        List<ActionCandidate> passed = new ArrayList<>();
        
        for (ActionCandidate candidate : candidates) {
            String rejectionReason = evaluatePolicy(candidate, merchant, state);
            if (rejectionReason == null) {
                passed.add(candidate);
            } else {
                candidate.setStatus("REJECTED");
                candidate.setRejectionReason(rejectionReason);
            }
        }
        
        return passed;
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
        
        double margin = ((finalPrice - totalCost) / finalPrice) * 100.0;
        
        if (margin < merchant.getMinMarginPercent()) {
            return String.format("Margin of %.1f%% falls below minimum requirement of %.1f%%", margin, merchant.getMinMarginPercent());
        }
        
        return null;
    }
}
