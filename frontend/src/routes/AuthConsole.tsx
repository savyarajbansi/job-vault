import { useState } from "react";

import {
  AuthTokensResponse,
  AuthUser,
  getAccessToken,
  login,
  logout,
  refresh,
  whoami
} from "../api/auth";

const initialCreds = { email: "", password: "" };
const AUTH_RECOVERY_CODE = "ERR_AUTH_003";

function toUserMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : "Request failed.";
  if (message.includes(AUTH_RECOVERY_CODE)) {
    return "Your session has ended. Please sign in again.";
  }
  return message;
}

export default function AuthConsole() {
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
      setStatus("Signed in.");
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
      setStatus("No active session found.");
      setBusy(false);
      return;
    }

    setStatus("Logging out...");
    try {
      await logout();
      clearSessionState();
      setStatus("Logged out.");
    } catch (err) {
      setError(toUserMessage(err));
      setStatus(null);
    } finally {
      setBusy(false);
    }
  };

  const token = auth?.accessToken ?? getAccessToken();
  const tokenPreview = token ? `${token.slice(0, 24)}${token.length > 24 ? "..." : ""}` : "n/a";

  return (
    <main>
      <h1>Auth</h1>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void handleLogin();
        }}
      >
        <p>
          <label htmlFor="email">Email</label>
          <br />
          <input
            id="email"
            type="email"
            value={credentials.email}
            onChange={(event) =>
              setCredentials({ ...credentials, email: event.target.value })
            }
          />
        </p>
        <p>
          <label htmlFor="password">Password</label>
          <br />
          <input
            id="password"
            type="password"
            value={credentials.password}
            onChange={(event) =>
              setCredentials({ ...credentials, password: event.target.value })
            }
          />
        </p>
        <p>
          <button type="submit" disabled={busy}>
            Sign in
          </button>{" "}
          <button type="button" onClick={() => void handleRefresh()} disabled={busy}>
            Refresh
          </button>{" "}
          <button type="button" onClick={() => void handleWhoAmI()} disabled={busy}>
            Who am I
          </button>{" "}
          <button type="button" onClick={() => void handleLogout()} disabled={busy}>
            Logout
          </button>
        </p>
      </form>

      <section>
        <h2>Session</h2>
        <pre>Access token: {tokenPreview}</pre>
        <pre>Expires at: {auth?.accessTokenExpiresAt ?? "n/a"}</pre>
        <pre>Refresh expiry: {auth?.refreshTokenExpiresAt ?? "n/a"}</pre>
      </section>

      <section>
        <h2>Profile</h2>
        <pre>User ID: {profile?.id ?? "n/a"}</pre>
        <pre>Roles: {profile?.roles?.join(", ") ?? "n/a"}</pre>
      </section>

      {status && <p>{status}</p>}
      {error && <p>{error}</p>}
    </main>
  );
}
