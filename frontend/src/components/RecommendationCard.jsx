import React from 'react';
import { ShieldCheck } from 'lucide-react';

const RecommendationCard = ({ product }) => {
  return (
    <div style={{
      maxWidth: '80%',
      margin: '0 0 1.5rem 3rem',
      backgroundColor: 'white',
      borderRadius: '16px',
      padding: '1.5rem',
      boxShadow: 'var(--shadow-md)',
      border: '1px solid var(--color-border)'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
        <div>
          <h3 style={{ fontSize: '1.125rem', fontWeight: 600, margin: '0 0 0.25rem 0' }}>{product.name}</h3>
          <p style={{ fontSize: '0.875rem', color: 'var(--color-text-secondary)', margin: 0 }}>{product.description}</p>
        </div>
      </div>
      
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', margin: '1.5rem 0' }}>
        <span style={{ fontSize: '1.5rem', fontWeight: 700 }}>₹{product.finalPrice}</span>
        <span style={{ fontSize: '1rem', color: 'var(--color-text-muted)', textDecoration: 'line-through' }}>₹{product.originalPrice}</span>
        <span style={{ 
          fontSize: '0.75rem', 
          fontWeight: 600, 
          color: '#15803d', 
          backgroundColor: '#dcfce7', 
          padding: '4px 8px', 
          borderRadius: '12px' 
        }}>
          Save ₹{product.originalPrice - product.finalPrice}
        </span>
      </div>

      <button style={{
        width: '100%',
        padding: '0.875rem',
        backgroundColor: 'var(--color-accent)',
        color: 'white',
        border: 'none',
        borderRadius: '8px',
        fontSize: '1rem',
        fontWeight: 500,
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '0.5rem',
        transition: 'var(--transition-fast)'
      }}>
        <ShieldCheck size={18} />
        Pay with Razorpay
      </button>
    </div>
  );
};

export default RecommendationCard;
