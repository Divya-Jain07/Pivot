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
                          "Also extract any specific products or accessories the customer is explicitly asking to bundle into 'requestedItems' (as a list of strings). " +
                          "Also extract any specific discount percentage the customer explicitly asked for into 'requestedDiscountPercent' (as a Double, e.g., 5.0 for 5%). " +
                          "Return ONLY the requested fields. DO NOT include any numeric score fields like fitScore or confidence.")
                    .param("message", message)
                    .param("prevContext", prevContext))
                .call()
                .entity(ExtractedState.class);
    }
}
