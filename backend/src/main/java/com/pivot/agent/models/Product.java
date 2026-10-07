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
@Document(collection = "products")
public class Product {
    @Id
    private String productId;
    private String name;
    private String category;
    private double price;
    private double cost;
    private int inventory;
    private Integer ramGb;
    private Integer storageGb;
    private Double weightKg;
    private Double screenSizeInch;
    private String cpuTier;
    private Boolean dedicatedGpu;
    private Boolean touchScreen;
    private Double userRating;
    private Integer ratingCount;
    private Double batteryWh;
    private List<String> features;
    private List<String> useCases;
    private List<String> tags;
    private List<String> crossSell;
    private List<String> upsell;
}
