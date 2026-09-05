package com.pivot.agent.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCapability {
    private String productId;
    private String performance; // HIGH, MEDIUM, LOW, UNKNOWN
    private String portability; // HIGH, MEDIUM, LOW, UNKNOWN
    private List<String> useCases;
}
