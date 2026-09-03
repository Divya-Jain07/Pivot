package com.pivot.agent.models;

import java.util.List;

public record ExtractedState(
    String category,
    String useCase,
    Double budget,
    String priceSensitivity,
    List<String> requestedItems,
    Double requestedDiscountPercent
) {}
