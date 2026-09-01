package com.pivot.agent.controllers;

import com.pivot.agent.models.AgentDecision;
import com.pivot.agent.models.Order;
import com.pivot.agent.repositories.AgentDecisionRepository;
import com.pivot.agent.repositories.OrderRepository;
import com.pivot.agent.services.RazorpayService;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final AgentDecisionRepository agentDecisionRepository;
    private final OrderRepository orderRepository;
    private final RazorpayService razorpayService;

    public record CreateOrderRequest(String decisionId) {}

    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            AgentDecision decision = agentDecisionRepository.findById(request.decisionId())
                    .orElseThrow(() -> new RuntimeException("Decision not found"));

            AgentDecision.Candidate selected = decision.getSelectedAction();
            if (selected == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No selected action found for this decision"));
            }

            String orderReceipt = "receipt_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Call Razorpay API
            String razorpayOrderId = razorpayService.createOrder(selected.getFinalAmount(), orderReceipt);

            // Save our local Order
            Order order = Order.builder()
                    .orderId(orderReceipt)
                    .razorpayOrderId(razorpayOrderId)
                    .productId(selected.getProductId())
                    .customerInput(decision.getCustomerInput())
                    .amount(selected.getAmount())
                    .discount(selected.getDiscount())
                    .finalAmount(selected.getFinalAmount())
                    .paymentStatus("PENDING")
                    .build();
            
            orderRepository.save(order);

            return ResponseEntity.ok(Map.of(
                    "razorpayOrderId", razorpayOrderId,
                    "orderId", order.getOrderId(),
                    "finalAmount", selected.getFinalAmount(),
                    "productId", selected.getProductId()
            ));

        } catch (RazorpayException e) {
            log.error("Error creating Razorpay order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create order with payment gateway"));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        
        log.info("Received Razorpay webhook");
        
        if (!razorpayService.verifyWebhookSignature(payload, signature)) {
            log.error("Webhook signature verification failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        try {
            JSONObject jsonPayload = new JSONObject(payload);
            String event = jsonPayload.getString("event");
            
            if ("order.paid".equals(event) || "payment.captured".equals(event)) {
                JSONObject entity = jsonPayload.getJSONObject("payload").getJSONObject(event.equals("order.paid") ? "order" : "payment").getJSONObject("entity");
                String rzpOrderId = entity.getString("order_id");
                
                Order order = orderRepository.findByRazorpayOrderId(rzpOrderId);
                if (order != null) {
                    order.setPaymentStatus("SUCCESS");
                    orderRepository.save(order);
                    log.info("Order {} marked as SUCCESS", rzpOrderId);
                } else {
                    log.warn("Order not found for razorpayOrderId: {}", rzpOrderId);
                }
            }
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Error processing webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }
}
