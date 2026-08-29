package com.pivot.agent.models;

public record ExtractedState(
    String category,
    String useCase,
    Double budget,
    String priceSensitivity
) {}
