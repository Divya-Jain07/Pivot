package com.pivot.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivot.agent.dto.ActionCandidate;
import com.pivot.agent.models.AgentDecision;
import com.pivot.agent.models.ExtractedState;
import com.pivot.agent.models.Merchant;
import com.pivot.agent.models.Product;
import com.pivot.agent.models.ProductCapability;
import com.pivot.agent.repositories.AgentDecisionRepository;
import com.pivot.agent.repositories.MerchantRepository;
import com.pivot.agent.repositories.ProductRepository;
import com.pivot.agent.services.CandidateGeneratorService;
import com.pivot.agent.services.CustomerFitService;
import com.pivot.agent.services.DecisionEngineService;
import com.pivot.agent.services.DecisionPipelineService;
import com.pivot.agent.services.PolicyEngineService;
import com.pivot.agent.services.ProductCapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacySeedCharacterizationTest {

    private ProductRepository productRepository;
    private MerchantRepository merchantRepository;
    private AgentDecisionRepository agentDecisionRepository;

    private static final ObjectMapper mapper = new ObjectMapper();
    private static List<Product> legacyProducts;
    private static Merchant legacyMerchant;

    @BeforeEach
    void setUp() throws Exception {
        productRepository = mock(ProductRepository.class);
        merchantRepository = mock(MerchantRepository.class);
        agentDecisionRepository = mock(AgentDecisionRepository.class);

        SeedData seed = mapper.readValue(loadResource("legacy-dataseed.json"), SeedData.class);
        legacyProducts = seed.products;
        legacyMerchant = seed.merchant;

        when(merchantRepository.findAll()).thenReturn(List.of(legacyMerchant));
        when(productRepository.findAll()).thenReturn(legacyProducts);
        when(productRepository.findByCategory("laptop")).thenReturn(
                legacyProducts.stream().filter(p -> "laptop".equalsIgnoreCase(p.getCategory())).toList());
        when(productRepository.findByCategory("accessory")).thenReturn(
                legacyProducts.stream().filter(p -> "accessory".equalsIgnoreCase(p.getCategory())).toList());
        for (Product product : legacyProducts) {
            when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));
        }
        when(agentDecisionRepository.save(any(AgentDecision.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static InputStream loadResource(String name) {
        return LegacySeedCharacterizationTest.class.getClassLoader().getResourceAsStream(name);
    }

    @Test
    void legacySeedLoadsAndContainsTheCurrentCatalog() {
        assertNotNull(legacyProducts);
        assertEquals(22, legacyProducts.size());
        assertEquals(12, legacyProducts.stream().filter(p -> "laptop".equalsIgnoreCase(p.getCategory())).count());
        assertEquals(10, legacyProducts.stream().filter(p -> "accessory".equalsIgnoreCase(p.getCategory())).count());
        assertNotNull(legacyMerchant);
        assertEquals(12.0, legacyMerchant.getMinMarginPercent());
        assertEquals(10.0, legacyMerchant.getMaxDiscountPercent());
    }

    @Test
    void policyEngineFreezesLegacyRejectionsForOutOfStockDiscountAndMargin() {
        PolicyEngineService policyEngineService = new PolicyEngineService();
        Merchant merchant = legacyMerchant;

        Product outOfStock = legacyProducts.stream().filter(p -> "p6".equals(p.getProductId())).findFirst().orElseThrow();
        outOfStock.setInventory(0);
        ActionCandidate outOfStockCandidate = ActionCandidate.builder()
                .actionName("Buy " + outOfStock.getName())
                .baseProduct(outOfStock)
                .bundledProducts(List.of())
                .discountPercent(0.0)
                .type("BASE")
                .build();

        List<ActionCandidate> stockResult = policyEngineService.enforcePolicy(List.of(outOfStockCandidate), merchant, baseState());
        assertTrue(stockResult.isEmpty());
        assertEquals("Product is out of stock.", outOfStockCandidate.getRejectionReason());

        Product discountProduct = legacyProducts.stream().filter(p -> "p3".equals(p.getProductId())).findFirst().orElseThrow();
        ActionCandidate discountCandidate = ActionCandidate.builder()
                .actionName("Buy " + discountProduct.getName() + " with 7% discount")
                .baseProduct(discountProduct)
                .bundledProducts(List.of())
                .discountPercent(7.0)
                .type("DISCOUNT")
                .build();
        List<ActionCandidate> invalidDiscountResult = policyEngineService.enforcePolicy(List.of(discountCandidate), merchant, baseState());
        assertTrue(invalidDiscountResult.isEmpty());
        assertEquals("7.0% discount is not a valid discount step.", discountCandidate.getRejectionReason());

        Product marginProduct = legacyProducts.stream().filter(p -> "p1".equals(p.getProductId())).findFirst().orElseThrow();
        ActionCandidate marginCandidate = ActionCandidate.builder()
                .actionName("Buy " + marginProduct.getName() + " with 10% discount")
                .baseProduct(marginProduct)
                .bundledProducts(List.of())
                .discountPercent(10.0)
                .type("DISCOUNT")
                .build();
        List<ActionCandidate> marginResult = policyEngineService.enforcePolicy(List.of(marginCandidate), merchant, baseState());
        assertTrue(marginResult.isEmpty());
        assertTrue(marginCandidate.getRejectionReason() != null && marginCandidate.getRejectionReason().contains("Margin of"));
    }

    @Test
    void decisionEngineUsesCurrentScoringAndBudgetRules() {
        DecisionEngineService decisionEngineService = new DecisionEngineService(
                new CustomerFitService(),
                new ProductCapabilityService());

        Product productA = legacyProducts.stream().filter(p -> "p1".equals(p.getProductId())).findFirst().orElseThrow();
        Product productB = legacyProducts.stream().filter(p -> "p2".equals(p.getProductId())).findFirst().orElseThrow();
        ActionCandidate matchA = ActionCandidate.builder().actionName("Buy " + productA.getName()).baseProduct(productA).bundledProducts(List.of()).discountPercent(0.0).type("BASE").build();
        ActionCandidate matchB = ActionCandidate.builder().actionName("Buy " + productB.getName()).baseProduct(productB).bundledProducts(List.of()).discountPercent(0.0).type("BASE").build();

        ExtractedState state = baseState();
        ActionCandidate winner = decisionEngineService.selectBestCandidate(List.of(matchA, matchB), state, legacyMerchant);
        assertNotNull(winner);
        assertEquals("Buy " + productA.getName(), winner.getActionName());

        ExtractedState strictBudgetState = new ExtractedState(
                "reason",
                "laptop",
                60000.0,
                true,
                List.of("business"),
                "business",
                Map.of("performance", "MEDIUM", "portability", "HIGH"),
                List.of(),
                "medium",
                List.of(),
                List.of(),
                null,
                "DISCOVERY",
                false);

        Product productBudget = legacyProducts.stream().filter(p -> "p8".equals(p.getProductId())).findFirst().orElseThrow();
        ActionCandidate budgetCandidate = ActionCandidate.builder().actionName("Buy " + productBudget.getName()).baseProduct(productBudget).bundledProducts(List.of()).discountPercent(0.0).type("BASE").build();
        decisionEngineService.selectBestCandidate(List.of(budgetCandidate), strictBudgetState, legacyMerchant);
        assertNotNull(budgetCandidate.getFinalScore());
    }

    @Test
    void customerFitAndCapabilityUseCurrentLegacyRules() {
        ProductCapabilityService capabilityService = new ProductCapabilityService();
        ProductCapability p6Capability = capabilityService.evaluate(legacyProducts.stream().filter(p -> "p6".equals(p.getProductId())).findFirst().orElseThrow());
        assertEquals("MEDIUM", p6Capability.getPerformance());
        assertEquals("HIGH", p6Capability.getPortability());

        ExtractedState state = new ExtractedState(
                "student",
                "laptop",
                50000.0,
                false,
                List.of("student", "browsing"),
                "student",
                Map.of("performance", "MEDIUM", "portability", "HIGH"),
                List.of(),
                "medium",
                List.of(),
                List.of(),
                null,
                "DISCOVERY",
                false);

        ProductCapability capability = capabilityService.evaluate(legacyProducts.stream().filter(p -> "p1".equals(p.getProductId())).findFirst().orElseThrow());
        double fit = new CustomerFitService().calculateFit(state, capability);
        assertTrue(fit > 0.0);
        assertTrue(fit <= 100.0);
    }

    @Test
    void productSupportsStructuredLaptopFields() {
        Product laptop = Product.builder()
                .productId("p-structured")
                .name("Structured Laptop")
                .category("laptop")
                .price(65000)
                .cost(52000)
                .inventory(4)
                .features(List.of("Intel Core i5", "16GB RAM", "512GB SSD", "14-inch display"))
                .useCases(List.of("student"))
                .tags(List.of("mid-range"))
                .crossSell(List.of())
                .upsell(List.of())
                .ramGb(16)
                .storageGb(512)
                .weightKg(1.4)
                .screenSizeInch(14.0)
                .cpuTier("medium")
                .dedicatedGpu(false)
                .userRating(4.5)
                .ratingCount(150)
                .batteryWh(58.8)
                .build();

        assertEquals(16, laptop.getRamGb());
        assertEquals(512, laptop.getStorageGb());
        assertEquals(1.4, laptop.getWeightKg());
        assertEquals(14.0, laptop.getScreenSizeInch());
        assertEquals("medium", laptop.getCpuTier());
        assertFalse(laptop.getDedicatedGpu());
        assertEquals(4.5, laptop.getUserRating());
        assertEquals(150, laptop.getRatingCount());
        assertEquals(58.8, laptop.getBatteryWh());
    }

    @Test
    void capabilityServiceUsesStructuredFieldsAndCanonicalVocabulary() {
        ProductCapabilityService capabilityService = new ProductCapabilityService();

        Product gamingLaptop = Product.builder()
                .productId("p-gaming")
                .name("Gaming Laptop")
                .category("laptop")
                .price(95000)
                .cost(76000)
                .inventory(7)
                .ramGb(32)
                .storageGb(1024)
                .weightKg(1.2)
                .screenSizeInch(15.6)
                .cpuTier("high")
                .dedicatedGpu(true)
                .userRating(4.7)
                .ratingCount(200)
                .batteryWh(82.0)
                .useCases(List.of("gaming", "programming", "video editing"))
                .build();

        ProductCapability gamingCapability = capabilityService.evaluate(gamingLaptop);
        assertEquals("HIGH", gamingCapability.getPerformance());
        assertEquals("HIGH", gamingCapability.getPortability());
        assertTrue(ProductCapabilityService.getAllowedUseCases().containsAll(gamingCapability.getUseCases()));

        List<String> normalized = ProductCapabilityService.normalizeUseCases(List.of("gaming", "student", "not-valid", "programming"));
        assertEquals(List.of("gaming", "student", "programming"), normalized);
    }

        @Test
        void explicitTouchscreenPreferenceImprovesCustomerFitAndRequiredTouchscreenIsEnforced() {
        Product touchscreenLaptop = Product.builder()
            .productId("touchscreen-laptop")
            .name("Touchscreen Laptop")
            .category("laptop")
            .price(50000)
            .cost(40000)
            .inventory(5)
            .touchScreen(true)
            .ramGb(16)
            .storageGb(512)
            .batteryWh(60.0)
            .useCases(List.of("student"))
            .build();
        Product standardLaptop = Product.builder()
            .productId("standard-laptop")
            .name("Standard Laptop")
            .category("laptop")
            .price(50000)
            .cost(40000)
            .inventory(5)
            .touchScreen(false)
            .ramGb(16)
            .storageGb(512)
            .batteryWh(60.0)
            .useCases(List.of("student"))
            .build();
        ExtractedState preferenceState = new ExtractedState(
            "touchscreen preferred", "laptop", 50000.0, true, List.of("student"), "student",
            Map.of("touchscreen", "HIGH"), List.of(), "medium", List.of(), List.of(), null, "LAPTOP_RECOMMENDATION", false);
        CustomerFitService customerFitService = new CustomerFitService();
        ProductCapabilityService capabilityService = new ProductCapabilityService();

        double touchscreenFit = customerFitService.calculateFit(preferenceState, capabilityService.evaluate(touchscreenLaptop));
        double standardFit = customerFitService.calculateFit(preferenceState, capabilityService.evaluate(standardLaptop));
        assertEquals(35.0, touchscreenFit - standardFit);

        ExtractedState requiredState = new ExtractedState(
            "touchscreen required", "laptop", 50000.0, true, List.of("student"), "student",
            Map.of("touchscreen", "REQUIRED"), List.of(), "medium", List.of(), List.of(), null, "LAPTOP_RECOMMENDATION", false);
        ActionCandidate standardCandidate = ActionCandidate.builder()
            .actionName("Buy Standard Laptop")
            .baseProduct(standardLaptop)
            .bundledProducts(List.of())
            .discountPercent(0.0)
            .type("BASE")
            .build();

        assertTrue(new PolicyEngineService().enforcePolicy(List.of(standardCandidate), legacyMerchant, requiredState).isEmpty());
        assertEquals("Required touchscreen capability is unavailable or unverified.", standardCandidate.getRejectionReason());
        }

        @Test
        void candidateGenerationUsesStrictBudgetAndFlexibleTenThousandTolerance() {
        Product laptop = Product.builder()
            .productId("budget-laptop")
            .name("Budget Laptop")
            .category("laptop")
                .price(55000)
                .cost(40000)
            .inventory(5)
            .build();
        when(productRepository.findAll()).thenReturn(List.of(laptop));
        CandidateGeneratorService candidateGeneratorService = new CandidateGeneratorService(productRepository);

        ExtractedState flexibleState = new ExtractedState(
            "flexible", "laptop", 50000.0, false, List.of("business"), "business",
            Map.of(), List.of(), "medium", List.of(), List.of(), null, "LAPTOP_RECOMMENDATION", false);
        ExtractedState strictState = new ExtractedState(
            "strict", "laptop", 50000.0, true, List.of("business"), "business",
            Map.of(), List.of(), "medium", List.of(), List.of(), null, "LAPTOP_RECOMMENDATION", false);

        List<ActionCandidate> flexibleCandidates = candidateGeneratorService.generateCandidates(flexibleState, legacyMerchant);
        assertTrue(flexibleCandidates.stream()
            .anyMatch(candidate -> "budget-laptop".equals(candidate.getBaseProduct().getProductId())));
        List<ActionCandidate> strictCandidates = candidateGeneratorService.generateCandidates(strictState, legacyMerchant);
        assertTrue(strictCandidates.stream().anyMatch(candidate -> candidate.getDiscountPercent() == 10.0));
        List<ActionCandidate> strictPassed = new PolicyEngineService().enforcePolicy(strictCandidates, legacyMerchant, strictState);
        assertFalse(strictPassed.isEmpty());
        assertTrue(strictPassed.stream().allMatch(candidate -> candidate.getDiscountPercent() > 0.0
            && candidate.getBaseProduct().getPrice() * (1.0 - candidate.getDiscountPercent() / 100.0) <= strictState.budget()));
        }

        @Test
        void candidateLimitRetainsProductsMatchingTouchscreenPreference() {
        List<Product> catalog = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            catalog.add(Product.builder()
                .productId("cheap-" + index)
                .name("Cheap Laptop " + index)
                .category("laptop")
                .price(30000 + index * 500)
                .cost(25000)
                .inventory(5)
                .touchScreen(false)
                .build());
        }
        Product touchscreenProduct = Product.builder()
            .productId("touchscreen-58999")
            .name("Touchscreen Laptop")
            .category("laptop")
            .price(58999)
            .cost(48000)
            .inventory(5)
            .touchScreen(true)
            .build();
        catalog.add(touchscreenProduct);
        when(productRepository.findAll()).thenReturn(catalog);
        ExtractedState state = new ExtractedState(
            "touchscreen preferred", "laptop", 60000.0, true, List.of("student"), "student",
            Map.of("touchscreen", "HIGH"), List.of(), "high", List.of(), List.of(), null,
            "LAPTOP_RECOMMENDATION", false);

        List<ActionCandidate> candidates = new CandidateGeneratorService(productRepository)
            .generateCandidates(state, legacyMerchant);

        assertTrue(candidates.stream().filter(candidate -> "BASE".equals(candidate.getType()))
            .anyMatch(candidate -> "touchscreen-58999".equals(candidate.getBaseProduct().getProductId())));
        }

        @Test
        void highTouchscreenPreferenceCanOutrankCheaperNonTouchscreenLaptop() {
        Product cheapLaptop = Product.builder()
            .productId("cheap-non-touchscreen")
            .name("Cheap Laptop")
            .category("laptop")
            .price(25990)
            .cost(20000)
            .inventory(5)
            .touchScreen(false)
            .useCases(List.of("student"))
            .tags(List.of("clear_overstock"))
            .build();
        Product touchscreenLaptop = Product.builder()
            .productId("touchscreen-under-budget")
            .name("Touchscreen Laptop")
            .category("laptop")
            .price(58999)
            .cost(45329)
            .inventory(5)
            .touchScreen(true)
            .useCases(List.of("student"))
            .tags(List.of("clear_overstock"))
            .build();
        ExtractedState state = new ExtractedState(
            "touchscreen preferred", "laptop", 60000.0, true, List.of("student"), "student",
            Map.of("touchscreen", "HIGH"), List.of(), "high", List.of(), List.of(), null,
            "LAPTOP_RECOMMENDATION", false);
        ActionCandidate cheapCandidate = ActionCandidate.builder()
            .actionName("Buy Cheap Laptop").baseProduct(cheapLaptop).bundledProducts(List.of())
            .discountPercent(0.0).type("BASE").build();
        ActionCandidate touchscreenCandidate = ActionCandidate.builder()
            .actionName("Buy Touchscreen Laptop").baseProduct(touchscreenLaptop).bundledProducts(List.of())
            .discountPercent(0.0).type("BASE").build();

        ActionCandidate winner = new DecisionEngineService(new CustomerFitService(), new ProductCapabilityService())
            .selectBestCandidate(List.of(cheapCandidate, touchscreenCandidate), state, legacyMerchant);

        assertEquals("touchscreen-under-budget", winner.getBaseProduct().getProductId());
        }

        @Test
        void generatedCatalogRecommendsAvailableTouchscreenForStudentUnderBudget() throws Exception {
        SeedData seed = mapper.readValue(loadResource("dataseed.json"), SeedData.class);
        when(productRepository.findAll()).thenReturn(seed.products);
        when(merchantRepository.findAll()).thenReturn(List.of(seed.merchant));
        ExtractedState state = new ExtractedState(
            "student wants touchscreen", "laptop", 60000.0, true, List.of("student"), "student",
            Map.of("touchscreen", "HIGH"), List.of(), "high", List.of(), List.of(), null,
            "LAPTOP_RECOMMENDATION", false);
        DecisionPipelineService pipelineService = new DecisionPipelineService(
            new CandidateGeneratorService(productRepository),
            new PolicyEngineService(),
            new DecisionEngineService(new CustomerFitService(), new ProductCapabilityService()),
            merchantRepository,
            agentDecisionRepository);

        AgentDecision decision = pipelineService.runPipeline("touchscreen under 60000 for studying", state);

        assertNotNull(decision.getSelectedAction());
        assertTrue(decision.getSelectedAction().getReasons().contains("Has the touchscreen you prefer"));
        Product selected = seed.products.stream()
            .filter(product -> product.getProductId().equals(decision.getSelectedAction().getProductId()))
            .findFirst()
            .orElseThrow();
        assertEquals(Boolean.TRUE, selected.getTouchScreen());
        assertTrue(decision.getSelectedAction().getFinalAmount() <= state.budget());
        }

    @Test
    void candidateGeneratorAndPipelineFreezeCurrentLegacyBehavior() {
        CandidateGeneratorService candidateGeneratorService = new CandidateGeneratorService(productRepository);
        List<ActionCandidate> candidates = candidateGeneratorService.generateCandidates(baseState(), legacyMerchant);
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().anyMatch(c -> "BASE".equals(c.getType())));

        DecisionPipelineService pipelineService = new DecisionPipelineService(
                candidateGeneratorService,
                new PolicyEngineService(),
                new DecisionEngineService(new CustomerFitService(), new ProductCapabilityService()),
                merchantRepository,
                agentDecisionRepository);

        ExtractedState studentState = new ExtractedState(
                "student",
                "laptop",
                50000.0,
                false,
                List.of("student", "browsing"),
                "student",
                Map.of("performance", "MEDIUM", "portability", "HIGH"),
                List.of(),
                "medium",
                List.of(),
                List.of(),
                null,
                "DISCOVERY",
                false);

        AgentDecision decision = pipelineService.runPipeline("student laptop", studentState);
        assertNotNull(decision);
        assertNull(decision.getSelectedAction());
        assertTrue(decision.getCandidates().size() >= 1);
        assertFalse(decision.getCandidates().get(0).getReasons().isEmpty());
    }

    @Test
    void pipelineAsksForBudgetBeforeSelectingWhenBudgetIsMissing() {
        CandidateGeneratorService candidateGeneratorService = new CandidateGeneratorService(productRepository);
        DecisionPipelineService pipelineService = new DecisionPipelineService(
                candidateGeneratorService,
                new PolicyEngineService(),
                new DecisionEngineService(new CustomerFitService(), new ProductCapabilityService()),
                merchantRepository,
                agentDecisionRepository);
        ExtractedState noBudgetState = new ExtractedState(
                "programming laptop", "laptop", null, null, List.of("programming"), "programming",
                Map.of(), List.of(), "medium", List.of(), List.of(), null, "LAPTOP_RECOMMENDATION", false);

        AgentDecision decision = pipelineService.runPipeline("I need a laptop for programming", noBudgetState);

        assertNull(decision.getSelectedAction());
        assertTrue(decision.getCandidates().stream().allMatch(candidate -> "CONSIDERED".equals(candidate.getStatus())));

        ExtractedState noBudgetLimitState = new ExtractedState(
            "no budget limit", "laptop", null, false, List.of("programming"), "programming",
            Map.of(), List.of(), "medium", List.of(), List.of(), null, "LAPTOP_RECOMMENDATION", false);
        AgentDecision flexibleDecision = pipelineService.runPipeline("I have no fixed budget", noBudgetLimitState);
        assertNotNull(flexibleDecision.getSelectedAction());
    }

    private ExtractedState baseState() {
        return new ExtractedState(
                "buy a laptop",
                "laptop",
                50000.0,
                false,
                List.of("student", "browsing"),
                "student",
                new HashMap<>(Map.of("performance", "MEDIUM", "portability", "HIGH")),
                List.of(),
                "medium",
                List.of(),
                List.of(),
                null,
                "DISCOVERY",
                false);
    }

    static class SeedData {
        public Merchant merchant;
        public List<Product> products;
    }
}
