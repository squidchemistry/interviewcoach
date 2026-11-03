import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { sessionsAPI } from '../services/api';

function InterviewSetup() {
  const navigate = useNavigate();
  const [role, setRole] = useState('');
  const [experience, setExperience] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      // Creates a persisted session; questions come back with their IDs.
      const session = await sessionsAPI.create(role, experience);
      navigate(`/sessions/${session.id}/run`, { state: { session } });
    } catch (err) {
      setError(err.message || 'Failed to generate questions. Please try again.');
      setLoading(false);
    }
  };

  return (
    <div className="container fade-in">
      <div className="geometric-bg"></div>
      
      <div className="hero">
        <h1>AI Interview Coach</h1>
        <p className="subtitle">
          Structured, scalable interviews powered by AI. Practice on your schedule,
          get instant feedback, and ace your next interview.
        </p>
      </div>

      <div className="card">
        <h2>Start Your Practice</h2>
        <p style={{ marginBottom: '30px', color: 'var(--bauhaus-gray)' }}>
          Set up your personalized interview session. We'll generate realistic questions
          based on your role and experience level.
        </p>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="role">Target Role</label>
            <input
              id="role"
              type="text"
              value={role}
              onChange={(e) => setRole(e.target.value)}
              placeholder="e.g., Frontend Developer, Data Scientist, Product Manager"
              required
              disabled={loading}
            />
          </div>

          <div className="form-group">
            <label htmlFor="experience">Experience Level</label>
            <select
              id="experience"
              value={experience}
              onChange={(e) => setExperience(e.target.value)}
              required
              disabled={loading}
            >
              <option value="">Select your experience level</option>
              <option value="0-2 years">0-2 years (Entry Level)</option>
              <option value="2-5 years">2-5 years (Mid-Level)</option>
              <option value="5-10 years">5-10 years (Senior)</option>
              <option value="10+ years">10+ years (Lead/Principal)</option>
            </select>
          </div>

          {error && (
            <div className="error-banner" role="alert">
              {error}
            </div>
          )}

          <button type="submit" disabled={loading}>
            {loading ? 'Generating Questions...' : 'Generate Interview Questions'}
          </button>
        </form>
      </div>

      {loading && (
        <div className="loading">
          <div className="spinner"></div>
          <p className="loading-text">Preparing Your Interview</p>
        </div>
      )}

      <div style={{ 
        marginTop: '60px', 
        padding: '30px', 
        background: 'var(--bauhaus-white)',
        border: '3px solid var(--bauhaus-black)',
        borderLeft: '10px solid var(--bauhaus-blue)'
      }}>
        <h3 style={{ marginBottom: '15px' }}>How It Works</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '20px' }}>
          <div>
            <div style={{ 
              width: '40px', 
              height: '40px', 
              background: 'var(--bauhaus-red)', 
              border: '3px solid var(--bauhaus-black)',
              marginBottom: '10px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 'bold',
              color: 'white'
            }}>1</div>
            <strong>Set Your Profile</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)', marginTop: '5px' }}>
              Tell us your role and experience level
            </p>
          </div>
          <div>
            <div style={{ 
              width: '40px', 
              height: '40px', 
              background: 'var(--bauhaus-yellow)', 
              border: '3px solid var(--bauhaus-black)',
              marginBottom: '10px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 'bold'
            }}>2</div>
            <strong>Answer Questions</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)', marginTop: '5px' }}>
              Respond to AI-generated interview questions
            </p>
          </div>
          <div>
            <div style={{ 
              width: '40px', 
              height: '40px', 
              background: 'var(--bauhaus-blue)', 
              border: '3px solid var(--bauhaus-black)',
              marginBottom: '10px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 'bold',
              color: 'white'
            }}>3</div>
            <strong>Get Feedback</strong>
            <p style={{ fontSize: '0.9rem', color: 'var(--bauhaus-gray)', marginTop: '5px' }}>
              Receive detailed AI-powered evaluation
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default InterviewSetup;
