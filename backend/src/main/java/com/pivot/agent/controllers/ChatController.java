package com.pivot.agent.controllers;

import com.pivot.agent.dto.ChatRequest;
import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.services.ExtractionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ExtractionService extractionService;
    private final Map<String, ExtractedState> sessionState = new ConcurrentHashMap<>();

    public ChatController(ExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    @PostMapping("/extract")
    public ExtractedState extract(@RequestBody ChatRequest request) {
        ExtractedState state = extractionService.extractIntent(request.message());
        sessionState.put(request.sessionId(), state);
        return state;
    }
}
