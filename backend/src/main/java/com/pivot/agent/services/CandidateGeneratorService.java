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

    private static final int MAX_BASE_CANDIDATES = 40;

    private final ProductRepository productRepository;

    public List<ActionCandidate> generateCandidates(ExtractedState state, Merchant merchant) {
        List<Product> catalog = productRepository.findAll();
        List<Product> products = catalog.stream()
                .filter(product -> product != null && product.getInventory() > 0)
                .filter(product -> state.category() == null || matchesCategory(product, state.category()))
                .filter(product -> state.budget() == null || product.getPrice() <= state.budget() * 1.5)
                .sorted(java.util.Comparator.comparing(Product::getPrice))
                .limit(MAX_BASE_CANDIDATES)
                .toList();

        List<ActionCandidate> candidates = new ArrayList<>();
        java.util.Map<String, Product> productById = catalog.stream()
                .filter(product -> product != null && product.getProductId() != null)
                .collect(java.util.stream.Collectors.toMap(Product::getProductId, product -> product, (left, right) -> left));

        for (Product product : products) {
            candidates.add(ActionCandidate.builder()
                    .actionName("Buy " + product.getName())
                    .baseProduct(product)
                    .bundledProducts(List.of())
                    .discountPercent(0.0)
                    .type("BASE")
                    .status("CONSIDERED")
                    .build());

            if (product.getCrossSell() != null) {
                for (String crossId : product.getCrossSell()) {
                    Product crossProd = productById.get(crossId);
                    if (crossProd != null && crossProd.getInventory() > 0) {
                        candidates.add(ActionCandidate.builder()
                                .actionName("Buy " + product.getName() + " + " + crossProd.getName())
                                .baseProduct(product)
                                .bundledProducts(List.of(crossProd))
                                .discountPercent(0.0)
                                .type("BUNDLE")
                                .status("CONSIDERED")
                                .build());
                    }
                }
            }

            if (state.requestedItems() != null && !state.requestedItems().isEmpty()) {
                List<Product> dynamicBundle = new ArrayList<>();
                for (String reqItem : state.requestedItems()) {
                    catalog.stream()
                            .filter(p -> p != null && p.getInventory() > 0)
                            .filter(p -> !p.getProductId().equals(product.getProductId()))
                            .filter(p -> state.category() == null || !state.category().equalsIgnoreCase(p.getCategory()))
                            .filter(p -> matchesRequestedItem(p, reqItem))
                            .findFirst()
                            .ifPresent(dynamicBundle::add);
                }

                if (!dynamicBundle.isEmpty()) {
                    List<Product> uniqueBundleProducts = dynamicBundle.stream().distinct().toList();
                    StringBuilder bundleName = new StringBuilder("Buy " + product.getName());
                    for (Product bp : uniqueBundleProducts) {
                        bundleName.append(" + ").append(bp.getName());
                    }
                    candidates.add(ActionCandidate.builder()
                            .actionName(bundleName.toString())
                            .baseProduct(product)
                            .bundledProducts(uniqueBundleProducts)
                            .discountPercent(0.0)
                            .type("BUNDLE")
                            .status("CONSIDERED")
                            .build());
                }
            }

            if (product.getUpsell() != null) {
                for (String upId : product.getUpsell()) {
                    Product upProd = productById.get(upId);
                    if (upProd != null && upProd.getInventory() > 0) {
                        candidates.add(ActionCandidate.builder()
                                .actionName("Upgrade to " + upProd.getName())
                                .baseProduct(upProd)
                                .bundledProducts(List.of())
                                .discountPercent(0.0)
                                .type("UPGRADE")
                                .status("CONSIDERED")
                                .build());
                    }
                }
            }

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

        List<ActionCandidate> uniqueCandidates = new ArrayList<>();
        java.util.Set<String> seenSignatures = new java.util.HashSet<>();

        for (ActionCandidate c : candidates) {
            String bundleIds = "";
            if (c.getBundledProducts() != null && !c.getBundledProducts().isEmpty()) {
                bundleIds = c.getBundledProducts().stream()
                        .map(Product::getProductId)
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
            }
            String signature = c.getBaseProduct().getProductId() + ":" + c.getType() + ":" + bundleIds + ":" + c.getDiscountPercent();
            if (seenSignatures.add(signature)) {
                uniqueCandidates.add(c);
            }
        }

        return uniqueCandidates.stream()
                .limit(120)
                .toList();
    }

    private boolean matchesCategory(Product product, String requestedCategory) {
        if (product == null || product.getCategory() == null || requestedCategory == null) {
            return true;
        }
        String productCategory = product.getCategory().toLowerCase();
        String requested = requestedCategory.toLowerCase();
        return productCategory.contains(requested) || requested.contains(productCategory);
    }

    private boolean matchesRequestedItem(Product product, String requestedItem) {
        if (product == null || requestedItem == null) {
            return false;
        }
        String item = requestedItem.toLowerCase();
        return (product.getCategory() != null && product.getCategory().toLowerCase().contains(item))
                || (product.getName() != null && product.getName().toLowerCase().contains(item));
    }
}
