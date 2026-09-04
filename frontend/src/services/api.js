export const api = {
  async extractIntent(sessionId, message) {
    const response = await fetch('/api/chat/extract', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ sessionId, message })
    });
    
    if (!response.ok) {
      throw new Error(`Failed to extract intent: ${response.statusText}`);
    }
    
    return response.json();
  },

  async respondDecision(sessionId, decisionId) {
    const response = await fetch('/api/chat/respond-decision', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ sessionId, decisionId })
    });

    if (!response.ok) {
      throw new Error(`Failed to get response: ${response.statusText}`);
    }

    // Backend returns a plain string, so we use text()
    return response.text();
  }
};
