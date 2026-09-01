package com.pivot.agent.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "agentDecisions")
public class AgentDecision {
    @Id
    private String decisionId;
    private Instant timestamp;
    private String customerInput;
    private ExtractedState extractedState;
    private List<Candidate> candidates;
    private List<PolicyRejection> policyRejections;
    private Candidate selectedAction;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtractedState {
        private String category;
        private String useCase;
        private Double budget;
        private String priceSensitivity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Candidate {
        private String action; // E.g., "Buy PRODUCT_A", "Buy PRODUCT_A with 10% discount"
        private String productId;
        private double amount;
        private double discount;
        private double finalAmount;
        private Factors factors;
        private Map<String, Double> weights;
        private double finalScore;
        private String status; // "SELECTED", "REJECTED", "CONSIDERED"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Factors {
        private double customerFit;
        private double budgetFit;
        private double merchantValue;
        private double strategicValue;
        private double offerSuitability;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PolicyRejection {
        private String action;
        private String reason;
    }
}
