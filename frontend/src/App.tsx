import { useState } from "react";
import {
  AuthTokensResponse,
  AuthUser,
  getAccessToken,
  login,
  logout,
  refresh,
  whoami
} from "./api/auth";

const initialCreds = { email: "", password: "" };
const AUTH_RECOVERY_CODE = "ERR_AUTH_003";

function toUserMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : "Request failed.";
  if (message.includes(AUTH_RECOVERY_CODE)) {
    return "Your session has ended. Please sign in again.";
  }
  return message;
}

export default function App() {
  const [credentials, setCredentials] = useState(initialCreds);
  const [auth, setAuth] = useState<AuthTokensResponse | null>(null);
  const [profile, setProfile] = useState<AuthUser | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const clearSessionState = () => {
    setAuth(null);
    setProfile(null);
  };

  const handleLogin = async () => {
    setBusy(true);
    setError(null);
    setStatus("Signing in...");
    try {
      const result = await login(credentials.email, credentials.password);
      setAuth(result);
      setProfile(result.user);
      setStatus("Signed in. Access token is stored in localStorage.");
    } catch (err) {
      setError(toUserMessage(err));
      setStatus(null);
    } finally {
      setBusy(false);
    }
  };

  const handleRefresh = async () => {
    setBusy(true);
    setError(null);
    setStatus("Refreshing access token...");
    try {
      const result = await refresh();
      setAuth(result);
      setProfile(result.user);
      setStatus("Access token refreshed.");
    } catch (err) {
      setError(toUserMessage(err));
      setStatus(null);
    } finally {
      setBusy(false);
    }
  };

  const handleWhoAmI = async () => {
    if (!getAccessToken()) {
      clearSessionState();
      setStatus(null);
      setError("Sign in to load your profile.");
      return;
    }
    setBusy(true);
    setError(null);
    setStatus("Fetching profile...");
    try {
      const result = await whoami();
      setProfile(result);
      setStatus("Profile loaded.");
    } catch (err) {
      setError(toUserMessage(err));
      setStatus(null);
    } finally {
      setBusy(false);
    }
  };

  const handleLogout = async () => {
    setBusy(true);
    setError(null);
    if (!getAccessToken()) {
      clearSessionState();
      setStatus("No active session found. Cleared local sign-in state.");
      setBusy(false);
      return;
    }

    setStatus("Logging out...");
    try {
      await logout();
      clearSessionState();
      setStatus("Logged out and cleared local session.");
    } catch (err) {
      setError(toUserMessage(err));
      setStatus(null);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="app">
      <section className="hero">
        <h1>JobVault Auth Console</h1>
        <p>
          A focused workspace for testing JWT login, refresh, and logout. Tokens
          are persisted in localStorage, and refresh lives in a secure cookie.
        </p>
      </section>

      <section className="grid">
        <div className="card">
          <h2>Sign in</h2>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            value={credentials.email}
            placeholder="user@example.com"
            onChange={(event) =>
              setCredentials({ ...credentials, email: event.target.value })
            }
          />
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={credentials.password}
            placeholder="Enter password"
            onChange={(event) =>
              setCredentials({ ...credentials, password: event.target.value })
            }
          />
          <div className="actions" style={{ marginTop: 16 }}>
            <button onClick={handleLogin} disabled={busy}>
              Sign in
            </button>
            <button className="secondary" onClick={handleRefresh} disabled={busy}>
              Refresh token
            </button>
            <button className="ghost" onClick={handleLogout} disabled={busy}>
              Logout
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Session snapshot</h2>
          <p className="mono">Access token (localStorage)</p>
          <p className="mono">
            {(auth?.accessToken ?? getAccessToken())
              ? (auth?.accessToken ?? getAccessToken())!.slice(0, 36) + "..."
              : "—"}
          </p>
          <p className="mono">Expires at: {auth?.accessTokenExpiresAt ?? "—"}</p>
          <p className="mono">
            Refresh expiry: {auth?.refreshTokenExpiresAt ?? "—"}
          </p>
          <div className="actions" style={{ marginTop: 16 }}>
            <button className="ghost" onClick={handleWhoAmI} disabled={busy}>
              Who am I
            </button>
          </div>
        </div>

        <div className="card">
          <h2>Profile</h2>
          <p className="mono">User ID: {profile?.id ?? "—"}</p>
          <p className="mono">Roles: {profile?.roles?.join(", ") ?? "—"}</p>
          {status && (
            <div className="status" role="status" aria-live="polite" aria-atomic="true">
              {status}
            </div>
          )}
          {error && (
            <div className="status" role="alert" aria-live="assertive" aria-atomic="true">
              {error}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
