import { Link } from "react-router-dom";
import { Button } from "../components/ui";

export default function NotFound() {
  return (
    <div
      style={{
        minHeight: "calc(100vh - 56px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "2rem",
        textAlign: "center",
      }}
    >
      <div>
        <p
          style={{
            fontFamily: "var(--font-display)",
            fontSize: "5rem",
            fontWeight: 700,
            color: "var(--border)",
            lineHeight: 1,
            marginBottom: "1rem",
          }}
        >
          404
        </p>
        <h1 style={{ marginBottom: "0.5rem" }}>Page not found</h1>
        <p style={{ color: "var(--ink-muted)", marginBottom: "1.5rem" }}>
          The page you're looking for doesn't exist.
        </p>
        <Link to="/">
          <Button variant="secondary">Go home</Button>
        </Link>
      </div>
    </div>
  );
}
