package com.pivot.agent.services;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RazorpayService {

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayService(
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret,
            @Value("${razorpay.webhook.secret}") String webhookSecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    public String createOrder(double amount, String receiptId) throws RazorpayException {
        RazorpayClient razorpay = new RazorpayClient(keyId, keySecret);

        JSONObject orderRequest = new JSONObject();
        // Razorpay expects amount in paise (multiply by 100)
        int amountInPaise = (int) Math.round(amount * 100);
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", receiptId);

        Order order = razorpay.orders.create(orderRequest);
        return order.get("id");
    }

    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            log.error("Failed to verify Razorpay webhook signature", e);
            return false;
        }
    }
}
