import { Route, Routes } from "react-router-dom";

import AppLayout from "./layout/AppLayout";
import AuthConsole from "./routes/AuthConsole";
import Home from "./routes/Home";
import NotFound from "./routes/NotFound";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<AppLayout />}>
        <Route index element={<Home />} />
        <Route path="auth" element={<AuthConsole />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
