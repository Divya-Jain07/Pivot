package com.pivot.agent.repositories;

import com.pivot.agent.models.AgentDecision;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentDecisionRepository extends MongoRepository<AgentDecision, String> {
}
