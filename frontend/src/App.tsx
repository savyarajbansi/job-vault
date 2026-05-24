import { Route, Routes } from "react-router-dom";

import AppLayout from "./layout/AppLayout";
import AdminMetricsPage from "./routes/AdminMetrics";
import AuthConsole from "./routes/AuthConsole";
import Home from "./routes/Home";
import NotFound from "./routes/NotFound";
import SeekerWorkspace from "./routes/SeekerWorkspace";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AppLayout />}>
        <Route index element={<Home />} />
        <Route path="auth" element={<AuthConsole />} />
        <Route path="seeker" element={<SeekerWorkspace />} />
        <Route path="admin/metrics" element={<AdminMetricsPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
