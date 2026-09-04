import React, { useState } from 'react';
import { SendHorizontal, Loader2 } from 'lucide-react';

const MessageInput = ({ onSendMessage, isTyping }) => {
  const [input, setInput] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (input.trim() && !isTyping) {
      onSendMessage(input.trim());
      setInput('');
    }
  };

  return (
    <div style={{
      padding: '1.5rem 2rem',
      borderTop: '1px solid var(--color-border)',
      backgroundColor: 'var(--color-bg-primary)',
      position: 'absolute',
      bottom: 0,
      width: '100%'
    }}>
      <form onSubmit={handleSubmit} style={{
        position: 'relative',
        display: 'flex',
        alignItems: 'center'
      }}>
        <input 
          type="text" 
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={isTyping}
          placeholder="Ask for recommendations..." 
          style={{
            width: '100%',
            padding: '1rem 3rem 1rem 1.5rem',
            borderRadius: '24px',
            border: '1px solid var(--color-border)',
            outline: 'none',
            fontSize: '1rem',
            boxShadow: 'var(--shadow-sm)',
            transition: 'var(--transition-fast)',
            opacity: isTyping ? 0.7 : 1
          }}
        />
        <button 
          type="submit"
          disabled={!input.trim() || isTyping}
          style={{
            position: 'absolute',
            right: '8px',
            background: input.trim() && !isTyping ? 'var(--color-accent)' : '#CBD5E1',
            color: 'white',
            border: 'none',
            borderRadius: '50%',
            width: '36px',
            height: '36px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: input.trim() && !isTyping ? 'pointer' : 'default',
            transition: 'var(--transition-fast)'
          }}
        >
          {isTyping ? <Loader2 size={18} className="spinner" /> : <SendHorizontal size={18} />}
        </button>
      </form>
      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
        .spinner {
          animation: spin 1s linear infinite;
        }
      `}</style>
    </div>
  );
};

export default MessageInput;
