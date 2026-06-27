import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./api/authContext";

import AppLayout from "./layout/AppLayout";
import Home from "./routes/Home";
import AuthPage from "./routes/AuthPage";
import JobDetailPage from "./routes/JobDetail";
import SeekerDashboard from "./routes/SeekerDashboard";
import SeekerMatches from "./routes/SeekerMatches";
import SeekerApplications from "./routes/SeekerApplications";
import EmployerDashboard from "./routes/EmployerDashboard";
import JobEditor from "./routes/JobEditor";
import CandidateMatches from "./routes/CandidateMatches";
import ApplicationReview from "./routes/ApplicationReview";
import NotFound from "./routes/NotFound";
import { Spinner } from "./components/ui";

function LoadingGate() {
  return (
    <div style={{ minHeight: "calc(100vh - 56px)", display: "flex", alignItems: "center", justifyContent: "center" }}>
      <Spinner size={28} />
    </div>
  );
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isSessionReady } = useAuth();
  if (!isSessionReady) {
    return <LoadingGate />;
  }
  if (!isAuthenticated) {
    return <Navigate to="/auth" replace />;
  }
  return <>{children}</>;
}

function RequireRole({
  role,
  fallback,
  children,
}: {
  role: "EMPLOYER" | "JOB_SEEKER";
  fallback: string;
  children: React.ReactNode;
}) {
  const { isAuthenticated, isSessionReady, roles } = useAuth();
  if (!isSessionReady) {
    return <LoadingGate />;
  }
  if (!isAuthenticated) {
    return <Navigate to="/auth" replace />;
  }
  if (!roles.includes(role)) {
    return <Navigate to={fallback} replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AppLayout />}>
        <Route index element={<Home />} />
        <Route path="auth" element={<AuthPage />} />
        <Route path="jobs/:jobId" element={<JobDetailPage />} />

        {/* Seeker routes */}
        <Route
          path="seeker"
          element={
            <RequireRole role="JOB_SEEKER" fallback="/employer">
              <SeekerDashboard />
            </RequireRole>
          }
        />
        <Route
          path="seeker/matches"
          element={
            <RequireRole role="JOB_SEEKER" fallback="/employer">
              <SeekerMatches />
            </RequireRole>
          }
        />
        <Route
          path="seeker/applications"
          element={
            <RequireRole role="JOB_SEEKER" fallback="/employer">
              <SeekerApplications />
            </RequireRole>
          }
        />

        {/* Employer routes */}
        <Route
          path="employer"
          element={
            <RequireRole role="EMPLOYER" fallback="/seeker">
              <EmployerDashboard />
            </RequireRole>
          }
        />
        <Route
          path="employer/jobs/new"
          element={
            <RequireRole role="EMPLOYER" fallback="/seeker">
              <JobEditor mode="create" />
            </RequireRole>
          }
        />
        <Route
          path="employer/jobs/:jobId"
          element={
            <RequireRole role="EMPLOYER" fallback="/seeker">
              <JobEditor mode="edit" />
            </RequireRole>
          }
        />
        <Route
          path="employer/jobs/:jobId/matches"
          element={
            <RequireRole role="EMPLOYER" fallback="/seeker">
              <CandidateMatches />
            </RequireRole>
          }
        />
        <Route
          path="employer/jobs/:jobId/applications"
          element={
            <RequireRole role="EMPLOYER" fallback="/seeker">
              <ApplicationReview />
            </RequireRole>
          }
        />

        {/* Legacy redirects */}
        <Route path="admin/metrics" element={<Navigate to="/" replace />} />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}