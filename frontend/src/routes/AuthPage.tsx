import { useState, FormEvent, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../api/authContext";
import { ApiResponseError, request } from "../api/client";
import { AuthTokensResponse } from "../api/auth";
import { Button, Input, Alert, Card, Spinner } from "../components/ui";

type Tab = "signin" | "register";
type Role = "JOB_SEEKER" | "EMPLOYER";

interface FieldErrors {
  email?: string;
  password?: string;
  displayName?: string;
  general?: string;
}

function parseFieldErrors(payload: unknown): FieldErrors {
  if (!payload || typeof payload !== "object") return {};
  const p = payload as Record<string, unknown>;
  const details = p["details"] as Record<string, unknown> | undefined;
  if (!details) {
    const msg = (p["message"] as string) ?? "Something went wrong.";
    return { general: msg };
  }
  const reason = details["reason"] as string | undefined;
  if (reason === "validation_failed") {
    const fields = (details["fields"] as Record<string, string>) ?? {};
    return {
      email: fields["email"],
      password: fields["password"],
      displayName: fields["displayName"],
      general: Object.values(fields).filter(Boolean)[0] ?? "Please check the form.",
    };
  }
  if (reason === "email_exists") return { email: "An account with this email already exists." };
  if (reason === "invalid_role") return { general: "Invalid role selected." };
  if (reason === "role_not_configured") {
    return { general: "Registration is temporarily unavailable because required roles are not configured." };
  }
  const msg = (p["message"] as string) ?? "Something went wrong.";
  return { general: msg };
}

async function registerUser(
  email: string,
  password: string,
  displayName: string,
  role: Role
): Promise<AuthTokensResponse> {
  return request<AuthTokensResponse>("/api/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email, password, displayName, role }),
  });
}

