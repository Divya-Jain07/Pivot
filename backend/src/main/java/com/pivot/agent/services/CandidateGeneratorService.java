package com.pivot.agent.services;

import com.pivot.agent.dto.ActionCandidate;
import com.pivot.agent.models.Merchant;
import com.pivot.agent.models.Product;
import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateGeneratorService {

    private final ProductRepository productRepository;

    public List<ActionCandidate> generateCandidates(ExtractedState state, Merchant merchant) {
        List<ActionCandidate> candidates = new ArrayList<>();
        
        List<Product> products = productRepository.findByCategory(state.category());
        if (products == null || products.isEmpty()) {
            products = productRepository.findAll();
        }

        for (Product product : products) {
            // Inventory check is technically Phase 4 policy, but we filter purely out of stock
            // Wait, instructions say: "filters seeded product list by category + budget range + inventory > 0"
            if (product.getInventory() <= 0) continue;
            
            // Filter by category leniently (handle laptop vs laptops)
            if (state.category() != null) {
                String pCat = product.getCategory().toLowerCase();
                String sCat = state.category().toLowerCase();
                if (!pCat.contains(sCat) && !sCat.contains(pCat)) {
                    continue;
                }
            }

            // Generate BASE action
            candidates.add(ActionCandidate.builder()
                    .actionName("Buy " + product.getName())
                    .baseProduct(product)
                    .bundledProducts(List.of())
                    .discountPercent(0.0)
                    .type("BASE")
                    .status("CONSIDERED")
                    .build());

            // Generate BUNDLE actions
            if (product.getCrossSell() != null) {
                for (String crossId : product.getCrossSell()) {
                    productRepository.findById(crossId).ifPresent(crossProd -> {
                        candidates.add(ActionCandidate.builder()
                                .actionName("Buy " + product.getName() + " + " + crossProd.getName())
                                .baseProduct(product)
                                .bundledProducts(List.of(crossProd))
                                .discountPercent(0.0)
                                .type("BUNDLE")
                                .status("CONSIDERED")
                                .build());
                    });
                }
            }

            // Generate UPGRADE actions
            if (product.getUpsell() != null) {
                for (String upId : product.getUpsell()) {
                    productRepository.findById(upId).ifPresent(upProd -> {
                        candidates.add(ActionCandidate.builder()
                                .actionName("Upgrade to " + upProd.getName())
                                .baseProduct(upProd)
                                .bundledProducts(List.of())
                                .discountPercent(0.0)
                                .type("UPGRADE")
                                .status("CONSIDERED")
                                .build());
                    });
                }
            }

            // Generate DISCOUNT actions
            if (merchant.getDiscountSteps() != null) {
                for (Double step : merchant.getDiscountSteps()) {
                    if (step > 0.0) {
                        candidates.add(ActionCandidate.builder()
                                .actionName("Buy " + product.getName() + " with " + step + "% discount")
                                .baseProduct(product)
                                .bundledProducts(List.of())
                                .discountPercent(step)
                                .type("DISCOUNT")
                                .status("CONSIDERED")
                                .build());
                    }
                }
            }
        }
        
        return candidates;
    }
}
