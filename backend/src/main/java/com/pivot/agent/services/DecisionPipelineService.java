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

        // Convert the record to AgentDecision's nested state class
        AgentDecision.ExtractedState nestedState = AgentDecision.ExtractedState.builder()
                .category(state.category())
                .useCase(state.useCase())
                .budget(state.budget())
                .priceSensitivity(state.priceSensitivity())
                .build();

        // 1. Generate Candidates
        List<ActionCandidate> candidates = candidateGeneratorService.generateCandidates(nestedState, merchant);
        
        // 2. Enforce Policy
        List<ActionCandidate> passed = policyEngineService.enforcePolicy(candidates, merchant);
        
        List<AgentDecision.PolicyRejection> policyRejections = candidates.stream()
                .filter(c -> "REJECTED".equals(c.getStatus()))
                .map(c -> AgentDecision.PolicyRejection.builder()
                        .action(c.getActionName())
                        .reason(c.getRejectionReason())
                        .build())
                .collect(Collectors.toList());

        // 3. Score & Select Best
        ActionCandidate bestAction = decisionEngineService.selectBestCandidate(passed, nestedState, merchant);

        // Map candidates to AgentDecision format
        List<AgentDecision.Candidate> mappedCandidates = candidates.stream()
                .map(c -> AgentDecision.Candidate.builder()
                        .action(c.getActionName())
                        .status(c.getStatus())
                        .finalScore(c.getFinalScore())
                        .factors(AgentDecision.Factors.builder()
                                .customerFit(c.getCustomerFit())
                                .budgetFit(c.getBudgetFit())
                                .merchantValue(c.getMerchantValue())
                                .strategicValue(c.getStrategicValue())
                                .offerSuitability(c.getOfferSuitability())
                                .build())
                        .weights(Map.of(
                                "customerFit", 0.40,
                                "budgetFit", 0.20,
                                "merchantValue", 0.20,
                                "strategicValue", 0.10,
                                "offerSuitability", 0.10
                        ))
                        .build())
                .collect(Collectors.toList());

        AgentDecision.Candidate mappedSelected = null;
        if (bestAction != null) {
            mappedSelected = mappedCandidates.stream()
                    .filter(c -> c.getAction().equals(bestAction.getActionName()))
                    .findFirst()
                    .orElse(null);
        }

        AgentDecision decision = AgentDecision.builder()
                .timestamp(Instant.now())
                .customerInput(customerInput)
                .extractedState(nestedState)
                .candidates(mappedCandidates)
                .policyRejections(policyRejections)
                .selectedAction(mappedSelected)
                .build();

        return agentDecisionRepository.save(decision);
    }
}
