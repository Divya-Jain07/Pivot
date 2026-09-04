import React from 'react';
import { User, Sparkles } from 'lucide-react';

const ChatBubble = ({ message, isAi }) => {
  const renderMessage = (text) => {
    if (!text) return null;
    const parts = text.split(/(\*\*.*?\*\*)/g);
    return parts.map((part, index) => {
      if (part.startsWith('**') && part.endsWith('**')) {
        return <strong key={index} style={{ fontWeight: 600 }}>{part.slice(2, -2)}</strong>;
      }
      return <span key={index}>{part}</span>;
    });
  };

  return (
    <div style={{
      display: 'flex',
      gap: '1rem',
      marginBottom: '1.5rem',
      flexDirection: isAi ? 'row' : 'row-reverse'
    }}>
      <div style={{
        width: '32px',
        height: '32px',
        borderRadius: '50%',
        backgroundColor: isAi ? '#F3F4F6' : 'var(--color-accent)',
        color: isAi ? 'var(--color-text-primary)' : 'white',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0
      }}>
        {isAi ? <Sparkles size={16} /> : <User size={16} />}
      </div>
      
      <div style={{
        maxWidth: '80%',
        padding: '1rem',
        borderRadius: '12px',
        backgroundColor: isAi ? 'transparent' : 'white',
        boxShadow: isAi ? 'none' : 'var(--shadow-sm)',
        border: isAi ? 'none' : '1px solid var(--color-border)',
        lineHeight: 1.5,
        color: 'var(--color-text-primary)'
      }}>
        {renderMessage(message)}
      </div>
    </div>
  );
};

export default ChatBubble;
