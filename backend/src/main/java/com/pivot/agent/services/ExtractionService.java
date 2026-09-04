package com.pivot.agent.services;

import com.pivot.agent.models.ExtractedState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ExtractionService {

    private final ChatClient chatClient;

    public ExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public ExtractedState extractIntent(String message, ExtractedState previousState) {
        String prevContext = previousState != null ? 
            String.format("Previous State: Category='%s', Budget=%s, UseCase='%s', Sensitivity='%s', RequestedItems='%s', RequestedDiscountPercent='%s'. Update this state based on the new message. If the new message doesn't change a field, keep the previous value.", 
                previousState.category(), previousState.budget(), previousState.useCase(), previousState.priceSensitivity(), previousState.requestedItems(), previousState.requestedDiscountPercent()) 
            : "No previous state.";

        return chatClient.prompt()
                .user(userSpec -> userSpec
                    .text("Extract the customer intent from this message: {message}. " +
                          "{prevContext} " +
                          "GROUND RULES: " +
                          "- Return ONLY the requested fields. DO NOT include any numeric score fields like fitScore or confidence.\n" +
                          "- requestedDiscountPercent must be a Double (e.g., 5.0 for 5%).\n" +
                          "- isStrictBudget is true if they imply a hard ceiling.\n" +
                          "- negativePreferences is a list of things they explicitly or implicitly reject.\n\n" +
                          "EXAMPLES:\n" +
                          "User: 'I want a laptop under 80000, maybe with a bag' -> budget: 80000, requestedItems: ['bag'], isStrictBudget: false, negativePreferences: []\n" +
                          "User: 'stick to the budget! just the laptop' -> isStrictBudget: true, negativePreferences: ['bundle', 'accessories']\n" +
                          "User: 'I can't go over 50k and I hate AMD' -> budget: 50000, isStrictBudget: true, negativePreferences: ['AMD']")
                    .param("message", message)
                    .param("prevContext", prevContext))
                .call()
                .entity(ExtractedState.class);
    }
}
