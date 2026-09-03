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

        AgentDecision.Candidate consideredAlternative = null;
        if (decision.getExtractedState() != null && decision.getCandidates() != null) {
            java.util.List<String> requestedItems = decision.getExtractedState().requestedItems();
            Double reqDiscount = decision.getExtractedState().requestedDiscountPercent();
            
            String selectedId = decision.getSelectedAction() != null ? decision.getSelectedAction().getAction() : "";
            String selectedProductId = decision.getSelectedAction() != null ? decision.getSelectedAction().getProductId() : null;
            
            consideredAlternative = decision.getCandidates().stream()
                .filter(c -> "CONSIDERED".equals(c.getStatus()) && !c.getAction().equals(selectedId))
                .filter(c -> selectedProductId == null || selectedProductId.equals(c.getProductId()))
                .filter(c -> {
                    boolean matchesItem = false;
                    if (requestedItems != null && !requestedItems.isEmpty()) {
                        for (String item : requestedItems) {
                            if (c.getAction().toLowerCase().contains(item.toLowerCase())) {
                                matchesItem = true;
                                break;
                            }
                        }
                    }
                    boolean matchesDiscount = false;
                    if (reqDiscount != null && c.getAction().contains(String.valueOf(reqDiscount))) {
                        matchesDiscount = true;
                    }
                    return matchesItem || matchesDiscount;
                })
                .max(java.util.Comparator.comparingDouble(AgentDecision.Candidate::getFinalScore))
                .orElse(null);
        }
        
        String consideredAltStr = consideredAlternative != null ? 
            consideredAlternative.getAction() + " (Price: " + consideredAlternative.getFinalAmount() + ")" : "None";

        String prompt = """
            You are a helpful and persuasive AI Merchant Sales Agent. 
            
            Here is the conversation history so far:
            %s
            
            Based on our mathematical backend rules, the absolute best recommendation for the customer's latest message is:
            "%s"
            
            The backend also rejected some options due to strict merchant policies (e.g. minimum profit margins):
            %s
            
            Considered but Unselected Alternative (if customer asked for it):
            %s
            
            INSTRUCTIONS:
            1. You MUST NOT invent any new products, discounts, or bundles. You are strictly a spokesperson for the backend decision.
            2. Phrase the recommended action naturally and persuasively to the customer.
            3. If the customer's request matches a CONSIDERED candidate (not selected), offer it honestly as a real option with its actual price, noting the currently selected option scores slightly better on overall value. Never state that a considered-but-unselected option is unavailable, already at best pricing, or lacking in some invented way. Only use policy language ('I'm not able to offer that') for candidates that appear in policyRejections with a real reason.
            4. If the customer explicitly asked for a discount or an option that was rejected, gracefully explain why it's not possible. The rejection reason provided is an INTERNAL system reason (like profit margins). You MUST translate it into a polite, customer-facing excuse (e.g., "That discount exceeds our current promotional limits", "This item is already priced as low as possible"). DO NOT ever expose internal profit margins or backend metrics to the customer.
            5. IMPORTANT CONTEXT: Read the conversation history! If the recommended action is the exact same item/bundle that was already pitched in the previous turn, you MUST frame your response as a continuation of the conversation (e.g., "I know you're looking for a deal on this bundle, but it is truly our best value..."). DO NOT pitch the item as if you are introducing it for the very first time.
            6. Keep your response concise, friendly, and professional. Do not show the math or scores, just talk to the customer.
            """;

        return chatClient.prompt()
                .user(String.format(prompt, conversationHistory, selectedAction, rejections, consideredAltStr))
                .call()
                .content();
    }
}
