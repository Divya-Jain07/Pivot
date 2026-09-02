package com.pivot.agent.controllers;

import com.pivot.agent.dto.ChatRequest;
import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.services.ExtractionService;
import com.pivot.agent.services.DecisionPipelineService;
import com.pivot.agent.services.ResponsePhrasingService;
import com.pivot.agent.repositories.AgentDecisionRepository;
import com.pivot.agent.models.AgentDecision;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ExtractionService extractionService;
    private final DecisionPipelineService decisionPipelineService;
    private final ResponsePhrasingService responsePhrasingService;
    private final AgentDecisionRepository agentDecisionRepository;
    private final Map<String, ExtractedState> sessionState = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> sessionHistory = new ConcurrentHashMap<>();

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
        ExtractedState existing = sessionState.get(request.sessionId());
        
        ExtractedState updatedState = extractionService.extractIntent(request.message(), existing);
        
        sessionState.put(request.sessionId(), updatedState);
        
        StringBuilder history = sessionHistory.computeIfAbsent(request.sessionId(), k -> new StringBuilder());
        history.append("Customer: ").append(request.message()).append("\n");
        
        // Run Phase 3, 4, 5
        return decisionPipelineService.runPipeline(request.message(), updatedState);
    }



    public record RespondRequest(String decisionId, String sessionId) {}

    @PostMapping("/respond-decision")
    public String respondDecision(@RequestBody RespondRequest request) {
        AgentDecision decision = agentDecisionRepository.findById(request.decisionId())
                .orElseThrow(() -> new RuntimeException("Decision not found"));
                
        StringBuilder history = sessionHistory.getOrDefault(request.sessionId(), new StringBuilder());
        
        String response = responsePhrasingService.generateResponse(decision, history.toString());
        
        // Append the AI's response to the history for the next turn
        history.append("Agent: ").append(response).append("\n\n");
        sessionHistory.put(request.sessionId(), history);
        
        return response;
    }
}
