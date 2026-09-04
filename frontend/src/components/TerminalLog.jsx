import React from 'react';

const TerminalLog = ({ logs }) => {
  return (
    <div className="terminal-scroll" style={{
      fontFamily: 'var(--font-mono)',
      fontSize: '0.85rem',
      lineHeight: 1.6,
      overflowY: 'auto',
      padding: '0 1.5rem 1.5rem 1.5rem',
      height: 'calc(100% - 60px)',
      color: 'var(--color-terminal-text)'
    }}>
      {logs.map((log, index) => (
        <div key={index} style={{ marginBottom: '1.5rem' }}>
          <div style={{ color: 'var(--color-terminal-muted)', marginBottom: '0.5rem' }}>
            {`> ${log.step}`}
          </div>
          
          <pre style={{ 
            fontFamily: 'inherit',
            margin: 0,
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word'
          }}>
            {log.content.map((line, i) => {
              let color = 'inherit';
              if (line.includes('SELECTED')) color = 'var(--color-terminal-selected)';
              if (line.includes('REJECTED')) color = 'var(--color-terminal-rejected)';
              if (line.includes('CONSIDERED')) color = 'var(--color-terminal-considered)';
              if (line.includes('reason:')) color = 'var(--color-terminal-rejected)';
              
              return (
                <div key={i} style={{ color, display: 'flex', gap: '1rem' }}>
                  <span>{line}</span>
                </div>
              );
            })}
          </pre>
        </div>
      ))}
    </div>
  );
};

export default TerminalLog;
