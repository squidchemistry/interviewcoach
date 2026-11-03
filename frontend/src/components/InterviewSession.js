import React, { useState } from 'react';

function InterviewSession({ question, questionNumber, totalQuestions, onSubmit }) {
  const [answer, setAnswer] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!answer.trim()) return;

    setSubmitting(true);
    setError('');
    try {
      await onSubmit(answer);
      setAnswer('');
    } catch (err) {
      // Keep the typed answer so the candidate can retry.
      setError(err.message || 'Could not submit your answer. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const progress = (questionNumber / totalQuestions) * 100;

  return (
    <div className="container fade-in">
      <div className="geometric-bg"></div>

      <div style={{ marginBottom: '40px' }}>
        <h2>Interview Session</h2>
        <p className="subtitle">
          Take your time and provide detailed answers. The AI will evaluate your response.
        </p>
      </div>

      <div className="progress-container">
        <div className="progress-label">
          <span>Progress</span>
          <span>{questionNumber} of {totalQuestions}</span>
        </div>
        <div className="progress">
          <div className="progress-bar" style={{ width: `${progress}%` }}></div>
        </div>
      </div>

      <div className="question-card">
        <div className="question-number">
          Question {questionNumber} / {totalQuestions}
        </div>
        <div className="question-text">{question}</div>
      </div>

      <form onSubmit={handleSubmit}>
        <div className="card card-blue">
          <div className="form-group" style={{ marginBottom: '0' }}>
            <label htmlFor="answer">Your Answer</label>
            <textarea
              id="answer"
              value={answer}
              onChange={(e) => setAnswer(e.target.value)}
              placeholder="Type your answer here... Be specific and explain your thinking process."
              required
              disabled={submitting}
            />
          </div>
        </div>

        {error && (
          <div className="error-banner" role="alert">
            {error}
          </div>
        )}

        <div className="button-group">
          <button type="submit" disabled={submitting || !answer.trim()}>
            {submitting ? 'Evaluating Answer...' : 
             questionNumber === totalQuestions ? 'Complete Interview' : 'Submit & Continue'}
          </button>
        </div>
      </form>

      {submitting && (
        <div className="loading">
          <div className="spinner"></div>
          <p className="loading-text">AI is analyzing your answer</p>
        </div>
      )}

      <div style={{
        marginTop: '40px',
        padding: '20px',
        background: 'var(--bauhaus-yellow)',
        border: '3px solid var(--bauhaus-black)',
        opacity: '0.9'
      }}>
        <strong style={{ display: 'block', marginBottom: '8px' }}>💡 Interview Tips</strong>
        <ul style={{ paddingLeft: '20px', fontSize: '0.9rem' }}>
          <li>Structure your answer with clear points</li>
          <li>Provide specific examples from your experience</li>
          <li>Explain your reasoning and decision-making process</li>
          <li>Be concise but thorough</li>
        </ul>
      </div>
    </div>
  );
}

export default InterviewSession;
