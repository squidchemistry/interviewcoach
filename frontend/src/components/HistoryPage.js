import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { sessionsAPI } from '../services/api';

export const PAGE_SIZE = 10;

// Paginated list of stored sessions: GET /v1/sessions?limit=&offset=.
// The page number lives in the URL (?page=2) so it survives refresh/back.
function HistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(1, parseInt(searchParams.get('page') || '1', 10) || 1);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setError('');
    sessionsAPI
      .list({ limit: PAGE_SIZE, offset: (page - 1) * PAGE_SIZE })
      .then((result) => !cancelled && setData(result))
      .catch((err) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [page]);

  const totalPages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;
  const goTo = (p) => setSearchParams({ page: String(p) });

  return (
    <div className="container fade-in">
      <div className="geometric-bg"></div>

      <div style={{ marginBottom: '40px' }}>
        <h1>Interview History</h1>
        <p className="subtitle">Every practice session is saved. Open one to review the feedback or continue it.</p>
      </div>

      {error && <div className="error-banner" role="alert">{error}</div>}

      {!data && !error && (
        <div className="loading">
          <div className="spinner"></div>
          <p className="loading-text">Loading sessions</p>
        </div>
      )}

      {data && data.items.length === 0 && (
        <div className="card">
          <h3>No sessions yet</h3>
          <p style={{ margin: '15px 0 25px', color: 'var(--bauhaus-gray)' }}>
            Your completed and in-progress interviews will appear here.
          </p>
          <Link to="/interview" className="button-link">Start your first interview →</Link>
        </div>
      )}

      {data && data.items.length > 0 && (
        <>
          <ul className="history-list">
            {data.items.map((s) => (
              <li key={s.id} className="history-item">
                <div className="history-main">
                  <Link to={`/sessions/${s.id}`} className="history-role">{s.role}</Link>
                  <div className="history-meta">
                    {s.experience} · {new Date(s.created_at).toLocaleString()}
                  </div>
                </div>
                <span className={`status-badge ${s.status}`}>
                  {s.status === 'completed' ? 'Completed' : 'In progress'}
                </span>
                <div className="history-stat">
                  {s.answered_questions}/{s.total_questions} answered
                </div>
                <div className="history-stat">
                  Avg {s.average_score === null ? '–' : s.average_score.toFixed(1)}
                </div>
                {s.status !== 'completed' && (
                  <Link to={`/sessions/${s.id}/run`} className="history-continue">Continue →</Link>
                )}
              </li>
            ))}
          </ul>

          <nav className="pagination" aria-label="History pages">
            <button type="button" onClick={() => goTo(page - 1)} disabled={page <= 1}>
              ← Previous
            </button>
            <span>
              Page {page} of {totalPages} · {data.total} sessions
            </span>
            <button type="button" onClick={() => goTo(page + 1)} disabled={page >= totalPages}>
              Next →
            </button>
          </nav>
        </>
      )}
    </div>
  );
}

export default HistoryPage;
