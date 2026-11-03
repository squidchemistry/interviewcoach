import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import Summary from './Summary';
import { sessionsAPI } from '../services/api';

function scoreClass(score) {
  if (score >= 7) return 'high';
  if (score >= 5) return 'medium';
  return 'low';
}

function FeedbackList({ title, className, items }) {
  if (!items || items.length === 0) return null;
  return (
    <div style={{ marginBottom: '20px' }}>
      <div className={`feedback-title ${className}`}>{title}</div>
      <ul className="feedback-list">
        {items.map((item, i) => (
          <li key={i}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

// Results for one stored session: GET /v1/sessions/{id} + /summary.
function SessionDetail() {
  const { sessionId } = useParams();
  const [session, setSession] = useState(null);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    Promise.all([sessionsAPI.get(sessionId), sessionsAPI.summary(sessionId)])
      .then(([sessionData, summaryData]) => {
        if (cancelled) return;
        setSession(sessionData);
        setSummary(summaryData);
      })
      .catch((err) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  if (error) {
    return (
      <div className="container">
        <div className="error-banner" role="alert">{error}</div>
        <Link to="/history" className="button-link">← Back to history</Link>
      </div>
    );
  }

  if (!session || !summary) {
    return (
      <div className="container">
        <div className="loading">
          <div className="spinner"></div>
          <p className="loading-text">Loading results</p>
        </div>
      </div>
    );
  }

  const inProgress = session.status !== 'completed';

  return (
    <div className="container fade-in">
      <div className="geometric-bg"></div>

      <div style={{ marginBottom: '40px', textAlign: 'center' }}>
        <h1>{inProgress ? 'Interview In Progress' : 'Interview Complete'}</h1>
        <p className="subtitle">
          {session.role} · {session.experience} · started{' '}
          {new Date(session.created_at).toLocaleString()}
        </p>
      </div>

      <Summary summary={summary} />

      {inProgress && (
        <div className="button-group" style={{ marginTop: '30px' }}>
          <Link to={`/sessions/${session.id}/run`} className="button-link">
            Continue Interview →
          </Link>
        </div>
      )}

      <h2 style={{ marginBottom: '30px', marginTop: '60px' }}>Detailed Feedback</h2>

      {session.questions.map((question) => {
        const evaluation = question.answer?.evaluation;
        return (
          <div key={question.id} className="evaluation-card fade-in">
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '25px', marginBottom: '25px' }}>
              <div className={`score-badge ${evaluation ? scoreClass(evaluation.score) : ''}`}>
                {evaluation ? `${evaluation.score}/10` : '–'}
              </div>
              <div style={{ flex: 1 }}>
                <h3 style={{ marginBottom: '10px' }}>Question {question.position + 1}</h3>
                <div className="question-text" style={{ fontSize: '1.1rem', color: 'var(--bauhaus-blue)' }}>
                  {question.text}
                </div>
              </div>
            </div>

            {question.answer ? (
              <div className="feedback-section">
                <div className="answer-box">
                  <strong>Your Answer:</strong>
                  <p>{question.answer.text}</p>
                </div>
                <FeedbackList title="Strengths" className="strengths" items={evaluation.strengths} />
                <FeedbackList title="Areas for Improvement" className="weaknesses" items={evaluation.weaknesses} />
                {evaluation.improved_answer && (
                  <div>
                    <div className="feedback-title improved">Improved Answer Example</div>
                    <div className="improved-answer-box">{evaluation.improved_answer}</div>
                  </div>
                )}
              </div>
            ) : (
              <p className="subtitle">Not answered yet.</p>
            )}
          </div>
        );
      })}

      <div className="button-group" style={{ marginTop: '50px' }}>
        <Link to="/interview" className="button-link">Start New Interview</Link>
        <Link to="/history" className="button-link">View History</Link>
      </div>
    </div>
  );
}

export default SessionDetail;
