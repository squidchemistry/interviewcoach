import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import './App.css';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import Home from './components/Home';
import InterviewSetup from './components/InterviewSetup';
import InterviewRunner from './components/InterviewRunner';
import HistoryPage from './components/HistoryPage';
import SessionDetail from './components/SessionDetail';

// Interview state lives on the server (sessions are persisted), so every
// page is addressable by URL and survives a refresh.
export function AppRoutes() {
  return (
    <div className="App">
      <Navbar />

      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/interview" element={<InterviewSetup />} />
        <Route path="/sessions/:sessionId/run" element={<InterviewRunner />} />
        <Route path="/sessions/:sessionId" element={<SessionDetail />} />
        <Route path="/history" element={<HistoryPage />} />
      </Routes>

      <Footer />
    </div>
  );
}

function App() {
  return (
    <Router>
      <AppRoutes />
    </Router>
  );
}

export default App;
