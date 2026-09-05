package com.pivot.agent.models;

import java.util.List;

import java.util.Map;

public record ExtractedState(
    String customerIntentReasoning,
    String category,
    Double budget,
    Boolean isStrictBudget,
    List<String> useCases,
    String primaryUseCase,
    Map<String, String> priorities,
    List<String> negativePreferences,
    String priceSensitivity,
    List<String> requestedItems,
    List<String> ownedItems,
    Double requestedDiscountPercent,
    String decisionStage,
    Boolean isReadyToCheckout
) {}
