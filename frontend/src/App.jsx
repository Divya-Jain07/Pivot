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

  const handleCheckout = async (product, idx) => {
    try {
      // 1. Call backend to create Order
      const orderData = await api.createOrder(product.decisionId);
      
      // 2. Initialize Razorpay
      const options = {
        key: 'rzp_test_TWng88f9gaN4hA', // Razorpay Test Key from .env
        amount: orderData.finalAmount * 100, // paise
        currency: 'INR',
        name: 'Pivot AI Agent',
        description: product.name,
        order_id: orderData.razorpayOrderId,
        handler: function (response) {
          // 3. On success, update UI
          handlePaymentSuccess(orderData.orderId, product, idx);
        },
        theme: {
          color: '#3399cc'
        }
      };
      
      const rzp1 = new window.Razorpay(options);
      rzp1.open();
    } catch (error) {
      console.error("Error launching Razorpay:", error);
    }
  };

  const handlePaymentSuccess = (orderId, product, idx) => {
    // Update the specific message's product card
    const updatedMessages = [...messages];
    updatedMessages[idx].product = {
      ...updatedMessages[idx].product,
      paymentStatus: 'SUCCESS',
      orderId: orderId
    };
    setMessages(updatedMessages);

    // Append to terminal logs
    setTerminalLogs(prev => [
      ...prev,
      {
        step: "06. PAYMENT CONFIRMED",
        content: [
          `payment_status   SUCCESS`,
          `order_id         ${orderId}`,
          `amount           ₹${product.finalPrice}`
        ]
      },
      {
        step: "STATUS: ORDER CONFIRMED",
        content: []
      }
    ]);
  };

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
          decisionId: decision.decisionId,
          name: selected.action || 'Recommended Product',
          description: `Product ID: ${selected.productId}`,
          originalPrice: selected.amount,
          finalPrice: selected.finalAmount,
          showCheckout: decision.extractedState?.decisionStage === 'CHECKOUT' || decision.extractedState?.isReadyToCheckout,
          paymentStatus: 'PENDING'
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
          `timestamp         ${new Date(decision.timestamp || Date.now()).toLocaleString()}`,
          `budget            ~₹${state.budget || 0}`,
          `use_case          ${state.useCases ? state.useCases.join(', ') : 'N/A'}`,
          `category          ${state.category || 'N/A'}`,
          `price_sensitivity ${state.priceSensitivity || 'N/A'}`,
          `decision_stage    ${state.decisionStage || 'N/A'}`,
          `requested_items   ${state.requestedItems ? state.requestedItems.join(', ') : 'None'}`
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
          const rejection = decision.policyRejections?.find(r => r.action === c.action);
          const reason = rejection ? rejection.reason : 'Failed internal minimum thresholds or constraints';
          evalContent.push(`         reason: ${reason}`);
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
        
        const isCheckout = decision.extractedState?.decisionStage === 'CHECKOUT' || decision.extractedState?.isReadyToCheckout;
        if (isCheckout) {
          logs.push({
            step: "STATUS: READY FOR CHECKOUT",
            content: []
          });
        }
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
                {msg.product && (
                  <RecommendationCard 
                    product={{...msg.product, onCheckout: () => handleCheckout(msg.product, idx)}} 
                  />
                )}
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
