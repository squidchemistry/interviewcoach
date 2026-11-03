import React, { useEffect, useState } from 'react';
import { Navigate, useLocation, useParams } from 'react-router-dom';
import InterviewSession from './InterviewSession';
import { sessionsAPI } from '../services/api';

// Runs a persisted session one unanswered question at a time. Each answer is
// saved as it is submitted, so a refresh (or coming back from History)
// resumes at the next unanswered question.
function InterviewRunner() {
  const { sessionId } = useParams();
  const location = useLocation();
  const passed = location.state?.session?.id === sessionId ? location.state.session : null;
  const [session, setSession] = useState(passed);
  const [error, setError] = useState('');
  const needsFetch = !passed;

  useEffect(() => {
    if (!needsFetch) return undefined;
    let cancelled = false;
    sessionsAPI
      .get(sessionId)
      .then((data) => !cancelled && setSession(data))
      .catch((err) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, [sessionId, needsFetch]);

  if (error) {
    return (
      <div className="container">
        <div className="error-banner" role="alert">{error}</div>
      </div>
    );
  }

  if (!session) {
    return (
      <div className="container">
        <div className="loading">
          <div className="spinner"></div>
          <p className="loading-text">Loading session</p>
        </div>
      </div>
    );
  }

  const next = session.questions.find((q) => !q.answer);
  if (session.status === 'completed' || !next) {
    return <Navigate to={`/sessions/${session.id}`} replace />;
  }

  const submitAnswer = async (answer) => {
    const result = await sessionsAPI.submitAnswer(session.id, next.id, answer);
    setSession((prev) => ({
      ...prev,
      status: result.session_status,
      answered_questions: result.answered_questions,
      questions: prev.questions.map((q) =>
        q.id === result.question_id ? { ...q, answer: result.answer } : q
      ),
    }));
  };

  return (
    <InterviewSession
      key={next.id}
      question={next.text}
      questionNumber={next.position + 1}
      totalQuestions={session.total_questions}
      onSubmit={submitAnswer}
    />
  );
}

export default InterviewRunner;
