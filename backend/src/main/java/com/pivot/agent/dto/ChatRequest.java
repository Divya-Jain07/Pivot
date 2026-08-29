package com.pivot.agent.dto;

public record ChatRequest(
    String message,
    String sessionId
) {}
