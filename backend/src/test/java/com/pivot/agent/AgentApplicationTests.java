package com.pivot.agent;

import com.pivot.agent.controllers.ChatController;
import com.pivot.agent.models.ExtractedState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AgentApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void compactSummaryKeepsLatestMessageAndDropsRawHistory() {
		ExtractedState state = new ExtractedState(
				"reasoning",
				"laptop",
				70000.0,
				true,
				List.of("programming", "travel"),
				"programming",
				Map.of("performance", "HIGH", "portability", "HIGH"),
				List.of("no bundle"),
				"medium",
				List.of("mouse"),
				List.of(),
				null,
				"LAPTOP_RECOMMENDATION",
				false
		);

		String summary = ChatController.buildCompactSummary(state, "Need a lightweight coding laptop under 70k");

		assertTrue(summary.contains("Latest user message: Need a lightweight coding laptop under 70k"));
		assertTrue(summary.contains("Use cases: programming, travel"));
		assertTrue(summary.contains("Decision stage: LAPTOP_RECOMMENDATION"));
		assertTrue(summary.length() < 500);
	}

}
