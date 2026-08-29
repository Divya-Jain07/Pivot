package com.pivot.agent.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "merchants")
public class Merchant {
    @Id
    private String merchantId;
    private double minMarginPercent;
    private double maxDiscountPercent;
    private List<Double> discountSteps;
    private String primaryObjective;
}
