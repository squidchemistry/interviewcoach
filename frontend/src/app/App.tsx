import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { lazy, Suspense, useState, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { PageSpinner } from '../components/ui/PageSpinner';
import { AuthProvider } from '../features/auth/AuthProvider';
import { GuestOnly, RequireAdmin, RequireAuth, RequireOnboarded } from '../features/auth/guards';
import { LoginPage } from '../features/auth/LoginPage';
import { OAuthCompletePage } from '../features/auth/OAuthCompletePage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { HomePage } from '../features/home/HomePage';
import { NotFoundPage } from '../features/not-found/NotFoundPage';
import { OnboardingPage } from '../features/profile/OnboardingPage';
import { ProfilePage } from '../features/profile/ProfilePage';
import { Layout } from './Layout';
import { createQueryClient } from './queryClient';

// Feature routes are code-split; the heavy ones (Monaco, Mermaid, admin) never load on the landing page.
const NewInterviewPage = lazy(() => import('../features/interviews/NewInterviewPage').then((m) => ({ default: m.NewInterviewPage })));
const HistoryPage = lazy(() => import('../features/interviews/HistoryPage').then((m) => ({ default: m.HistoryPage })));
const InterviewRoomPage = lazy(() => import('../features/interviews/room/InterviewRoomPage'));
const ReportPage = lazy(() => import('../features/report/ReportPage'));
const PublicReportPage = lazy(() => import('../features/report/PublicReportPage'));
const DashboardPage = lazy(() => import('../features/dashboard/DashboardPage'));
const QuestionsPage = lazy(() => import('../features/questions/QuestionsPage').then((m) => ({ default: m.QuestionsPage })));
const QuestionDetailPage = lazy(() => import('../features/questions/QuestionDetailPage').then((m) => ({ default: m.QuestionDetailPage })));
const ResumePage = lazy(() => import('../features/resume/ResumePage').then((m) => ({ default: m.ResumePage })));
const RevisionPage = lazy(() => import('../features/revision/RevisionPage').then((m) => ({ default: m.RevisionPage })));
const BillingPage = lazy(() => import('../features/billing/BillingPage').then((m) => ({ default: m.BillingPage })));
const AdminPage = lazy(() => import('../features/admin/AdminPage'));

export function AppProviders({ children, client }: { children: ReactNode; client?: QueryClient }) {
  const [queryClient] = useState(() => client ?? createQueryClient());
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );
}

function Page({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageSpinner label="Loading" />}>{children}</Suspense>;
}

function Candidate({ children }: { children: ReactNode }) {
  return (
    <RequireOnboarded>
      <Page>{children}</Page>
    </RequireOnboarded>
  );
}

export function AppRoutes() {
  return (
    <Routes>
      <Route
        path="r/:token"
        element={
          <Page>
            <PublicReportPage />
          </Page>
        }
      />
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<GuestOnly><LoginPage /></GuestOnly>} />
        <Route path="register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
        <Route path="auth/complete" element={<OAuthCompletePage />} />
        <Route path="onboarding" element={<RequireAuth><OnboardingPage /></RequireAuth>} />
        <Route path="profile" element={<RequireAuth><ProfilePage /></RequireAuth>} />
        <Route path="interviews/new" element={<Candidate><NewInterviewPage /></Candidate>} />
        <Route path="interviews" element={<Candidate><HistoryPage /></Candidate>} />
        <Route path="interviews/:id" element={<Candidate><InterviewRoomPage /></Candidate>} />
        <Route path="interviews/:id/report" element={<Candidate><ReportPage /></Candidate>} />
        <Route path="dashboard" element={<Candidate><DashboardPage /></Candidate>} />
        <Route path="questions" element={<Candidate><QuestionsPage /></Candidate>} />
        <Route path="questions/:idOrSlug" element={<Candidate><QuestionDetailPage /></Candidate>} />
        <Route path="resume" element={<Candidate><ResumePage /></Candidate>} />
        <Route path="revision" element={<Candidate><RevisionPage /></Candidate>} />
        <Route path="billing" element={<Candidate><BillingPage /></Candidate>} />
        <Route path="admin" element={<RequireAdmin><Page><AdminPage /></Page></RequireAdmin>} />
        <Route path="admin/users" element={<Navigate to="/admin?tab=users" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function App() {
  return (
    <AppProviders>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AppProviders>
  );
}
