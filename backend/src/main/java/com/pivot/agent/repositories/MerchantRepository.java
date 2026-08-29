package com.pivot.agent.repositories;

import com.pivot.agent.models.Merchant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantRepository extends MongoRepository<Merchant, String> {
}
