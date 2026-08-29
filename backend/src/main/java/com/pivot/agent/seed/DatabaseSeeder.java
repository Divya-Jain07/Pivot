package com.pivot.agent.seed;

import com.pivot.agent.models.Merchant;
import com.pivot.agent.models.Product;
import com.pivot.agent.repositories.MerchantRepository;
import com.pivot.agent.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final ProductRepository productRepository;
    private final MerchantRepository merchantRepository;

    @org.springframework.beans.factory.annotation.Value("${spring.data.mongodb.uri:NOT_FOUND}")
    private String mongoUri;

    public DatabaseSeeder(ProductRepository productRepository, MerchantRepository merchantRepository) {
        this.productRepository = productRepository;
        this.merchantRepository = merchantRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing database with seed data...");
        logger.info("Loaded Mongo URI: {}", mongoUri);

        // Clear existing data for a clean start on each run (optional, useful for testing)
        productRepository.deleteAll();
        merchantRepository.deleteAll();

        seedProducts();
        seedMerchant();

        logger.info("Database seeding completed!");
        logger.info("Products count: {}", productRepository.count());
        logger.info("Merchants count: {}", merchantRepository.count());
    }

    private void seedProducts() {
        // Cross-sell items
        Product mouse = Product.builder()
                .productId("PROD-M1")
                .name("Ergo Wireless Mouse")
                .category("accessories")
                .price(2000.0)
                .cost(1000.0)
                .inventory(50)
                .features(List.of("wireless", "ergonomic", "optical"))
                .useCases(List.of("productivity", "travel"))
                .tags(List.of("accessory"))
                .build();

        Product laptopBag = Product.builder()
                .productId("PROD-B1")
                .name("Premium Laptop Backpack")
                .category("accessories")
                .price(3500.0)
                .cost(1500.0)
                .inventory(30)
                .features(List.of("waterproof", "15-inch fit", "padded"))
                .useCases(List.of("travel", "college", "work"))
                .tags(List.of("accessory", "slowMoving:true")) // Tagged for strategic bonus
                .build();

        Product usbHub = Product.builder()
                .productId("PROD-U1")
                .name("Type-C 7-in-1 Hub")
                .category("accessories")
                .price(2500.0)
                .cost(1200.0)
                .inventory(100)
                .features(List.of("type-c", "hdmi", "usb3.0"))
                .useCases(List.of("productivity", "macbook"))
                .tags(List.of("accessory"))
                .build();

        productRepository.saveAll(Arrays.asList(mouse, laptopBag, usbHub));

        // Laptops
        Product entryLaptop = Product.builder()
                .productId("PROD-L1")
                .name("BasicBook 14")
                .category("laptops")
                .price(40000.0)
                .cost(32000.0)
                .inventory(20)
                .features(List.of("intel i3", "8gb ram", "256gb ssd"))
                .useCases(List.of("student", "browsing", "light work"))
                .tags(List.of("entry-level"))
                .crossSell(List.of("PROD-M1", "PROD-B1"))
                .upsell(List.of("PROD-L2"))
                .build();

        Product upgradeLaptop = Product.builder() // Small price gap upgrade
                .productId("PROD-L2")
                .name("BasicBook 14 Plus")
                .category("laptops")
                .price(43000.0)
                .cost(33000.0)
                .inventory(15)
                .features(List.of("intel i5", "8gb ram", "512gb ssd"))
                .useCases(List.of("student", "productivity", "multitasking"))
                .tags(List.of("entry-level", "value-pick"))
                .crossSell(List.of("PROD-M1", "PROD-B1"))
                .upsell(List.of("PROD-L3"))
                .build();

        Product midLaptop = Product.builder()
                .productId("PROD-L3")
                .name("ProBook 15")
                .category("laptops")
                .price(60000.0)
                .cost(48000.0)
                .inventory(25)
                .features(List.of("ryzen 5", "16gb ram", "512gb ssd"))
                .useCases(List.of("programming", "business", "heavy multitasking"))
                .tags(List.of("mid-range", "popular"))
                .crossSell(List.of("PROD-M1", "PROD-B1", "PROD-U1"))
                .upsell(List.of("PROD-L4"))
                .build();

        Product proLaptop = Product.builder()
                .productId("PROD-L4")
                .name("UltraBook Pro 15")
                .category("laptops")
                .price(85000.0)
                .cost(70000.0)
                .inventory(10)
                .features(List.of("intel i7", "16gb ram", "1tb ssd", "dedicated gpu"))
                .useCases(List.of("programming", "video editing", "gaming"))
                .tags(List.of("high-end", "creator"))
                .crossSell(List.of("PROD-M1", "PROD-B1", "PROD-U1"))
                .upsell(List.of("PROD-L5"))
                .build();

        Product premiumLaptop = Product.builder()
                .productId("PROD-L5")
                .name("DevMachine Max")
                .category("laptops")
                .price(150000.0)
                .cost(120000.0)
                .inventory(5)
                .features(List.of("m2 chip", "32gb ram", "1tb ssd"))
                .useCases(List.of("programming", "ai development", "professional"))
                .tags(List.of("premium", "developer"))
                .crossSell(List.of("PROD-U1", "PROD-B1"))
                .build();

        productRepository.saveAll(Arrays.asList(entryLaptop, upgradeLaptop, midLaptop, proLaptop, premiumLaptop));
    }

    private void seedMerchant() {
        Merchant merchant = Merchant.builder()
                .merchantId("MERCH-001")
                .minMarginPercent(15.0) // 15%
                .maxDiscountPercent(10.0) // 10%
                .discountSteps(List.of(0.0, 5.0, 10.0))
                .primaryObjective("Maximize profit while moving slow-moving inventory")
                .build();

        merchantRepository.save(merchant);
    }
}
