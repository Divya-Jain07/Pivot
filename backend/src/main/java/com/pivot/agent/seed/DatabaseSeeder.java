package com.pivot.agent.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pivot.agent.models.Merchant;
import com.pivot.agent.models.Product;
import com.pivot.agent.repositories.MerchantRepository;
import com.pivot.agent.repositories.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final ProductRepository productRepository;
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${spring.data.mongodb.uri:NOT_FOUND}")
    private String mongoUri;

    public DatabaseSeeder(ProductRepository productRepository, MerchantRepository merchantRepository, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.merchantRepository = merchantRepository;
        this.objectMapper = objectMapper;
    }

    public static class SeedData {
        public Merchant merchant;
        public List<Product> products;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing database with seed data...");

        // Clear existing data for a clean start on each run
        productRepository.deleteAll();
        merchantRepository.deleteAll();

        try {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("dataseed.json");
            if (resource.exists()) {
                try (java.io.InputStream inputStream = resource.getInputStream()) {
                    SeedData data = objectMapper.readValue(inputStream, SeedData.class);
                    if (data.products != null) {
                        productRepository.saveAll(data.products);
                    }
                    if (data.merchant != null) {
                        merchantRepository.save(data.merchant);
                    }
                    logger.info("Successfully loaded data from classpath:dataseed.json");
                }
            } else {
                logger.warn("classpath:dataseed.json not found!");
            }
        } catch (Exception e) {
            logger.error("Error reading dataseed.json", e);
        }

        logger.info("Database seeding completed!");
        logger.info("Products count: {}", productRepository.count());
        logger.info("Merchants count: {}", merchantRepository.count());
    }
}
