package com.pivot.agent.services;

import com.pivot.agent.dto.ActionCandidate;
import com.pivot.agent.models.AgentDecision;
import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.models.Merchant;
import com.pivot.agent.repositories.AgentDecisionRepository;
import com.pivot.agent.repositories.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DecisionPipelineService {

    private final CandidateGeneratorService candidateGeneratorService;
    private final PolicyEngineService policyEngineService;
    private final DecisionEngineService decisionEngineService;
    private final MerchantRepository merchantRepository;
    private final AgentDecisionRepository agentDecisionRepository;

    public AgentDecision runPipeline(String customerInput, ExtractedState state) {
        Merchant merchant = merchantRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No merchant found in DB"));

        // 1. Generate Candidates
        List<ActionCandidate> allCandidates = candidateGeneratorService.generateCandidates(state, merchant);
        
        // Hierarchical pipeline: Only consider BASE products (laptops) first
        List<ActionCandidate> baseCandidates = allCandidates.stream()
                .filter(c -> "BASE".equals(c.getType()))
                .collect(Collectors.toList());
        
        // 2. Enforce Policy
        List<ActionCandidate> passed = policyEngineService.enforcePolicy(baseCandidates, merchant, state);

        List<AgentDecision.PolicyRejection> policyRejections = baseCandidates.stream()
                .filter(c -> "REJECTED".equals(c.getStatus()))
                .map(c -> AgentDecision.PolicyRejection.builder()
                        .action(c.getActionName())
                        .reason(c.getRejectionReason())
                        .build())
                .limit(12)
                .collect(Collectors.toList());

        // 3. Score & Select Best Laptop
        ActionCandidate bestAction = decisionEngineService.selectBestCandidate(passed, state, merchant);
        
        boolean isDiscovery = "DISCOVERY".equalsIgnoreCase(state.decisionStage())
            || (state.budget() == null && state.isStrictBudget() == null)
            || state.useCases() == null
            || state.useCases().isEmpty();
        if (isDiscovery) {
            bestAction = null;
            passed.forEach(c -> {
                if ("SELECTED".equals(c.getStatus())) {
                    c.setStatus("CONSIDERED");
                }
            });
        }
        
        // 4. Cross-sell accessories hierarchically (Only if we are past the initial laptop recommendation)
        boolean allowCrossSell = state.decisionStage() == null || !"LAPTOP_RECOMMENDATION".equalsIgnoreCase(state.decisionStage());
        if (allowCrossSell && bestAction != null && state.requestedItems() != null && !state.requestedItems().isEmpty()) {
            final String bestId = bestAction.getBaseProduct().getProductId();
            
            // Try to find a bundle that matches the most requested items
            ActionCandidate matchingBundle = allCandidates.stream()
                .filter(c -> "BUNDLE".equals(c.getType()) && c.getBaseProduct().getProductId().equals(bestId))
                .max(java.util.Comparator.comparingInt(c -> {
                    if (c.getBundledProducts() == null || c.getBundledProducts().isEmpty()) return 0;
                    int matches = 0;
                    for (String req : state.requestedItems()) {
                        boolean match = c.getBundledProducts().stream().anyMatch(p -> 
                            p.getName().toLowerCase().contains(req.toLowerCase()) || 
                            (p.getCategory() != null && p.getCategory().toLowerCase().contains(req.toLowerCase())));
                        if (match) matches++;
                    }
                    return matches;
                }))
                .filter(c -> {
                    if (c.getBundledProducts() == null || c.getBundledProducts().isEmpty()) return false;
                    for (String req : state.requestedItems()) {
                        boolean match = c.getBundledProducts().stream().anyMatch(p -> 
                            p.getName().toLowerCase().contains(req.toLowerCase()) || 
                            (p.getCategory() != null && p.getCategory().toLowerCase().contains(req.toLowerCase())));
                        if (match) return true;
                    }
                    return false;
                })
                .orElse(null);
                
            // Fallback to any predefined bundle if no specific match
            if (matchingBundle == null) {
                matchingBundle = allCandidates.stream()
                    .filter(c -> "BUNDLE".equals(c.getType()) && c.getBaseProduct().getProductId().equals(bestId))
                    .findFirst().orElse(null);
            }
                
            if (matchingBundle != null) {
                bestAction.setStatus("CONSIDERED"); // Remove SELECTED status from the base product
                decisionEngineService.selectBestCandidate(List.of(matchingBundle), state, merchant);
                bestAction = matchingBundle;
                passed.add(bestAction);
            }
        }
        
        // For output mapping, we only show the ones that passed through the hierarchical pipeline
        List<ActionCandidate> finalCandidatesToMap = passed;

        // Map candidates to AgentDecision format
        List<AgentDecision.Candidate> mappedCandidates = finalCandidatesToMap.stream()
                .map(c -> {
                    double amount = c.getBaseProduct().getPrice();
                    if (c.getBundledProducts() != null) {
                        for (var p : c.getBundledProducts()) {
                            amount += p.getPrice();
                        }
                    }
                    double discountVal = amount * (c.getDiscountPercent() / 100.0);
                    double finalAmount = amount - discountVal;
                    
                    return AgentDecision.Candidate.builder()
                        .action(c.getActionName())
                        .productId(c.getBaseProduct().getProductId())
                        .amount(amount)
                        .discount(discountVal)
                        .finalAmount(finalAmount)
                        .status(c.getStatus())
                        .finalScore(c.getFinalScore())
                        .preferenceCompromised(c.isPreferenceCompromised())
                        .factors(AgentDecision.Factors.builder()
                                .customerFit(c.getCustomerFit())
                                .budgetFit(c.getBudgetFit())
                                .merchantValue(c.getMerchantValue())
                                .strategicValue(c.getStrategicValue())
                                .offerSuitability(c.getOfferSuitability())
                                .build())
                        .weights(Map.of(
                            "customerFit", 0.65,
                            "budgetFit", 0.15,
                            "merchantValue", 0.10,
                            "strategicValue", 0.05,
                            "offerSuitability", 0.05
                        ))
                        .reasons(buildReasons(c, state))
                        .build();
                })
                .collect(Collectors.toList());

        AgentDecision.Candidate mappedSelected = null;
        if (bestAction != null) {
            final String finalActionName = bestAction.getActionName();
            mappedSelected = mappedCandidates.stream()
                    .filter(c -> c.getAction().equals(finalActionName))
                    .findFirst()
                    .orElse(null);
        }

        AgentDecision decision = AgentDecision.builder()
                .timestamp(Instant.now())
                .customerInput(customerInput)
                .extractedState(state)
                .candidates(mappedCandidates)
                .policyRejections(policyRejections)
                .selectedAction(mappedSelected)
                .build();

        return agentDecisionRepository.save(decision);
    }

    private List<String> buildReasons(ActionCandidate candidate, ExtractedState state) {
        List<String> reasons = new java.util.ArrayList<>();
        var product = candidate.getBaseProduct();

        if (state.useCases() != null && product.getUseCases() != null) {
            List<String> matchingUseCases = state.useCases().stream()
                    .filter(requested -> product.getUseCases().stream()
                            .anyMatch(available -> available.equalsIgnoreCase(requested)))
                    .distinct()
                    .toList();
            if (!matchingUseCases.isEmpty()) {
                reasons.add("Catalog use cases match: " + String.join(", ", matchingUseCases));
            }
        }

        if (state.priorities() != null && state.priorities().entrySet().stream()
                .anyMatch(entry -> "touchscreen".equalsIgnoreCase(entry.getKey().replaceAll("[^a-zA-Z]", ""))
                        && !"LOW".equalsIgnoreCase(entry.getValue()))
                && Boolean.TRUE.equals(product.getTouchScreen())) {
            reasons.add("Has the touchscreen you prefer");
        }

        double finalPrice = product.getPrice();
        if (candidate.getBundledProducts() != null) {
            finalPrice += candidate.getBundledProducts().stream().mapToDouble(com.pivot.agent.models.Product::getPrice).sum();
        }
        finalPrice *= 1.0 - candidate.getDiscountPercent() / 100.0;
        if (state.budget() != null && finalPrice <= state.budget()) {
            reasons.add("Within your stated budget");
        } else if (state.budget() != null && !Boolean.TRUE.equals(state.isStrictBudget())
                && finalPrice <= state.budget() + 10_000.0) {
            reasons.add("Within your flexible budget range");
        }

        if (reasons.isEmpty()) {
            reasons.add("Best overall fit among eligible options");
        }
        return reasons.stream().limit(3).toList();
    }
}
