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

    public ExtractedState extractIntent(String message) {
        return chatClient.prompt()
                .user(userSpec -> userSpec
                    .text("Extract the customer intent from this message: {message}. " +
                          "Return ONLY the requested fields. DO NOT include any numeric score fields like fitScore or confidence.")
                    .param("message", message))
                .call()
                .entity(ExtractedState.class);
    }
}
