import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useState, useEffect } from "react";
import { getAccessToken, logout } from "../api/auth";
import { GlobalStyles } from "../components/ui";

export default function AppLayout() {
  const navigate = useNavigate();
  const [hasSession, setHasSession] = useState(!!getAccessToken());

  useEffect(() => {
    const check = () => setHasSession(!!getAccessToken());
    window.addEventListener("storage", check);
    return () => window.removeEventListener("storage", check);
  }, []);

  const handleLogout = async () => {
    await logout().catch(() => null);
    setHasSession(false);
    navigate("/auth");
  };

  return (
    <>
      <GlobalStyles />
      <style>{`
        .nav-link {
          font-size: 0.875rem;
          font-weight: 500;
          color: var(--ink-muted);
          padding: 0.375rem 0.75rem;
          border-radius: var(--radius-sm);
          transition: color 0.15s, background 0.15s;
          text-decoration: none;
        }
        .nav-link:hover { color: var(--ink); background: var(--bg-subtle); text-decoration: none; }
        .nav-link.active { color: var(--accent); background: var(--accent-faint); }
        .nav-logout {
          font-size: 0.875rem;
          font-weight: 500;
          color: var(--ink-muted);
          padding: 0.375rem 0.75rem;
          border-radius: var(--radius-sm);
          border: none;
          background: none;
          cursor: pointer;
          transition: color 0.15s, background 0.15s;
        }
        .nav-logout:hover { color: var(--warn); background: var(--warn-faint); }
      `}</style>

      {/* Header */}
      <header
        style={{
          background: "var(--bg-card)",
          borderBottom: "1px solid var(--border)",
          position: "sticky",
          top: 0,
          zIndex: 100,
        }}
      >
        <div
          style={{
            maxWidth: 1100,
            margin: "0 auto",
            padding: "0 1.5rem",
            height: 56,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "1rem",
          }}
        >
          {/* Wordmark */}
          <NavLink
            to={hasSession ? "/seeker" : "/"}
            style={{ textDecoration: "none", display: "flex", alignItems: "baseline", gap: "0.25rem" }}
          >
            <span
              style={{
                fontFamily: "var(--font-display)",
                fontSize: "1.25rem",
                fontWeight: 700,
                color: "var(--ink)",
                letterSpacing: "-0.01em",
              }}
            >
              Job
            </span>
            <span
              style={{
                fontFamily: "var(--font-display)",
                fontSize: "1.25rem",
                fontWeight: 700,
                color: "var(--accent)",
                letterSpacing: "-0.01em",
              }}
            >
              Vault
            </span>
          </NavLink>

          {/* Nav links */}
          <nav style={{ display: "flex", alignItems: "center", gap: "0.25rem" }}>
            {hasSession ? (
              <>
                <NavLink to="/seeker" className="nav-link">
                  Dashboard
                </NavLink>
                <NavLink to="/seeker/matches" className="nav-link">
                  Matches
                </NavLink>
                <button className="nav-logout" onClick={() => void handleLogout()}>
                  Sign out
                </button>
              </>
            ) : (
              <NavLink to="/auth" className="nav-link">
                Sign in
              </NavLink>
            )}
          </nav>
        </div>
      </header>

      {/* Page content */}
      <div style={{ flex: 1 }}>
        <Outlet />
      </div>
    </>
  );
}
