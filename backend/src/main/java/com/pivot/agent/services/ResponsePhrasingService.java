package com.pivot.agent.services;

import com.pivot.agent.models.AgentDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class ResponsePhrasingService {

    private final ChatClient chatClient;

    public ResponsePhrasingService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateResponse(AgentDecision decision, String conversationHistory) {
        String selectedAction = decision.getSelectedAction() != null ? decision.getSelectedAction().getAction() : "No suitable option found";
        
        String rejections = decision.getPolicyRejections().stream()
                .map(r -> r.getAction() + " (Rejected because: " + r.getReason() + ")")
                .collect(Collectors.joining("\n"));

        String prompt = """
            You are a helpful and persuasive AI Merchant Sales Agent. 
            
            Here is the conversation history so far:
            %s
            
            Based on our mathematical backend rules, the absolute best recommendation for the customer's latest message is:
            "%s"
            
            The backend also rejected some options due to strict merchant policies (e.g. minimum profit margins):
            %s
            
            INSTRUCTIONS:
            1. You MUST NOT invent any new products, discounts, or bundles. You are strictly a spokesperson for the backend decision.
            2. Phrase the recommended action naturally and persuasively to the customer.
            3. If the customer explicitly asked for a discount or an option that was rejected, gracefully explain why it's not possible. The rejection reason provided is an INTERNAL system reason (like profit margins). You MUST translate it into a polite, customer-facing excuse (e.g., "That discount exceeds our current promotional limits", "This item is already priced as low as possible"). DO NOT ever expose internal profit margins or backend metrics to the customer.
            4. IMPORTANT CONTEXT: Read the conversation history! If the recommended action is the exact same item/bundle that was already pitched in the previous turn, you MUST frame your response as a continuation of the conversation (e.g., "I know you're looking for a deal on this bundle, but it is truly our best value..."). DO NOT pitch the item as if you are introducing it for the very first time.
            5. Keep your response concise, friendly, and professional. Do not show the math or scores, just talk to the customer.
            """;

        return chatClient.prompt()
                .user(String.format(prompt, conversationHistory, selectedAction, rejections))
                .call()
                .content();
    }
}
