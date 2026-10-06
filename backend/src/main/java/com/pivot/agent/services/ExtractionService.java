package com.pivot.agent.services;

import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.repositories.ProductRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ExtractionService {

    private final ChatClient chatClient;
    private final ProductRepository productRepository;

    public ExtractionService(ChatClient.Builder chatClientBuilder, ProductRepository productRepository) {
        this.chatClient = chatClientBuilder.build();
        this.productRepository = productRepository;
    }

    public ExtractedState extractIntent(String message, ExtractedState previousState) {
        String canonicalUseCases = String.join(", ", ProductCapabilityService.getAllowedUseCases());

        String prevContext = previousState != null ? 
            String.format("Previous State: Budget=%s, UseCases='%s', PrimaryUseCase='%s', Priorities='%s', NegativePreferences='%s', RequestedItems='%s', OwnedItems='%s', DecisionStage='%s'. Update this state based on the new message. If the new message doesn't change a field, keep the previous value. Merge lists instead of overwriting them if appropriate.", 
                previousState.budget(), previousState.useCases(), previousState.primaryUseCase(), previousState.priorities(), previousState.negativePreferences(), previousState.requestedItems(), previousState.ownedItems(), previousState.decisionStage()) 
            : "No previous state.";

        try {
            return chatClient.prompt()
                    .user(userSpec -> userSpec
                        .text("Extract the customer intent from this message: {message}. " +
                              "{prevContext} " +
                              "GROUND RULES: " +
                              "- Return ONLY the requested fields. DO NOT include any numeric score fields like fitScore or confidence.\n" +
                              "- customerIntentReasoning must be your brief chain of thought before emitting the state.\n" +
                              "- useCases MUST be chosen strictly from this canonical list: [{canonicalUseCases}].\n" +
                              "- priorities is a map of attributes to importance (HIGH, MEDIUM, LOW). E.g. 'performance': 'HIGH'.\n" +
                              "- decisionStage should be one of DISCOVERY, LAPTOP_RECOMMENDATION, COMPARISON, ACCESSORY_DISCOVERY, CHECKOUT. Rule: start at DISCOVERY. Move to LAPTOP_RECOMMENDATION when you have enough info (like use cases). Move to ACCESSORY_DISCOVERY when the user agrees to the laptop or expresses strong positive sentiment/intent to buy it (even if asking for a discount). Move to CHECKOUT when they are ready to pay.\n" +
                              "- isReadyToCheckout is true if the customer explicitly says they are ready to pay, buy, checkout, or place the order.\n" +
                              "- requestedItems is a list of specific products, accessories, or categories the customer explicitly asks for (e.g., keyboard, mouse, bag).\n" +
                              "- negativePreferences is a list of things they explicitly or implicitly reject. Determine this based on general principles of their intent (e.g., if they say 'stick to the budget' or 'just the laptop', they are rejecting bundles and accessories; if they mention hating a brand, exclude it).\n")
                        .param("message", message)
                        .param("prevContext", prevContext)
                        .param("canonicalUseCases", canonicalUseCases))
                    .call()
                    .entity(ExtractedState.class);
        } catch (Exception e) {
            return fallbackExtractIntent(message, previousState);
        }
    }

    public ExtractedState fallbackExtractIntent(String message, ExtractedState previousState) {
        String lower = message == null ? "" : message.toLowerCase();

        java.util.List<String> useCases = previousState != null && previousState.useCases() != null ? new java.util.ArrayList<>(previousState.useCases()) : new java.util.ArrayList<>();
        if (useCases.isEmpty()) {
            if (lower.contains("program") || lower.contains("code") || lower.contains("developer") || lower.contains("software")) {
                useCases.add("programming");
            }
            if (lower.contains("study") || lower.contains("student") || lower.contains("college")) {
                useCases.add("student");
            }
            if (lower.contains("travel") || lower.contains("portable") || lower.contains("lightweight")) {
                useCases.add("travel");
            }
            if (lower.contains("gaming") || lower.contains("game")) {
                useCases.add("gaming");
            }
            if (lower.contains("office") || lower.contains("business") || lower.contains("work")) {
                useCases.add("business");
            }
            if (lower.contains("video") || lower.contains("edit") || lower.contains("creator")) {
                useCases.add("video editing");
            }
            if (useCases.isEmpty()) {
                useCases.add("browsing");
            }
        }

        java.util.Map<String, String> priorities = previousState != null && previousState.priorities() != null ? new java.util.HashMap<>(previousState.priorities()) : new java.util.HashMap<>();
        if (useCases.contains("programming") || useCases.contains("gaming") || useCases.contains("video editing")) {
            priorities.put("performance", "HIGH");
        }
        if (useCases.contains("travel") || lower.contains("portable") || lower.contains("lightweight")) {
            priorities.put("portability", "HIGH");
        }
        if (priorities.isEmpty()) {
            priorities.put("performance", "MEDIUM");
        }

        java.util.List<String> requestedItems = previousState != null && previousState.requestedItems() != null ? new java.util.ArrayList<>(previousState.requestedItems()) : new java.util.ArrayList<>();
        if (lower.contains("mouse")) requestedItems.add("mouse");
        if (lower.contains("bag") || lower.contains("backpack")) requestedItems.add("bag");
        if (lower.contains("keyboard")) requestedItems.add("keyboard");
        if (lower.contains("monitor")) requestedItems.add("monitor");
        requestedItems = requestedItems.stream().distinct().toList();

        java.util.List<String> negativePreferences = previousState != null && previousState.negativePreferences() != null ? new java.util.ArrayList<>(previousState.negativePreferences()) : new java.util.ArrayList<>();
        if (lower.contains("no bundle") || lower.contains("just the laptop") || lower.contains("only the laptop")) {
            negativePreferences.add("bundle");
        }
        negativePreferences = negativePreferences.stream().distinct().toList();

        Double budget = previousState != null ? previousState.budget() : null;
        if (budget == null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:under|below|within|budget|upto|up to|rs\\.?|₹)?\\s?(\\d+(?:\\.\\d+)?)\\s?(k|000)?").matcher(lower);
            if (matcher.find()) {
                String value = matcher.group(1);
                double parsed = Double.parseDouble(value);
                if (matcher.group(2) != null && "k".equalsIgnoreCase(matcher.group(2))) {
                    parsed *= 1000;
                }
                budget = parsed;
            }
        }

        String decisionStage = previousState != null && previousState.decisionStage() != null ? previousState.decisionStage() : "DISCOVERY";
        if (useCases != null && !useCases.isEmpty() && !"DISCOVERY".equalsIgnoreCase(decisionStage)) {
            decisionStage = "LAPTOP_RECOMMENDATION";
        }

        return new ExtractedState(
                "Fallback extraction after AI quota/rate-limit failure.",
                previousState != null ? previousState.category() : "laptop",
                budget,
                previousState != null ? previousState.isStrictBudget() : null,
                useCases,
                useCases.isEmpty() ? null : useCases.get(0),
                priorities,
                negativePreferences,
                previousState != null ? previousState.priceSensitivity() : null,
                requestedItems,
                previousState != null ? previousState.ownedItems() : null,
                previousState != null ? previousState.requestedDiscountPercent() : null,
                decisionStage,
                previousState != null && Boolean.TRUE.equals(previousState.isReadyToCheckout())
        );
    }
}
