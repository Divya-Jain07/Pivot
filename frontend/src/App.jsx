import React, { useState, useEffect } from 'react';
import './App.css';
import ChatBubble from './components/ChatBubble';
import MessageInput from './components/MessageInput';
import RecommendationCard from './components/RecommendationCard';
import TerminalLog from './components/TerminalLog';
import { api } from './services/api';

function App() {
  const [sessionId, setSessionId] = useState('');
  const [messages, setMessages] = useState([
    { isAi: true, message: "Hello! I'm PIVOT, your AI Commerce Agent. How can I help you find the perfect product today?" }
  ]);
  const [terminalLogs, setTerminalLogs] = useState([]);
  const [isTyping, setIsTyping] = useState(false);

  useEffect(() => {
    // Generate simple UUID for session
    setSessionId(crypto.randomUUID());
  }, []);

  const handleSendMessage = async (text) => {
    // 1. Add user message
    const newMessages = [...messages, { isAi: false, message: text }];
    setMessages(newMessages);
    setIsTyping(true);

    try {
      // 2. Extract Intent and get Pipeline Decision
      const decision = await api.extractIntent(sessionId, text);
      
      // Update Terminal Logs
      const newLogs = formatTerminalLogs(decision);
      setTerminalLogs(newLogs);

      // 3. Get AI Response
      const aiText = await api.respondDecision(sessionId, decision.decisionId);
      
      // Check if a product was selected to show recommendation card
      const selected = decision.candidates?.find(c => c.status === 'SELECTED');
      let productCard = null;
      if (selected) {
        productCard = {
          name: selected.action || 'Recommended Product',
          description: `Product ID: ${selected.productId}`,
          originalPrice: selected.amount,
          finalPrice: selected.finalAmount
        };
      }

      setMessages([...newMessages, { isAi: true, message: aiText, product: productCard }]);
    } catch (error) {
      console.error("API Error:", error);
      setMessages([...newMessages, { isAi: true, message: "Sorry, I encountered an error connecting to my internal engine." }]);
    } finally {
      setIsTyping(false);
    }
  };

  const formatTerminalLogs = (decision) => {
    const logs = [];
    
    // Step 1: Customer Understanding
    if (decision.extractedState) {
      const state = decision.extractedState;
      logs.push({
        step: "01. CUSTOMER UNDERSTANDING",
        content: [
          `timestamp         ${decision.timestamp || new Date().toISOString()}`,
          `budget            ~₹${state.budget || 0}`,
          `use_case          ${state.useCase || 'N/A'}`,
          `category          ${state.category || 'N/A'}`,
          `price_sensitivity ${state.priceSensitivity || 'N/A'}`
        ]
      });
    }

    // Step 2 & 3: Evaluation
    if (decision.candidates && decision.candidates.length > 0) {
      logs.push({
        step: "02. CANDIDATE RETRIEVAL",
        content: [`retrieved ${decision.candidates.length} products from catalog`]
      });

      const evalContent = ["id       product                    cust_fit  budget_fit  score  status"];
      
      decision.candidates.forEach(c => {
        const cFit = (c.factors?.customerFit || 0).toFixed(2);
        const bFit = (c.factors?.budgetFit || 0).toFixed(2);
        const score = c.status === 'REJECTED' ? '—' : (c.finalScore || 0).toFixed(2);
        const name = (c.action || 'Unknown').substring(0, 25).padEnd(25);
        
        evalContent.push(`${(c.productId || 'p_?').padEnd(8)} ${name}  ${cFit.padEnd(8)}  ${bFit.padEnd(10)}  ${score.padEnd(5)}  ${c.status}`);
        
        if (c.status === 'REJECTED') {
          evalContent.push(`         reason: Failed internal minimum thresholds or constraints`);
        }
      });

      logs.push({
        step: "03. EVALUATION PIPELINE",
        content: evalContent
      });

      // Step 4: Decision
      const selected = decision.candidates.find(c => c.status === 'SELECTED');
      if (selected) {
        logs.push({
          step: "04. DECISION",
          content: [
            `best_score        ${(selected.finalScore || 0).toFixed(2)}`,
            `selected_product  ${selected.action}`,
            `base_price        ₹${selected.amount}`,
            `discount          ₹${selected.discount}`,
            `final_price       ₹${selected.finalAmount}`
          ]
        });
        
        logs.push({
          step: "STATUS: READY FOR CHECKOUT",
          content: []
        });
      } else {
        logs.push({
          step: "04. DECISION",
          content: ["No candidate met the required thresholds for selection."]
        });
      }
    }

    return logs;
  };

  return (
    <div className="app-container">
      {/* Left Panel - Conversational UI */}
      <div className="left-panel">
        <div style={{ padding: '2rem 2rem 0', display: 'flex', flexDirection: 'column', height: 'calc(100% - 90px)' }}>
          <h1 style={{ fontWeight: 600, fontSize: '1.25rem', marginBottom: '2rem' }}>Conversation</h1>
          
          <div style={{ overflowY: 'auto', flex: 1, paddingBottom: '2rem' }}>
            {messages.map((msg, idx) => (
              <React.Fragment key={idx}>
                <ChatBubble isAi={msg.isAi} message={msg.message} />
                {msg.product && <RecommendationCard product={msg.product} />}
              </React.Fragment>
            ))}
          </div>
        </div>
        
        <MessageInput onSendMessage={handleSendMessage} isTyping={isTyping} />
      </div>
      
      {/* Right Panel - Terminal */}
      <div className="right-panel">
        <div style={{ padding: '1.5rem 1.5rem 1rem 1.5rem' }}>
          <h2 style={{ 
            fontFamily: 'var(--font-mono)', 
            fontSize: '0.85rem', 
            letterSpacing: '0.05em', 
            opacity: 0.8,
            margin: 0
          }}>
            PIVOT ENGINE TERMINAL
          </h2>
        </div>
        
        <TerminalLog logs={terminalLogs} />
      </div>
    </div>
  );
}

export default App;
