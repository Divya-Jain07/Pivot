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
        java.util.Set<String> allUseCases = productRepository.findAll().stream()
            .filter(p -> p.getUseCases() != null)
            .flatMap(p -> p.getUseCases().stream())
            .collect(java.util.stream.Collectors.toSet());
        String canonicalUseCases = String.join(", ", allUseCases);

        String prevContext = previousState != null ? 
            String.format("Previous State: Budget=%s, UseCases='%s', PrimaryUseCase='%s', Priorities='%s', NegativePreferences='%s', RequestedItems='%s', OwnedItems='%s', DecisionStage='%s'. Update this state based on the new message. If the new message doesn't change a field, keep the previous value. Merge lists instead of overwriting them if appropriate.", 
                previousState.budget(), previousState.useCases(), previousState.primaryUseCase(), previousState.priorities(), previousState.negativePreferences(), previousState.requestedItems(), previousState.ownedItems(), previousState.decisionStage()) 
            : "No previous state.";

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
    }
}
