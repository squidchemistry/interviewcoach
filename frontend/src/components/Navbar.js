import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

function Navbar() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();

  const isActive = (path) => location.pathname === path;

  return (
    <nav style={{
      background: 'var(--bauhaus-black)',
      padding: '15px 30px',
      borderBottom: '5px solid var(--bauhaus-red)',
      position: 'sticky',
      top: 0,
      zIndex: 1000,
      boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)'
    }}>
      <div style={{
        maxWidth: '1200px',
        margin: '0 auto',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center'
      }}>
        {/* Logo */}
        <Link to="/" style={{
          color: 'white',
          textDecoration: 'none',
          fontSize: '1.4rem',
          fontWeight: 'bold',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          transition: 'transform 0.2s'
        }}
        onMouseOver={(e) => e.currentTarget.style.transform = 'scale(1.05)'}
        onMouseOut={(e) => e.currentTarget.style.transform = 'scale(1)'}
        >
          <span style={{
            background: 'var(--bauhaus-red)',
            padding: '8px 12px',
            border: '2px solid white',
            borderRadius: '4px'
          }}>
            🎯
          </span>
          AI Interview Coach
        </Link>

        {/* Desktop Navigation */}
        <div style={{ 
          display: 'flex', 
          gap: '30px',
          alignItems: 'center'
        }}
        className="desktop-nav"
        >
          <Link to="/" style={{
            color: isActive('/') ? 'var(--bauhaus-yellow)' : 'white',
            textDecoration: 'none',
            fontWeight: '600',
            fontSize: '1rem',
            transition: 'color 0.2s',
            position: 'relative',
            padding: '5px 0'
          }}>
            Home
            {isActive('/') && (
              <span style={{
                position: 'absolute',
                bottom: '-8px',
                left: 0,
                right: 0,
                height: '3px',
                background: 'var(--bauhaus-yellow)'
              }}></span>
            )}
          </Link>

          <Link to="/interview" style={{
            color: isActive('/interview') ? 'var(--bauhaus-yellow)' : 'white',
            textDecoration: 'none',
            fontWeight: '600',
            fontSize: '1rem',
            transition: 'color 0.2s',
            position: 'relative',
            padding: '5px 0'
          }}>
            Start Interview
            {isActive('/interview') && (
              <span style={{
                position: 'absolute',
                bottom: '-8px',
                left: 0,
                right: 0,
                height: '3px',
                background: 'var(--bauhaus-yellow)'
              }}></span>
            )}
          </Link>

          <Link to="/history" style={{
            color: isActive('/history') ? 'var(--bauhaus-yellow)' : 'white',
            textDecoration: 'none',
            fontWeight: '600',
            fontSize: '1rem',
            transition: 'color 0.2s',
            position: 'relative',
            padding: '5px 0'
          }}>
            History
            {isActive('/history') && (
              <span style={{
                position: 'absolute',
                bottom: '-8px',
                left: 0,
                right: 0,
                height: '3px',
                background: 'var(--bauhaus-yellow)'
              }}></span>
            )}
          </Link>

          <a 
            href="https://github.com/squidchemistry" 
            target="_blank" 
            rel="noopener noreferrer"
            style={{
              color: 'white',
              textDecoration: 'none',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '8px 16px',
              background: 'rgba(255, 255, 255, 0.1)',
              border: '2px solid rgba(255, 255, 255, 0.3)',
              borderRadius: '4px',
              transition: 'all 0.2s',
              fontSize: '0.9rem',
              fontWeight: '600'
            }}
            onMouseOver={(e) => {
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.2)';
              e.currentTarget.style.borderColor = 'white';
            }}
            onMouseOut={(e) => {
              e.currentTarget.style.background = 'rgba(255, 255, 255, 0.1)';
              e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.3)';
            }}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
            GitHub
          </a>
        </div>

        {/* Mobile Menu Button */}
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          style={{
            display: 'none',
            background: 'transparent',
            border: '2px solid white',
            color: 'white',
            padding: '8px 12px',
            cursor: 'pointer',
            fontSize: '1.2rem'
          }}
          className="mobile-menu-btn"
        >
          {mobileMenuOpen ? '✕' : '☰'}
        </button>
      </div>

      {/* Mobile Menu */}
      {mobileMenuOpen && (
        <div style={{
          display: 'none',
          flexDirection: 'column',
          gap: '15px',
          marginTop: '20px',
          padding: '20px',
          background: 'rgba(255, 255, 255, 0.05)',
          border: '2px solid rgba(255, 255, 255, 0.1)',
          borderRadius: '4px'
        }}
        className="mobile-menu"
        >
          <Link 
            to="/" 
            onClick={() => setMobileMenuOpen(false)}
            style={{
              color: isActive('/') ? 'var(--bauhaus-yellow)' : 'white',
              textDecoration: 'none',
              fontWeight: '600',
              fontSize: '1rem',
              padding: '10px'
            }}
          >
            Home
          </Link>
          <Link 
            to="/interview" 
            onClick={() => setMobileMenuOpen(false)}
            style={{
              color: isActive('/interview') ? 'var(--bauhaus-yellow)' : 'white',
              textDecoration: 'none',
              fontWeight: '600',
              fontSize: '1rem',
              padding: '10px'
            }}
          >
            Start Interview
          </Link>
          <Link
            to="/history"
            onClick={() => setMobileMenuOpen(false)}
            style={{
              color: isActive('/history') ? 'var(--bauhaus-yellow)' : 'white',
              textDecoration: 'none',
              fontWeight: '600',
              fontSize: '1rem',
              padding: '10px'
            }}
          >
            History
          </Link>
          <a
            href="https://github.com/squidchemistry"
            target="_blank" 
            rel="noopener noreferrer"
            style={{
              color: 'white',
              textDecoration: 'none',
              fontWeight: '600',
              fontSize: '1rem',
              padding: '10px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
            GitHub
          </a>
        </div>
      )}
    </nav>
  );
}

export default Navbar;
