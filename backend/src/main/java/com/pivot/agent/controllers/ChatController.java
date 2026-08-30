package com.pivot.agent.controllers;

import com.pivot.agent.dto.ChatRequest;
import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.services.ExtractionService;
import com.pivot.agent.services.DecisionPipelineService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ExtractionService extractionService;
    private final DecisionPipelineService decisionPipelineService;
    private final Map<String, ExtractedState> sessionState = new ConcurrentHashMap<>();

    public ChatController(ExtractionService extractionService, DecisionPipelineService decisionPipelineService) {
        this.extractionService = extractionService;
        this.decisionPipelineService = decisionPipelineService;
    }

    @PostMapping("/extract")
    public Object extract(@RequestBody ChatRequest request) {
        ExtractedState state = extractionService.extractIntent(request.message());
        sessionState.put(request.sessionId(), state);
        
        // Run Phase 3, 4, 5
        return decisionPipelineService.runPipeline(request.message(), state);
    }
}
