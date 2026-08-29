package com.pivot.agent.repositories;

import com.pivot.agent.models.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends MongoRepository<Order, String> {
    Order findByRazorpayOrderId(String razorpayOrderId);
}
