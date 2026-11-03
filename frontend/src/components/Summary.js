import React from 'react';

// Renders the server-computed summary from GET /v1/sessions/{id}/summary.
function Summary({ summary }) {
  const average = summary.average_score;

  const getPerformanceLevel = (avg) => {
    if (avg === null || avg === undefined) return { text: 'Not started', color: 'var(--bauhaus-gray)', emoji: '⏳' };
    if (avg >= 8) return { text: 'Excellent', color: '#4CAF50', emoji: '🌟' };
    if (avg >= 6) return { text: 'Good', color: 'var(--bauhaus-blue)', emoji: '👍' };
    if (avg >= 4) return { text: 'Fair', color: 'var(--bauhaus-yellow)', emoji: '📈' };
    return { text: 'Needs Work', color: 'var(--bauhaus-red)', emoji: '💪' };
  };

  const performance = getPerformanceLevel(average);

  return (
    <div className="summary-card">
      <h2>Performance Summary</h2>

      <div className="summary-stats">
        <div className="stat-item">
          <div className="stat-value" style={{ color: performance.color }}>
            {performance.emoji} {average === null ? '–' : average.toFixed(1)}
          </div>
          <div className="stat-label">Average Score</div>
          <div className="stat-sublabel" style={{ color: performance.color }}>
            {performance.text}
          </div>
        </div>

        <div className="stat-item">
          <div className="stat-value">
            {summary.answered_questions}/{summary.total_questions}
          </div>
          <div className="stat-label">Questions</div>
          <div className="stat-sublabel">Answered</div>
        </div>

        <div className="stat-item">
          <div className="stat-value" style={{ color: '#4CAF50' }}>
            {summary.strong_answers}
          </div>
          <div className="stat-label">Strong Answers</div>
          <div className="stat-sublabel">Score ≥ 7</div>
        </div>

        <div className="stat-item">
          <div className="stat-value" style={{ color: 'var(--bauhaus-red)' }}>
            {summary.weak_answers}
          </div>
          <div className="stat-label">Weak Answers</div>
          <div className="stat-sublabel">Score &lt; 5</div>
        </div>
      </div>

      {summary.top_weaknesses.length > 0 && (
        <div className="focus-areas">
          <strong>⚠️ Focus Areas</strong>
          <ul>
            {summary.top_weaknesses.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default Summary;
