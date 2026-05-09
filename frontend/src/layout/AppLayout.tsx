import { NavLink, Outlet } from "react-router-dom";

function navLinkClass({ isActive }: { isActive: boolean }): string {
  return `nav-link${isActive ? " active" : ""}`;
}

export default function AppLayout() {
  return (
    <div className="app">
      <header className="app-header">
        <div className="brand">
          <span className="brand-mark">JobVault</span>
          <span className="brand-tagline">Trustworthy matching for every role.</span>
        </div>
        <nav className="nav">
          <NavLink to="/" end className={navLinkClass}>
            Overview
          </NavLink>
          <NavLink to="/auth" className={navLinkClass}>
            Auth Console
          </NavLink>
        </nav>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
      <footer className="app-footer">
        <span>JobVault v1 scaffold</span>
        <span>Frontend uses Vite + React + TypeScript</span>
      </footer>
    </div>
  );
}
