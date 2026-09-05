package com.pivot.agent.dto;

import com.pivot.agent.models.Product;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ActionCandidate {
    private String actionName;
    private Product baseProduct;
    private List<Product> bundledProducts;
    private double discountPercent;
    private String type; // BASE, BUNDLE, DISCOUNT, UPGRADE
    
    private double customerFit;
    private double budgetFit;
    private double merchantValue;
    private double strategicValue;
    private double offerSuitability;
    
    private double finalScore;
    private String status; // SELECTED, REJECTED, CONSIDERED
    private String rejectionReason;
    private boolean preferenceCompromised;
}
