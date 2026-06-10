import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../api/authContext";
import { GlobalStyles } from "../components/ui";

export default function AppLayout() {
  const navigate = useNavigate();
  const { isAuthenticated, user, logout } = useAuth();

  const handleLogout = async () => {
    await logout();
    navigate("/auth");
  };

  // Derive display name: use name, fall back to email prefix, fall back to "Account"
  const displayName = user?.displayName
    || (user?.email ? user.email.split("@")[0] : null)
    || "Account";

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
        .nav-user-chip {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.25rem 0.625rem 0.25rem 0.375rem;
          border-radius: 999px;
          background: var(--bg-subtle);
          border: 1px solid var(--border);
        }
        .nav-avatar {
          width: 26px;
          height: 26px;
          border-radius: 50%;
          background: var(--accent);
          color: #fff;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 0.6875rem;
          font-weight: 700;
          flex-shrink: 0;
          text-transform: uppercase;
          letter-spacing: 0.02em;
        }
        .nav-user-name {
          font-size: 0.8125rem;
          font-weight: 500;
          color: var(--ink-2);
          max-width: 120px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
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
            to={isAuthenticated ? "/seeker" : "/"}
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
            {isAuthenticated ? (
              <>
                <NavLink to="/seeker" className="nav-link">
                  Dashboard
                </NavLink>
                <NavLink to="/seeker/matches" className="nav-link">
                  Matches
                </NavLink>

                {/* User chip */}
                <div className="nav-user-chip" style={{ marginLeft: "0.5rem" }}>
                  <div className="nav-avatar">
                    {displayName.charAt(0)}
                  </div>
                  <span className="nav-user-name">{displayName}</span>
                </div>

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
