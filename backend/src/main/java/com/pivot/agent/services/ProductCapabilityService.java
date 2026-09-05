package com.pivot.agent.services;

import com.pivot.agent.models.Product;
import com.pivot.agent.models.ProductCapability;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Collections;

@Service
public class ProductCapabilityService {

    public ProductCapability evaluate(Product product) {
        String performance = "UNKNOWN";
        String portability = "UNKNOWN";
        
        if (product.getFeatures() != null) {
            String featuresStr = String.join(" ", product.getFeatures()).toLowerCase();
            
            // Performance parsing
            if (featuresStr.contains("32gb") || featuresStr.contains("16gb") || featuresStr.contains("i7") || featuresStr.contains("ryzen 7") || featuresStr.contains("i9") || featuresStr.contains("rtx")) {
                performance = "HIGH";
            } else if (featuresStr.contains("8gb") || featuresStr.contains("i5") || featuresStr.contains("ryzen 5")) {
                performance = "MEDIUM";
            } else if (featuresStr.contains("4gb") || featuresStr.contains("i3") || featuresStr.contains("celeron")) {
                performance = "LOW";
            }
            
            // Portability parsing
            if (featuresStr.contains("1.1kg") || featuresStr.contains("1.2kg") || featuresStr.contains("1.3kg") || (product.getName() != null && (product.getName().toLowerCase().contains("air") || product.getName().toLowerCase().contains("lite")))) {
                portability = "HIGH";
            } else if (featuresStr.contains("1.4kg") || featuresStr.contains("1.5kg") || featuresStr.contains("1.6kg")) {
                portability = "MEDIUM";
            } else if (featuresStr.contains("1.7kg") || featuresStr.contains("2.0kg") || featuresStr.contains("gaming")) {
                portability = "LOW";
            }
        }
        
        List<String> useCases = product.getUseCases() != null ? product.getUseCases() : Collections.emptyList();
        
        return ProductCapability.builder()
                .productId(product.getProductId())
                .performance(performance)
                .portability(portability)
                .useCases(useCases)
                .build();
    }
}
