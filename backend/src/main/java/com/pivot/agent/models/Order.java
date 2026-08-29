package com.pivot.agent.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "orders")
public class Order {
    @Id
    private String orderId;
    private String razorpayOrderId;
    private String productId;
    private String customerInput;
    private double amount;
    private double discount;
    private double finalAmount;
    private String paymentStatus;
}
