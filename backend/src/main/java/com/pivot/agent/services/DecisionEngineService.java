package com.pivot.agent.services;

import com.pivot.agent.dto.ActionCandidate;
import com.pivot.agent.models.Merchant;
import com.pivot.agent.models.Product;
import com.pivot.agent.models.ExtractedState;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class DecisionEngineService {

    private static final double CUSTOMER_FIT_WEIGHT = 0.40;
    private static final double BUDGET_FIT_WEIGHT = 0.20;
    private static final double MERCHANT_VALUE_WEIGHT = 0.20;
    private static final double STRATEGIC_VALUE_WEIGHT = 0.10;
    private static final double OFFER_SUITABILITY_WEIGHT = 0.10;

    public ActionCandidate selectBestCandidate(List<ActionCandidate> passedCandidates, ExtractedState state, Merchant merchant) {
        if (passedCandidates.isEmpty()) return null;

        for (ActionCandidate candidate : passedCandidates) {
            scoreCandidate(candidate, state, merchant);
        }

        ActionCandidate winner = Collections.max(passedCandidates, Comparator.comparingDouble(ActionCandidate::getFinalScore));
        winner.setStatus("SELECTED");
        
        for (ActionCandidate c : passedCandidates) {
            if (c != winner) {
                c.setStatus("CONSIDERED");
            }
        }
        
        return winner;
    }

    private void scoreCandidate(ActionCandidate candidate, ExtractedState state, Merchant merchant) {
        double customerFit = calculateCustomerFit(candidate, state);
        double budgetFit = calculateBudgetFit(candidate, state);
        double merchantValue = calculateMerchantValue(candidate);
        double strategicValue = calculateStrategicValue(candidate, merchant.getPrimaryObjective());
        double offerSuitability = calculateOfferSuitability(candidate, state);

        candidate.setCustomerFit(customerFit);
        candidate.setBudgetFit(budgetFit);
        candidate.setMerchantValue(merchantValue);
        candidate.setStrategicValue(strategicValue);
        candidate.setOfferSuitability(offerSuitability);

        double finalScore = 
            (customerFit * CUSTOMER_FIT_WEIGHT) +
            (budgetFit * BUDGET_FIT_WEIGHT) +
            (merchantValue * MERCHANT_VALUE_WEIGHT) +
            (strategicValue * STRATEGIC_VALUE_WEIGHT) +
            (offerSuitability * OFFER_SUITABILITY_WEIGHT);
            
        candidate.setFinalScore(finalScore);
    }

    private double calculateCustomerFit(ActionCandidate candidate, ExtractedState state) {
        int score = 50;
        if (state.useCase() != null) {
            String useCase = state.useCase().toLowerCase();
            Product p = candidate.getBaseProduct();
            if (p.getUseCases() != null && p.getUseCases().stream().anyMatch(u -> u.toLowerCase().contains(useCase) || useCase.contains(u.toLowerCase()))) {
                score += 30;
            }
            if (p.getFeatures() != null && p.getFeatures().stream().anyMatch(f -> f.toLowerCase().contains(useCase) || useCase.contains(f.toLowerCase()))) {
                score += 20;
            }
            if (p.getTags() != null && p.getTags().stream().anyMatch(t -> t.toLowerCase().contains(useCase) || useCase.contains(t.toLowerCase()))) {
                score += 20;
            }
            
            if (candidate.getBundledProducts() != null) {
                for (Product bp : candidate.getBundledProducts()) {
                    if (bp.getUseCases() != null && bp.getUseCases().stream().anyMatch(u -> u.toLowerCase().contains(useCase) || useCase.contains(u.toLowerCase()))) {
                        score += 20;
                    }
                    if (bp.getFeatures() != null && bp.getFeatures().stream().anyMatch(f -> f.toLowerCase().contains(useCase) || useCase.contains(f.toLowerCase()))) {
                        score += 15;
                    }
                    if (bp.getTags() != null && bp.getTags().stream().anyMatch(t -> t.toLowerCase().contains(useCase) || useCase.contains(t.toLowerCase()))) {
                        score += 15;
                    }
                }
            }
        }
        if (state.negativePreferences() != null) {
            for (String negPref : state.negativePreferences()) {
                String neg = negPref.toLowerCase();
                if (candidate.getActionName().toLowerCase().contains(neg) || 
                    candidate.getType().toLowerCase().contains(neg)) {
                    score -= 50;
                }
            }
        }
        
        return Math.max(0.0, Math.min(100.0, score));
    }

    private double calculateBudgetFit(ActionCandidate candidate, ExtractedState state) {
        Double budget = state.budget();
        if (budget == null || budget <= 0) return 50.0;
        
        double price = candidate.getBaseProduct().getPrice();
        if (candidate.getBundledProducts() != null) {
            for (Product p : candidate.getBundledProducts()) {
                price += p.getPrice();
            }
        }
        price = price * (1.0 - (candidate.getDiscountPercent() / 100.0));
        
        double penalty = (Math.abs(price - budget) / budget) * 100.0;
        double fit = 100.0 - penalty;
        
        if (state.isStrictBudget() != null && state.isStrictBudget() && price > budget) {
            fit -= 100.0;
        }
        
        return Math.max(0.0, Math.min(100.0, fit));
    }

    private double calculateMerchantValue(ActionCandidate candidate) {
        double price = candidate.getBaseProduct().getPrice();
        double cost = candidate.getBaseProduct().getCost();
        if (candidate.getBundledProducts() != null) {
            for (Product p : candidate.getBundledProducts()) {
                price += p.getPrice();
                cost += p.getCost();
            }
        }
        
        double finalPrice = price * (1.0 - (candidate.getDiscountPercent() / 100.0));
        if (finalPrice <= 0) return 0;
        double margin = (finalPrice - cost) / finalPrice;
        
        double score = margin * 200.0; 
        return Math.max(0.0, Math.min(100.0, score));
    }

    private double calculateStrategicValue(ActionCandidate candidate, String primaryObjective) {
        if (primaryObjective == null) return 50.0;
        Product p = candidate.getBaseProduct();
        if (p.getTags() != null && p.getTags().contains(primaryObjective)) {
            return 100.0;
        }
        
        if (candidate.getBundledProducts() != null) {
            for (Product bp : candidate.getBundledProducts()) {
                if (bp.getTags() != null && bp.getTags().contains(primaryObjective)) {
                    return 85.0;
                }
            }
        }
        return 50.0;
    }

    private double calculateOfferSuitability(ActionCandidate candidate, ExtractedState state) {
        if (candidate.getDiscountPercent() == 0.0) {
            return 100.0;
        }
        
        String sensitivity = state.priceSensitivity() != null ? state.priceSensitivity().toLowerCase() : "medium";
        if ("high".equals(sensitivity)) {
            return 100.0;
        } else if ("low".equals(sensitivity)) {
            return 20.0;
        }
        
        return 60.0;
    }
}