export default function AuthPage() {
  const navigate = useNavigate();
  const { login, setUser, isAuthenticated, isSessionReady, roles } = useAuth();
  const [tab, setTab] = useState<Tab>("signin");

  useEffect(() => {
    if (!isSessionReady || !isAuthenticated) {
      return;
    }
    navigate(roles.includes("EMPLOYER") ? "/employer" : "/seeker", { replace: true });
  }, [navigate, isAuthenticated, isSessionReady, roles]);

  // Sign-in state
  const [siEmail, setSiEmail] = useState("");
  const [siPassword, setSiPassword] = useState("");
  const [siErrors, setSiErrors] = useState<FieldErrors>({});
  const [siLoading, setSiLoading] = useState(false);

  // Register state
  const [regEmail, setRegEmail] = useState("");
  const [regPassword, setRegPassword] = useState("");
  const [regDisplay, setRegDisplay] = useState("");
  const [regRole, setRegRole] = useState<Role>("JOB_SEEKER");
  const [regErrors, setRegErrors] = useState<FieldErrors>({});
  const [regLoading, setRegLoading] = useState(false);

  const handleSignIn = async (e: FormEvent) => {
    e.preventDefault();
    setSiErrors({});
    if (!siEmail) { setSiErrors({ email: "Email is required." }); return; }
    if (!siPassword) { setSiErrors({ password: "Password is required." }); return; }
    setSiLoading(true);
    try {
      const result = await login(siEmail, siPassword);
      setUser({
        id: result.user.id,
        email: siEmail,
        displayName: null,
        roles: result.user.roles,
      });
      navigate(result.user.roles.includes("EMPLOYER") ? "/employer" : "/seeker", { replace: true });
    } catch (err) {
      const raw = err instanceof Error ? err.message : "";
      if (raw.includes("ERR_AUTH_002") || raw.toLowerCase().includes("invalid")) {
        setSiErrors({ general: "Incorrect email or password. Please try again." });
      } else {
        setSiErrors({ general: raw || "Sign in failed. Please try again." });
      }
    } finally {
      setSiLoading(false);
    }
  };

  const handleRegister = async (e: FormEvent) => {
    e.preventDefault();
    setRegErrors({});
    if (!regEmail) { setRegErrors({ email: "Email is required." }); return; }
    if (!regPassword || regPassword.length < 8) {
      setRegErrors({ password: "Password must be at least 8 characters." });
      return;
    }
    setRegLoading(true);
    try {
      await registerUser(regEmail, regPassword, regDisplay, regRole);
      const result = await login(regEmail, regPassword);
      setUser({
        id: result.user.id,
        email: regEmail,
        displayName: regDisplay || null,
        roles: result.user.roles,
      });
      navigate(result.user.roles.includes("EMPLOYER") ? "/employer" : "/seeker", { replace: true });
    } catch (err) {
      const payload = err instanceof ApiResponseError ? err.payload : undefined;
      const parsed = parseFieldErrors(payload);
      if (Object.keys(parsed).length > 0) {
        setRegErrors(parsed);
      } else {
        const msg = err instanceof Error ? err.message : "";
        setRegErrors({ general: msg || "Registration failed. Please try again." });
      }
    } finally {
      setRegLoading(false);
    }
  };

  return (
    <>
      <style>{`
        @keyframes fadeUp {
          from { opacity: 0; transform: translateY(12px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .auth-card { animation: fadeUp 0.45s cubic-bezier(0.16,1,0.3,1) both; }
        .tab-btn {
          flex: 1;
          padding: 0.625rem 1rem;
          font-family: var(--font-body);
          font-size: 0.9375rem;
          font-weight: 500;
          border: none;
          background: transparent;
          color: var(--ink-muted);
          cursor: pointer;
          border-bottom: 2px solid transparent;
          transition: color 0.15s, border-color 0.15s;
        }
        .tab-btn.active {
          color: var(--accent);
          border-bottom-color: var(--accent);
        }
        .tab-btn:hover:not(.active) { color: var(--ink); }
        .role-card {
          flex: 1;
          padding: 0.875rem 1rem;
          border: 1.5px solid var(--border);
          border-radius: var(--radius-sm);
          background: transparent;
          cursor: pointer;
          text-align: left;
          transition: border-color 0.15s, background 0.15s;
          display: flex;
          flex-direction: column;
          gap: 0.25rem;
          font-family: var(--font-body);
        }
        .role-card.selected {
          border-color: var(--accent);
          background: var(--accent-faint);
        }
        .role-card:hover:not(.selected) { border-color: var(--ink-muted); }
      `}</style>

      {!isSessionReady ? (
        <div
          style={{
            minHeight: "calc(100vh - 56px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "2rem 1rem",
          }}
        >
          <Spinner size={28} />
        </div>
      ) : (
      <div
        style={{
          minHeight: "calc(100vh - 56px)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "2rem 1rem",
        }}
      >
        <div style={{ width: "100%", maxWidth: 440 }}>
          <div style={{ textAlign: "center", marginBottom: "2rem" }} className="auth-card">
            <h1 style={{ marginBottom: "0.5rem" }}>
              {tab === "signin" ? "Welcome back" : "Create an account"}
            </h1>
            <p style={{ color: "var(--ink-muted)", fontSize: "0.9375rem" }}>
              {tab === "signin"
                ? "Sign in to continue to JobVault."
                : "Join JobVault and find your next opportunity."}
            </p>
          </div>

          <Card style={{ animationDelay: "0.05s" }} padded={false}>
            <div style={{ display: "flex", borderBottom: "1px solid var(--border)" }}>
              <button
                className={`tab-btn ${tab === "signin" ? "active" : ""}`}
                onClick={() => { setTab("signin"); setSiErrors({}); }}
              >
                Sign in
              </button>
              <button
                className={`tab-btn ${tab === "register" ? "active" : ""}`}
                onClick={() => { setTab("register"); setRegErrors({}); }}
              >
                Register
              </button>
            </div>

            <div style={{ padding: "1.75rem" }}>
              {tab === "signin" && (
                <form onSubmit={(e) => void handleSignIn(e)} noValidate>
                  <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
                    {siErrors.general && <Alert tone="error">{siErrors.general}</Alert>}
                    <Input
                      label="Email address"
                      type="email"
                      autoComplete="email"
                      value={siEmail}
                      onChange={(e) => setSiEmail(e.target.value)}
                      error={siErrors.email}
                      placeholder="you@example.com"
                      required
                    />
                    <Input
                      label="Password"
                      type="password"
                      autoComplete="current-password"
                      value={siPassword}
                      onChange={(e) => setSiPassword(e.target.value)}
                      error={siErrors.password}
                      placeholder="••••••••"
                      required
                    />
                    <Button type="submit" fullWidth loading={siLoading} size="lg" style={{ marginTop: "0.25rem" }}>
                      Sign in
                    </Button>
                  </div>
                </form>
              )}

              {tab === "register" && (
                <form onSubmit={(e) => void handleRegister(e)} noValidate>
                  <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
                    {regErrors.general && <Alert tone="error">{regErrors.general}</Alert>}
                    <Input
                      label="Full name"
                      type="text"
                      autoComplete="name"
                      value={regDisplay}
                      onChange={(e) => setRegDisplay(e.target.value)}
                      error={regErrors.displayName}
                      placeholder="Jordan Lee"
                    />
                    <Input
                      label="Email address"
                      type="email"
                      autoComplete="email"
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      error={regErrors.email}
                      placeholder="you@example.com"
                      required
                    />
                    <Input
                      label="Password"
                      type="password"
                      autoComplete="new-password"
                      value={regPassword}
                      onChange={(e) => setRegPassword(e.target.value)}
                      error={regErrors.password}
                      placeholder="At least 8 characters"
                      hint="Minimum 8 characters"
                      required
                    />
                    <div style={{ display: "flex", flexDirection: "column", gap: "0.375rem" }}>
                      <span style={{ fontSize: "0.8125rem", fontWeight: 500, color: "var(--ink-2)", letterSpacing: "0.02em" }}>
                        I am a…
                      </span>
                      <div style={{ display: "flex", gap: "0.75rem" }}>
                        <button
                          type="button"
                          className={`role-card ${regRole === "JOB_SEEKER" ? "selected" : ""}`}
                          onClick={() => setRegRole("JOB_SEEKER")}
                        >
                          <span style={{ fontSize: "1.25rem" }}>🔍</span>
                          <span style={{ fontWeight: 600, fontSize: "0.9375rem", color: "var(--ink)" }}>Job Seeker</span>
                          <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", lineHeight: 1.4 }}>
                            Find opportunities that match your skills
                          </span>
                        </button>
                        <button
                          type="button"
                          className={`role-card ${regRole === "EMPLOYER" ? "selected" : ""}`}
                          onClick={() => setRegRole("EMPLOYER")}
                        >
                          <span style={{ fontSize: "1.25rem" }}>🏢</span>
                          <span style={{ fontWeight: 600, fontSize: "0.9375rem", color: "var(--ink)" }}>Employer</span>
                          <span style={{ fontSize: "0.8125rem", color: "var(--ink-muted)", lineHeight: 1.4 }}>
                            Post jobs and discover candidates
                          </span>
                        </button>
                      </div>
                    </div>
                    <Button type="submit" fullWidth loading={regLoading} size="lg" style={{ marginTop: "0.25rem" }}>
                      Create account
                    </Button>
                  </div>
                </form>
              )}
            </div>
          </Card>

          <p style={{ textAlign: "center", marginTop: "1.25rem", fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
            By continuing you agree to our terms of service and privacy policy.
          </p>
        </div>
      </div>
      )}
    </>
  );
}
