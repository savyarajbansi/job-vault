import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getMyApplications, withdrawApplication } from "../api/seeker";
import type { SeekerApplicationItem } from "../api/seeker";
import type { ApplicationStatus } from "../api/employer";
import { Alert, Badge, Button, Card, Spinner } from "../components/ui";

function statusTone(status: ApplicationStatus): "neutral" | "success" | "warn" | "accent" {
  if (status === "ACCEPTED") return "success";
  if (status === "REJECTED" || status === "WITHDRAWN") return "warn";
  if (status === "SUBMITTED" || status === "UNDER_REVIEW") return "accent";
  return "neutral";
}

function statusLabel(status: ApplicationStatus): string {
  const map: Record<ApplicationStatus, string> = {
    DRAFT: "Draft",
    SUBMITTED: "Applied",
    UNDER_REVIEW: "Under review",
    ACCEPTED: "Accepted",
    REJECTED: "Not selected",
    WITHDRAWN: "Withdrawn",
  };
  return map[status];
}

function formatDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

function withdrawError(error: unknown): string {
  if (error instanceof Error && error.message.includes("ERR_AUTH_003")) {
    return "Your session has ended. Please sign in again.";
  }
  return "Could not withdraw this application. Please try again.";
}

export default function SeekerApplications() {
  const [items, setItems] = useState<SeekerApplicationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await getMyApplications());
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      setError(
        msg.includes("ERR_AUTH_003")
          ? "Your session has ended. Please sign in again."
          : "Could not load your applications."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const handleWithdraw = async (id: string) => {
    setBusyId(id);
    setActionError(null);
    try {
      await withdrawApplication(id);
      await load();
    } catch (err) {
      setActionError(withdrawError(err));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <main style={{ maxWidth: 900, margin: "0 auto", padding: "2rem 1.5rem 3rem" }}>
      <div style={{ marginBottom: "1.5rem" }}>
        <h1 style={{ marginBottom: "0.375rem" }}>My applications</h1>
        <p style={{ color: "var(--ink-muted)" }}>
          Every job you've applied to or saved as a draft, with its current status.
        </p>
      </div>

      {error && <Alert tone={error.includes("session") ? "info" : "error"}>{error}</Alert>}
      {actionError && (
        <div style={{ marginTop: "1rem" }}>
          <Alert tone="error">{actionError}</Alert>
        </div>
      )}

      {loading ? (
        <div style={{ display: "flex", justifyContent: "center", padding: "4rem 0" }}>
          <Spinner size={32} />
        </div>
      ) : items.length === 0 ? (
        <Card>
          <p style={{ color: "var(--ink-muted)", marginBottom: "1rem" }}>
            You haven't applied to anything yet.
          </p>
          <Link to="/seeker/matches">
            <Button>Browse matches</Button>
          </Link>
        </Card>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
          {items.map((item) => (
            <Card key={item.id}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "1rem" }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", marginBottom: "0.35rem", flexWrap: "wrap" }}>
                    {item.jobId ? (
                      <Link to={`/jobs/${item.jobId}`} style={{ textDecoration: "none" }}>
                        <h2 style={{ fontSize: "1.0625rem", margin: 0, color: "var(--ink)" }}>
                          {item.jobTitle ?? "Job no longer available"}
                        </h2>
                      </Link>
                    ) : (
                      <h2 style={{ fontSize: "1.0625rem", margin: 0 }}>
                        {item.jobTitle ?? "Job no longer available"}
                      </h2>
                    )}
                    <Badge tone={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
                  </div>
                  {item.companyName && (
                    <p style={{ fontSize: "0.875rem", color: "var(--ink-2)", marginBottom: "0.35rem" }}>
                      {item.companyName}
                    </p>
                  )}
                  <p style={{ fontSize: "0.8125rem", color: "var(--ink-muted)" }}>
                    {item.status === "DRAFT" ? "Not yet submitted" : `Applied ${formatDate(item.submittedAt)}`}
                  </p>
                </div>

                {(item.status === "SUBMITTED" || item.status === "UNDER_REVIEW") && (
                  <Button
                    variant="secondary"
                    size="sm"
                    loading={busyId === item.id}
                    onClick={() => void handleWithdraw(item.id)}
                  >
                    Withdraw
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </main>
  );
}
