import { Navigate, Route, Routes } from "react-router-dom";
import { getAccessToken } from "./api/auth";

import AppLayout from "./layout/AppLayout";
import Home from "./routes/Home";
import AuthPage from "./routes/AuthPage";
import SeekerDashboard from "./routes/SeekerDashboard";
import SeekerMatches from "./routes/SeekerMatches";
import NotFound from "./routes/NotFound";

function RequireAuth({ children }: { children: React.ReactNode }) {
  if (!getAccessToken()) {
    return <Navigate to="/auth" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AppLayout />}>
        <Route index element={<Home />} />
        <Route path="auth" element={<AuthPage />} />

        {/* Seeker routes */}
        <Route
          path="seeker"
          element={
            <RequireAuth>
              <SeekerDashboard />
            </RequireAuth>
          }
        />
        <Route
          path="seeker/matches"
          element={
            <RequireAuth>
              <SeekerMatches />
            </RequireAuth>
          }
        />

        {/* Legacy redirects */}
        <Route path="admin/metrics" element={<Navigate to="/" replace />} />

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
