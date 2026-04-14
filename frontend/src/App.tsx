import { useState } from "react";
import {
  AuthTokensResponse,
  AuthUser,
  login,
  logout,
  refresh,
  whoami
} from "./api/auth";

const initialCreds = { email: "", password: "" };

export default function App() {
  const [credentials, setCredentials] = useState(initialCreds);
  const [auth, setAuth] = useState<AuthTokensResponse | null>(null);
  const [profile, setProfile] = useState<AuthUser | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleLogin = async () => {
    setBusy(true);
    setError(null);
    setStatus("Signing in...");
    try {
      const result = await login(credentials.email, credentials.password);
      setAuth(result);
      setProfile(result.user);
      setStatus("Signed in. Access token is stored in memory only.");
    } catch (err) {
      setError((err as Error).message);
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
      setError((err as Error).message);
      setStatus(null);
    } finally {
      setBusy(false);
    }
  };

  const handleWhoAmI = async () => {
    if (!auth?.accessToken) {
      setError("No access token in memory.");
      return;
    }
    setBusy(true);
    setError(null);
    setStatus("Fetching profile...");
    try {
      const result = await whoami(auth.accessToken);
      setProfile(result);
      setStatus("Profile loaded.");
    } catch (err) {
      setError((err as Error).message);
      setStatus(null);
    } finally {
      setBusy(false);
    }
  };

  const handleLogout = async () => {
    if (!auth?.accessToken) {
      setAuth(null);
      setProfile(null);
      return;
    }
    setBusy(true);
    setError(null);
    setStatus("Logging out...");
    try {
      await logout(auth.accessToken);
      setAuth(null);
      setProfile(null);
      setStatus("Logged out. Refresh cookie cleared.");
    } catch (err) {
      setError((err as Error).message);
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
          never touch local storage, and refresh lives in a secure cookie.
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
          <p className="mono">Access token (memory only)</p>
          <p className="mono">
            {auth?.accessToken ? auth.accessToken.slice(0, 36) + "..." : "—"}
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
          {status && <div className="status">{status}</div>}
          {error && <div className="status">{error}</div>}
        </div>
      </section>
    </div>
  );
}
