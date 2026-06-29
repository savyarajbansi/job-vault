import { useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../api/authContext";
import { Button } from "../components/ui";

export default function Home() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    if (isAuthenticated) navigate("/seeker", { replace: true });
  }, [isAuthenticated, navigate]);

  return (
    <>
      <style>{`
        @keyframes fadeUp {
          from { opacity: 0; transform: translateY(14px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .hero-1 { animation: fadeUp 0.5s cubic-bezier(0.16,1,0.3,1) 0.1s both; }
        .hero-2 { animation: fadeUp 0.5s cubic-bezier(0.16,1,0.3,1) 0.2s both; }
        .hero-3 { animation: fadeUp 0.5s cubic-bezier(0.16,1,0.3,1) 0.32s both; }
        .hero-4 { animation: fadeUp 0.5s cubic-bezier(0.16,1,0.3,1) 0.42s both; }
      `}</style>

      <div
        style={{
          minHeight: "calc(100vh - 56px)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "3rem 1.5rem",
        }}
      >
        <div style={{ maxWidth: 680, textAlign: "center" }}>
          {/* Tag */}
          <div className="hero-1" style={{ marginBottom: "1.25rem" }}>
            <span
              style={{
                display: "inline-block",
                padding: "0.375rem 1rem",
                background: "var(--accent-faint)",
                color: "var(--accent)",
                borderRadius: "999px",
                fontSize: "0.8125rem",
                fontWeight: 600,
                letterSpacing: "0.04em",
                textTransform: "uppercase",
              }}
            >
              Intelligent job matching
            </span>
          </div>

          {/* Headline */}
          <h1
            className="hero-2"
            style={{
              marginBottom: "1rem",
              fontSize: "clamp(2rem, 5vw, 3.25rem)",
              lineHeight: 1.1,
              letterSpacing: "-0.02em",
            }}
          >
            Your resume,{" "}
            <span style={{ color: "var(--accent)", fontStyle: "italic" }}>matched precisely</span>
            {" "}to the right roles.
          </h1>

          {/* Sub */}
          <p
            className="hero-3"
            style={{
              fontSize: "1.0625rem",
              color: "var(--ink-muted)",
              lineHeight: 1.65,
              maxWidth: 480,
              margin: "0 auto 2rem",
            }}
          >
            Upload your resume and JobVault identifies your skills, ranks open positions by fit,
            and shows exactly what's holding you back from a perfect match.
          </p>

          {/* CTAs */}
          <div className="hero-4" style={{ display: "flex", justifyContent: "center", gap: "0.75rem", flexWrap: "wrap" }}>
            <Link to="/auth">
              <Button size="lg">Get started</Button>
            </Link>
            <Link to="/jobs">
              <Button variant="secondary" size="lg">Browse open roles</Button>
            </Link>
            <Link to="/auth">
              <Button variant="ghost" size="lg">Sign in</Button>
            </Link>
          </div>

          {/* Feature pills */}
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              flexWrap: "wrap",
              gap: "0.625rem",
              marginTop: "3rem",
            }}
            className="hero-4"
          >
            {[
              "Resume parsing",
              "Skill gap analysis",
              "Ranked job matches",
              "Application tracking",
            ].map((f) => (
              <span
                key={f}
                style={{
                  padding: "0.375rem 0.875rem",
                  background: "var(--bg-card)",
                  border: "1px solid var(--border)",
                  borderRadius: "999px",
                  fontSize: "0.8125rem",
                  color: "var(--ink-2)",
                }}
              >
                {f}
              </span>
            ))}
          </div>
        </div>
      </div>
    </>
  );
}