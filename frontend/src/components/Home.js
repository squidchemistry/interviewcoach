import React from 'react';
import { Link } from 'react-router-dom';

function Home() {
  return (
    <div className="container fade-in">
      <div className="geometric-bg"></div>
      
      <div className="hero" style={{ marginBottom: '60px' }}>
        <h1 style={{ fontSize: '3.5rem', marginBottom: '20px' }}>
          AI Interview Coach
        </h1>
        <p className="subtitle" style={{ fontSize: '1.3rem', maxWidth: '700px', margin: '0 auto' }}>
          Master your interview skills with AI-powered practice sessions.
          Get instant feedback, improve your answers, and land your dream job.
        </p>
      </div>

      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', 
        gap: '30px',
        marginBottom: '60px'
      }}>
        <div className="card" style={{ 
          borderLeft: '10px solid var(--bauhaus-red)',
          transition: 'transform 0.3s ease'
        }}>
          <div style={{ 
            fontSize: '3rem', 
            marginBottom: '15px'
          }}>🎯</div>
          <h3 style={{ marginBottom: '15px' }}>Personalized Questions</h3>
          <p style={{ color: 'var(--bauhaus-gray)', marginBottom: '25px', lineHeight: '1.6' }}>
            AI generates role-specific interview questions tailored to your experience level
          </p>
          <Link to="/interview" className="button-link">
            Start Interview →
          </Link>
        </div>

        <div className="card" style={{ 
          borderLeft: '10px solid var(--bauhaus-blue)',
          transition: 'transform 0.3s ease'
        }}>
          <div style={{ 
            fontSize: '3rem', 
            marginBottom: '15px'
          }}>💡</div>
          <h3 style={{ marginBottom: '15px' }}>Instant Feedback</h3>
          <p style={{ color: 'var(--bauhaus-gray)', marginBottom: '25px', lineHeight: '1.6' }}>
            Get detailed AI analysis of your answers with strengths, weaknesses, and improvements
          </p>
          <div style={{ 
            padding: '12px 24px',
            background: 'var(--bauhaus-blue)',
            color: 'white',
            border: '3px solid var(--bauhaus-black)',
            fontWeight: 'bold',
            display: 'inline-block',
            opacity: '0.6'
          }}>
            Available After Interview
          </div>
        </div>

        <div className="card" style={{ 
          borderLeft: '10px solid var(--bauhaus-yellow)',
          transition: 'transform 0.3s ease'
        }}>
          <div style={{ 
            fontSize: '3rem', 
            marginBottom: '15px'
          }}>📊</div>
          <h3 style={{ marginBottom: '15px' }}>Track Progress</h3>
          <p style={{ color: 'var(--bauhaus-gray)', marginBottom: '25px', lineHeight: '1.6' }}>
            Every session is saved. Review past feedback and see how you improve over time
          </p>
          <Link to="/history" className="button-link">
            View History →
          </Link>
        </div>
      </div>

      <div style={{ 
        padding: '50px 40px',
        background: 'var(--bauhaus-white)',
        border: '3px solid var(--bauhaus-black)',
        marginBottom: '60px'
      }}>
        <h2 style={{ marginBottom: '30px', textAlign: 'center' }}>How It Works</h2>
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', 
          gap: '40px',
          marginTop: '40px'
        }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{ 
              width: '60px', 
              height: '60px', 
              background: 'var(--bauhaus-red)', 
              border: '3px solid var(--bauhaus-black)',
              margin: '0 auto 20px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 'bold',
              fontSize: '1.5rem',
              color: 'white'
            }}>1</div>
            <strong style={{ display: 'block', marginBottom: '10px' }}>Choose Your Role</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)' }}>
              Select your target position and experience level
            </p>
          </div>

          <div style={{ textAlign: 'center' }}>
            <div style={{ 
              width: '60px', 
              height: '60px', 
              background: 'var(--bauhaus-yellow)', 
              border: '3px solid var(--bauhaus-black)',
              margin: '0 auto 20px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 'bold',
              fontSize: '1.5rem'
            }}>2</div>
            <strong style={{ display: 'block', marginBottom: '10px' }}>Answer Questions</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)' }}>
              Respond to AI-generated interview questions
            </p>
          </div>

          <div style={{ textAlign: 'center' }}>
            <div style={{ 
              width: '60px', 
              height: '60px', 
              background: 'var(--bauhaus-blue)', 
              border: '3px solid var(--bauhaus-black)',
              margin: '0 auto 20px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 'bold',
              fontSize: '1.5rem',
              color: 'white'
            }}>3</div>
            <strong style={{ display: 'block', marginBottom: '10px' }}>Get Feedback</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)' }}>
              Receive detailed evaluation and improvement tips
            </p>
          </div>

          <div style={{ textAlign: 'center' }}>
            <div style={{ 
              width: '60px', 
              height: '60px', 
              background: '#4CAF50', 
              border: '3px solid var(--bauhaus-black)',
              margin: '0 auto 20px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 'bold',
              fontSize: '1.5rem',
              color: 'white'
            }}>4</div>
            <strong style={{ display: 'block', marginBottom: '10px' }}>Improve & Repeat</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)' }}>
              Practice regularly to master your skills
            </p>
          </div>
        </div>
      </div>

      <div style={{
        textAlign: 'center',
        padding: '60px 40px',
        background: 'linear-gradient(135deg, var(--bauhaus-blue) 0%, var(--bauhaus-red) 100%)',
        border: '3px solid var(--bauhaus-black)',
        color: 'white'
      }}>
        <h2 style={{ marginBottom: '20px', color: 'white' }}>Ready to Ace Your Interview?</h2>
        <p style={{ fontSize: '1.1rem', marginBottom: '30px', opacity: '0.95' }}>
          Start practicing now and build confidence for your next opportunity
        </p>
        <Link to="/interview" style={{
          padding: '15px 40px',
          background: 'var(--bauhaus-yellow)',
          color: 'var(--bauhaus-black)',
          border: '3px solid var(--bauhaus-black)',
          fontWeight: 'bold',
          fontSize: '1.1rem',
          textDecoration: 'none',
          display: 'inline-block',
          transition: 'transform 0.2s ease'
        }}>
          Begin Practice Session
        </Link>
      </div>

      <div style={{
        marginTop: '60px',
        padding: '30px',
        background: 'var(--bauhaus-white)',
        border: '3px solid var(--bauhaus-black)',
        borderTop: '10px solid var(--bauhaus-red)'
      }}>
        <h3 style={{ marginBottom: '20px' }}>Why Practice Matters</h3>
        <div style={{ 
          display: 'grid', 
          gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', 
          gap: '25px'
        }}>
          <div>
            <strong style={{ color: 'var(--bauhaus-blue)' }}>✓ Build Confidence</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)', marginTop: '8px' }}>
              Reduce anxiety by practicing in a low-pressure environment
            </p>
          </div>
          <div>
            <strong style={{ color: 'var(--bauhaus-blue)' }}>✓ Refine Answers</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)', marginTop: '8px' }}>
              Learn to structure responses effectively and concisely
            </p>
          </div>
          <div>
            <strong style={{ color: 'var(--bauhaus-blue)' }}>✓ Identify Gaps</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)', marginTop: '8px' }}>
              Discover areas where you need more preparation
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Home;
