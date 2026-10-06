package com.pivot.agent.controllers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pivot.agent.dto.ChatRequest;
import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.services.ExtractionService;
import com.pivot.agent.services.DecisionPipelineService;
import com.pivot.agent.services.ResponsePhrasingService;
import com.pivot.agent.repositories.AgentDecisionRepository;
import com.pivot.agent.models.AgentDecision;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ExtractionService extractionService;
    private final DecisionPipelineService decisionPipelineService;
    private final ResponsePhrasingService responsePhrasingService;
    private final AgentDecisionRepository agentDecisionRepository;
    // Fix #5 — Bounded Caffeine caches replace unbounded ConcurrentHashMaps.
    // Evicts entries idle for 2 hours; caps total size at 10,000 sessions.
    private final Cache<String, ExtractedState> sessionState = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    private final Cache<String, String> sessionHistory = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();

    public ChatController(ExtractionService extractionService, 
                          DecisionPipelineService decisionPipelineService,
                          ResponsePhrasingService responsePhrasingService,
                          AgentDecisionRepository agentDecisionRepository) {
        this.extractionService = extractionService;
        this.decisionPipelineService = decisionPipelineService;
        this.responsePhrasingService = responsePhrasingService;
        this.agentDecisionRepository = agentDecisionRepository;
    }

    @PostMapping("/extract")
    public Object extract(@RequestBody ChatRequest request) {
        ExtractedState existing = sessionState.getIfPresent(request.sessionId());

        ExtractedState updatedState = extractionService.extractIntent(request.message(), existing);

        sessionState.put(request.sessionId(), updatedState);

        String compactSummary = buildCompactSummary(updatedState, request.message());
        sessionHistory.put(request.sessionId(), compactSummary);

        // Run Phase 3, 4, 5
        return decisionPipelineService.runPipeline(request.message(), updatedState);
    }



    public record RespondRequest(String decisionId, String sessionId) {}

    @PostMapping("/respond-decision")
    public String respondDecision(@RequestBody RespondRequest request) {
        AgentDecision decision = agentDecisionRepository.findById(request.decisionId())
                .orElseThrow(() -> new RuntimeException("Decision not found"));

        String compactSummary = sessionHistory.getIfPresent(request.sessionId());
        if (compactSummary == null || compactSummary.isBlank()) {
            compactSummary = buildCompactSummary(decision.getExtractedState(), decision.getCustomerInput());
        }

        String response = responsePhrasingService.generateResponse(decision, compactSummary);

        String updatedSummary = buildCompactSummary(decision.getExtractedState(), decision.getCustomerInput())
                + "\nLast agent reply: " + response;
        sessionHistory.put(request.sessionId(), updatedSummary);

        return response;
    }

    public static String buildCompactSummary(ExtractedState state, String latestMessage) {
        StringBuilder summary = new StringBuilder();

        if (state != null) {
            summary.append("Budget: ").append(state.budget() == null ? "n/a" : state.budget());
            summary.append(" | Use cases: ").append(state.useCases() == null || state.useCases().isEmpty() ? "n/a" : String.join(", ", state.useCases()));
            summary.append(" | Primary use case: ").append(state.primaryUseCase() == null ? "n/a" : state.primaryUseCase());
            summary.append(" | Priorities: ").append(state.priorities() == null || state.priorities().isEmpty() ? "n/a" : state.priorities());
            summary.append(" | Negative preferences: ").append(state.negativePreferences() == null || state.negativePreferences().isEmpty() ? "n/a" : String.join(", ", state.negativePreferences()));
            summary.append(" | Requested items: ").append(state.requestedItems() == null || state.requestedItems().isEmpty() ? "n/a" : String.join(", ", state.requestedItems()));
            summary.append(" | Decision stage: ").append(state.decisionStage() == null ? "n/a" : state.decisionStage());
        }

        if (latestMessage != null && !latestMessage.isBlank()) {
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            summary.append("Latest user message: ").append(latestMessage.trim());
        }

        return summary.toString();
    }
}
