import { useEffect, useRef, useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { getNotifications, getUnreadNotificationCount, markNotificationRead, NotificationItem } from "../api/notifications";
import { useAuth } from "../api/authContext";
import { GlobalStyles, Badge, Button, Card, Spinner } from "../components/ui";

const BADGE_POLL_MS = 60_000;   // badge count: every 60 s
const PANEL_POLL_MS = 30_000;   // panel list: every 30 s while open

function formatRelativeTime(iso: string): string {
  const date = new Date(iso);
  const diffMs = date.getTime() - Date.now();
  const diffMinutes = Math.round(diffMs / 60000);
  const absMinutes = Math.abs(diffMinutes);
  if (absMinutes < 60) {
    return `${absMinutes}m ${diffMinutes >= 0 ? "from now" : "ago"}`;
  }
  const diffHours = Math.round(diffMinutes / 60);
  const absHours = Math.abs(diffHours);
  if (absHours < 24) {
    return `${absHours}h ${diffHours >= 0 ? "from now" : "ago"}`;
  }
  const diffDays = Math.round(diffHours / 24);
  return `${Math.abs(diffDays)}d ${diffDays >= 0 ? "from now" : "ago"}`;
}

function NotificationBell() {
  const { isAuthenticated, isSessionReady } = useAuth();
  const [open, setOpen] = useState(false);
  const [count, setCount] = useState<number | null>(null);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);

  // ── Badge count: poll every 60 s while authenticated ────────────────────
  useEffect(() => {
    if (!isAuthenticated || !isSessionReady) {
      setCount(null);
      setItems([]);
      return;
    }

    let cancelled = false;

    const loadCount = async () => {
      try {
        const response = await getUnreadNotificationCount();
        if (!cancelled) setCount(response.unreadCount);
      } catch {
        if (!cancelled) setCount(null);
      }
    };

    void loadCount();
    const id = setInterval(() => void loadCount(), BADGE_POLL_MS);

    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [isAuthenticated, isSessionReady]);

  // ── Panel list: load immediately on open, then re-poll every 30 s ───────
  useEffect(() => {
    if (!open || !isAuthenticated || !isSessionReady) {
      return;
    }

    let cancelled = false;

    const loadNotifications = async () => {
      if (items.length === 0) setLoading(true);
      setError(null);

      try {
        const response = await getNotifications();
        if (!cancelled) {
          setItems(response);
          const unread = response.filter((n) => !n.isRead).length;
          setCount(unread);
        }
      } catch {
        if (!cancelled) setError("Could not load notifications.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void loadNotifications();
    const id = setInterval(() => void loadNotifications(), PANEL_POLL_MS);

    return () => {
      cancelled = true;
      clearInterval(id);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, isAuthenticated, isSessionReady]);

  // ── Click-outside to close ───────────────────────────────────────────────
  useEffect(() => {
    const handler = (event: MouseEvent) => {
      if (!panelRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  if (!isAuthenticated || !isSessionReady) {
    return null;
  }

  const unread = count ?? 0;

  return (
    <div ref={panelRef} style={{ position: "relative" }}>
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`Notifications${unread > 0 ? `, ${unread} unread` : ""}`}
        style={{
          position: "relative",
          border: "1px solid var(--border)",
          background: open ? "var(--accent-faint)" : "var(--bg-card)",
          color: "var(--ink-2)",
          borderRadius: "999px",
          padding: "0.4rem 0.75rem",
          display: "inline-flex",
          alignItems: "center",
          gap: "0.5rem",
          transition: "background 0.15s, border-color 0.15s, color 0.15s",
        }}
      >
        <span aria-hidden="true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22zm7-6V11a7 7 0 0 0-5-6.71V3a2 2 0 1 0-4 0v1.29A7 7 0 0 0 5 11v5l-2 2v1h18v-1l-2-2z"/>
          </svg>
        </span>
        <span style={{ fontSize: "0.8125rem", fontWeight: 500 }}>Alerts</span>
        {unread > 0 && (
          <span
            style={{
              minWidth: 18,
              height: 18,
              padding: "0 0.35rem",
              borderRadius: 999,
              background: "var(--warn)",
              color: "#fff",
              fontSize: "0.6875rem",
              fontWeight: 700,
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            {unread}
          </span>
        )}
      </button>

      {open && (
        <Card
          style={{
            position: "absolute",
            right: 0,
            left: "auto",
            top: "calc(100% + 0.6rem)",
            width: 360,
            maxWidth: "min(360px, calc(100vw - 2rem))",
            zIndex: 120,
            boxShadow: "var(--shadow-lg)",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: "1rem", marginBottom: "1rem" }}>
            <h2 style={{ fontSize: "1rem" }}>Notifications</h2>
            {loading && <Spinner size={16} />}
          </div>

          {error && <p style={{ color: "var(--warn)", fontSize: "0.875rem", marginBottom: "0.75rem" }}>{error}</p>}

          {!loading && items.length === 0 && !error && (
            <p style={{ color: "var(--ink-muted)", fontSize: "0.875rem" }}>No unread updates.</p>
          )}

          <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem", maxHeight: 360, overflowY: "auto" }}>
            {items.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={async () => {
                  if (!item.isRead) {
                    try {
                      await markNotificationRead(item.id);
                      setItems((current) =>
                        current.map((notification) =>
                          notification.id === item.id ? { ...notification, isRead: true } : notification
                        )
                      );
                      setCount((current) => Math.max(0, (current ?? 0) - 1));
                    } catch {
                      setError("Could not mark notification as read.");
                    }
                  }
                }}
                style={{
                  textAlign: "left",
                  border: "1px solid var(--border)",
                  borderRadius: "var(--radius-sm)",
                  background: item.isRead ? "var(--bg-card)" : "var(--accent-faint)",
                  padding: "0.75rem",
                  color: "inherit",
                }}
              >
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "0.75rem" }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.35rem" }}>
                      <Badge tone={item.isRead ? "neutral" : "accent"}>{item.type.replace(/_/g, " ")}</Badge>
                      {!item.isRead && <span style={{ fontSize: "0.75rem", color: "var(--accent)" }}>Unread</span>}
                    </div>
                    <p style={{ fontSize: "0.875rem", color: "var(--ink-2)", lineHeight: 1.5 }}>{item.message}</p>
                  </div>
                </div>
                <p style={{ marginTop: "0.45rem", fontSize: "0.75rem", color: "var(--ink-muted)" }}>
                  {formatRelativeTime(item.createdAt)}
                </p>
              </button>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}

export default function AppLayout() {
  const navigate = useNavigate();
  const { isAuthenticated, isSessionReady, roles, user, logout } = useAuth();

  const handleLogout = async () => {
    await logout();
    navigate("/auth");
  };

  const displayName = user?.displayName
    || (user?.email ? user.email.split("@")[0] : null)
    || "Account";
  const isEmployer = roles.includes("EMPLOYER");
  const homeRoute = isEmployer ? "/employer" : "/seeker";

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
            maxWidth: "var(--page-max-width)",
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
            to={isAuthenticated ? homeRoute : "/"}
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
            {isAuthenticated && isSessionReady ? (
              <>
                {isEmployer ? (
                  <>
                    <NavLink to="/employer" end className="nav-link">
                      Dashboard
                    </NavLink>
                    <NavLink to="/employer/jobs/new" className="nav-link">
                      Post a job
                    </NavLink>
                  </>
                ) : (
                  <>
                    <NavLink to="/seeker" end className="nav-link">
                      Dashboard
                    </NavLink>
                    <NavLink to="/jobs" className="nav-link">
                      Browse jobs
                    </NavLink>
                    <NavLink to="/seeker/matches" className="nav-link">
                      Matches
                    </NavLink>
                    <NavLink to="/seeker/applications" className="nav-link">
                      Applications
                    </NavLink>
                    <NavLink to="/seeker/profile" className="nav-link">
                      Profile
                    </NavLink>
                  </>
                )}

                <NotificationBell />

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
            ) : isAuthenticated && !isSessionReady ? (
              <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
                <Spinner size={18} />
                <span style={{ fontSize: "0.875rem", color: "var(--ink-muted)" }}>Restoring session</span>
              </div>
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
