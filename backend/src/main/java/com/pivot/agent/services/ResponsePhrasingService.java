package com.pivot.agent.services;

import com.pivot.agent.models.AgentDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

import com.pivot.agent.repositories.ProductRepository;
import com.pivot.agent.models.Product;

@Service
public class ResponsePhrasingService {

    private final ChatClient chatClient;
    private final ProductRepository productRepository;

    public ResponsePhrasingService(ChatClient.Builder chatClientBuilder, ProductRepository productRepository) {
        this.chatClient = chatClientBuilder.build();
        this.productRepository = productRepository;
    }

    public String generateResponse(AgentDecision decision, String conversationHistory) {
        String selectedAction = decision.getSelectedAction() != null ? decision.getSelectedAction().getAction() : "No suitable option found";
        
        String rejections = decision.getPolicyRejections().stream()
                .map(r -> r.getAction() + " (Rejected because: " + r.getReason() + ")")
                .collect(Collectors.joining("\n"));

        AgentDecision.Candidate consideredAlternative = null;
        String selectedSpecs = "";
        
        if (decision.getExtractedState() != null && decision.getCandidates() != null) {
            java.util.List<String> requestedItems = decision.getExtractedState().requestedItems();
            Double reqDiscount = decision.getExtractedState().requestedDiscountPercent();
            
            String selectedId = decision.getSelectedAction() != null ? decision.getSelectedAction().getAction() : "";
            String selectedProductId = decision.getSelectedAction() != null ? decision.getSelectedAction().getProductId() : null;
            
            if (selectedProductId != null) {
                Product p = productRepository.findById(selectedProductId).orElse(null);
                if (p != null) {
                    if (p.getFeatures() != null) {
                        selectedSpecs = "ACTUAL SPECIFICATIONS:\n- " + String.join("\n- ", p.getFeatures());
                    }
                }
            }
            
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

        Double budget = decision.getExtractedState() != null ? decision.getExtractedState().budget() : null;
        Double selectedFinalPrice = decision.getSelectedAction() != null ? decision.getSelectedAction().getFinalAmount() : null;
        String budgetContext = "";
        if (budget != null && selectedFinalPrice != null) {
            if (selectedFinalPrice > budget) {
                budgetContext = String.format("Note: The recommended action costs %.2f, which EXCEEDS the customer's budget of %.2f. You MUST NOT claim it fits their budget. Acknowledge it is slightly over budget but frame it as a worthy upgrade.", selectedFinalPrice, budget);
            } else {
                budgetContext = String.format("Note: The recommended action costs %.2f, which is within the customer's budget of %.2f.", selectedFinalPrice, budget);
            }
        }
        
        boolean preferenceCompromised = decision.getSelectedAction() != null && decision.getSelectedAction().isPreferenceCompromised();
        String preferenceContext = "";
        if (preferenceCompromised) {
            preferenceContext = "Note: The recommended action violates one of the customer's negative preferences (e.g., they asked for no bundle, but we are offering a bundle). You MUST honestly acknowledge this and explain that the preferred option is currently unavailable or out of stock, so this is the best remaining option.";
        }

        String stageContext = "";
        if (decision.getExtractedState() != null) {
            String stage = decision.getExtractedState().decisionStage();
            java.util.List<String> useCases = decision.getExtractedState().useCases();
            if ("DISCOVERY".equalsIgnoreCase(stage) || (useCases == null || useCases.isEmpty())) {
                stageContext = "STAGE CONTEXT: The customer is in the DISCOVERY phase. They haven't provided enough information (like use cases). DO NOT forcefully pitch the recommendation. Instead, politely ask what they will mainly use the laptop for (e.g. studying, programming, gaming, business) to give them a better recommendation.";
            } else if ("ACCESSORY_DISCOVERY".equalsIgnoreCase(stage)) {
                String allAccessories = productRepository.findAll().stream()
                    .filter(prod -> "accessory".equalsIgnoreCase(prod.getCategory()))
                    .map(Product::getName)
                    .collect(Collectors.joining(", "));
                
                if (!allAccessories.isEmpty()) {
                    stageContext = "STAGE CONTEXT: The customer is in the ACCESSORY_DISCOVERY phase. You MUST proactively cross-sell by suggesting multiple accessories from our catalog that pair well with their laptop. Available accessories: " + allAccessories + ". Subtly mention these as great additions to their order and explicitly list them out as bullet points, do not wait for them to ask.";
                } else {
                    stageContext = "STAGE CONTEXT: The customer is in the ACCESSORY_DISCOVERY phase. Ask them if they need any accessories to go with their laptop.";
                }
            }
        }

        String prompt = """
            You are a helpful and persuasive AI Merchant Sales Agent. 
            
            Here is the conversation history so far:
            %s
            
            Based on our mathematical backend rules, the absolute best recommendation for the customer's latest message is:
            "%s"
            
            %s
            
            The backend also rejected some options due to strict merchant policies (e.g. minimum profit margins):
            %s
            
            Considered but Unselected Alternative (if customer asked for it):
            %s
            
            BUDGET CONTEXT:
            %s
            
            PREFERENCE CONTEXT:
            %s
            
            %s
            
            INSTRUCTIONS:
            1. You MUST NOT invent any new products, discounts, or bundles. You are strictly a spokesperson for the backend decision.
            2. Phrase the recommended action naturally and persuasively to the customer. (Unless STAGE CONTEXT tells you to ask a question first).
            3. If the customer's request matches a CONSIDERED candidate (not selected), offer it honestly as a real option with its actual price, noting the currently selected option scores slightly better on overall value. Never state that a considered-but-unselected option is unavailable, already at best pricing, or lacking in some invented way. Only use policy language ('I'm not able to offer that') for candidates that appear in policyRejections with a real reason.
            4. If the customer explicitly asked for a discount or an option that was rejected, gracefully explain why it's not possible. The rejection reason provided is an INTERNAL system reason (like profit margins). You MUST translate it into a polite, customer-facing excuse (e.g., "That discount exceeds our current promotional limits", "This item is already priced as low as possible"). DO NOT ever expose internal profit margins or backend metrics to the customer.
            5. IMPORTANT CONTEXT: Read the conversation history! If the recommended action is the exact same item/bundle that was already pitched in the previous turn, you MUST frame your response as a continuation of the conversation (e.g., "I know you're looking for a deal on this bundle, but it is truly our best value..."). DO NOT pitch the item as if you are introducing it for the very first time.
            6. BUDGET HONESTY: Strictly adhere to the BUDGET CONTEXT. If it is over budget, do not pretend it is under budget.
            7. PREFERENCE HONESTY: Read the PREFERENCE CONTEXT. If it states that a negative preference was violated, you MUST honestly acknowledge it politely (e.g. "I know you wanted just the laptop, but the standalone version is currently unavailable, so this bundle is the best remaining option..."). Do NOT fabricate unavailability if the PREFERENCE CONTEXT is empty.
            8. Keep your response concise, friendly, and professional. Do not show the math or scores, just talk to the customer.
            9. STAGE ADHERENCE: If STAGE CONTEXT is provided, you MUST follow its instructions over pitching the recommendation.
            10. ANTI-HALLUCINATION: NEVER hallucinate product specifications like processors, RAM, or display size. You MUST ONLY use the features provided in ACTUAL SPECIFICATIONS. Do not invent an Intel Core i5 if the specs say AMD Ryzen.
            11. FORMATTING: Whenever you list bullet points (like options, features, or specifications), you MUST use actual line breaks (e.g. - item 1 \\n - item 2). DO NOT place them on a single line separated by asterisks or hyphens.
            """;

        return chatClient.prompt()
                .user(String.format(prompt, conversationHistory, selectedAction, selectedSpecs, rejections, consideredAltStr, budgetContext, preferenceContext, stageContext))
                .call()
                .content();
    }
}
